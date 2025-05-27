package org.ivy.settlement.core.chain.consensus.sequence;

import io.libp2p.core.peer.PeerId;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainRetrievalRequestMsg;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainRetrievalResponseMsg;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.consensus.sequence.safety.settlement.SettlementChainsVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * description:
 * @author carrot
 */
public class IvySettlementChainsSyncCoordinator {

    static final Logger logger = LoggerFactory.getLogger("consensus");

    static final int MAX_RETRY_TIME = 3;

    ConsensusNetInvoker netInvoker;

    SettlementChainsVerifier settlementChainsVerifier;

    public IvySettlementChainsSyncCoordinator(ConsensusNetInvoker netInvoker, SettlementChainsVerifier settlementChainsVerifier) {
        this.netInvoker = netInvoker;
        this.settlementChainsVerifier = settlementChainsVerifier;
    }

    public ProcessResult<Void> crossChainFastForwardSync(SettlementChainOffsets targetOffsets, PeerId preferredPeer) {
        //var mainChainSyncRes = syncMainChain(targetOffsets.getMainChain().getHeight(), preferredPeer);
        var mainChainSyncRes = syncCrossChainMock(targetOffsets.getMainChain().getChain(), targetOffsets.getMainChain().getHeight(), preferredPeer);
        if (!mainChainSyncRes.isSuccess()) {
            return mainChainSyncRes;
        }

        return ProcessResult.SUCCESSFUL;
    }

    private ProcessResult<Void> syncMainChain(long targetChainHeight, PeerId preferredPeer) {
        var selfLatestNumber = this.settlementChainsVerifier.getLatestOnChainCrossChainOffsets();
        if (targetChainHeight <= selfLatestNumber) {
            return ProcessResult.SUCCESSFUL;
        }
        return doSync(selfLatestNumber + 1, targetChainHeight, preferredPeer);
    }

    private ProcessResult<Void> syncCrossChainMock(int chain, long targetChainHeight, PeerId preferredPeer) {
        //logger.info("syncCrossChainMock targetChainHeight[{}] to {}", targetChainHeight, preferredPeer);

        if (true) {
            return ProcessResult.SUCCESSFUL;
        }

        if (targetChainHeight < 57 && targetChainHeight > 52 && !netInvoker.peerId.toBase58().equals("16Uiu2HAkzQC6T1dxwr3MTB1UckC2Qeb2PwqZgdcuFDsSEKZ6xZoB"))  {
//            if (!syncProcessRes.isSuccess()) return syncProcessRes;
            logger.info("syncCrossChainMock targetChainHeight[{}] to {}", targetChainHeight, preferredPeer);
            return doSync(targetChainHeight - 4, targetChainHeight, preferredPeer);
        } else {
            return ProcessResult.SUCCESSFUL;
        }
    }

    private ProcessResult<Void> doSync(long startHeight, long endHeight, PeerId remotePeer) {
        var requestMsg = new SettlementChainRetrievalRequestMsg(startHeight);
        for (var i = 0; i < MAX_RETRY_TIME; i++) {
            try {
                var response = netInvoker.rpcSend(remotePeer, requestMsg);
                if (response == null) {
                    return ProcessResult.ofError("mainChainFastForwardSync error!");
                }
                var crossChainRetrievalResponseMsg = new SettlementChainRetrievalResponseMsg(response.getEncoded());
                logger.info("doSync retrieval resp:{}", crossChainRetrievalResponseMsg);
                return switch (crossChainRetrievalResponseMsg.getStatus()) {
                    case SUCCESS -> doSuccess(crossChainRetrievalResponseMsg);
                    case CONTINUE -> continueSync(crossChainRetrievalResponseMsg, endHeight, remotePeer);
                    case NOT_FOUND -> ProcessResult.ofError("block not found!");
                    default -> ProcessResult.ofError("un except status!");
                };
            } catch (Exception e) {
                logger.warn("vyCrossChainSyncCoordinator fastForwardSync  error!", e);
            }
        }

        return ProcessResult.ofError("IvyCrossChainSyncCoordinator fastForwardSync un know error!");
    }

    private ProcessResult<Void> doSuccess(SettlementChainRetrievalResponseMsg settlementChainRetrievalResponseMsg) {
        return this.settlementChainsVerifier.syncCrossChain(settlementChainRetrievalResponseMsg.getCrossChinBlocks());
    }

    private ProcessResult<Void> continueSync(SettlementChainRetrievalResponseMsg settlementChainRetrievalResponseMsg, long endHeight, PeerId remotePeer) {
        var syncRes = this.settlementChainsVerifier.syncCrossChain(settlementChainRetrievalResponseMsg.getCrossChinBlocks());
        if (syncRes.isSuccess()) {
            return doSync(settlementChainRetrievalResponseMsg.getLastHeight() + 1, endHeight, remotePeer);
        } else {
            return syncRes;
        }
    }

    public void release() {
        this.netInvoker = null;
        this.settlementChainsVerifier = null;
    }
}
