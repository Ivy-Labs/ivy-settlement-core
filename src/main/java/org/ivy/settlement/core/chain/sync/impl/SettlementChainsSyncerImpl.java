package org.ivy.settlement.core.chain.sync.impl;

import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.datasource.model.EthLogEvent;
import org.ivy.settlement.core.chain.consensus.sequence.model.RetrievalStatus;

import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffset;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.UpdateFollowerChainEvent;
import org.ivy.settlement.ethereum.model.BeaconBlockRecord;
import org.ivy.settlement.ethereum.model.LatestUploadBlobState;
import org.ivy.settlement.ethereum.model.event.AddChainEvent;
import org.ivy.settlement.ethereum.model.event.RemoveChainEvent;
import org.ivy.settlement.ethereum.model.event.VoterUpdateEvent;
import org.ivy.settlement.ethereum.store.BeaconChainStore;
import org.ivy.settlement.ethereum.model.settlement.LatestFollowerChainBlockBatch;
import org.ivy.settlement.ethereum.model.settlement.SettlementBlockInfo;
import org.ivy.settlement.ethereum.model.settlement.SettlementBlockInfos;
import org.ivy.settlement.core.chain.sync.SettlementChainsSyncer;
import org.ivy.settlement.core.chain.sync.OnChainStatus;
import org.ivy.settlement.follower.store.FollowerChainStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import static org.ivy.settlement.infrastructure.anyhow.Assert.ensure;
import static org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets.MAIN_CHAIN_CODE;
import static org.ivy.settlement.core.chain.consensus.sequence.model.settlement.UpdateFollowerChainEvent.CHAIN_JOIN;
import static org.ivy.settlement.core.chain.consensus.sequence.model.settlement.UpdateFollowerChainEvent.CHAIN_QUIT;
import static org.ivy.settlement.ethereum.model.constants.EthLogActionEnum.MANAGER_CHAIN;
import static org.ivy.settlement.ethereum.model.constants.EthLogActionEnum.UPDATE_VOTER;

/**
 * description:
 * @author carrot
 */
public class SettlementChainsSyncerImpl implements SettlementChainsSyncer {

    private static final int MAX_CROSS_BLOCK_RETRIEVAL_NUM = 4;

    public static final int AWAIT_LOGS_TIME = 200;

    BeaconChainStore beaconChainStore;

    FollowerChainStore followerChainStore;

    public SettlementChainsSyncerImpl(BeaconChainStore beaconChainStore, FollowerChainStore followerChainStore) {
        this.beaconChainStore = beaconChainStore;
        this.followerChainStore = followerChainStore;
    }

    public ProcessResult<Void> syncMainChain(List<BeaconBlockRecord> settlementChainBlocks) {
        return beaconChainStore.persist(settlementChainBlocks);
    }

    @Override
    public ProcessResult<Void> syncFollowerChain(List<LatestFollowerChainBlockBatch> blockBatches) {
        return this.followerChainStore.persist(blockBatches);
    }

    public Pair<RetrievalStatus, List<BeaconBlockRecord>> getSettlementChainBlocks(long startHeight) {
        var number = this.beaconChainStore.getCurrentLatestValidNumber();
        RetrievalStatus status;
        List<BeaconBlockRecord> settlementChainBlocks;
        if (number < startHeight) {
            return Pair.of(RetrievalStatus.NOT_FOUND, Collections.emptyList());
        } else {
            var endHeight = Math.min(startHeight + MAX_CROSS_BLOCK_RETRIEVAL_NUM, number);
            status = endHeight < number ? RetrievalStatus.CONTINUE : RetrievalStatus.SUCCESS;
            settlementChainBlocks = this.getSettlementChainBlocks(startHeight, endHeight);
            return Pair.of(status, settlementChainBlocks);
        }
    }

    public List<BeaconBlockRecord> getSettlementChainBlocks(long startHeight, long endHeight) {
        return this.beaconChainStore.getBeaconBlockRecords(startHeight, endHeight);
    }

    public long getLatestMainChainFinalityNumber() {
        return this.beaconChainStore.getCurrentLatestValidNumber();
    }

    @Override
    public List<VoterUpdateEvent> getVoterUpdateEvents(long startHeight, long endHeight) {
        if (startHeight > endHeight) return Collections.emptyList();
        var op = this.beaconChainStore.retrievalLog(startHeight, endHeight, UPDATE_VOTER);
        if (op.isEmpty()) return Collections.emptyList();
        var updateEvents = new ArrayList<VoterUpdateEvent>(8);
        for (var list : op.get()) {
            for (EthLogEvent ethLogEvent : list) {
                VoterUpdateEvent e = (VoterUpdateEvent) ethLogEvent;
                updateEvents.add(e);
            }
        }

        return updateEvents;
    }


    public List<UpdateFollowerChainEvent> getUpdateFollowerChainEvents(long startHeight, long endHeight) {
        var op = this.beaconChainStore.retrievalLog(startHeight, endHeight, MANAGER_CHAIN);

        ensure(op.isPresent(), "getUpdateFollowerChainEvents from {} to {} is not exist!", startHeight, endHeight);
        var res = new ArrayList<UpdateFollowerChainEvent>(8);
        for (var eventList : op.get()) {
            for (var event : eventList) {
                if (event instanceof AddChainEvent addChainEvent) {
                    res.add(new UpdateFollowerChainEvent(CHAIN_JOIN, addChainEvent.getComeIntoEffectHeight(), addChainEvent.getChainId()));
                } else if (event instanceof RemoveChainEvent removeChainEvent) {
                    res.add(new UpdateFollowerChainEvent(CHAIN_QUIT, removeChainEvent.getComeIntoEffectHeight(), removeChainEvent.getChainId()));
                }
            }
        }

        return res;
    }

    public long getLatestConfirmHeight(SettlementChainOffset offset) {
        if (offset.getChain() == MAIN_CHAIN_CODE) return this.getLatestMainChainFinalityNumber();

        return this.followerChainStore.retrievalFollowerChainOffset(offset.getChain());
    }

    public SettlementBlockInfos generateSettlementBlockInfos(SettlementChainOffsets parentOffsets, SettlementChainOffsets currentOffsets) {
        var infos = new TreeMap<Integer, List<SettlementBlockInfo>>();
        for (var currentOffset : currentOffsets.getFollowerChains().values()) {
            var parentOffset = parentOffsets.getFollowerChain(currentOffset.getChain());
            var logInfoOp = this.followerChainStore.retrievalFollowerChainLog(currentOffset.getChain(), parentOffset.getHeight() + 1, currentOffset.getHeight());
            while (logInfoOp.isEmpty()) {
                awaitLogs();
                logInfoOp = this.followerChainStore.retrievalFollowerChainLog(currentOffset.getChain(), parentOffset.getHeight() + 1, currentOffset.getHeight());
            }

            if (!logInfoOp.get().isEmpty()) {
                infos.put(currentOffset.getChain(), logInfoOp.get());
            }
        }

        var mainChainSettlementOp = this.beaconChainStore.retrievalMainChainSettlement(parentOffsets.getMainChain().getHeight(), currentOffsets.getMainChain().getHeight());
        while (mainChainSettlementOp.isEmpty()) {
            awaitLogs();
            mainChainSettlementOp = this.beaconChainStore.retrievalMainChainSettlement(parentOffsets.getMainChain().getHeight(), currentOffsets.getMainChain().getHeight());
        }

        if (!mainChainSettlementOp.get().isEmpty()) {
            infos.put(currentOffsets.getMainChain().getChain(), mainChainSettlementOp.get());
        }

        return new SettlementBlockInfos(infos);
    }


    public boolean checkSettlementInfos(SettlementChainOffsets parentChainOffsets, SettlementChainOffsets currentChainOffsets, SettlementBlockInfos targetSettlementBlockInfos) {
        var selfInfos = generateSettlementBlockInfos(parentChainOffsets, currentChainOffsets);
        return selfInfos.equals(targetSettlementBlockInfos);
    }

    public OnChainStatus getSettlementBlobOnChainResult(long blobNum) {
        var latestUploadBlobState = this.beaconChainStore.getLatestUploadBlobState();
        if (blobNum > latestUploadBlobState.getCurrentNumber()) return OnChainStatus.UNKNOWN;

        if (blobNum < latestUploadBlobState.getCurrentNumber()) return OnChainStatus.SUCCESS;

        if (latestUploadBlobState.getStatus() == LatestUploadBlobState.UPLOAD_SUCCESS) return OnChainStatus.SUCCESS;

        if (latestUploadBlobState.getStatus() == LatestUploadBlobState.UPLOAD_RESIGN) return OnChainStatus.RESIGN;

        return OnChainStatus.UNKNOWN;
    }

    private void awaitLogs() {
        try {
            Thread.sleep(AWAIT_LOGS_TIME);
        } catch (InterruptedException ignored) {
        }
    }
}
