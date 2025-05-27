package org.ivy.settlement.core.chain.consensus.fastpath;


import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.ledger.StateLedger;
import org.ivy.settlement.core.chain.ledger.model.Block;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.ivy.settlement.core.chain.consensus.fastpath.BlockCheckContext.BlockCheckStatus.COMMIT_BLOCK;
import static org.ivy.settlement.core.chain.consensus.fastpath.BlockCheckContext.BlockCheckStatus.VOTING_BLOCK;

/**
 * description:
 * @author carrot
 */
class BlockCheckContext {

    static final Logger logger = LoggerFactory.getLogger("fast_path");

    public enum BlockCommitType {
        NORMAL,
        SYNC
    }

    public enum BlockCheckStatus {
        VOTING_BLOCK,
        COMMIT_BLOCK
    }

    // pk to sign
    volatile Map<ByteArrayWrapper, Signature> sameHashSigns = new HashMap<>();

    BiConsumer<BlockCheckContext, BlockCommitType> commitCallback;

    volatile EpochState epochState;

    volatile long currentConsensusNumber;

    volatile byte[] currentConsensusHash;

    volatile BlockCheckStatus currentStatus;

    public BlockCheckContext(BiConsumer<BlockCheckContext, BlockCommitType> commitCallback, long currentConsensusNumber) {
        this.commitCallback = commitCallback;
        this.currentConsensusNumber = currentConsensusNumber;
        this.currentStatus = COMMIT_BLOCK;
    }

    public void reset(StateLedger stateLedger, Block block) {
        this.currentConsensusNumber = block.getNumber();
        this.currentConsensusHash = block.getHash();
        var latestEpochState = stateLedger.getLatestLedger().getCurrentEpochState();
        if (latestEpochState.getEpoch() == block.getEpoch()) {
            this.epochState = latestEpochState;
        } else {
            this.epochState = stateLedger.getEpochState(block.getEpoch());
        }

        this.currentStatus = VOTING_BLOCK;
    }

    public void checkSign(ByteArrayWrapper pk, long number,  byte[] hash, Signature sign) {
        if (this.currentStatus != VOTING_BLOCK) {
            return;
        };

        if (number != this.currentConsensusNumber) {
            logger.info("check sign for number is {}, current check number is {}", number, this.currentConsensusNumber);
            return;
        }

        if (!this.epochState.getValidatorVerifier().containPublicKey(pk)) {
            logger.warn("[{}] is not a validator in this epoch, ignore!", pk);
            return;
        }

        if (Arrays.equals(hash, this.currentConsensusHash)) {
            var signature = this.sameHashSigns.get(pk);
            if (signature == null) {
                var verifySignRes = epochState.getValidatorVerifier().verifySignature(pk, hash, sign);
                if (!verifySignRes.isSuccess()) {
                    logger.warn("node[{}] for block [{}-[{}] verify sign error", pk, number, Hex.toHexString(hash));
                    return;
                }
                //logger.info("will put node[{}] block[{}]-[{}] sign", pk, number, Hex.toHexString(hash));
                sameHashSigns.put(pk, sign);
            } else {
                //repeat sign
                logger.info("receive repeat sign from [{}] block[{}] sign", pk, number);
                return;
            }

            var verifyPowerRes = this.epochState.getValidatorVerifier().checkVotingPower(sameHashSigns.keySet());
            if (verifyPowerRes.isSuccess()) {
                this.commit(this.currentConsensusNumber);
            }
        } else {
            logger.error("un except hash, current check hash is [{}], but receive[{}]", Hex.toHexString(this.currentConsensusHash), Hex.toHexString(hash));
        }
    }

    void commit(long number) {
        if (number == this.currentConsensusNumber) {
            //common condition
            this.commitCallback.accept(this, BlockCommitType.NORMAL);
            this.currentStatus = COMMIT_BLOCK;
            this.sameHashSigns.clear();
        } else {
            logger.info("context do commit for number:{}, but receive:{}, ignore", this.currentConsensusNumber, number);
        }
    }

    void syncCommit(long number) {
        if (number >= this.currentConsensusNumber) {
            //sync condition
            this.currentStatus = COMMIT_BLOCK;
            this.currentConsensusNumber = number;
            this.sameHashSigns.clear();
            this.commitCallback.accept(this, BlockCommitType.SYNC);
        } else {
            logger.info("context do sync commit for number:{}, but receive:{}, ignore", this.currentConsensusNumber, number);
        }
    }

    public Map<ByteArrayWrapper, Signature> getSameHashSigns() {
        return sameHashSigns;
    }

    public long getCurrentCheckNumber() {
        return this.currentConsensusNumber;
    }

    public byte[] getCurrentCheckHash() {
        return this.currentConsensusHash;
    }

    public boolean isCommit() {
        return currentStatus == COMMIT_BLOCK;
    }

    @Override
    public String toString() {
        return "BlockCheckContext{" +
                "currentCheckNumber=" + this.currentConsensusNumber +
                ", currentStatus=" + currentStatus +
                '}';
    }
}
