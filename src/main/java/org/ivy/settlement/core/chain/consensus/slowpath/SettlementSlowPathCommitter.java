package org.ivy.settlement.core.chain.consensus.slowpath;

import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecureKey;
import org.ivy.settlement.infrastructure.string.StringUtils;
import org.ivy.settlement.infrastructure.async.IrisSettlementWorker;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.consensus.fastpath.SettlementBlobGenerator;
import org.ivy.settlement.core.chain.consensus.slowpath.model.LocalBatchSignMsg;
import org.ivy.settlement.core.chain.ledger.StateLedger;
import org.ivy.settlement.core.chain.ledger.model.BatchSign;
import org.ivy.settlement.core.chain.ledger.model.SettlementBatch;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.core.chain.network.NetInvoker;
import org.ivy.settlement.core.chain.sync.SettlementChainsSyncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;

/**
 * description:
 * @author carrot
 */
public class  SettlementSlowPathCommitter {

    static final Logger logger = LoggerFactory.getLogger("slow_path");

    StateLedger stateLedger;

    SettlementChainsSyncer settlementChainsSyncer;

    NetInvoker netInvoker;

    SlowPathCommitCoordinator coordinator;

    volatile BatchCheckContext currentBatchCheckContext;

    SecureKey secureKey;

    Map<Long, Pair<SettlementBatch, CountDownLatch>> commitAwaitConditions;

    Map<Long, SettlementBatch> confirmBlobs;

    Map<Long, BatchSign> confirmBlobSigns;

    boolean test;

    int maxCommitBlobCache;

    SettlementBlobGenerator settlementBlobGenerator;

    SettlementBatchFinality settlementBatchFinality;

    public SettlementSlowPathCommitter(IrisCoreSystemConfig irisCoreSystemConfig, StateLedger stateLedger, SettlementBlobGenerator settlementBlobGenerator, SettlementChainsSyncer settlementChainsSyncer, SettlementBatchFinality settlementBatchFinality, NetInvoker netInvoker, boolean test) {
        this.stateLedger = stateLedger;
        this.settlementChainsSyncer = settlementChainsSyncer;
        this.netInvoker = netInvoker;
        this.secureKey = irisCoreSystemConfig.getMyKey();
        this.coordinator = new SlowPathCommitCoordinator(irisCoreSystemConfig, this);
        this.commitAwaitConditions = new HashMap<>();
        this.confirmBlobSigns = new ConcurrentHashMap<>();
        this.confirmBlobs = new ConcurrentHashMap<>();
        var commitCallback = new BiConsumer<BatchCheckContext, BatchCheckContext.BlobCommitType>() {
            @Override
            public void accept(BatchCheckContext blobCheckContext, BatchCheckContext.BlobCommitType commitType) {
                var awaitPair = SettlementSlowPathCommitter.this.commitAwaitConditions.remove(blobCheckContext.getCurrentCheckNumber());
                if (awaitPair == null) {
                    logger.error("commit callback for blob number[{}] is not exist!", blobCheckContext.getCurrentCheckNumber());
                } else {
                    var blob = awaitPair.getLeft();
                    blob.recordCommitSign(blobCheckContext.getSameHashSigns());
                    //SettlementSlowPathCommitter.this.doCommitBlob(block);
                    awaitPair.getRight().countDown();
                }
            }
        };
        this.currentBatchCheckContext = new BatchCheckContext(commitCallback);
        this.test = test;
        this.maxCommitBlobCache = irisCoreSystemConfig.getSlowPathMaxCommitBlobInMemory();
        this.settlementBlobGenerator = settlementBlobGenerator;
        this.settlementBatchFinality = settlementBatchFinality;
    }

    public void start() {

        new IrisSettlementWorker("slow_path_committer_thread") {

            @Override
            protected void doWork() throws Exception {
                var settlementBlob = SettlementSlowPathCommitter.this.settlementBlobGenerator.generateBlob();
                SettlementSlowPathCommitter.this.stateLedger.doPersistWillSignedBlob(settlementBlob);
                var signEpoch = settlementBlob.getBornEpoch();
                SettlementSlowPathCommitter.this.awaitConsensusSign(settlementBlob, signEpoch);
                var signEpochState = SettlementSlowPathCommitter.this.currentBatchCheckContext.getEpochState();
                while (!SettlementSlowPathCommitter.this.settlementBatchFinality.onChain(signEpochState, settlementBlob)) {
                    signEpoch++;
                    while (SettlementSlowPathCommitter.this.stateLedger.getEpochState(signEpoch) == null) {
                        Thread.sleep(1000);
                    }
                    SettlementSlowPathCommitter.this.awaitConsensusSign(settlementBlob, signEpoch);
                    signEpochState = SettlementSlowPathCommitter.this.currentBatchCheckContext.getEpochState();
                }
                SettlementSlowPathCommitter.this.doConfirmBlob(settlementBlob);
            }

            @Override
            protected void doException(Throwable e) {
                super.doException(e);
            }
        }.start();

        this.coordinator.start();
    }

    private void awaitConsensusSign(SettlementBatch willBeSignedBlob, long signEpoch) throws InterruptedException {
        var finishCommitCondition = new CountDownLatch(1);
        this.commitAwaitConditions.put(willBeSignedBlob.getNumber(), Pair.of(willBeSignedBlob, finishCommitCondition));
        this.coordinator.slowPathMsgQueue.put(new LocalBatchSignMsg(signEpoch, willBeSignedBlob, secureKey.getPubKey(), new Signature(secureKey.sign(willBeSignedBlob.getHash()))));
        finishCommitCondition.await();
    }

    private void doConfirmBlob(SettlementBatch confirmSettlementBatch) {
        var blobSign = confirmSettlementBatch.getBlobSign();
        if (blobSign == null) {
            logger.error("un except condition for blobSign is null when do commit blob[{}]", confirmSettlementBatch.getEndBlockNumber());
            throw new RuntimeException(StringUtils.format("un except condition for blobSign is null when do commit blob[{}]", confirmSettlementBatch.getEndBlockNumber()));
        }

        this.stateLedger.doPersistConfirmBlob(confirmSettlementBatch);
        this.confirmBlobs.put(confirmSettlementBatch.getNumber(), confirmSettlementBatch);
        this.confirmBlobSigns.put(confirmSettlementBatch.getNumber(), blobSign);
        this.confirmBlobs.remove(confirmSettlementBatch.getNumber() - this.maxCommitBlobCache);
        this.confirmBlobSigns.remove(confirmSettlementBatch.getNumber() - this.maxCommitBlobCache);

        logger.info("all chain trace finish all consensus blob [{}-{}]",
                confirmSettlementBatch.getNumber(),
                Hex.toHexString(confirmSettlementBatch.getHash()));
    }

    public BatchSign getCommitBlobSign(long number) {
        var sign = this.confirmBlobSigns.get(number);
        if (sign == null) {
            sign = this.stateLedger.getBlobSignByNumber(number);
        }
        return sign;
    }
}
