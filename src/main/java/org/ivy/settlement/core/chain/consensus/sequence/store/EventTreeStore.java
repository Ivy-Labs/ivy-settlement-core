package org.ivy.settlement.core.chain.consensus.sequence.store;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.infrastructure.anyhow.Assert;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.core.chain.consensus.sequence.ChainedBFT;
import org.ivy.settlement.core.chain.consensus.sequence.executor.ConsensusEventExecutor;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.consensus.sequence.model.chainConfig.OnChainConfigPayload;
import org.ivy.settlement.core.chain.txpool.TxnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * description:
 * @author carrot
 */
public class EventTreeStore {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    private EventTree eventTree;

    private ConsensusEventExecutor consensusEventExecutor;

    public PersistentLivenessStore livenessStorage;

    int maxPrunedEventsInMemory;

    boolean reimportUnCommitEvent;

    private TxnManager txnManager;

    public EventTreeStore(PersistentLivenessStore livenessStorage, LivenessStorageData.RecoveryData initialData, ConsensusEventExecutor consensusEventExecutor, TxnManager txnManager, int maxPrunedEventsInMemory, boolean reimportUnCommitEvent) {
        this.consensusEventExecutor = consensusEventExecutor;
        this.livenessStorage = livenessStorage;
        this.maxPrunedEventsInMemory = maxPrunedEventsInMemory;
        this.txnManager = txnManager;
        this.reimportUnCommitEvent = reimportUnCommitEvent;
        if (initialData != null) {
            var highestTc = initialData.getHighestTimeoutCertificate();
            var highestTwoChainTc = initialData.getHighestTwoChainTimeoutCertificate();
            this.eventTree = buildEventTree(initialData, highestTc, highestTwoChainTc);
            doRecovery(initialData);
        }
    }

    public void releaseResource() {
        eventTree.txnManager = null;
        eventTree = null;
        txnManager = null;
        consensusEventExecutor = null;
        livenessStorage = null;
    }

    private EventTree buildEventTree(LivenessStorageData.RecoveryData initialData, Optional<TimeoutCertificate> highestTc, Optional<TwoChainTimeoutCertificate> highestTwoChainTc) {
        var root = initialData.getRoot();
        var executedEventOutput = initialData.getExecutedEventOutput();
        Assert.ensure(root.rootQc.getCertifiedEvent().getNumber() == executedEventOutput.getEventNumber(),
                "root qc version {} doesn't match committed trees {}", root.rootQc.getCertifiedEvent().getNumber(), executedEventOutput.getEventNumber());

        Assert.ensure(Arrays.equals(root.rootQc.getCertifiedEvent().getExecutedStateId(), executedEventOutput.getStateRoot()),
                "root qc state id {} doesn't match committed trees {}", Hex.toHexString(root.rootQc.getCertifiedEvent().getExecutedStateId()), Hex.toHexString(executedEventOutput.getStateRoot()));

        var rootExecutedOutput = new ExecutedEventOutput(executedEventOutput.getEventNumber(), executedEventOutput.getStateHash(), executedEventOutput.getStateRoot(), executedEventOutput.getOutput(), root.rootQc.getCertifiedEvent().getNewCurrentEpochState(), root.rootQc.getCertifiedEvent().getNewNextEpochState());
        //executedEventOutput.resetEpcohState();
        var executedEvent = new ExecutedEvent(root.rootEvent, rootExecutedOutput);

        return new EventTree(this.txnManager, executedEvent, root.rootQc, root.rootLi, highestTc, highestTwoChainTc, this.maxPrunedEventsInMemory, this.reimportUnCommitEvent);
    }

    public void rebuild(LivenessStorageData.RecoveryData initialData) {
        // Rollover the previous highest TC from the old tree to the new one.
        var preHtc = this.getHighestTimeoutCert();
        var preHTwoChainTc = this.getHighestTwoChainTimeoutCert();
        this.eventTree = buildEventTree(initialData, preHtc, preHTwoChainTc);
        doRecovery(initialData);

        // If we fail to commit B_i via state computer and crash, after restart our highest commit cert
        // will not match the latest commit B_j(j<i) of state computer.
        // This introduces an inconsistent state if we send out SyncInfo and others try to sync to
        // B_i and figure out we only have B_j.
        // Here we commit up to the highest_commit_cert to maintain highest_commit_cert == state_computer.committed_trees.
        if (getHighestCommitCert().getCommitEvent().getRound() > getRoot().getRound()) {
            var hqc = getHighestCommitCert();
            var finalityProof = hqc.getLedgerInfoWithSignatures();
            //var latestOffsets = this.getEvent(hqc.getCertifiedEvent().getId()).getEvent().getEventData().getPayload().getCrossChainOffsets();
            //var latestOffsets = this.getEvent(hqc.getCommitEvent().getId()).getEvent().getEventData().getPayload().getCrossChainOffsets();
            commit(finalityProof, null);
        }
    }

    private void doRecovery(LivenessStorageData.RecoveryData initialData) {
        var root = initialData.getRoot();
        for (var event : initialData.events) {
            // root event has been execute ,ignore
            if (Arrays.equals(event.getId(), root.getRootEvent().getId())) continue;
            logger.info("doRecovery event  epoch[{}] round[{}] number[{}] rootId:[{}]", event.getEpoch(), event.getRound(), event.getEventNumber(), Hex.toHexString(event.getId()));
            var exeAndInsertRes = executeAndInsertEvent(event);
            if (!exeAndInsertRes.isSuccess()) {
                logger.error("[BlockStore] failed to insert event during build error :{}", exeAndInsertRes.getErrMsg());
                System.exit(-1);
            }
        }

        for (var qc : initialData.quorumCerts) {
            if (Arrays.equals(qc.getCertifiedEvent().getId(), root.getRootEvent().getId())) continue;

            var insertQCRes = insertSingleQuorumCert(qc);
            if (!insertQCRes.isSuccess()) {
                logger.error("[BlockStore] failed to insert qc during build error :{}", insertQCRes.getErrMsg());
                System.exit(-1);
            }
        }
    }

    public ProcessResult<ExecutedEvent> executeAndInsertEvent(Event event) {
        try {
            var oldExecutedEvent = getEvent(event.getId());
            if (oldExecutedEvent != null) return ProcessResult.ofSuccess(oldExecutedEvent);

            var executedEvent = executeEvent(event);

            livenessStorage.saveTree(List.of(event), Collections.emptyList());
            this.eventTree.insertEvent(executedEvent);
            return ProcessResult.ofSuccess(executedEvent);
        } catch (Exception e) {
            logger.error("executeAndInsertEvent error!{}", ExceptionUtils.getStackTrace(e));
            return ProcessResult.ofError(e.getMessage());
        }
    }

    private ExecutedEvent executeEvent(Event event) {
        Assert.ensure(getRoot().getRound() < event.getRound(),
                "Event with old round");
        var parentEvent = getEvent(event.getParentId());

        Assert.ensure(parentEvent != null,
                "Event with missing parent {}", Hex.toHexString(event.getParentId()));

        // Reconfiguration rule - if a block is a child of pending reconfiguration, it needs to be empty
        // So we roll over the executed state until it's committed and we start new epoch.
        ExecutedEventOutput executedOutput;
        if ((parentEvent.getExecutedEventOutput().hasReconfiguration() && !Arrays.equals(parentEvent.getId(), this.eventTree.rootId))) {
            executedOutput = parentEvent.getExecutedEventOutput();
            //executedOutput = new ExecutedEventOutput(parentEvent.getStateOutput(), parentEvent.getEventNumber(), parentEvent.getStateRoot(), parentEvent.getExecutedEventOutput().getEpochState());
        } else {
            // need to deal with empty event
            // Although NIL events don't have payload, we still send a T::default() to compute
            // because we may inject a event prologue transaction.
            executedOutput = consensusEventExecutor.execute(event, parentEvent);
        }

        return new ExecutedEvent(event, executedOutput);
    }

    public ProcessResult<Void> insertSingleQuorumCert(QuorumCert qc) {
        try {
            //eventTreeLock.writeLock().lock();
            // If the parent event is not the root event (i.e not None), ensure the executed state
            // of a event is consistent with its QuorumCert, otherwise persist the QuorumCert's
            // state and on restart, a new execution will agree with it.  A new execution will match
            // the QuorumCert's state on the next restart will work if there is a memory
            // corruption, for example.
            var executedEvent = getEvent(qc.getCertifiedEvent().getId());
            Assert.ensure(executedEvent != null,
                    "QuorumCert for event {} not found", Hex.toHexString(qc.getCertifiedEvent().getId()));
            Assert.ensure(executedEvent.getEventInfo().equals(qc.getCertifiedEvent()),
                    "QC for event {} has different {} than local {}", Hex.toHexString(qc.getCertifiedEvent().getId()), qc.getCertifiedEvent(), executedEvent.getEventInfo());

            executedEvent.setSignatures(qc.getLedgerInfoWithSignatures().getSignatures());

            this.livenessStorage.saveTree(Collections.emptyList(), List.of(qc));

            this.eventTree.insertQC(qc);
            return ProcessResult.SUCCESSFUL;
        } catch (Exception e) {
            logger.error("insertSingleQuorumCert {} warn!{}", qc, ExceptionUtils.getStackTrace(e));
            return ProcessResult.ofError(e.getMessage());
        } finally {
            //eventTreeLock.writeLock().unlock();
        }
    }

    public ProcessResult<Void> insertTimeoutCertificate(TimeoutCertificate timeoutCertificate) {
        try {
            var timeoutCertificateOptional = this.getHighestTimeoutCert();
            var currentTcRound = timeoutCertificateOptional.map(TimeoutCertificate::getRound).orElse(0L);

            if (timeoutCertificate.getRound() <= currentTcRound) return ProcessResult.SUCCESSFUL;

            this.livenessStorage.saveHighestTimeoutCertificate(timeoutCertificate);

            this.eventTree.replaceTimeoutCert(timeoutCertificate);
            return ProcessResult.SUCCESSFUL;
        } catch (Exception e) {
            logger.error("insertTimeoutCertificate {} error!{}", timeoutCertificate, ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    public ProcessResult<Void> insertTwoChainTimeoutCertificate(TwoChainTimeoutCertificate twoChainTimeoutCertificate) {
        var currentTcRound = this.getHighestTwoChainTimeoutCert().map(TwoChainTimeoutCertificate::getRound).orElse(0L);
        if (twoChainTimeoutCertificate.getRound() <= currentTcRound) return ProcessResult.SUCCESSFUL;

        this.livenessStorage.saveHighestTwoChainTimeoutCertificate(twoChainTimeoutCertificate);
        this.eventTree.replaceTwoChainTimeoutCert(twoChainTimeoutCertificate);
        return ProcessResult.SUCCESSFUL;
    }

    public ProcessResult<Void> commit(LedgerInfoWithSignatures finalityProof, Function<HotstuffChainSyncInfo, Void> broadcastFun) {
        try {
            var eventIdToCommit = finalityProof.getLedgerInfo().getConsensusEventId();
            var eventToCommit = getEvent(eventIdToCommit);
            if (eventToCommit == null) {
                var errorInfo = String.format("Committed event [%s] not found", Hex.toHexString(eventIdToCommit));
                logger.error(errorInfo);
                throw new RuntimeException(errorInfo);
            }
            var latestOffsets = eventToCommit.getEvent().getPayload().getCrossChainOffsets();
            var eventsToCommit = pathFromRoot(eventIdToCommit);
            consensusEventExecutor.commit(eventsToCommit, latestOffsets, finalityProof);
            pruneTree(eventToCommit.getId());

            if (finalityProof.getLedgerInfo().getNewCurrentEpochState().isPresent()) {
                if (broadcastFun != null) {
                    broadcastFun.apply(this.getHotstuffChainSyncInfo());
                }
                ChainedBFT.publishConfigPayload(OnChainConfigPayload.build(finalityProof.getLedgerInfo().getNewCurrentEpochState().get()));
            }
            return ProcessResult.SUCCESSFUL;
        } catch (Exception e) {
            logger.error("EventTreeStore.commit {} error!{}", finalityProof, ExceptionUtils.getStackTrace(e));
            throw new RuntimeException(e);
        }
    }

    public void pruneTree(byte[] nextRootId) {
        var id2Remove = this.eventTree.findEventToPrune(nextRootId);

        if (logger.isDebugEnabled()) {
            logger.debug("event trace trace, pruneTree id2Remove [{}]", id2Remove);
        }

        this.livenessStorage.pruneTree(id2Remove.stream().map(ByteArrayWrapper::getData).collect(Collectors.toList()));
        this.eventTree.processPrunedEvents(nextRootId, id2Remove);
    }

    //================================start do read
    public Pair<QuorumCert, List<Event>> getThreeChainCommitPair() {
        return eventTree.getThreeChainCommitPair();
    }

    public Pair<QuorumCert, List<Event>> getTwoChainCommitPair() {
        return eventTree.getTwoChainCommitPair();
    }

    public ExecutedEvent getRoot() {
        return eventTree.getRootEvent();
    }

    public ExecutedEvent getEvent(byte[] eventId) {
        return eventTree.getEvent(eventId);
    }

    public boolean eventExists(byte[] eventId) {
        return eventTree.eventExists(eventId);
    }

    public QuorumCert getQCForEvent(byte[] eventId) {
        return eventTree.getQCForEvent(eventId);
    }

    public List<ExecutedEvent> pathFromRoot(byte[] eventId) {
        return eventTree.pathFromRoot(eventId);
    }

    public ExecutedEvent getHighestCertifiedEvent() {
        return eventTree.getHighestCertifiedEvent();
    }

    public QuorumCert getHighestQuorumCert() {
        return eventTree.getHighestQuorumCert();
    }

    public QuorumCert getHighestCommitCert() {
        return eventTree.getHighestCommitCert();
    }

    public Optional<TimeoutCertificate> getHighestTimeoutCert() {
        return eventTree.getHighestTimeoutCert();
    }

    public Optional<TwoChainTimeoutCertificate> getHighestTwoChainTimeoutCert() {
        return eventTree.getHighestTwoChainTimeoutCert();
    }

    public HotstuffChainSyncInfo getHotstuffChainSyncInfo() {
        return HotstuffChainSyncInfo.buildWithoutEncode(this.getHighestQuorumCert(),
                this.getHighestCommitCert(),
                this.getHighestTwoChainTimeoutCert());
    }
    //================================end do read


    //===================for RecoveryMsgProcessor==============
    public EventTreeStore(PersistentLivenessStore livenessStorage, int maxPrunedEventsInMemory, boolean reimportUnCommitEvent) {
        this.livenessStorage = livenessStorage;
        this.maxPrunedEventsInMemory = maxPrunedEventsInMemory;
        this.reimportUnCommitEvent = reimportUnCommitEvent;
    }

    public PersistentLivenessStore getLivenessStorage() {
        return livenessStorage;
    }

    public boolean isStateConsistent() {
        return this.consensusEventExecutor.isStateConsistent();
    }
}
