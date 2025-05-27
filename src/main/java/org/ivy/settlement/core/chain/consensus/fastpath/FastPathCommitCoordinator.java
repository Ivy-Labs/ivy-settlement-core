package org.ivy.settlement.core.chain.consensus.fastpath;

import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.async.ThanosThreadFactory;
import org.ivy.settlement.infrastructure.async.IrisSettlementWorker;
import org.ivy.settlement.core.chain.consensus.fastpath.model.*;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.core.chain.network.protocols.MessageDuplexDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * description:
 * @author carrot
 */
public class FastPathCommitCoordinator {

    static final Logger logger = LoggerFactory.getLogger("fast_path");

    static final boolean TRACE_ABLE = logger.isTraceEnabled();

    static final int MAX_FUTURE_BLOCK_SIGN_RESP_SIZE = 16;

    static ScheduledThreadPoolExecutor timeoutScheduled = new ScheduledThreadPoolExecutor(1, new ThanosThreadFactory("async_consensus_schedule_processor"));

    int checkTimeoutMs;

    SettlementFastPathCommitter settlementFastPathCommitter;

    BlockSyncCoordinator blockSyncCoordinator;

    ArrayBlockingQueue<FastPathMsg> fastPathMsgQueue;

    Map<Long, List<BlockSignResponseMsg>> pendingBlockSignResponse;


    public FastPathCommitCoordinator(SettlementFastPathCommitter settlementFastPathCommitter, BlockSyncCoordinator blockSyncCoordinator, int checkTimeoutMs) {
        this.settlementFastPathCommitter = settlementFastPathCommitter;
        this.blockSyncCoordinator = blockSyncCoordinator;
        this.fastPathMsgQueue = new ArrayBlockingQueue<>(10000);
        this.pendingBlockSignResponse = new HashMap<>(1024);
        this.checkTimeoutMs = checkTimeoutMs;
    }

    public void start() {
        new IrisSettlementWorker("fast_path_coordinator_net_thread") {
            @Override
            protected void doWork() throws Exception {
                var msg = MessageDuplexDispatcher.getFastPathMsg();
                FastPathMsg fastPathMsg = switch (FastPathCommand.fromByte(Objects.requireNonNull(msg).getCode())) {
                    case GET_BLOCK_SIGN_REQ -> new GetBlockSignRequestMsg(msg.getEncoded());
                    case BLOCK_SIGN_RESP -> new BlockSignResponseMsg(msg.getEncoded());
                    case GET_COMMIT_BLOCK_REQ -> new GetCommitBlockRequestMsg(msg.getEncoded());
                    default -> throw new RuntimeException("un except msg type!");
                };

                fastPathMsg.setRemoteType(msg.getRemoteType());
                fastPathMsg.setRpcId(msg.getRpcId());
                fastPathMsg.setNodeId(msg.getNodeId());
                fastPathMsgQueue.put(fastPathMsg);
            }
        }.start();

        new IrisSettlementWorker("fast_path_coordinator_process_thread") {
            @Override
            protected void doWork() throws Exception {
                var globalStateVerifierMsg = fastPathMsgQueue.take();
                switch (globalStateVerifierMsg.getCommand()) {
                    case LOCAL_BLOCK_SIGN -> processLocalBlockSign((LocalBlockSignMsg) globalStateVerifierMsg);
                    case GET_BLOCK_SIGN_REQ -> processGetBlockSignReq((GetBlockSignRequestMsg) globalStateVerifierMsg);
                    case GET_COMMIT_BLOCK_REQ ->
                            processGetCommitBlockReq((GetCommitBlockRequestMsg) globalStateVerifierMsg);
                    case BLOCK_SIGN_RESP -> processBlockSignResp((BlockSignResponseMsg) globalStateVerifierMsg);
                    case BLOCK_SIGN_TIMEOUT -> processBlockSignTimeout((BlockCheckTimeoutMsg) globalStateVerifierMsg);
                    default -> throw new RuntimeException("un except msg type!");
                }
                globalStateVerifierMsg.releaseReference();
            }
        }.start();
    }


    private void processLocalBlockSign(LocalBlockSignMsg localBlockSignMsg) {
        if (localBlockSignMsg.block.getNumber() != this.settlementFastPathCommitter.stateLedger.getLatestBeExecutedNum() + 1) {
            logger.warn("local block[{}] has processed, current processing block[{}], ignored", localBlockSignMsg.block.getNumber(), this.settlementFastPathCommitter.stateLedger.getLatestBeExecutedNum() + 1);
            if (this.settlementFastPathCommitter.stateLedger.getLatestBeExecutedNum() >= localBlockSignMsg.block.getNumber()) {
                this.settlementFastPathCommitter.currentBlockCheckContext.syncCommit(localBlockSignMsg.block.getNumber());
                this.releasePending(localBlockSignMsg.block.getNumber());
            }
            return;
        }

        var publicKeyWrapper = new ByteArrayWrapper(localBlockSignMsg.publicKey);
        logger.info("[{}]processLocalBlockSign[{}]-[{}]", publicKeyWrapper, localBlockSignMsg.block.getNumber(), this.settlementFastPathCommitter.stateLedger.getLatestBeExecutedNum() + 1);
        this.settlementFastPathCommitter.currentBlockCheckContext.reset(settlementFastPathCommitter.stateLedger, localBlockSignMsg.block);
        this.settlementFastPathCommitter.currentBlockCheckContext.checkSign(publicKeyWrapper, localBlockSignMsg.block.getNumber(), localBlockSignMsg.block.getHash(), localBlockSignMsg.signature);
        this.settlementFastPathCommitter.netInvoker.broadcast(new BlockSignResponseMsg(localBlockSignMsg.block.getNumber(), localBlockSignMsg.block.getHash(), settlementFastPathCommitter.currentBlockCheckContext.getSameHashSigns()));

        var getBlockSignRequestMsgs = this.pendingBlockSignResponse.get(localBlockSignMsg.block.getNumber());
        if (getBlockSignRequestMsgs != null) {
            for (var blockSignResponseMsg : getBlockSignRequestMsgs) {
                for (var entry : blockSignResponseMsg.getSignatures().entrySet()) {
                    this.settlementFastPathCommitter.currentBlockCheckContext.checkSign(entry.getKey(), blockSignResponseMsg.getNumber(), blockSignResponseMsg.getHash(), entry.getValue());
                }
            }
        }

        if (this.settlementFastPathCommitter.currentBlockCheckContext.isCommit()) {
            //logger.info("process local block[{}] commit!", this.settlementFastPathCommitter.currentBlockCheckContext.getCurrentCheckNumber());
            this.releasePending(localBlockSignMsg.block.getNumber());
            return;
        }
        this.doScheduled(localBlockSignMsg.block.getNumber());
    }

    private void processGetBlockSignReq(GetBlockSignRequestMsg getBlockSignRequestMsg) {
        if (TRACE_ABLE) {
            logger.trace("processGetBlockSignReq:{}", getBlockSignRequestMsg);
        }
        if (getBlockSignRequestMsg.getNumber() > settlementFastPathCommitter.stateLedger.getLatestBeExecutedNum()) {
            return;
        }

        var hash = new byte[0];
        var signatures = new HashMap<ByteArrayWrapper, Signature>();

        if (getBlockSignRequestMsg.getNumber() <= settlementFastPathCommitter.stateLedger.getLatestBeExecutedNum()) {
            var blockSign = this.settlementFastPathCommitter.getCommitBlockSign(getBlockSignRequestMsg.getNumber());
            signatures.putAll(blockSign.getSignatures());
            hash = ByteUtil.copyFrom(blockSign.getHash());
        } else if (this.settlementFastPathCommitter.currentBlockCheckContext.getCurrentCheckNumber() == getBlockSignRequestMsg.getNumber()) {
            signatures.putAll(this.settlementFastPathCommitter.currentBlockCheckContext.getSameHashSigns());
            hash = ByteUtil.copyFrom(this.settlementFastPathCommitter.currentBlockCheckContext.getCurrentCheckHash());
        }

        var responseMsg = new BlockSignResponseMsg(getBlockSignRequestMsg.getNumber(), hash, signatures);
        this.settlementFastPathCommitter.netInvoker.directSend(List.of(getBlockSignRequestMsg.getPeerId()), responseMsg);
    }

    private void processGetCommitBlockReq(GetCommitBlockRequestMsg req) {
        var retrievalRes = this.blockSyncCoordinator.retrievalBlocks(req.getStartNumber(), req.getEndNumber());
        var responseMsg = new CommitBlockResponseMsg(retrievalRes.getLeft(), retrievalRes.getRight());
        responseMsg.setRpcId(req.getRpcId());
        this.settlementFastPathCommitter.netInvoker.rpcResponse(req.getPeerId(), responseMsg);
    }

    private void processBlockSignResp(BlockSignResponseMsg blockSignResponseMsg) {
        if (TRACE_ABLE) {
            logger.trace("receive from[{}]-[{}]processBlockSignResp:{}", blockSignResponseMsg.getPeerId(), this.settlementFastPathCommitter.currentBlockCheckContext.getCurrentCheckNumber(), blockSignResponseMsg);
        }

        if (this.settlementFastPathCommitter.currentBlockCheckContext.getCurrentCheckNumber() == blockSignResponseMsg.getNumber()) {
            for (var entry : blockSignResponseMsg.getSignatures().entrySet()) {
                this.settlementFastPathCommitter.currentBlockCheckContext.checkSign(entry.getKey(), blockSignResponseMsg.getNumber(), blockSignResponseMsg.getHash(), entry.getValue());
                if (this.settlementFastPathCommitter.currentBlockCheckContext.isCommit()) {
                    releasePending(this.settlementFastPathCommitter.currentBlockCheckContext.getCurrentCheckNumber());
                    return;
                }
            }
        } else {
            //logger.info("");
            var beforeSyncLatestBeExecutedNum = this.settlementFastPathCommitter.stateLedger.getLatestBeExecutedNum();
            if (blockSignResponseMsg.getNumber() <= beforeSyncLatestBeExecutedNum) {
                logger.info("block sign response number:{}, self latest be executed number:{}, ignore!", blockSignResponseMsg.getNumber(), beforeSyncLatestBeExecutedNum);
                return;
            }

            if (blockSignResponseMsg.getNumber() > beforeSyncLatestBeExecutedNum + 1) {
                logger.info("before num:{}, target:{}", beforeSyncLatestBeExecutedNum, blockSignResponseMsg.getNumber());
                var endSyncNumber = Math.min(this.settlementFastPathCommitter.stateLedger.getLatestConsensusNumber(), blockSignResponseMsg.getNumber() - 1);
                var syncRes = this.blockSyncCoordinator.fastForward(beforeSyncLatestBeExecutedNum + 1, endSyncNumber, blockSignResponseMsg.getPeerId());
                if (!syncRes.isSuccess()) {
                    logger.warn("sync error:{}", syncRes.getErrMsg());
                }

                var afterSyncLatestBeExecutedNum = this.settlementFastPathCommitter.stateLedger.getLatestBeExecutedNum();
                logger.info("sync success before num:{}, target:{}", beforeSyncLatestBeExecutedNum, afterSyncLatestBeExecutedNum);

                if (afterSyncLatestBeExecutedNum > beforeSyncLatestBeExecutedNum) {
                    for (var i = beforeSyncLatestBeExecutedNum + 1; i <= afterSyncLatestBeExecutedNum; i++) {
                        this.settlementFastPathCommitter.currentBlockCheckContext.syncCommit(i);
                        this.releasePending(i);
                    }
                }
            } else {
                //logger.info("receive future resp number:{}, current context number:{}, current be executed number:{}", blockSignResponseMsg.getNumber(), this.settlementFastPathCommitter.currentBlockCheckContext.getCurrentCheckNumber(), beforeSyncLatestBeExecutedNum);
                this.pendingBlockSignResponse
                        .computeIfAbsent(blockSignResponseMsg.getNumber(), k -> new ArrayList<>(16))
                        .add(blockSignResponseMsg);
            }
        }
    }

    private void processBlockSignTimeout(BlockCheckTimeoutMsg blockCheckTimeoutMsg) {
        if (blockCheckTimeoutMsg.number != this.settlementFastPathCommitter.currentBlockCheckContext.getCurrentCheckNumber() || settlementFastPathCommitter.currentBlockCheckContext.isCommit()) {
            return;
        }
        this.doScheduled(blockCheckTimeoutMsg.number);
        this.settlementFastPathCommitter.netInvoker.broadcast(new GetBlockSignRequestMsg(blockCheckTimeoutMsg.number));
    }

    private void doScheduled(long number) {
        timeoutScheduled.schedule(() -> {
            try {
                fastPathMsgQueue.put(new BlockCheckTimeoutMsg(number));
            } catch (InterruptedException e) {
                logger.error("timeout scheduled for number[{}] error:{}", number, e.getMessage());
            }
        }, checkTimeoutMs, TimeUnit.MILLISECONDS);
    }

    private void releasePending(long number) {
        var blockSignResponseQueue = this.pendingBlockSignResponse.remove(number);
        if (blockSignResponseQueue != null) {
            for (BlockSignResponseMsg blockSignResponseMsg : blockSignResponseQueue) {
                blockSignResponseMsg.doRelease();
            }
        }
    }
}
