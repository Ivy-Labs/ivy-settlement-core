package org.ivy.settlement.core.chain.sync.impl;

import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecureKey;
import org.ivy.settlement.infrastructure.crypto.HashUtil;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.model.RetrievalStatus;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffset;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.UpdateFollowerChainEvent;
import org.ivy.settlement.ethereum.model.BeaconBlockRecord;
import org.ivy.settlement.ethereum.model.event.VoterUpdateEvent;
import org.ivy.settlement.ethereum.model.settlement.LatestFollowerChainBlockBatch;
import org.ivy.settlement.ethereum.model.settlement.SettlementBlockInfo;
import org.ivy.settlement.ethereum.model.settlement.SettlementBlockInfos;
import org.ivy.settlement.core.chain.sync.SettlementChainsSyncer;
import org.ivy.settlement.core.chain.sync.OnChainStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;

import static org.ivy.settlement.ethereum.model.event.VoterUpdateEvent.NODE_DEATH;
import static org.ivy.settlement.ethereum.model.event.VoterUpdateEvent.NODE_UPDATE;

/**
 * description:
 * @author carrot
 */
public class SettlementChainsSyncerMock implements SettlementChainsSyncer {

    private static final int MAX_CROSS_BLOCK_RETRIEVAL_NUM = 4;

    volatile SettlementChainOffsets syncSettlementChainOffsets;

    volatile long latestMainChainFinalityNumber;

    public ProcessResult<Void> syncMainChain(List<BeaconBlockRecord> settlementChainBlocks) {
        return ProcessResult.SUCCESSFUL;
    }

    @Override
    public ProcessResult<Void> syncFollowerChain(List<LatestFollowerChainBlockBatch> blockBatches) {
        return ProcessResult.SUCCESSFUL;
    }

    public Pair<RetrievalStatus, List<BeaconBlockRecord>> getSettlementChainBlocks(long startHeight) {
//        if (startHeight == 51) {
//            var hash = HashUtil.sha256(new byte[]{1});
//            var ccbs = List.of(
//                    new CrossChainBlock(51, hash, hash, new ArrayList<>()),
//                    new CrossChainBlock(52, hash, hash, List.of(hash)),
//                    new CrossChainBlock(53, hash, hash, List.of(hash, hash)));
//            return Pair.of(FALL_BEHIND, ccbs);
//        } else {
//            if (true) {
//                return Pair.of(FINISH_SYNC, List.of(new CrossChainBlock(54, HashUtil.sha256(new byte[]{2}), HashUtil.sha256(new byte[]{3}), new ArrayList<>())));
//            }
//        }
        var offset = this.syncSettlementChainOffsets.getMainChain();
        RetrievalStatus status;
        List<BeaconBlockRecord> settlementChainBlocks;
        if (offset.getHeight() < startHeight) {
            return Pair.of(RetrievalStatus.NOT_FOUND, Collections.emptyList());
        } else {
            var endHeight = Math.min(startHeight + MAX_CROSS_BLOCK_RETRIEVAL_NUM, offset.getHeight());
            status = endHeight < offset.getHeight() ? RetrievalStatus.CONTINUE : RetrievalStatus.SUCCESS;
            settlementChainBlocks = this.getSettlementChainBlocks(startHeight, endHeight);
            return Pair.of(status, settlementChainBlocks);
        }
    }

    public List<BeaconBlockRecord> getSettlementChainBlocks(long startHeight, long endHeight) {
        return new ArrayList<>();
    }

    public long getLatestMainChainFinalityNumber() {
        return latestMainChainFinalityNumber;
    }


    @Override
    public List<VoterUpdateEvent> getVoterUpdateEvents(long startHeight, long endHeight) {
        if (startHeight < 37 && endHeight > 37) {
            byte[] pk = SecureKey.fromPrivate(Hex.decode("0300011ad4cceb35d855c861d8967ba5531a27908c7b1daa6e156a63340b1d3ed57baa")).getRawPubKeyBytes();
            var uve = new VoterUpdateEvent(NODE_DEATH, 1, pk, 0, 0, 1);
            //logger.info("mock death update:{}", uve);
            return List.of(uve);
        } else if (startHeight < 128 && endHeight > 128) {
            byte[] pk = SecureKey.fromPrivate(Hex.decode("0300011ad4cceb35d855c861d8967ba5531a27908c7b1daa6e156a63340b1d3ed57baa")).getRawPubKeyBytes();
            var uve = new VoterUpdateEvent(NODE_UPDATE, 1, pk, 1, 4, 2);
            //logger.info("mock join update:{}", uve);
            return List.of(uve);
        } else if (startHeight < 168 && endHeight > 168) {
            byte[] pk = SecureKey.fromPrivate(Hex.decode("0300011ad4cceb35d855c861d8967ba5531a27908c7b1daa6e156a63340b1d3ed57baa")).getRawPubKeyBytes();
            var uve = new VoterUpdateEvent(NODE_UPDATE, 1, pk, 4, 7, 3);
            //logger.info("mock join update:{}", uve);
            return List.of(uve);
        } else if (startHeight < 188 && endHeight > 188) {

            byte[] pk4 = SecureKey.fromPrivate(Hex.decode("030001e621716c6791a638a0dacc855b73d46d967c50830ed4d99d5b846370e746c513")).getRawPubKeyBytes();
            var uve4 = new VoterUpdateEvent(NODE_DEATH, 4, pk4, 0, 0, 2);
            //logger.info("mock join update:{}", uve4);

            byte[] pk2 = SecureKey.fromPrivate(Hex.decode("030001ba797542ef428cc2249fe496a4e2062f9e20ac23b9fdea659cb0639244cf74f4")).getRawPubKeyBytes();
            var uve2 = new VoterUpdateEvent(NODE_UPDATE, 2, pk2, 3, 8, 3);
            //logger.info("mock join update:{}", uve2);
            return List.of(uve4, uve2);
        }

        return new ArrayList<>();
    }

    public List<UpdateFollowerChainEvent> getUpdateFollowerChainEvents(long startHeight, long endHeight) {
        if (startHeight < 37 && endHeight > 37) {
            return List.of(new UpdateFollowerChainEvent(2, 50, 3));
        } else if (startHeight < 77 && endHeight > 77) {
            return List.of(new UpdateFollowerChainEvent(1, 50, 4));
        }
        return new ArrayList<>();
    }

    public long getLatestConfirmHeight(SettlementChainOffset offset) {
        return offset.getHeight() + 5;
        //return offset.getHeight() + 8;
    }

    public SettlementBlockInfos generateSettlementBlockInfos(SettlementChainOffsets parentOffsets, SettlementChainOffsets currentOffsets) {
        var infos = new TreeMap<Integer, List<SettlementBlockInfo>>();
        for (var offset : currentOffsets.getFollowerChains().values()) {
            var hash = HashUtil.sha3(offset.getEncoded());
            infos.put(offset.getChain(), List.of(new SettlementBlockInfo(offset.getChain(), offset.getHeight(), hash, hash, Collections.emptyList())));
        }

        return new SettlementBlockInfos(infos);
    }


    public boolean checkSettlementInfos(SettlementChainOffsets parentChainOffsets, SettlementChainOffsets settlementChainOffsets, SettlementBlockInfos settlementBlockInfos) {
        return true;
    }

    public OnChainStatus getSettlementBlobOnChainResult(long blobNum) {
        return OnChainStatus.UNKNOWN;
    }
}
