package org.ivy.settlement.core.chain.consensus.sequence.store;

import org.apache.commons.collections4.CollectionUtils;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * description:
 * @author carrot
 */
public class PersistentLivenessStore {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    ConsensusChainStore consensusChainStore;

    public PersistentLivenessStore(ConsensusChainStore consensusChainStore) {
        this.consensusChainStore = consensusChainStore;
    }

    public LivenessStorageData start() {
        logger.info("Start consensus recovery!");
        var lastVote = consensusChainStore.getLastVoteMsg();
        var highestTimeoutCertificate = consensusChainStore.getHighestTimeoutCertificate();
        var highestTwoChainTimeoutCertificate = consensusChainStore.getHighestTwoChainTimeoutCertificate();
        var events = consensusChainStore.getAllEvents();
        var quorumCerts = consensusChainStore.getAllQuorumCerts();
        var latestLedger = consensusChainStore.getLatestLedger();

//        if (!latestLedger.getLatestLedgerInfo().getLedgerInfo().getCrossChainOffsets().equals(latestLedger.getLatestCrossChainOffsets())) {
//            logger.error("un except start state, ledger offsets[{}], record offsets[{}]", latestLedger.getLatestLedgerInfo().getLedgerInfo().getCrossChainOffsets(), latestLedger.getLatestCrossChainOffsets());
//            throw new RuntimeException("un except start state, ledger offsets");
//        }

        // change the epoch
        var ledgerRecoveryData = LivenessStorageData.LedgerRecoveryData.build(latestLedger.getLatestLedgerInfo().getLedgerInfo(), latestLedger.getLatestCrossChainOffsets());
        try {
            LivenessStorageData.RecoveryData recoveryData =
                    LivenessStorageData.RecoveryData
                            .build(this.consensusChainStore, lastVote,
                                    ledgerRecoveryData,
                                    events,
                                    quorumCerts,
                                    latestLedger.getCommitExecutedEventOutput(),
                                    highestTimeoutCertificate,
                                    highestTwoChainTimeoutCertificate);

            this.pruneTree(recoveryData.takeEventsToPrune());

            if (recoveryData.getLastVote().isEmpty()) {
                this.consensusChainStore.deleteLastVoteMsg();
            }

            if (recoveryData.getHighestTimeoutCertificate().isEmpty()) {
                this.consensusChainStore.deleteHighestTimeoutCertificate();
            }

            if (recoveryData.getHighestTwoChainTimeoutCertificate().isEmpty()) {
                this.consensusChainStore.deleteHighestTwoChainTimeoutCertificate();
            }
            return recoveryData;
        } catch (Exception e) {
            logger.warn("will start RecoveryMsgProcessor!");
            return ledgerRecoveryData;
        }
    }

    public void saveTree(List<Event> events, List<QuorumCert> qcs) {
        consensusChainStore.saveEventsAndQuorumCertificates(events, qcs);
    }

    public void saveHighestTimeoutCertificate(TimeoutCertificate highestTimeoutCertificate) {
        consensusChainStore.saveHighestTimeoutCertificate(highestTimeoutCertificate);
    }

    public void saveHighestTwoChainTimeoutCertificate(TwoChainTimeoutCertificate highestTwoChainTimeoutCertificate) {
        consensusChainStore.saveHighestTwoChainTimeoutCertificate(highestTwoChainTimeoutCertificate);
    }

    public void pruneTree(List<byte[]> eventIds) {
        if (CollectionUtils.isEmpty(eventIds)) return;
        consensusChainStore.deleteEventsAndQuorumCertificates(eventIds);
    }

    public void saveVote(Vote vote) {
        consensusChainStore.saveLastVoteMsg(vote);
    }
}
