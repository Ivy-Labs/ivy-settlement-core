package org.ivy.settlement.core.chain.consensus.slowpath;

import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.ledger.model.SettlementBatch;

/**
 * description:
 * @author carrot
 */
public interface SettlementBatchFinality {

    boolean onChain(EpochState signEpoch, SettlementBatch settlementBatch);
}
