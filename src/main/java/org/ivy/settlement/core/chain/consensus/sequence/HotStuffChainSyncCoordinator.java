package org.ivy.settlement.core.chain.consensus.sequence;

import io.libp2p.core.peer.PeerId;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ivy.settlement.infrastructure.anyhow.Assert;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.executor.ConsensusEventExecutor;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.consensus.sequence.safety.settlement.SettlementChainsVerifier;
import org.ivy.settlement.core.chain.consensus.sequence.store.EventTreeStore;
import org.ivy.settlement.core.chain.consensus.sequence.store.LivenessStorageData;
import org.ivy.settlement.core.chain.network.protocols.base.Message;
import org.ivy.settlement.core.chain.txpool.TxnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

import static org.ivy.settlement.core.chain.consensus.sequence.model.RetrievalStatus.SUCCESS;
import static org.ivy.settlement.core.chain.consensus.sequence.HotStuffChainSyncCoordinator.NeedFetchResult.*;

/**
 * description:
 * @author carrot
 */
public class HotStuffChainSyncCoordinator {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    public enum NeedFetchResult {
        QCRoundBeforeRoot,
        QCAlreadyExist,
        QCEventExist,
        NeedFetch,
    }

    public EventTreeStore eventTreeStore;

    public ConsensusEventExecutor consensusEventExecutor;

    public ConsensusNetInvoker netInvoker;

    public TxnManager txnManager;

    SettlementChainsVerifier settlementChainsVerifier;


    public HotStuffChainSyncCoordinator(EventTreeStore eventTreeStore, ConsensusEventExecutor consensusEventExecutor, ConsensusNetInvoker netInvoker, TxnManager txnManager, SettlementChainsVerifier settlementChainsVerifier) {
        this.consensusEventExecutor = consensusEventExecutor;
        this.netInvoker = netInvoker;
        this.eventTreeStore = eventTreeStore;
        this.txnManager = txnManager;
        this.settlementChainsVerifier = settlementChainsVerifier;
    }

//========================start  addCerts======================================================

    public ProcessResult<Void> addCerts(HotstuffChainSyncInfo syncInfo, PeerId preferredPeer) {
        try {
            ProcessResult<Void> syncRes = syncToHighestCommitCert(syncInfo.getHighestCommitCert(), preferredPeer);
            if (!syncRes.isSuccess()) {
                return syncRes;
            }

            ProcessResult<Void> insertHCCRes = insertQuorumCert(syncInfo.getHighestCommitCert(), preferredPeer, false);
            if (!insertHCCRes.isSuccess()) {
                return insertHCCRes;
            }

            ProcessResult<Void> insertHQCRes = insertQuorumCert(syncInfo.getHighestQuorumCert(), preferredPeer, false);
            if (!insertHQCRes.isSuccess()) {
                return insertHQCRes;
            }

            if (syncInfo.getHighestTwoChainTimeoutCert().isPresent()) {
                return eventTreeStore.insertTwoChainTimeoutCertificate(syncInfo.getHighestTwoChainTimeoutCert().get());
            }

            return ProcessResult.SUCCESSFUL;
        } catch (Exception e) {
            logger.warn("add certs warn! {}", ExceptionUtils.getStackTrace(e));
            return ProcessResult.ofError("");
        }
    }

    private ProcessResult<Void> syncToHighestCommitCert(QuorumCert highestCommitCert, PeerId preferredPeer) {
        if (!needSyncForQuorumCert(highestCommitCert)) return ProcessResult.SUCCESSFUL;

        ProcessResult<LivenessStorageData.RecoveryData> recoveryDataRes = fastForwardSync(highestCommitCert, preferredPeer);
        if (!recoveryDataRes.isSuccess()) {
            return ProcessResult.ofError(recoveryDataRes.getErrMsg());
        }

        eventTreeStore.rebuild(recoveryDataRes.result);
        return ProcessResult.SUCCESSFUL;
    }

    private boolean needSyncForQuorumCert(QuorumCert qc) {
        //a=eventTreeStore.eventExists,
        //b=eventTreeStore.getRoot().getRound() >= qc.getCommitEvent().getRound()
        // 情况一，a 为true， b为false,总体为false，无需同步，该种情况说明自身节点有 commit qc，但没有执行commit
        // 情况二，a 为false, b为true,总体为false，无需同步，该种情况说明自身节点的commit qc 已大于 远端节点的 commit qc，但自身内存中（event tree）的commit qc 已被清除。
        // 情况三，a 为false, b为false,总体为true，需要同步
        return !(
                eventTreeStore.eventExists(qc.getCommitEvent().getId())
                || eventTreeStore.getRoot().getRound() >= qc.getCommitEvent().getRound()
        );
    }

    private NeedFetchResult needFetchForQuorumCert(QuorumCert qc) {
        if (qc.getCertifiedEvent().getRound() < eventTreeStore.getRoot().getRound()) {
            return QCRoundBeforeRoot;
        }

        if (eventTreeStore.getQCForEvent(qc.getCertifiedEvent().getId()) != null) {
            return QCAlreadyExist;
        }

        if (eventTreeStore.eventExists(qc.getCertifiedEvent().getId())) {
            return QCEventExist;
        }

        return NeedFetch;
    }

    private ProcessResult<Void> fetchQuorumCert(QuorumCert qc, PeerId preferredPeer) {
        var pending = new LinkedList<Event>();

        var retrieveQc = qc;
        while (true) {
            if (eventTreeStore.eventExists(retrieveQc.getCertifiedEvent().getId())) {
                break;
            }

            var eventsRes = retrieveEventForQc(retrieveQc, 1, preferredPeer);
            if (!eventsRes.isSuccess()) {
                return ProcessResult.ofError(eventsRes.getErrMsg());
            }

            // retrieve_block_for_qc guarantees that blocks has exactly 1 element
            var event = eventsRes.result.remove(0);
//            var crossChainSyncRes = ivyCrossChainSyncCoordinator.crossChainFastForwardSync(event.getPayload(), preferredPeer);
//            if (!crossChainSyncRes.isSuccess()) {
//                return crossChainSyncRes;
//            }

            retrieveQc = event.getQuorumCert();
            pending.push(event);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("fetchQuorumCert, current qc:[{}]", qc);
            logger.debug("fetchQuorumCert, current fetch events:[{}]", pending);
        }

        while (pending.peek() != null) {
            var event = pending.pop();
            var insertQCRes = eventTreeStore.insertSingleQuorumCert(event.getQuorumCert());
            if (!insertQCRes.isSuccess()) {
                return ProcessResult.ofError(insertQCRes.getErrMsg());
            }

            var exeAndInsertRes = eventTreeStore.executeAndInsertEvent(event);
            txnManager.removeEvent(event.getEventData());
            if (!exeAndInsertRes.isSuccess()) {
                return ProcessResult.ofError(exeAndInsertRes.getErrMsg());
            }
        }

        return eventTreeStore.insertSingleQuorumCert(qc);
    }
//========================end  addCerts======================================================


//========================start  insertQuorumCert======================================================
    public ProcessResult<Void> insertQuorumCert(QuorumCert qc, PeerId preferredPeer, boolean broadcastChange) {
        switch (needFetchForQuorumCert(qc)) {
            case NeedFetch:
                var fetchResult = fetchQuorumCert(qc, preferredPeer);
                if (!fetchResult.isSuccess()) {
                    return fetchResult;
                }
                break;
            case QCEventExist:
                if (logger.isDebugEnabled()) {
                    logger.debug("insertQuorumCert QCEventExist:[{}]", qc);
                }
                var insertQCRes = eventTreeStore.insertSingleQuorumCert(qc);
                if (!insertQCRes.isSuccess()) {
                    return insertQCRes;
                }
                var executedEvent = eventTreeStore.getEvent(qc.getCertifiedEvent().getId());
                if (executedEvent != null && !executedEvent.getEvent().getEventData().allEmpty()) {
                    txnManager.removeEvent(executedEvent.getEvent().getEventData());
                }

                break;
            default:
                //do noting
        }

        if (this.eventTreeStore.getRoot().getRound() < qc.getCommitEvent().getRound()) {
            var finalityProof = qc.getLedgerInfoWithSignatures();
            //var latestOffsets = this.eventTreeStore.getEvent(qc.getCommitEvent().getId()).getEvent().getPayload().getCrossChainOffsets();

            Function<HotstuffChainSyncInfo, Void> broadcastFun = null;
            if (broadcastChange && finalityProof.getLedgerInfo().getNewCurrentEpochState().isPresent()) {
                broadcastFun = changeMsg -> {
                    changeMsg.getEncoded();
                    HotStuffChainSyncCoordinator.this.netInvoker.broadcast(changeMsg, false);
                    return null;
                };
            }

            var commitRes = this.eventTreeStore.commit(finalityProof, broadcastFun);
            if (!commitRes.isSuccess()) {
                return commitRes.appendErrorMsg("insertQuorumCert, eventTreeStore.commit error!");
            }
        }
        return ProcessResult.SUCCESSFUL;
    }

//========================end  insertQuorumCert======================================================

//========================start  fastForwardSync======================================================

    /**
     * this method do not rely on EventTreeStore, so it can be invoked
     * at any time
     */
    public ProcessResult<LivenessStorageData.RecoveryData> fastForwardSync(QuorumCert highestCommitCert, PeerId preferredPeer) {

        if (logger.isDebugEnabled()) {
            logger.debug("start fastForwardSync highestCommitCert![{}]", highestCommitCert);
        }

        //for three chain commit
        //var eventRes = retrieveEventForQc(highestCommitCert, 3, preferredPeer);

        //for two chain commit
        var eventRes = retrieveEventForQc(highestCommitCert, 2, preferredPeer);
        if (!eventRes.isSuccess()) {
            return ProcessResult.ofError(eventRes.getErrMsg());
        }

        List<Event> events = eventRes.result;

        if (logger.isDebugEnabled()) {
            logger.debug("start fastForwardSync eventRes![{}]", events);
        }

        Assert.ensure(Arrays.equals(highestCommitCert.getCommitEvent().getId(), events.get(1).getId()),
                "should have 2-chain and equal");

//        Assert.ensure(Arrays.equals(highestCommitCert.getCommitEvent().getId(), events.get(2).getId()),
//                "should have 3-chain and equal");

        List<QuorumCert> quorumCerts = new ArrayList<>(2);
        quorumCerts.add(highestCommitCert);
        quorumCerts.add(events.get(0).getQuorumCert());
        //quorumCerts.add(events.get(1).getQuorumCert());

        for (int i = 0; i < 2; i++) {
            Assert.ensure(Arrays.equals(events.get(i).getId(), quorumCerts.get(i).getCertifiedEvent().getId()),
                    "un except event and qc pair!");
        }

//        var crossChainSyncRes = ivyCrossChainSyncCoordinator.crossChainFastForwardSync(events.get(0).getPayload(), preferredPeer);
//        if (!crossChainSyncRes.isSuccess()) {
//            return ProcessResult.ofError("fastForwardSync error:" + crossChainSyncRes.getErrMsg());
//        }
        try {
            // If a node restarts in the middle of state synchronization, it is going to try to catch up
            // to the stored quorum certs as the new root.
            this.eventTreeStore.getLivenessStorage().saveTree(events, quorumCerts);
        } catch (Exception e) {
            return ProcessResult.ofError(e.getMessage());
        }


        //for two chain commit
        Event commitEvent = events.get(1);
        QuorumCert commitQC = quorumCerts.get(1);

        //for three chain commit
        //Event commitEvent = events.get(2);
        //QuorumCert commitQC = quorumCerts.get(2);

        // 同步 commit cert 之前的event(block)
        logger.info("do sync from fastForwardSync ledger: {}", highestCommitCert.getLedgerInfoWithSignatures().getLedgerInfo());
        ProcessResult<Void> syncProcessRes = consensusEventExecutor.syncTo(highestCommitCert.getLedgerInfoWithSignatures(), preferredPeer);
        if (!syncProcessRes.isSuccess()) {
            ProcessResult.ofError(syncProcessRes.getErrMsg());
        }

        logger.debug("fastForwardSync success!");
        LivenessStorageData storageData = eventTreeStore.getLivenessStorage().start();
        if (!(storageData instanceof LivenessStorageData.RecoveryData)) {
            return ProcessResult.ofError("Failed to construct recovery data after fast forward sync");
        }

        return ProcessResult.ofSuccess((LivenessStorageData.RecoveryData)storageData);
    }
//========================end  fastForwardSync======================================================

    private ProcessResult<List<Event>> retrieveEventForQc(QuorumCert qc, int eventsNum, PeerId preferredPeer) {
        byte[] eventId = qc.getCertifiedEvent().getId();
        EventRetrievalRequestMsg eventRetrievalRequestMsg = new EventRetrievalRequestMsg(eventId, eventsNum);
        Message response = netInvoker.rpcSend(preferredPeer, eventRetrievalRequestMsg, 5000);
        if (response == null) {
            logger.warn(
                    "Failed to fetch event {} from {}, try again",
                    Hex.toHexString(eventId), preferredPeer);

            return ProcessResult.ofError(String.format("Failed to fetch event %s from %s", Hex.toHexString(eventId), preferredPeer));

        }

        EventRetrievalResponseMsg retrievalResponseMsg = new EventRetrievalResponseMsg(response.getEncoded());
        if (retrievalResponseMsg.getStatus() != SUCCESS) {
            logger.error(
                    "Failed to fetch event {} from {} ,status: {}",
                    Hex.toHexString(eventId), preferredPeer, retrievalResponseMsg.getStatus());

            return ProcessResult.ofError(String.format("Failed to fetch event %s from %s ,status: %s !", Hex.toHexString(eventId), preferredPeer, retrievalResponseMsg.getStatus()));
        }
        return ProcessResult.ofSuccess(retrievalResponseMsg.getEvents());
    }


    public void release() {
        this.eventTreeStore = null;
        this.txnManager = null;
        this.consensusEventExecutor = null;
        this.netInvoker = null;
    }
}
