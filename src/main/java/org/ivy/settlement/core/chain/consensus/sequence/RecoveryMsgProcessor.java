package org.ivy.settlement.core.chain.consensus.sequence;

import io.libp2p.core.peer.PeerId;
import org.ivy.settlement.infrastructure.anyhow.Assert;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.executor.ConsensusEventExecutor;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.consensus.sequence.store.LivenessStorageData;

import java.util.List;

/**
 * description:
 * @author carrot
 */
public class RecoveryMsgProcessor extends ConsensusProcessor<LivenessStorageData.RecoveryData> {

    ConsensusNetInvoker netInvoker;

    ConsensusEventExecutor consensusEventExecutor;

    HotStuffChainSyncCoordinator hotStuffChainSyncCoordinator;

    IvySettlementChainsSyncCoordinator ivySettlementChainsSyncCoordinator;

    long lastCommittedRound;

    public RecoveryMsgProcessor(EpochState epochState, ConsensusNetInvoker netInvoker, ConsensusEventExecutor consensusEventExecutor, HotStuffChainSyncCoordinator hotStuffChainSyncCoordinator, long lastCommittedRound) {
        this.epochState = epochState;
        this.netInvoker = netInvoker;
        this.consensusEventExecutor = consensusEventExecutor;
        this.hotStuffChainSyncCoordinator = hotStuffChainSyncCoordinator;
        this.lastCommittedRound = lastCommittedRound;
        this.ivySettlementChainsSyncCoordinator = new IvySettlementChainsSyncCoordinator(netInvoker, hotStuffChainSyncCoordinator.settlementChainsVerifier);
    }

    public void releaseResource() {
        this.epochState = null;
        this.netInvoker = null;
        this.consensusEventExecutor = null;
        this.hotStuffChainSyncCoordinator.release();
        this.hotStuffChainSyncCoordinator = null;
        this.ivySettlementChainsSyncCoordinator.release();
        this.ivySettlementChainsSyncCoordinator = null;
    }

    @Override
    public ProcessResult<LivenessStorageData.RecoveryData> process(ConsensusMsg consensusMsg) {
        switch (consensusMsg.getCommand()) {
            case PROPOSAL:
                return processProposalMsg((ProposalMsg) consensusMsg);
            case VOTE:
                return processVoteMsg((VoteMsg) consensusMsg);
            default:
                break;
        }
        return ProcessResult.ofError("un know message!");
    }

    @Override
    public void saveTree(List<Event> events, List<QuorumCert> qcs) {
        this.hotStuffChainSyncCoordinator.eventTreeStore.livenessStorage.saveTree(events, qcs);
    }

    public ProcessResult<LivenessStorageData.RecoveryData> processProposalMsg(
            ProposalMsg proposalMsg) {
        var offsetsSyncRes = this.ivySettlementChainsSyncCoordinator.crossChainFastForwardSync(proposalMsg.getProposal().getPayload().getCrossChainOffsets(), proposalMsg.getPeerId());
        if (!offsetsSyncRes.isSuccess()) {
            logger.warn("recover sync offsets error:{}", offsetsSyncRes.getErrMsg());
            return ProcessResult.ofError(offsetsSyncRes.getErrMsg());
        }
        return syncUp(proposalMsg.getHotstuffChainSyncInfo(), proposalMsg.getPeerId());
    }

    public ProcessResult<LivenessStorageData.RecoveryData> processVoteMsg(
            VoteMsg voteMsg) {
        return syncUp(voteMsg.getHotstuffChainSyncInfo(), voteMsg.getPeerId());
    }

    private ProcessResult<LivenessStorageData.RecoveryData> syncUp(HotstuffChainSyncInfo syncInfo, PeerId preferredPeer) {
        ProcessResult<Void> verifyRes = syncInfo.verify(this.epochState.getValidatorVerifier());
        if (!verifyRes.isSuccess()) {
            logger.warn("recover syncUp error! {}", verifyRes.getErrMsg());
            return ProcessResult.ofError("syncInfo verify error!");
        }
        Assert.ensure(syncInfo.getHighestRound() > lastCommittedRound,
                "[RecoveryMsgProcessor] Received sync info has lower round number than committed event");
        Assert.ensure(syncInfo.getEpoch() == this.epochState.getEpoch(),
                "[RecoveryMsgProcessor] Received sync info is in different epoch than committed event");

        return hotStuffChainSyncCoordinator.fastForwardSync(syncInfo.getHighestCommitCert(), preferredPeer);
    }
}
