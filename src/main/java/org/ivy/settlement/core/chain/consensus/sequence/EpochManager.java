package org.ivy.settlement.core.chain.consensus.sequence;

import io.libp2p.core.peer.PeerId;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.consensus.sequence.executor.ConsensusEventExecutor;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.*;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.consensus.sequence.model.chainConfig.OnChainConfigPayload;
import org.ivy.settlement.core.chain.consensus.sequence.safety.SafetyRules;
import org.ivy.settlement.core.chain.consensus.sequence.safety.settlement.SettlementChainsVerifier;
import org.ivy.settlement.core.chain.consensus.sequence.store.EventTreeStore;
import org.ivy.settlement.core.chain.consensus.sequence.store.LivenessStorageData;
import org.ivy.settlement.core.chain.consensus.sequence.store.PersistentLivenessStore;
import org.ivy.settlement.core.chain.txpool.TxnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * description:
 * @author carrot
 */
public class EpochManager {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    IrisCoreSystemConfig config;

    ConsensusNetInvoker netInvoker;

    ConsensusEventExecutor consensusEventExecutor;

    TxnManager txnManager;

    PersistentLivenessStore livenessStorage;

    ConsensusProcessor msgProcessor;

    SafetyRules safetyRules;

    SettlementChainsVerifier settlementChainsVerifier;

    //author-> public key
    public EpochManager(ConsensusNetInvoker netInvoker, ConsensusEventExecutor consensusEventExecutor, TxnManager txnManager, PersistentLivenessStore livenessStorage, SettlementChainsVerifier settlementChainsVerifier, IrisCoreSystemConfig irisCoreSystemConfig) {
        this.netInvoker = netInvoker;
        this.consensusEventExecutor = consensusEventExecutor;
        this.txnManager = txnManager;
        this.livenessStorage = livenessStorage;
        this.config = irisCoreSystemConfig;
        this.settlementChainsVerifier = settlementChainsVerifier;
        this.safetyRules = new SafetyRules(irisCoreSystemConfig.getMyKey());
    }

//========================start startProcess===============================
    public void startProcess(OnChainConfigPayload payload) {
        EpochState epochState = payload.getEpochState();
        LivenessStorageData livenessStorageData = this.livenessStorage.start();
        if (msgProcessor != null) {
            msgProcessor.releaseResource();
        }

        if (livenessStorageData instanceof LivenessStorageData.RecoveryData) {
            startRoundProcess((LivenessStorageData.RecoveryData) livenessStorageData, epochState);
        } else if (livenessStorageData instanceof LivenessStorageData.LedgerRecoveryData) {
            startSyncProcessor((LivenessStorageData.LedgerRecoveryData) livenessStorageData, epochState);
        } else {
            logger.error("un expect type");
        }
    }

    private void startRoundProcess(LivenessStorageData.RecoveryData recoveryData, EpochState epochState) {
        logger.info("Starting {} with genesis {}", recoveryData, epochState);

        logger.info("Create EventTreeStore");
        var lastVote = recoveryData.getLastVote();
        var eventTreeStore = new EventTreeStore(livenessStorage, recoveryData, consensusEventExecutor, txnManager, config.getMaxPrunedEventsInMemory(), config.reimportUnCommitEvent());

        logger.info("Update SafetyRules");
        safetyRules.initialize(epochState);

        logger.info("Create ProposalGenerator");
        var proposalGenerator = new ProposalGenerator(safetyRules.getAuthor(), eventTreeStore, txnManager, settlementChainsVerifier);

        logger.info("Create RoundState");
        var roundState = new RoundState(config.getRoundTimeoutBaseMS(), 1.5, 4);

        logger.info("Create ProposerElection");
        var proposerElection = createProposerElection(epochState);

        logger.info("Update NetInvoker");
        //NetInvoker netInvoker = new NetInvoker(new PeerManager(this.config));
        netInvoker.updateEligibleNodes(epochState, consensusEventExecutor);

        var roundMsgProcessor = new RoundMsgProcessor(
                epochState,
                eventTreeStore,
                roundState,
                proposerElection,
                proposalGenerator,
                safetyRules,
                netInvoker,
                txnManager,
                new HotStuffChainSyncCoordinator(eventTreeStore, consensusEventExecutor, netInvoker, this.txnManager, this.settlementChainsVerifier),
                livenessStorage, settlementChainsVerifier);
        roundMsgProcessor.start(lastVote);
        this.msgProcessor = roundMsgProcessor;
        logger.info("RoundManager started");
    }

    private ProposerElection createProposerElection(EpochState epochState) {
        return switch (config.getProposerType()) {
            case ProposerElection.RotatingProposer ->
                    new RotatingProposerElection(epochState.getOrderedPublishKeys(), config.getContiguousRounds());
            default -> null;
        };
    }

    private void startSyncProcessor(LivenessStorageData.LedgerRecoveryData ledgerRecoveryData, EpochState epochState) {
        this.netInvoker.updateEligibleNodes(epochState, consensusEventExecutor);
        this.msgProcessor = new RecoveryMsgProcessor(epochState, netInvoker, consensusEventExecutor, new HotStuffChainSyncCoordinator(new EventTreeStore(this.livenessStorage, config.getMaxPrunedEventsInMemory(), config.reimportUnCommitEvent()), consensusEventExecutor, netInvoker, this.txnManager, this.settlementChainsVerifier), ledgerRecoveryData.getCommitRound());
    }
//========================end startProcess===============================


//========================start processMessage===============================
    public void processMessage(ConsensusMsg consensusMsg) {
        if (processEpoch(consensusMsg)) {
            var processResult = msgProcessor.process(consensusMsg);
            if ((msgProcessor instanceof RecoveryMsgProcessor) && processResult.isSuccess()) {
                logger.info("Recovered from RecoveryMsgProcessor");
                msgProcessor.releaseResource();
                startRoundProcess((LivenessStorageData.RecoveryData)processResult.getResult(), consensusEventExecutor.getLatestLedgerInfo().getCurrentEpochState());
            }
        }
    }

    private boolean processEpoch(ConsensusMsg consensusMsg) {
        switch (consensusMsg.getCommand()) {
            case PROPOSAL, VOTE, HOTSTUFF_CHAIN_SYNC -> {
                if (this.getEpoch() == consensusMsg.getEpoch()) {
                    return true;
                } else {
                    if (consensusMsg.getNodeId() == null) {
                        //if means peer node has bug or self msg, we can ignore
                        return false;
                    }
                    processDifferentEpoch(consensusMsg.getPeerId(), consensusMsg.getEpoch());
                }
            }
            case LOCAL_TIMEOUT, EVENT_RETRIEVAL_REQ, CROSS_CHAIN_RETRIEVAL_REQ -> {
                return true;
            }
            case LATEST_LEDGER_REQ -> {
                LatestLedgerInfoRequestMsg request = (LatestLedgerInfoRequestMsg) consensusMsg;
                processLatestLedgerReq(request);
            }
            case LATEST_LEDGER_RESP -> {
                LatestLedgerInfoResponseMsg response = (LatestLedgerInfoResponseMsg) consensusMsg;
                doSync(response);
            }
            default -> logger.warn("Unexpected messages: {}", consensusMsg);
        }
        return false;
    }

    private void processDifferentEpoch(PeerId peerId, long differentEpoch) {
        if (differentEpoch < getEpoch()) {
            netInvoker.directSend(peerId, genLatestLedgerInfoResponseMsg());
        } else if (differentEpoch > getEpoch()) {
            ledgerSync(peerId);
        } else {
            logger.warn("Same epoch should not come to process_different_epoch");
        }
    }

    private void ledgerSync(PeerId peerId) {
        var requestMsg = new LatestLedgerInfoRequestMsg();
        var response = netInvoker.rpcSend(peerId, requestMsg);
        if (response == null) {
            logger.warn("ledgerSync error!");
            return;
        }
        var latestLedgerInfoResponseMsg = new LatestLedgerInfoResponseMsg(response.getEncoded());
        latestLedgerInfoResponseMsg.setNodeId(peerId.bytes);
        doSync(latestLedgerInfoResponseMsg);
    }

    private void processLatestLedgerReq(LatestLedgerInfoRequestMsg request) {
        var response = genLatestLedgerInfoResponseMsg();
        response.setRpcId(request.getRpcId());
        netInvoker.rpcResponse(request.getPeerId(), response);
    }

    private void doSync(LatestLedgerInfoResponseMsg response) {
        if (logger.isDebugEnabled()) {
            logger.debug("receive LatestLedgerInfoResponseMsg:{}", response);
        }
        if (response.getStatus() != LatestLedgerInfoResponseMsg.LedgerRetrievalStatus.SUCCESSED) {
            return;
        }

        if (response.getLatestLedger().getLedgerInfo().getNumber() <= consensusEventExecutor.getLatestLedgerInfo().getLatestLedgerInfo().getLedgerInfo().getNumber()) {
            return;
        }

        try {
            // todo :: check the LatestLedgerInfoResponseMsg for signature
            var highestCommitCert = response.getLatestCommitQC();
            var events = response.getTwoChainEvents();

            var validEvents = new ArrayList<Event>();
            validEvents.add(events.get(0));

            var quorumCerts = new ArrayList<QuorumCert>(2);
            quorumCerts.add(highestCommitCert);

            // avoid epoch change case
            if (!Arrays.equals(events.get(0).getId(), events.get(1).getId())) {
                validEvents.add(events.get(1));
                quorumCerts.add(events.get(0).getQuorumCert());
            }

            for (var i = 0; i < validEvents.size(); i++) {
                if (!Arrays.equals(events.get(i).getId(), quorumCerts.get(i).getCertifiedEvent().getId())) {
                    throw new RuntimeException("do sync, un expect event id match!");
                }
            }

            this.msgProcessor.saveTree(validEvents, quorumCerts);

            logger.info("syncTo[{}]-[{}]-[{}]", events.get(1).getEventData().getNumber(), Hex.toHexString(events.get(1).getEventData().getHash()), response.getLatestLedger().getLedgerInfo());

            consensusEventExecutor.syncTo(response.getLatestLedger(), response.getPeerId());
        } catch (Exception e) {
            logger.warn("epoch manager doSync error!{}", ExceptionUtils.getStackTrace(e));
        }
        startProcess(OnChainConfigPayload.build(consensusEventExecutor.getLatestLedgerInfo().getCurrentEpochState()));
    }

    private LatestLedgerInfoResponseMsg genLatestLedgerInfoResponseMsg() {

        if (msgProcessor == null || msgProcessor instanceof RecoveryMsgProcessor) {
            return new LatestLedgerInfoResponseMsg(LatestLedgerInfoResponseMsg.LedgerRetrievalStatus.CURRENT_NODE_IS_SYNCING, null, null, null);
        }

        //var threeChainPair = ((RoundMsgProcessor)msgProcessor).eventTreeStore.getThreeChainCommitPair();
        var twoChainPair = ((RoundMsgProcessor)msgProcessor).eventTreeStore.getTwoChainCommitPair();

        //return new LatestLedgerInfoResponseMsg(LatestLedgerInfoResponseMsg.LedgerRetrievalStatus.SUCCESSED, consensusEventExecutor.getLatestLedgerInfo().getLatestLedgerInfo(), threeChainPair.getKey(), threeChainPair.getValue());
        return new LatestLedgerInfoResponseMsg(LatestLedgerInfoResponseMsg.LedgerRetrievalStatus.SUCCESSED, consensusEventExecutor.getLatestLedgerInfo().getLatestLedgerInfo(), twoChainPair.getKey(), twoChainPair.getValue());


        //new LatestLedgerInfoResponseMsg(consensusEventExecutor.getLatestLedgerInfo().getLatestLedgerInfo());
    }
//========================end processMessage===============================

    public boolean isSyncing() {
        return this.consensusEventExecutor.isSyncing();
    }

    public ValidatorVerifier getVerifier() {
        return getEpochState().getValidatorVerifier();
    }

    private long getEpoch() {
        return this.getEpochState().getEpoch();
    }

    private EpochState getEpochState() {
        if (this.msgProcessor == null) {
            logger.error("EpochManager not started yet");

            throw  new RuntimeException("EpochManager not started yet");
        }

        return this.msgProcessor.getEpochState();
    }
}
