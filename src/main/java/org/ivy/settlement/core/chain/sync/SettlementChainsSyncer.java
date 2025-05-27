package org.ivy.settlement.core.chain.sync;

import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffset;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.consensus.sequence.model.RetrievalStatus;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.*;
import org.ivy.settlement.ethereum.model.BeaconBlockRecord;
import org.ivy.settlement.ethereum.model.event.VoterUpdateEvent;
import org.ivy.settlement.ethereum.model.settlement.LatestFollowerChainBlockBatch;
import org.ivy.settlement.ethereum.model.settlement.SettlementBlockInfos;

import java.util.List;

/**
 * description:
 * @author carrot
 */
public interface SettlementChainsSyncer {

    ProcessResult<Void> syncMainChain(List<BeaconBlockRecord> settlementChainBlocks);

    ProcessResult<Void> syncFollowerChain(List<LatestFollowerChainBlockBatch> blockBatches);

    Pair<RetrievalStatus, List<BeaconBlockRecord>> getSettlementChainBlocks(long startHeight);

    List<BeaconBlockRecord> getSettlementChainBlocks(long startHeight, long endHeight);

    long getLatestMainChainFinalityNumber();

//    boolean hasCurrentEpochChange(long startHeight, long endHeight);
//
//    boolean hasNextEpochChange(long startHeight, long endHeight);

//    /**
//     * @param epochState  need to update
//     * @param startHeight exclude height
//     * @param endHeight   include height
//     */
//    void updateEpochState(EpochState epochState, long startHeight, long endHeight);

    List<VoterUpdateEvent> getVoterUpdateEvents(long startHeight, long endHeight);

    List<UpdateFollowerChainEvent> getUpdateFollowerChainEvents(long startHeight, long endHeight);

    long getLatestConfirmHeight(SettlementChainOffset offset);

    SettlementBlockInfos generateSettlementBlockInfos(SettlementChainOffsets parentOffsets, SettlementChainOffsets currentOffsets);


    boolean checkSettlementInfos(SettlementChainOffsets parentChainOffsets, SettlementChainOffsets settlementChainOffsets, SettlementBlockInfos settlementBlockInfos);

    OnChainStatus getSettlementBlobOnChainResult(long blobNum);
}
