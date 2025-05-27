package org.ivy.settlement.core.chain.consensus.sequence;

import io.libp2p.core.peer.PeerId;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.async.IrisSettlementWorker;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.consensus.sequence.model.sync.FinalizedChainSyncCommand;
import org.ivy.settlement.core.chain.consensus.sequence.model.sync.FinalizedChainSyncRequestMsg;
import org.ivy.settlement.core.chain.consensus.sequence.model.sync.FinalizedChainSyncResponseMsg;
import org.ivy.settlement.core.chain.consensus.sequence.safety.settlement.SettlementChainsVerifier;
import org.ivy.settlement.core.chain.consensus.sequence.store.ConsensusChainStore;
import org.ivy.settlement.core.chain.consensus.sequence.executor.GlobalExecutor;
import org.ivy.settlement.core.chain.network.NetInvoker;
import org.ivy.settlement.core.chain.network.protocols.MessageDuplexDispatcher;
import org.ivy.settlement.core.chain.network.protocols.base.RemotingMessageType;
import org.ivy.settlement.core.chain.txpool.TxnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets.EMPTY_OFFSETS;

/**
 * description:
 * @author carrot
 */
public class FinalizedChainSynchronizer {

    private static final Logger logger = LoggerFactory.getLogger("sync-layer2");

    // 32MB
    //private static final int TRANSFER_LIMIT_SIZE = 32 * 1024 *1024;

    private static final int TRANSFER_LIMIT_EVENT_NUM = 3;

    private static final int RETRIEVAL_TIMEOUT = 6000;

    private static final int MAX_FAIL_COUNT = 2;

    ConsensusChainStore consensusChainStore;

    NetInvoker netInvoker;

    SettlementChainsVerifier settlementChainsVerifier;

    GlobalExecutor globalExecutor;

    TxnManager txnManager;

    volatile boolean syncing;

    public FinalizedChainSynchronizer(NetInvoker netInvoker, SettlementChainsVerifier settlementChainsVerifier, ConsensusChainStore consensusChainStore, GlobalExecutor globalExecutor, TxnManager txnManager) {
        this.netInvoker = netInvoker;
        this.settlementChainsVerifier = settlementChainsVerifier;
        this.consensusChainStore = consensusChainStore;
        this.globalExecutor = globalExecutor;
        this.txnManager = txnManager;
        start();
    }

    public void start() {
        new IrisSettlementWorker("layer2_chain_sync_coordinator_thread") {
            @Override
            protected void doWork() throws Exception {
                var msg = MessageDuplexDispatcher.getLayer2StateSyncMsg();
                switch (FinalizedChainSyncCommand.fromByte(Objects.requireNonNull(msg).getCode())) {
                    case LAYER2_STATE_SYNC_REQUEST -> {
                        FinalizedChainSyncRequestMsg finalizedChainSyncRequestMsg = new FinalizedChainSyncRequestMsg(msg.getEncoded());
                        finalizedChainSyncRequestMsg.setNodeId(msg.getNodeId());
                        finalizedChainSyncRequestMsg.setRpcId(msg.getRpcId());
                        finalizedChainSyncRequestMsg.setRemoteType(msg.getRemoteType());
                        processSyncReq(finalizedChainSyncRequestMsg);
                    }
                    default -> {
                    }
                }
            }
        }.start();
    }

    public boolean isSyncing() {
        return syncing;
    }

    private void processSyncReq(FinalizedChainSyncRequestMsg request) {
        var start = System.currentTimeMillis();
        logger.info("receive sync req number:{}", request.getStartEventNumber());
        if (request.getStartEventNumber() < 0 || request.getStartEventNumber() > consensusChainStore.getLatestLedger().getLatestNumber()) {
            logger.warn("receive sync req UN_EXCEPT_NUMBER, current:[{}], except:[{}]", consensusChainStore.getLatestLedger().getLatestNumber(), request.getStartEventNumber());
            var responseMsg = new FinalizedChainSyncResponseMsg(FinalizedChainSyncResponseMsg.CommitEventRetrievalStatus.UN_EXCEPT_NUMBER, EMPTY_OFFSETS, Collections.EMPTY_LIST, Collections.EMPTY_LIST);
            responseMsg.setRpcId(request.getRpcId());
            responseMsg.setRemoteType(RemotingMessageType.RPC_RESPONSE_MESSAGE.getType());
            netInvoker.rpcResponse(request.getPeerId(), responseMsg);
            return;
        }
        var result = consensusChainStore.getEventDates(request.getStartEventNumber(), request.getStartEventNumber() + TRANSFER_LIMIT_EVENT_NUM);
        var end = System.currentTimeMillis();
        var responseMsg = new FinalizedChainSyncResponseMsg(FinalizedChainSyncResponseMsg.CommitEventRetrievalStatus.SUCCESSED, result.getLeft(), result.getMiddle(), result.getRight());
        logger.info("receive sync req success find, total cost[{}]", (end - start));
        responseMsg.setRpcId(request.getRpcId());
        responseMsg.setRemoteType(RemotingMessageType.RPC_RESPONSE_MESSAGE.getType());
        netInvoker.rpcResponse(request.getPeerId(), responseMsg);
    }

    public ProcessResult<Void> syncTo(LedgerInfoWithSignatures targetLatestLedgerInfo, PeerId syncPeer) {
        try {
            syncing = true;
            var failCount = 0;
            while (true) {
                if (consensusChainStore.getLatestLedger().getLatestNumber() >= targetLatestLedgerInfo.getLedgerInfo().getNumber()) {
                    return ProcessResult.SUCCESSFUL;
                }

                // self sync start number
                long currentSyncNumber = consensusChainStore.getLatestLedger().getLatestNumber() + 1;
                logger.debug("sync from number:{}, current max commit number:{}", currentSyncNumber, targetLatestLedgerInfo.getLedgerInfo().getNumber());
                var req = new FinalizedChainSyncRequestMsg(currentSyncNumber);
                req.setRemoteType(RemotingMessageType.RPC_REQUEST_MESSAGE.getType());
                var response = netInvoker.rpcSend(syncPeer, req, 30000);
                while (response == null) {
                    if (failCount == MAX_FAIL_COUNT) {
                        break;
                    }
                    response = netInvoker.rpcSend(syncPeer, req, RETRIEVAL_TIMEOUT);
                    failCount++;
                }

                if (response == null) {
                    logger.warn("Failed to fetch commit event from {}, sync fail!", syncPeer);
                    return ProcessResult.ofError(String.format("Retrieval peer[%s] commit event fail", syncPeer));
                }
                failCount = 0;

                FinalizedChainSyncResponseMsg responseMsg = new FinalizedChainSyncResponseMsg(response.getEncoded());
                var selfLatestOffsets = this.consensusChainStore.getEventData(consensusChainStore.getLatestLedger().getLatestNumber(), true, false).getPayload().getCrossChainOffsets();
                logger.info("self latest exe[{}]-[{}]", consensusChainStore.getLatestLedger().getLatestNumber(), selfLatestOffsets.getMainChain());
                ProcessResult<Void> undoProcess = doSync(responseMsg, targetLatestLedgerInfo, selfLatestOffsets);
                if (!undoProcess.isSuccess()) {
                    return ProcessResult.ofError(undoProcess.getErrMsg());
                }
            }
        } catch (Exception e) {
            //e.printStackTrace();
            logger.warn("Layer2ChainSynchronizer.syncTo {} warn! {}", targetLatestLedgerInfo, ExceptionUtils.getStackTrace(e));
            return ProcessResult.ofError("syncTo error:" + e.getMessage());
        } finally {
            syncing = false;
        }
    }

    private ProcessResult<Void> doSync(FinalizedChainSyncResponseMsg responseMsg, LedgerInfoWithSignatures targetLatestLedgerInfo, SettlementChainOffsets selfLatestOffsets) {
        var eventDatas = responseMsg.getEventDatas();
        var eventInfoWithSignaturesList = responseMsg.getEventInfoWithSignatureses();

        eventDatas.sort((o1, o2) -> (int) (o1.getNumber() - o2.getNumber()));
        var signaturesMap = eventInfoWithSignaturesList.stream().collect(Collectors.toMap(EventInfoWithSignatures::getNumber, eventInfoWithSignatures -> eventInfoWithSignatures));
        var parentOffsets = selfLatestOffsets;

        for (EventData currentData : eventDatas) {
            var eventInfoWithSignatures = signaturesMap.get(currentData.getNumber());
            if (eventInfoWithSignatures == null) {
                logger.error("doSync error! EventData {} has not signatures", currentData);
                return ProcessResult.ofError("doSync error!");
            }

            var latestOffsets = currentData.getPayload().getCrossChainOffsets();
            var caeRes = checkAndExecute(latestOffsets, parentOffsets, currentData, eventInfoWithSignatures, targetLatestLedgerInfo);
            parentOffsets = currentData.getPayload().getCrossChainOffsets();
            if (!caeRes.isSuccess()) {
                return ProcessResult.ofError(caeRes.getErrMsg());
            }
        }

        return ProcessResult.SUCCESSFUL;
    }

    private ProcessResult<Void> checkAndExecute(SettlementChainOffsets latestOffsets, SettlementChainOffsets parentOffsets, EventData eventData, EventInfoWithSignatures eventInfoWithSignatures, LedgerInfoWithSignatures latestLedgerInfo) {
        if (eventData.getNumber() != consensusChainStore.getLatestLedger().getLatestNumber() + 1) {
            logger.warn("un except event number, self latest number[{}], receive number[{}]", consensusChainStore.getLatestLedger().getLatestNumber(), eventData.getNumber());
            return ProcessResult.ofError("un except event number!");
        }

        //if (this.crossChainVerifier.crossChainOffsetsConsistencyCheck())

        // todo :: check the event data and signature and output  nextepochstate and currentepochstate, it will null?????
        var exeRes = doExecute(parentOffsets, eventData);
        if (exeRes.isSuccess()) {
            LedgerInfoWithSignatures latestLedger;
            if (latestLedgerInfo.getLedgerInfo().getNumber() == eventData.getNumber()) {
                latestLedger = latestLedgerInfo;
            } else {
                latestLedger = LedgerInfoWithSignatures.build(
                        LedgerInfo.build(
                                EventInfo.build(eventData.getEpoch(), eventData.getRound(), eventData.getHash(), exeRes.getResult().getStateRoot(), eventData.getNumber(), eventData.getTimestamp(), exeRes.getResult().getNewCurrentEpochState(), exeRes.getResult().getNewNextEpochState()),
                                eventData.getHash()
                        ),
                        eventInfoWithSignatures.getSignatures());
            }

            consensusChainStore.commit(List.of(eventData), List.of(eventInfoWithSignatures), exeRes.getResult(), latestOffsets, latestLedger);
            //this.txnManager.syncCommitEvent(eventData);

            logger.info("sync commit success lg[{}], output:[{}]", latestLedger, exeRes.getResult());

        } else {
            return ProcessResult.ofError(exeRes.getErrMsg());
        }
        return ProcessResult.SUCCESSFUL;
    }

    private ProcessResult<ExecutedEventOutput> doExecute(SettlementChainOffsets parentOffsets, EventData eventData) {
        var epochStateHolder = globalExecutor.doExecuteGlobalNodeEvents(eventData.getPayload().getCrossChainOffsets(), parentOffsets);
        var executedEventOutput = new ExecutedEventOutput(new HashMap<>(), eventData.getNumber(), consensusChainStore.getLatestLedger().getCommitExecutedEventOutput().getStateRoot(), epochStateHolder.first, epochStateHolder.second);
        return ProcessResult.ofSuccess(executedEventOutput);
    }
}
