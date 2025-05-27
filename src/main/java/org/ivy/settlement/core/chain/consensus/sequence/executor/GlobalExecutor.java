package org.ivy.settlement.core.chain.consensus.sequence.executor;

import io.libp2p.core.utils.Pair;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochStateHolder;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.consensus.sequence.store.ConsensusChainStore;
import org.ivy.settlement.ethereum.model.event.VoterUpdateEvent;
import org.ivy.settlement.core.chain.ledger.StateLedger;
import org.ivy.settlement.core.chain.sync.SettlementChainsSyncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * description:
 * @author carrot
 */
public class GlobalExecutor {

    static final Logger logger = LoggerFactory.getLogger("executor");

    AsyncExecutor asyncExecutor;

    StateLedger stateLedger;

    ConsensusChainStore consensusChainStore;

    SettlementChainsSyncer settlementChainsSyncer;

    public GlobalExecutor(StateLedger stateLedger, ConsensusChainStore consensusChainStore, SettlementChainsSyncer settlementChainsSyncer) {
        this.stateLedger = stateLedger;
        this.consensusChainStore = consensusChainStore;
        this.settlementChainsSyncer = settlementChainsSyncer;
        this.asyncExecutor = new AsyncExecutor(stateLedger, settlementChainsSyncer);
    }

    public void start() {
        this.asyncExecutor.start();
    }

    public Pair<EpochStateHolder, EpochStateHolder> doExecuteGlobalNodeEvents(SettlementChainOffsets currentSettlementChainOffsets, SettlementChainOffsets parentSettlementChainOffsets) {
        //logger.info("doExecuteGlobalNodeEvents parent offsets:[{}], current offsets:[{}]", parentCrossChainOffsets.getMainChain().getHeight(), currentCrossChainOffsets.getMainChain().getHeight());
        if (currentSettlementChainOffsets.getMainChain().getHeight() == parentSettlementChainOffsets.getMainChain().getHeight()) {
            return Pair.of(EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER, EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER);
        }

        var isEthEpochChange = this.consensusChainStore.getLatestLedger().getCurrentEpochState().isEpochBoundary(currentSettlementChainOffsets.getMainChain().getHeight());
        var voterUpdateEvents = this.settlementChainsSyncer.getVoterUpdateEvents(parentSettlementChainOffsets.getMainChain().getHeight(), currentSettlementChainOffsets.getMainChain().getHeight());
        if (isEthEpochChange) {
            //logger.info("-----------will do eth epoch change for current:[{}] and epoch[{}], -----------------  next[{}]", currentCrossChainOffsets, stateLedger.consensusChainStore.getLatestLedger().getCurrentEpochState(), stateLedger.consensusChainStore.getLatestLedger().getNextEpochState());
            //ignore the currentEpoch execute the eth event,when eth epoch change, and currentEpoch will  do the event execute when ledger commit
            var newCurrentEpochState = this.consensusChainStore.getLatestLedger().getNextEpochState().copyForNext(false, false);
            newCurrentEpochState.updateValidators(voterUpdateEvents);
            //this.settlementChainsSyncer.updateEpochState(newCurrentEpochState, parentSettlementChainOffsets.getMainChain().getHeight(), currentSettlementChainOffsets.getMainChain().getHeight());
            var newNextEpochState = newCurrentEpochState.copyForNext(true, true);
            logger.info("-----------will do eth epoch change [from {} to {}] for new current:[{}] and  next[{}]", parentSettlementChainOffsets.getMainChain().getHeight(), currentSettlementChainOffsets.getMainChain().getHeight(), newCurrentEpochState, newNextEpochState);
            return Pair.of(new EpochStateHolder(newCurrentEpochState), new EpochStateHolder(newNextEpochState));

        } else {
            //var hasCurrentEpochChange = this.settlementChainsSyncer.hasCurrentEpochChange(parentSettlementChainOffsets.getMainChain().getHeight(), currentSettlementChainOffsets.getMainChain().getHeight());
            var hasCurrentEpochChange = hasEpochChangeEvent(this.consensusChainStore.getLatestLedger().getCurrentEpochState().getEpoch(), voterUpdateEvents);
            if (hasCurrentEpochChange) {
                var newCurrentEpochState = this.consensusChainStore.getLatestLedger().getCurrentEpochState().copyForNext(false, true);
                newCurrentEpochState.updateValidators(voterUpdateEvents);
                //this.settlementChainsSyncer.updateEpochState(newCurrentEpochState, parentSettlementChainOffsets.getMainChain().getHeight(), currentSettlementChainOffsets.getMainChain().getHeight());

                var newNextEpochState = this.consensusChainStore.getLatestLedger().getNextEpochState().copyForNext(false, true);
                //this.settlementChainsSyncer.updateEpochState(newNextEpochState, parentSettlementChainOffsets.getMainChain().getHeight(), currentSettlementChainOffsets.getMainChain().getHeight());
                newNextEpochState.updateValidators(voterUpdateEvents);

                //logger.info("-----------will hasCurrentEpochChange common eth epoch change [from {} to {}] for new current:[{}] and  next[{}]", parentCrossChainOffsets.getMainChain().getHeight(), currentCrossChainOffsets.getMainChain().getHeight(), newCurrentEpochState, newNextEpochState);
                return Pair.of(new EpochStateHolder(newCurrentEpochState), new EpochStateHolder(newNextEpochState));
            } else {
                var hasNextEpochChange = this.hasEpochChangeEvent(this.stateLedger.getLatestLedger().getNextEpochState().getEpoch(), voterUpdateEvents);
                //var hasNextEpochChange = this.settlementChainsSyncer.hasNextEpochChange(parentSettlementChainOffsets.getMainChain().getHeight(), currentSettlementChainOffsets.getMainChain().getHeight());
                //logger.info("ethEpoch:[{}], hasNextEpochChange:{},", this.stateLedger.consensusChainStore.getLatestLedger().getCurrentEpochState().getEthEpoch(), hasNextEpochChange);
                if (!hasNextEpochChange) {
                    return Pair.of(EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER, EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER);
                }

                var newNextEpochState = this.consensusChainStore.getLatestLedger().getNextEpochState().copy();
                newNextEpochState.updateValidators(voterUpdateEvents);
                //this.settlementChainsSyncer.updateEpochState(newNextEpochState, parentSettlementChainOffsets.getMainChain().getHeight(), currentSettlementChainOffsets.getMainChain().getHeight());
                logger.info("-----------will !hasCurrentEpochChange common eth epoch change for  next[{}]", newNextEpochState);

                return Pair.of(EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER, new EpochStateHolder(newNextEpochState));
            }
        }
    }


    private boolean hasEpochChangeEvent(long epoch, List<VoterUpdateEvent> events) {
        for (var e : events) {
            if (e.getOpEthEpoch() == epoch) {
                return true;
            }
        }

        return false;
    }

}
