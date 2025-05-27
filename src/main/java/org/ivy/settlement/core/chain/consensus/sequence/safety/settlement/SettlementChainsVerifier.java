package org.ivy.settlement.core.chain.consensus.sequence.safety.settlement;

import org.apache.commons.lang3.tuple.Pair;
import org.bouncycastle.util.encoders.Hex;
import org.ivy.settlement.core.chain.consensus.sequence.model.RetrievalStatus;
import org.ivy.settlement.core.chain.consensus.sequence.model.Event;
import org.ivy.settlement.core.chain.consensus.sequence.model.ExecutedEvent;
import org.ivy.settlement.core.chain.consensus.sequence.model.LatestLedger;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffset;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.UpdateFollowerChainEvent;
import org.ivy.settlement.ethereum.model.BeaconBlockRecord;
import org.ivy.settlement.core.chain.sync.SettlementChainsSyncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;

/**
 * description:
 * @author carrot
 */
public class SettlementChainsVerifier {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    public final SettlementChainsSyncer settlementChainsSyncer;

    public final LatestLedger latestLedger;

    ConsensusIncrementHeightRules consensusIncrementHeightRules;

    public SettlementChainsVerifier(SettlementChainsSyncer settlementChainsSyncer, LatestLedger latestLedger) {
        this.settlementChainsSyncer = settlementChainsSyncer;
        this.latestLedger = latestLedger;
        this.consensusIncrementHeightRules = new ConsensusIncrementHeightRules();
    }

    public ProcessResult<Void> syncCrossChain(List<BeaconBlockRecord> settlementChainBlocks) {
        return this.settlementChainsSyncer.syncMainChain(settlementChainBlocks);
    }

    public Pair<RetrievalStatus, List<BeaconBlockRecord>> getCrossChainBlocks(long startHeight) {
        return this.settlementChainsSyncer.getSettlementChainBlocks(startHeight);
    }

    public long getLatestOnChainCrossChainOffsets() {
        return this.settlementChainsSyncer.getLatestMainChainFinalityNumber();
    }


    public boolean crossChainOffsetsConsistencyCheck(ExecutedEvent parentExecutedEvent, Event proposal) {
        var parentEvent = parentExecutedEvent.getEvent();
        if (!Arrays.equals(parentEvent.getId(), proposal.getParentId())) {
            logger.error("crossChainOffsetsCheck un except relation, parent event id[{}], proposal parent event id[{}], not equals", Hex.toHexString(parentEvent.getId()), Hex.toHexString(proposal.getParentId()));
            return false;
        }

        var parentCrossChainOffsets = parentEvent.getPayload().getCrossChainOffsets();
        var currentCrossChainOffsets = proposal.getPayload().getCrossChainOffsets();
        parentCrossChainOffsets = followerChainStatusTransfer(parentCrossChainOffsets, parentCrossChainOffsets.getMainChain().getHeight() + 1, currentCrossChainOffsets.getMainChain().getHeight());

        long parentTimeEpoch = this.latestLedger.getCurrentEpochState().calculateEpoch(parentCrossChainOffsets.getMainChain().getHeight());
        long currentTimeEpoch = this.latestLedger.getCurrentEpochState().calculateEpoch(currentCrossChainOffsets.getMainChain().getHeight());

        boolean isEthEpochChange = parentTimeEpoch == currentTimeEpoch + 1;
        if (isEthEpochChange && !this.latestLedger.getCurrentEpochState().isEpochBoundary(currentCrossChainOffsets.getMainChain().getHeight())) {
            logger.error("un except eth epoch change state, current main offset is {}, not boundary offset", currentCrossChainOffsets.getMainChain().getHeight());
            return false;
        }

        return consensusIncrementHeightRules.offsetsCheck(parentExecutedEvent.getExecutedEventOutput().hasReconfiguration(), proposal.getEventData().isEmptyPayload(), parentCrossChainOffsets, currentCrossChainOffsets);
    }

    /**
     *
     * @param sourceSettlementChainOffsets
     * @param start, include start height
     * @param end, include end height
     * @return
     */
    public SettlementChainOffsets followerChainStatusTransfer(SettlementChainOffsets sourceSettlementChainOffsets, long start, long end) {
        if (start > end) return sourceSettlementChainOffsets.copy();

        var updateFollowerChainEvents = this.settlementChainsSyncer.getUpdateFollowerChainEvents(start, end);
        var followerChains = sourceSettlementChainOffsets.getValidFollowerChains();

        for (var event : updateFollowerChainEvents) {
            if (event.getStatus() == UpdateFollowerChainEvent.CHAIN_JOIN) {
                followerChains.put(event.getChain(), new SettlementChainOffset(UpdateFollowerChainEvent.CHAIN_JOIN, event.getComeIntoEffectHeight(), event.getChain(), event.getComeIntoEffectHeight()));
            } else if (event.getStatus() == UpdateFollowerChainEvent.CHAIN_QUIT) {
                var quitOffset = followerChains.get(event.getChain());
                if (quitOffset == null) {
                    logger.warn("un except event[{}]", event);
                    continue;
                }
                quitOffset.setStatus(UpdateFollowerChainEvent.CHAIN_QUIT, event.getComeIntoEffectHeight());
            }
        }


        return new SettlementChainOffsets(sourceSettlementChainOffsets.getMainChain().copy(), followerChains);
    }

    public SettlementChainOffsets generateCrossChainOffsets(SettlementChainOffsets parentOffsets) {
        var maxIncrementHeightPerChain = this.consensusIncrementHeightRules.getMaxSizeForPerChain(parentOffsets.getFollowerChains().size());
        var newMainChainHeight = calculateNewHeight(parentOffsets.getMainChain(), maxIncrementHeightPerChain);

        var ethEpochChange = this.latestLedger.getCurrentEpochState().calculateEpoch(parentOffsets.getMainChain().getHeight()) + 1 == this.latestLedger.getCurrentEpochState().calculateEpoch(newMainChainHeight);
        if (ethEpochChange) {
            newMainChainHeight = this.latestLedger.getCurrentEpochState().calculateEpochBoundaryHeight(parentOffsets.getMainChain().getHeight());
        }

        var newCrossChainOffsets = followerChainStatusTransfer(parentOffsets, parentOffsets.getMainChain().getHeight() + 1, newMainChainHeight);
        newCrossChainOffsets.getMainChain().setHeight(newMainChainHeight);

        var totalDeltaIncrement = newCrossChainOffsets.getMainChain().getHeight() - parentOffsets.getMainChain().getHeight();
        maxIncrementHeightPerChain = this.consensusIncrementHeightRules.getMaxSizeForPerChain(newCrossChainOffsets.getFollowerChains().size());
        var maxIncrementForAllChain = this.consensusIncrementHeightRules.getMaxIncrementHeightForAllChain() - maxIncrementHeightPerChain;

        for (var newFollowerOffset : newCrossChainOffsets.getFollowerChains().values()) {
            totalDeltaIncrement += resetOffsetHeight(newFollowerOffset, maxIncrementHeightPerChain);
            if (totalDeltaIncrement > maxIncrementForAllChain) {
                break;
            }
        }

        newCrossChainOffsets.reEncoded();
        return newCrossChainOffsets;
    }

    private long resetOffsetHeight(SettlementChainOffset chainOffset, long maxIncrementHeightForPerChain) {
        var oldHeight = chainOffset.getHeight();
        var newMainChainHeight = calculateNewHeight(chainOffset, maxIncrementHeightForPerChain);
        chainOffset.setHeight(newMainChainHeight);
        return newMainChainHeight - oldHeight;
    }

    private long calculateNewHeight(SettlementChainOffset chainOffset, long maxIncrementHeightForPerChain) {
        var latestChainHeight = this.settlementChainsSyncer.getLatestConfirmHeight(chainOffset);
        var currentHeight = chainOffset.getHeight();

        if (currentHeight > latestChainHeight) {
            return currentHeight;
        }

        var newMaxChainHeight = maxIncrementHeightForPerChain + currentHeight;
        return Math.min(newMaxChainHeight, latestChainHeight);
    }
}
