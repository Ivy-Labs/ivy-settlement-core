package org.ivy.settlement.core.chain.consensus.fastpath.impl;

import org.ivy.settlement.ethereum.model.settlement.FastPathBlock;
import org.ivy.settlement.ethereum.model.settlement.FastPathBlocks;
import org.ivy.settlement.core.chain.consensus.fastpath.SettlementBlobGenerator;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.ledger.StateLedger;
import org.ivy.settlement.core.chain.ledger.model.Block;
import org.ivy.settlement.core.chain.ledger.model.SettlementBatch;
import org.ivy.settlement.core.chain.ledger.model.ValidatorPublicKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;

import static org.ivy.settlement.infrastructure.anyhow.Assert.ensure;

/**
 * description:
 * @author carrot
 */
public class SettlementBlobGeneratorImpl implements SettlementBlobGenerator {

    static final Logger logger = LoggerFactory.getLogger("fast_path");

    StateLedger stateLedger;

    BlockAccumulationContext blockAccumulationContext;

    ArrayBlockingQueue<SettlementBatch> toBeSignedQueue;

    public SettlementBlobGeneratorImpl(StateLedger stateLedger) {
        this.stateLedger = stateLedger;
        var latestWillSignedBlob = this.stateLedger.getBlobByNumber(this.stateLedger.getLatestWillSignedBlobNum());
        this.blockAccumulationContext = new BlockAccumulationContext(latestWillSignedBlob);
        this.toBeSignedQueue = new ArrayBlockingQueue<>(512);
    }

    class BlockAccumulationContext {

        EpochState epochState;

        long number;

        long timestamp;

        long endBlockNumber;

        int accumulationSize;

        int accumulationTime;

        TreeMap<Long, FastPathBlock> chainTables;

        BlockAccumulationContext(SettlementBatch blob) {
            this.epochState = SettlementBlobGeneratorImpl.this.stateLedger.getEpochState(blob.getBornEpoch());
            this.number = blob.getNumber();
            this.endBlockNumber = blob.getEndBlockNumber() + 1;
            this.timestamp = blob.getTimestamp();
            this.chainTables = new TreeMap<>();
            logger.info("start init accumulation context: epoch[{}], number[{}], endBlockNumber[{}]!", this.epochState.getEpoch(), this.number, this.endBlockNumber);
        }

        SettlementBatch accumulate(Block block) {
            ensure(block.getNumber() == this.endBlockNumber, "accumulate error, except {}, receive {}", this.endBlockNumber, block.getNumber());
            List<ValidatorPublicKeyInfo> validatorPublicKeyInfos = Collections.emptyList();
            if (this.epochState.getEpoch() != block.getEpoch()) {
                var nextEpoch = SettlementBlobGeneratorImpl.this.stateLedger.getEpochState(block.getEpoch());
                if (this.epochState.getEthEpoch() != nextEpoch.getEthEpoch()) {
                    validatorPublicKeyInfos = nextEpoch.getValidatorVerifier().getPk2ValidatorInfo().values().stream().toList();
                    this.epochState = nextEpoch;
                    var settlementBlob = this.buildSettlementBlob(validatorPublicKeyInfos);
                    this.accept(block);
                    return settlementBlob;
                } else {
                    this.epochState = nextEpoch;
                    this.accept(block);
                    return this.buildSettlementBlob(validatorPublicKeyInfos);
                }
            } else {
                this.accept(block);
                if ((this.accumulationTime >= this.epochState.getEmptyBlockTimes() && this.accumulationSize > 0)
                        ||
                        this.accumulationSize > this.epochState.getAccumulateSize()) {
                    return this.buildSettlementBlob(validatorPublicKeyInfos);
                } else {
                    return null;
                }
            }
        }

        SettlementBatch buildSettlementBlob(List<ValidatorPublicKeyInfo> validatorPublicKeyInfos) {
            //var endBlockNum = epochChange? this.endBlockNumber - 1 : this.endBlockNumber;
            this.number++;
            var blob = new SettlementBatch(this.epochState.getEpoch(), this.epochState.getEthEpoch(), this.number, this.endBlockNumber - 1, this.timestamp, new FastPathBlocks(this.chainTables), validatorPublicKeyInfos);
            this.chainTables.clear();
            this.accumulationSize = 0;
            this.accumulationTime = 0;
            return blob;
        }

        void accept(Block block) {
            this.endBlockNumber++;
            this.timestamp = block.getTimestamp();
            this.accumulationTime++;
            var settlementBlockInfos = block.getSettlementBlockInfos().getSettlementBlockInfos();
            var settlementSize = settlementBlockInfos.values().stream().mapToInt(List::size).sum();
            this.accumulationSize += settlementSize;
            logger.info("accept block[{}], current accumulationSize[{}]", block.getNumber(), this.accumulationSize);
            if (settlementSize != 0) {
                chainTables.put(block.getHeight(), block.exportFastPathBlock());

            }
        }
    }

    public void start() {
        this.recoverWillSignedBlob();
        this.recoverBlobGen();
    }

    @Override
    public void stop() {

    }

    void recoverWillSignedBlob() {
        var start = this.stateLedger.getLatestConfirmBlobNum() + 1;
        var end = this.stateLedger.getLatestWillSignedBlobNum();
        logger.info("recover will signed blob, from {} to {}", start, end);

        try {
            for (var i = start; i <= end; i++) {
                var blob = this.stateLedger.getBlobByNumber(i);
                logger.info("recover will signed blob, put blob[{}]", blob.getNumber());
                this.toBeSignedQueue.put(blob);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    void recoverBlobGen() {
        var start = this.blockAccumulationContext.endBlockNumber;
        var end = this.stateLedger.getLatestBeExecutedNum();
        logger.info("recover blob gen, from {} to {}", start, end);
        for (var i = start; i <= end; i++) {
            var block = this.stateLedger.getBlockByNumber(i);
            logger.info("recover blob gen, accumulate block[{}]!", block.getNumber());
            this.accumulate(block);
        }
    }

    public void accumulate(Block block) {
            try {
                //logger.info("will accumulate block[{}]!", block.getNumber());
                var settlementBlob = this.blockAccumulationContext.accumulate(block);
                if (settlementBlob != null) {
                    logger.info("gen blob[{}]-[{}]", settlementBlob.getNumber(), Hex.toHexString(settlementBlob.getHash()));
                    this.toBeSignedQueue.put(settlementBlob);
                }
            } catch (Exception e) {
                logger.warn("accumulate blob error!", e);
                throw new RuntimeException(e);
            }

    }

    public SettlementBatch generateBlob(){
        try {
            return this.toBeSignedQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
