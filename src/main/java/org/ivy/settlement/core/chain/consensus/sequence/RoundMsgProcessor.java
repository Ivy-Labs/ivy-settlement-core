package org.ivy.settlement.core.chain.consensus.sequence;

import io.libp2p.core.peer.PeerId;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ivy.settlement.infrastructure.anyhow.Assert;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.core.chain.consensus.sequence.model.RetrievalStatus;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.ProposalGenerator;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.ProposerElection;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.RoundState;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainRetrievalRequestMsg;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainRetrievalResponseMsg;
import org.ivy.settlement.core.chain.consensus.sequence.safety.SafetyRules;
import org.ivy.settlement.core.chain.consensus.sequence.safety.settlement.SettlementChainsVerifier;
import org.ivy.settlement.core.chain.consensus.sequence.store.EventTreeStore;
import org.ivy.settlement.core.chain.consensus.sequence.store.PersistentLivenessStore;
import org.ivy.settlement.core.chain.txpool.TxnManager;
import org.spongycastle.util.encoders.Hex;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


/**
 * description:
 * @author carrot
 */
public class RoundMsgProcessor extends ConsensusProcessor<Void> {

    RoundState roundState;

    EventTreeStore eventTreeStore;

    PersistentLivenessStore livenessStorage;

    TxnManager txnManager;

    HotStuffChainSyncCoordinator hotStuffChainSyncCoordinator;

    ProposalGenerator proposalGenerator;

    ProposerElection proposerElection;

    SafetyRules safetyRules;

    ConsensusNetInvoker netInvoker;

    SettlementChainsVerifier settlementChainsVerifier;

    IvySettlementChainsSyncCoordinator ivySettlementChainsSyncCoordinator;

    public RoundMsgProcessor(EpochState epochState, EventTreeStore eventTreeStore, RoundState roundState,
                             ProposerElection proposerElection, ProposalGenerator proposalGenerator,
                             SafetyRules safetyRules, ConsensusNetInvoker netInvoker, TxnManager txnManager,
                             HotStuffChainSyncCoordinator hotStuffChainSyncCoordinator,
                             PersistentLivenessStore livenessStorage,
                             SettlementChainsVerifier settlementChainsVerifier) {
        this.epochState = epochState;
        this.eventTreeStore = eventTreeStore;
        this.roundState = roundState;
        this.proposerElection = proposerElection;
        this.proposalGenerator = proposalGenerator;
        this.safetyRules = safetyRules;
        this.txnManager = txnManager;
        this.netInvoker = netInvoker;
        this.livenessStorage = livenessStorage;
        this.hotStuffChainSyncCoordinator = hotStuffChainSyncCoordinator;
        this.settlementChainsVerifier = settlementChainsVerifier;
        this.ivySettlementChainsSyncCoordinator = new IvySettlementChainsSyncCoordinator(netInvoker, settlementChainsVerifier);
    }

    public void releaseResource() {
        this.epochState = null;
        this.eventTreeStore.releaseResource();
        this.eventTreeStore = null;
        this.roundState = null;
        this.proposerElection = null;
        this.proposalGenerator = null;
        this.safetyRules = null;
        this.txnManager = null;
        this.netInvoker = null;
        this.livenessStorage = null;
        this.hotStuffChainSyncCoordinator.release();
        this.hotStuffChainSyncCoordinator = null;
        this.ivySettlementChainsSyncCoordinator.release();
        this.ivySettlementChainsSyncCoordinator = null;
    }

    public void start(Optional<Vote> lastVoteSent) {
        RoundState.NewRoundEvent newRoundEvent = roundState.processCertificates(this.eventTreeStore.getHotstuffChainSyncInfo());
        if (newRoundEvent == null) {
            logger.error("Can not jump start a round_state from existing certificates.");
            return;
        }
        lastVoteSent.ifPresent(vote -> this.roundState.recordVote(vote));
        processNewRoundLocalMsg(newRoundEvent);
    }

    @Override
    public ProcessResult<Void> process(ConsensusMsg consensusMsg) {
        ProcessResult<Void> verifyRes = consensusMsg.verify(this.epochState.getValidatorVerifier());
        if (!verifyRes.isSuccess()) {
            logger.warn("verify msg {} fail, reason:{}", consensusMsg, verifyRes.getErrMsg());
            return ProcessResult.ofError("verify msg error!");
        }

        switch (consensusMsg.getCommand()) {
            case PROPOSAL -> processProposalMsg((ProposalMsg) consensusMsg);
            case VOTE -> processVoteMsg((VoteMsg) consensusMsg);
            case HOTSTUFF_CHAIN_SYNC -> processChainSyncInfoMsg((HotstuffChainSyncInfo) consensusMsg);
            case LOCAL_TIMEOUT -> processLocalTimeout((RoundState.LocalTimeoutMsg) consensusMsg);
            case EVENT_RETRIEVAL_REQ -> processEventRetrieval((EventRetrievalRequestMsg) consensusMsg);
            case CROSS_CHAIN_RETRIEVAL_REQ -> processCrossChainRetrieval((SettlementChainRetrievalRequestMsg) consensusMsg);
            default -> {
            }
        }
        return ProcessResult.SUCCESSFUL;
    }


    //====================start processNewRoundLocalMsg============================
    public void processNewRoundLocalMsg(RoundState.NewRoundEvent newRoundEvent) {
        if (!proposerElection.isValidProposer(proposalGenerator.getAuthor(), newRoundEvent.getRound())) {
            return;
        }

        try {
            var proposalMsg = generateProposal(newRoundEvent);
            logger.info("generate new round[{}]  proposal for reason:{}, total size:{}", newRoundEvent.getRound(), newRoundEvent.getReason(), ByteUtil.getPrintSize(proposalMsg.getEncoded().length));
            netInvoker.broadcast(proposalMsg, true);
        } catch (Exception e) {
            logger.warn("processNewRoundLocalMsg error! {}", ExceptionUtils.getStackTrace(e));
        }

    }

    private ProposalMsg generateProposal(RoundState.NewRoundEvent newRoundEvent) {
        // Proposal generator will ensure that at most one proposal is generated per round
        var proposal = this.proposalGenerator.generateProposal(newRoundEvent.getRound());
        if (proposal == null) return null;

        var signProposal = safetyRules.signProposal(proposal);
        if (signProposal == null) {
            throw new RuntimeException("generateProposal, safetyRules.signProposal error!");
        }
        return ProposalMsg.build(signProposal, this.eventTreeStore.getHotstuffChainSyncInfo());
    }

//====================end processNewRoundLocalMsg============================

//====================start processProposalMsg============================

    /**
     * Process a ProposalMsg, pre_process would bring all the dependencies and filter out invalid
     * proposal, processProposedEvent would execute and decide whether to vote for it.
     */
    public void processProposalMsg(ProposalMsg proposalMsg) {
        if (IS_DEBUG_ENABLED) {
            logger.debug("processProposalMsg:" + proposalMsg);
        }

        logger.info("processProposalMsg, event[epoch[{}]-round[{}]-number[{}]-[{}]-parent[{}]-self[{}]]", proposalMsg.getProposal().getEpoch(), proposalMsg.getProposal().getRound(), proposalMsg.getProposal().getEventNumber(), proposalMsg.getProposal().getPayload().getCrossChainOffsets(), Hex.toHexString(proposalMsg.getProposal().getParentId()), Hex.toHexString(proposalMsg.getProposal().getId()));
        boolean stateConsistent = this.eventTreeStore.isStateConsistent();
        //ensure epoch change before stateConsistent
        if (!stateConsistent && proposalMsg.getProposal().hasGlobalEvents()) {
            logger.warn("global state un consistent now, we should not do node event or payload , wait again! ");
            return;
        }

        if (ensureRoundAndSyncUp(proposalMsg.getProposal().getRound(), proposalMsg.getHotstuffChainSyncInfo(), proposalMsg.getPeerId(), true).isSuccess()) {
            processProposedEvent(proposalMsg.getProposal(), proposalMsg.getPeerId());
        }
    }

    private ProcessResult<Void> ensureRoundAndSyncUp(long msgRound, HotstuffChainSyncInfo syncInfo, PeerId peerId, boolean helpRemote) {
        long currentRound = roundState.getCurrentRound();
        if (msgRound < currentRound) {
            logger.debug("ensureRoundAndSyncUp, Proposal round {} is less than current round {}", msgRound, currentRound);
            return ProcessResult.ofError();
        }

        ProcessResult<Void> syncUpRes = hotstuffChainSyncUp(syncInfo, peerId, helpRemote);
        if (!syncUpRes.isSuccess()) {
            return syncUpRes;
        }

        // roundState may catch up with the SyncInfo, check again
        if (msgRound != roundState.getCurrentRound()) {
            logger.warn("After sync, round {} doesn't match local {}", msgRound, roundState.getCurrentRound());
            return ProcessResult.ofError();
        }
        return ProcessResult.SUCCESSFUL;
    }

    private void processProposedEvent(Event proposal, PeerId preferredPeer) {
        if (!this.proposerElection.isValidProposer(proposal)) {
            logger.warn("[RoundManager] Proposer {} for event {} is not a valid proposer for this round", Hex.toHexString(proposal.getAuthor().get()), proposal);
            return;
        }


        var offsetsSyncRes = this.ivySettlementChainsSyncCoordinator.crossChainFastForwardSync(proposal.getPayload().getCrossChainOffsets(), preferredPeer);
        if (!offsetsSyncRes.isSuccess()) {
            logger.warn("processProposedEvent sync offsets error:{}", offsetsSyncRes.getErrMsg());
            return;
        }

        var parent = this.eventTreeStore.getEvent(proposal.getParentId());
        if (!this.settlementChainsVerifier.crossChainOffsetsConsistencyCheck(parent, proposal)) {
            logger.warn("[RoundManager] Proposer {} for event {} is not a valid cross offset for this round", Hex.toHexString(proposal.getAuthor().get()), proposal);
            return;
        }

        Vote vote = executeAndVote(proposal);
        if (vote == null) {
            return;
        }
        long proposalRound = proposal.getRound();
        PeerId peerId = proposerElection.getValidProposer(proposalRound + 1).getRight();

        this.roundState.recordVote(vote);

        VoteMsg voteMsg = VoteMsg.build(vote, this.eventTreeStore.getHotstuffChainSyncInfo());
        //logger.debug("doCheck proposal msg success:" + proposal);
        this.netInvoker.directSend(peerId, voteMsg);
    }

    private Vote executeAndVote(Event proposal) {
        ProcessResult<ExecutedEvent> executedEventRes = eventTreeStore.executeAndInsertEvent(proposal);
        if (!executedEventRes.isSuccess()) {
            throw new RuntimeException("executeAndVote, eventTreeStore.executeAndInsertEvent error!");
        }

        if (!epochState.getValidatorVerifier().containPublicKey(safetyRules.getAutorWrapper())) {
            logger.warn("current node is not a validator, ignore vote!");
            return null;
        }

        ExecutedEvent executedEvent = executedEventRes.getResult();

        // Short circuit if already voted.
        if (roundState.getVoteSent().isPresent()) {
            logger.warn("[RoundManager] Already vote on this round [{}]", this.roundState.getCurrentRound());
            return null;
        }

        ExecutedEvent parentEvent = eventTreeStore.getEvent(proposal.getParentId());
        Assert.ensure(parentEvent != null,
                "[RoundManager] Parent block not found after execution");

        VoteProposal voteProposal = VoteProposal.build(proposal, executedEvent.getEventNumber(), executedEvent.getStateRoot(), executedEvent.getExecutedEventOutput().getNewCurrentEpochState(), executedEvent.getExecutedEventOutput().getNewNextEpochState());
        Vote vote = safetyRules.constructAndSignVoteTwoChain(voteProposal, this.eventTreeStore.getHighestTwoChainTimeoutCert());
        if (vote == null) {
            return null;
        }

        livenessStorage.saveVote(vote);
        return vote;
    }

//====================end processProposalMsg============================

//====================start processVoteMsg============================

    /**
     * Upon new vote:
     * 1. Ensures we're processing the vote from the same round as local round
     * 2. Filter out votes for rounds that should not be processed by this validator (to avoid
     * potential attacks).
     * 2. Add the vote to the pending votes and check whether it finishes a QC.
     * 3. Once the QC/TC successfully formed, notify the RoundState.
     */
    public void processVoteMsg(VoteMsg voteMsg) {
        if (IS_DEBUG_ENABLED) {
            logger.debug("processVoteMsg:" + voteMsg);
        }

        if (ensureRoundAndSyncUp(voteMsg.getVote().getVoteData().getProposed().getRound(), voteMsg.getHotstuffChainSyncInfo(), voteMsg.getPeerId(), true).isSuccess()) {
            processVote(voteMsg.getVote(), voteMsg.getPeerId());
        }
    }

    private void processVote(Vote vote, PeerId preferredPeer) {
        if (!vote.isTwoChainTimeout()) {
            long nextRound = vote.getVoteData().getProposed().getRound() + 1;
            if (!proposerElection.isValidProposer(proposalGenerator.getAuthor(), nextRound)) {
                return;
            }
        }

        // Check if the QC already had related to block
        if (eventTreeStore.getQCForEvent(vote.getVoteData().getProposed().getId()) != null) return;

        // Sync up for timeout votes only.
        var voteReceptionResult = this.roundState.insertVote(vote, epochState.getValidatorVerifier());
        switch (voteReceptionResult.getVoteReception()) {
            case NewQuorumCertificate -> {
                if (IS_DEBUG_ENABLED) {
                    logger.debug("newQcAggregated.NewQuorumCertificate: " + voteReceptionResult.getResult());
                }
                newQcAggregated((QuorumCert) voteReceptionResult.getResult(), preferredPeer);
            }
            case NewTimeoutCertificate -> {
                if (IS_DEBUG_ENABLED) {
                    logger.debug("newQcAggregated.NewTimeoutCertificate: " + voteReceptionResult.getResult());
                }
                newTcAggregated((TimeoutCertificate) voteReceptionResult.getResult());
            }
            case NewTwoChainTimeoutCertificate -> {
                logger.info("create NewTwoChainTimeoutCertificate:{}", voteReceptionResult.getResult());
                newTwoChainTcAggregated((TwoChainTimeoutCertificate) voteReceptionResult.getResult());
            }
            case EchoTimeout -> {
                if (!this.roundState.isVoteTimeout()) {
                    logger.info("echo timeout for round:{}", vote.getVoteData().getProposed().getRound());
                    this.processLocalTimeout(new RoundState.LocalTimeoutMsg(vote.getVoteData().getProposed().getRound()));
                }
            }
            default -> {
            }
        }
    }

    private void newQcAggregated(QuorumCert qc, PeerId preferredPeer) {
        // Process local highest commit cert should be no-op, this will sync us to the QC
        var result = this.hotStuffChainSyncCoordinator.insertQuorumCert(qc, preferredPeer, true);
        if (!result.isSuccess()) {
            logger.error("[RoundManager] Failed to doCheck a newly aggregated QC");
            return;
        }
        processCertificates();
    }

    private void newTcAggregated(TimeoutCertificate tc) {
        var result = eventTreeStore.insertTimeoutCertificate(tc);
        if (!result.isSuccess()) {
            logger.error("[RoundManager] Failed to doCheck a newly aggregated TC");
            return;
        }
        processCertificates();
    }

    private void newTwoChainTcAggregated(TwoChainTimeoutCertificate tc) {
        var result = eventTreeStore.insertTwoChainTimeoutCertificate(tc);
        if (!result.isSuccess()) {
            logger.error("[RoundManager] Failed to doCheck a newly aggregated TC");
            return;
        }
        processCertificates();
    }

    //====================end processVoteMsg============================


    //====================start processChainSyncInfoMsg============================
    public void processChainSyncInfoMsg(HotstuffChainSyncInfo hotstuffChainSyncInfo) {
        // To avoid a ping-pong cycle between two peers that move forward together.
        if (!ensureRoundAndSyncUp(hotstuffChainSyncInfo.getHighestRound() + 1, hotstuffChainSyncInfo, hotstuffChainSyncInfo.getPeerId(), false).isSuccess()) {
            logger.debug("doCheck sync info warn!");
        }
    }

    private ProcessResult<Void> hotstuffChainSyncUp(HotstuffChainSyncInfo syncInfo, PeerId peerId, boolean helpRemote) {
        var localSyncInfo = this.eventTreeStore.getHotstuffChainSyncInfo();
        if (helpRemote && localSyncInfo.hasNewerCertificates(syncInfo)) {
            netInvoker.directSend(peerId, localSyncInfo);
        }

        if (syncInfo.hasNewerCertificates(localSyncInfo)) {
            // Some information in SyncInfo is ahead of what we have locally.
            // First verify the SyncInfo (didn't verify it in the yet).
            if (!syncInfo.verify(this.epochState.getValidatorVerifier()).isSuccess()) {
                return ProcessResult.ofError("syncUp, syncInfo.verify error!");
            }

            var processAddCertsResult = hotStuffChainSyncCoordinator.addCerts(syncInfo, peerId);
            if (!processAddCertsResult.isSuccess()) {
                logger.warn("Fail to sync up to {}: {}", syncInfo, processAddCertsResult.getErrMsg());
                return processAddCertsResult;
            }

            ProcessResult<Void> proCerRes = processCertificates();
            if (!proCerRes.isSuccess()) {
                return proCerRes.appendErrorMsg("syncUp, processCertificates error!");
            }
        }
        return ProcessResult.SUCCESSFUL;
    }
//====================end processChainSyncInfoMsg============================


    //====================start processEventRetrieval============================
    public void processEventRetrieval(EventRetrievalRequestMsg request) {
        var events = new ArrayList<Event>(8);
        var status = RetrievalStatus.SUCCESS;
        var id = request.getEventId();

        while (events.size() < request.getEventNum()) {
            var current = eventTreeStore.getEvent(id);
            if (current != null) {
                id = current.getParentId();
                events.add(current.getEvent());
            } else {
                status = RetrievalStatus.NOT_ENOUGH;
                break;
            }
        }

        if (events.isEmpty()) {
            status = RetrievalStatus.NOT_FOUND;
        }

        // do rpc response
        var eventRetrievalResponseMsg = new EventRetrievalResponseMsg(status, events);
        eventRetrievalResponseMsg.setRpcId(request.getRpcId());
        netInvoker.rpcResponse(request.getPeerId(), eventRetrievalResponseMsg);
    }

//====================end processEventRetrieval============================


//====================start processMainChainEmitEvent=======================================================

    public void processCrossChainRetrieval(SettlementChainRetrievalRequestMsg request) {
        var retrievalRes = this.settlementChainsVerifier.getCrossChainBlocks(request.getStartHeight());
        // do rpc response
        var crossChainRetrievalResponseMsg = new SettlementChainRetrievalResponseMsg(retrievalRes.getLeft(), retrievalRes.getRight());
        crossChainRetrievalResponseMsg.setRpcId(request.getRpcId());
        netInvoker.rpcResponse(request.getPeerId(), crossChainRetrievalResponseMsg);
    }


//======================end processMainChainEmitEvent=====================================================


    //====================start processLocalTimeout============================
    public void processLocalTimeout(RoundState.LocalTimeoutMsg localTimeoutMsg) {
        if (!epochState.getValidatorVerifier().containPublicKey(safetyRules.getAutorWrapper())) {
            logger.warn("current node is not a validator, ignore processLocalTimeout!");
            return;
        }

        long round = localTimeoutMsg.getRound();
        if (!roundState.processLocalTimeout(round)) {
            if (IS_DEBUG_ENABLED) {
                logger.debug("[RoundManager] local timeout round[{}] is stale", round);
            }
            // The timeout event is late: the node has already moved to another round.
            return;
        }

        boolean useLastVote;

        Vote timeoutVote;
        if (roundState.getVoteSent().isPresent() && roundState.getVoteSent().get().getVoteData().getProposed().getRound() == round) {
            useLastVote = true;
            timeoutVote = roundState.getVoteSent().get();
        } else {
            useLastVote = false;
            Event emptyEvent = proposalGenerator.generateEmptyEvent(round);
            logger.debug("Planning to vote for a NIL block {}", emptyEvent);
            timeoutVote = executeAndVote(emptyEvent);
        }

        if (timeoutVote == null) {
            return;
        }

        //logger.warn("Round {} timed out: {}, expected round proposer was {}, broadcasting the vote to all replicas", round, (useLastVote ? "already executed and voted at this round" : "will try to generate a backup vote"), Hex.toHexString(proposerElection.getValidProposer(round).getLeft()));
        if (!timeoutVote.isTwoChainTimeout()) {
            var twoChainTimeout = timeoutVote.getTwoChainTimeout(new QuorumCert(ByteUtil.copyFrom(this.eventTreeStore.getHighestQuorumCert().getEncoded())));
            var signature = this.safetyRules.signTwoChainTimeoutWithQc(twoChainTimeout, this.eventTreeStore.getHighestTwoChainTimeoutCert());
            if (signature == null) {
                logger.warn("sign {}, fail ", twoChainTimeout);
                return;
            }
            timeoutVote.addTwoChainTimeoutSignature(twoChainTimeout, signature);
        }

        this.roundState.recordVote(timeoutVote);
        var voteMsg = VoteMsg.build(timeoutVote, this.eventTreeStore.getHotstuffChainSyncInfo());
        //logger.info("will broadcast timeout vote:{}", voteMsg.getVote());
        netInvoker.broadcast(voteMsg, true);
    }

//====================end processLocalTimeout============================


    @Override
    public void saveTree(List<Event> events, List<QuorumCert> qcs) {
        this.livenessStorage.saveTree(events, qcs);
    }

    // This function is called only after all the dependencies of the given QC have been retrieved.
    private ProcessResult<Void> processCertificates() {
        var syncInfo = this.eventTreeStore.getHotstuffChainSyncInfo();
        var event = this.roundState.processCertificates(syncInfo);
        if (event != null) {
            processNewRoundLocalMsg(event);
        }
        return ProcessResult.SUCCESSFUL;
    }
}
