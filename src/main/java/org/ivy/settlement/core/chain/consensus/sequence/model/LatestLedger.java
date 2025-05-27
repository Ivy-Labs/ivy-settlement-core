package org.ivy.settlement.core.chain.consensus.sequence.model;


import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;

/**
 * description:
 * @author carrot
 */
public class LatestLedger {

    volatile LedgerInfoWithSignatures latestLedgerInfo;

    volatile ExecutedEventOutput commitExecutedEventOutput;

    volatile EpochState currentEpoch;

    volatile EpochState nextEpoch;

    volatile SettlementChainOffsets latestSettlementChainOffsets;

    public LatestLedger() {
    }

    public LatestLedger(LedgerInfoWithSignatures latestLedgerInfo,
                        ExecutedEventOutput commitExecutedEventOutput,
                        EpochState currentEpoch,
                        EpochState nextEpoch,
                        SettlementChainOffsets latestSettlementChainOffsets) {
        this.latestLedgerInfo = latestLedgerInfo;
        this.commitExecutedEventOutput = commitExecutedEventOutput;
        this.currentEpoch = currentEpoch;
        this.nextEpoch = nextEpoch;
        this.latestSettlementChainOffsets = latestSettlementChainOffsets;
    }

    public void reset(LedgerInfoWithSignatures latestLedgerInfo,
                      ExecutedEventOutput commitExecutedEventOutput,
                      SettlementChainOffsets settlementChainOffsets) {
        this.latestLedgerInfo = latestLedgerInfo;
        this.commitExecutedEventOutput = commitExecutedEventOutput;
        this.latestSettlementChainOffsets = settlementChainOffsets;
        if (commitExecutedEventOutput.hasReconfiguration()) {
            this.currentEpoch = commitExecutedEventOutput.getNewCurrentEpochState().get();
        }

        if (commitExecutedEventOutput.getNewNextEpochState().isPresent()) {
            this.nextEpoch = commitExecutedEventOutput.getNewNextEpochState().get();
        }
    }

    public LedgerInfoWithSignatures getLatestLedgerInfo() {
        return latestLedgerInfo;
    }



    public ExecutedEventOutput getCommitExecutedEventOutput() { return commitExecutedEventOutput; }

    public long getLatestNumber() { return latestLedgerInfo.getLedgerInfo().getNumber(); }

    public EpochState getCurrentEpochState() {
        return this.currentEpoch;
    }

    public EpochState getNextEpochState() {
        return nextEpoch;
    }

    public SettlementChainOffsets getLatestCrossChainOffsets() {
        return latestSettlementChainOffsets;
    }


    @Override
    public String toString() {
        return "LatestLedger{" +
                "latestLedgerInfo=" + latestLedgerInfo +
                ", commitExecutedEventOutput=" + commitExecutedEventOutput +
                ", currentEpoch=" + currentEpoch +
                ", nextEpoch=" + nextEpoch +
                ", latestCrossChainOffsets=" + latestSettlementChainOffsets +
                '}';
    }
}
