package org.ivy.settlement.core.chain.consensus.slowpath;

import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.async.ThanosThreadFactory;
import org.ivy.settlement.infrastructure.async.IrisSettlementWorker;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.consensus.slowpath.model.*;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.core.chain.network.protocols.MessageDuplexDispatcher;
import org.ivy.settlement.core.chain.sync.SettlementChainsSyncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * description:
 * @author carrot
 */
class SlowPathCommitCoordinator {

    static final Logger logger = LoggerFactory.getLogger("slow_path");

    static final boolean TRACE_ABLE = logger.isTraceEnabled();

    static final int MAX_FUTURE_BLOB_SIGN_RESP_SIZE = 16;

    static ScheduledThreadPoolExecutor timeoutScheduled = new ScheduledThreadPoolExecutor(1, new ThanosThreadFactory("async_consensus_schedule_processor"));

    int checkTimeoutMs;

    SettlementSlowPathCommitter settlementSlowPathCommitter;

    SettlementChainsSyncer settlementChainsSyncer;

    ArrayBlockingQueue<SlowPathMsg> slowPathMsgQueue;

    Map<Long, List<BatchSignResponseMsg>> pendingBlobSignResponse;

    SlowPathCommitCoordinator(IrisCoreSystemConfig irisCoreSystemConfig, SettlementSlowPathCommitter settlementSlowPathCommitter) {
        this.settlementSlowPathCommitter = settlementSlowPathCommitter;
        this.settlementChainsSyncer = settlementSlowPathCommitter.settlementChainsSyncer;
        this.slowPathMsgQueue = new ArrayBlockingQueue<>(10000);
        this.pendingBlobSignResponse = new HashMap<>(1024);
        this.checkTimeoutMs = irisCoreSystemConfig.getSlowPathCheckTimeoutMS();
    }

    void start() {
        new IrisSettlementWorker("slow_path_coordinator_net_thread") {
            @Override
            protected void doWork() throws Exception {
                var msg = MessageDuplexDispatcher.getSlowPathMsg();
                SlowPathMsg slowPathMsg = switch (SlowPathCommand.fromByte(Objects.requireNonNull(msg).getCode())) {
                    case GET_BLOB_SIGN_REQ -> new GetBatchSignRequestMsg(msg.getEncoded());
                    case BLOB_SIGN_RESP -> new BatchSignResponseMsg(msg.getEncoded());
                    default -> throw new RuntimeException("un except msg type!");
                };

                slowPathMsg.setRemoteType(msg.getRemoteType());
                slowPathMsg.setRpcId(msg.getRpcId());
                slowPathMsg.setNodeId(msg.getNodeId());
                slowPathMsgQueue.put(slowPathMsg);
            }
        }.start();

        new IrisSettlementWorker("slow_path_coordinator_process_thread") {
            @Override
            protected void doWork() throws Exception {
                var globalStateVerifierMsg = slowPathMsgQueue.take();
                switch (globalStateVerifierMsg.getCommand()) {
                    case LOCAL_BLOB_SIGN -> processLocalBlobSign((LocalBatchSignMsg) globalStateVerifierMsg);
                    case GET_BLOB_SIGN_REQ -> processGetBlobSignReq((GetBatchSignRequestMsg) globalStateVerifierMsg);
                    case BLOB_SIGN_RESP -> processBlobSignResp((BatchSignResponseMsg) globalStateVerifierMsg);
                    case BLOB_SIGN_TIMEOUT -> processBlobSignTimeout((BatchCheckTimeoutMsg) globalStateVerifierMsg);
                    default -> throw new RuntimeException("un except msg type!");
                }
                globalStateVerifierMsg.releaseReference();
            }
        }.start();
    }


    private void processLocalBlobSign(LocalBatchSignMsg localBatchSignMsg) {
        logger.info("process local blob[{}-{}]", localBatchSignMsg.settlementBatch.getNumber(), Hex.toHexString(localBatchSignMsg.settlementBatch.getHash()));
        var publicKeyWrapper = new ByteArrayWrapper(localBatchSignMsg.publicKey);
        this.settlementSlowPathCommitter.currentBatchCheckContext.reset(settlementSlowPathCommitter.stateLedger, localBatchSignMsg.signEpoch, localBatchSignMsg.settlementBatch);
        this.settlementSlowPathCommitter.currentBatchCheckContext.checkSign(publicKeyWrapper, localBatchSignMsg.settlementBatch.getNumber(), localBatchSignMsg.settlementBatch.getHash(), localBatchSignMsg.signature);
        this.settlementSlowPathCommitter.netInvoker.broadcast(new BatchSignResponseMsg(localBatchSignMsg.settlementBatch.getNumber(), localBatchSignMsg.settlementBatch.getHash(), settlementSlowPathCommitter.currentBatchCheckContext.getSameHashSigns()));

        var getBlockSignRequestMsgs = this.pendingBlobSignResponse.get(localBatchSignMsg.settlementBatch.getNumber());
        if (getBlockSignRequestMsgs != null) {
            for (var blockSignResponseMsg : getBlockSignRequestMsgs) {
                for (var entry : blockSignResponseMsg.getSignatures().entrySet()) {
                    this.settlementSlowPathCommitter.currentBatchCheckContext.checkSign(entry.getKey(), blockSignResponseMsg.getNumber(), blockSignResponseMsg.getHash(), entry.getValue());
                }
            }
        }

        if (this.settlementSlowPathCommitter.currentBatchCheckContext.isCommit()) {
            this.releasePending(localBatchSignMsg.settlementBatch.getNumber());
            return;
        }
        this.doScheduled(localBatchSignMsg.signEpoch, localBatchSignMsg.settlementBatch.getNumber());
    }

    private void processGetBlobSignReq(GetBatchSignRequestMsg getBatchSignRequestMsg) {
        if (TRACE_ABLE) {
            logger.trace("process get blob sign req:{}", getBatchSignRequestMsg);
        }
        logger.info("process get blob sign req:{}", getBatchSignRequestMsg);
        if (getBatchSignRequestMsg.getNumber() > settlementSlowPathCommitter.stateLedger.getLatestConfirmBlobNum()) {
            logger.info("get blob sign req has beyond self latest be signed blob num is {}, req is {}", settlementSlowPathCommitter.stateLedger.getLatestConfirmBlobNum(), getBatchSignRequestMsg);
            return;
        }

        var hash = new byte[0];
        var signatures = new HashMap<ByteArrayWrapper, Signature>();

        if (getBatchSignRequestMsg.getNumber() <= settlementSlowPathCommitter.stateLedger.getLatestConfirmBlobNum()) {
            var blockSign = this.settlementSlowPathCommitter.getCommitBlobSign(getBatchSignRequestMsg.getNumber());
            signatures.putAll(blockSign.getSignatures());
            hash = ByteUtil.copyFrom(blockSign.getHash());
        } else if (this.settlementSlowPathCommitter.currentBatchCheckContext.getCurrentCheckNumber() == getBatchSignRequestMsg.getNumber()) {
            signatures.putAll(this.settlementSlowPathCommitter.currentBatchCheckContext.getSameHashSigns());
            hash = ByteUtil.copyFrom(this.settlementSlowPathCommitter.currentBatchCheckContext.getCurrentCheckHash());
        }

        var responseMsg = new BatchSignResponseMsg(getBatchSignRequestMsg.getNumber(), hash, signatures);
        this.settlementSlowPathCommitter.netInvoker.directSend(List.of(getBatchSignRequestMsg.getPeerId()), responseMsg);
    }


    private void processBlobSignResp(BatchSignResponseMsg batchSignResponseMsg) {
        if (TRACE_ABLE) {
            logger.trace("receive from[{}]-[{}]process blob sign resp:{}", batchSignResponseMsg.getPeerId(), this.settlementSlowPathCommitter.currentBatchCheckContext.getCurrentCheckNumber(), batchSignResponseMsg);
        }

        logger.info("receive from[{}]-[{}]process blob sign resp:{}", batchSignResponseMsg.getPeerId(), this.settlementSlowPathCommitter.currentBatchCheckContext.getCurrentCheckNumber(), batchSignResponseMsg);

        var latestConfirmBlobNum = this.settlementSlowPathCommitter.stateLedger.getLatestConfirmBlobNum();
        if (batchSignResponseMsg.getNumber() <= latestConfirmBlobNum
                ||
                (batchSignResponseMsg.getNumber() - latestConfirmBlobNum) > MAX_FUTURE_BLOB_SIGN_RESP_SIZE) {
            return;
        }

        if (this.settlementSlowPathCommitter.currentBatchCheckContext.getCurrentCheckNumber() == batchSignResponseMsg.getNumber()) {
            for (var entry : batchSignResponseMsg.getSignatures().entrySet()) {
                this.settlementSlowPathCommitter.currentBatchCheckContext.checkSign(entry.getKey(), batchSignResponseMsg.getNumber(), batchSignResponseMsg.getHash(), entry.getValue());
                if (this.settlementSlowPathCommitter.currentBatchCheckContext.isCommit()) {
                    releasePending(this.settlementSlowPathCommitter.currentBatchCheckContext.getCurrentCheckNumber());
                    return;
                }
            }
        } else {
            logger.info("process blob sig resp for future blob number:{}, current process blob number:{}", batchSignResponseMsg.getNumber(), this.settlementSlowPathCommitter.currentBatchCheckContext.getCurrentCheckNumber());
            this.pendingBlobSignResponse
                    .computeIfAbsent(batchSignResponseMsg.getNumber(), k -> new ArrayList<>(16))
                    .add(batchSignResponseMsg);
        }
    }

    private void processBlobSignTimeout(BatchCheckTimeoutMsg batchCheckTimeoutMsg) {
        if (batchCheckTimeoutMsg.signEpoch != this.settlementSlowPathCommitter.currentBatchCheckContext.getCurrentSignEpoch() || batchCheckTimeoutMsg.number != this.settlementSlowPathCommitter.currentBatchCheckContext.getCurrentCheckNumber() || settlementSlowPathCommitter.currentBatchCheckContext.isCommit()) {
            return;
        }

//        if (this.settlementChainsSyncer.getSettlementBlobOnChainResult(MAIN_CHAIN_CODE, blobCheckTimeoutMsg.number) != OnChainStatus.UNKNOWN) {
//            this.settlementSlowPathCommitter.currentBlobCheckContext.commit(blobCheckTimeoutMsg.number);
//            return;
//        }

        logger.info("will process signEpoch[{}] blob[{}] for timeout!", batchCheckTimeoutMsg.signEpoch, batchCheckTimeoutMsg.number);
        this.doScheduled(batchCheckTimeoutMsg.signEpoch, batchCheckTimeoutMsg.number);
        this.settlementSlowPathCommitter.netInvoker.broadcast(new GetBatchSignRequestMsg(batchCheckTimeoutMsg.signEpoch, batchCheckTimeoutMsg.number));
    }

    private void doScheduled(long signEpoch, long number) {
        timeoutScheduled.schedule(() -> {
            try {
                slowPathMsgQueue.put(new BatchCheckTimeoutMsg(signEpoch, number));
            } catch (InterruptedException e) {
                logger.error("slow path timeout scheduled for number[{}] error:{}", number, e.getMessage());
            }
        }, checkTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private void releasePending(long number) {
        var blockSignResponseQueue = this.pendingBlobSignResponse.remove(number);
        if (blockSignResponseQueue != null) {
            for (BatchSignResponseMsg blockSignResponseMsg : blockSignResponseQueue) {
                blockSignResponseMsg.doRelease();
            }
        }
    }
}
