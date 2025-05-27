package org.ivy.settlement.core.chain.consensus.sequence.safety;

import org.ivy.settlement.core.chain.consensus.sequence.model.LedgerInfo;
import org.ivy.settlement.core.chain.consensus.sequence.model.LedgerInfoWithSignatures;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;

/**
 * description:
 * @author carrot
 */
public interface Verifier {

    ProcessResult<Void> verify(LedgerInfoWithSignatures ledgerInfo);

    boolean epochChangeVerificationRequired(long epoch);

    boolean isLedgerInfoStale(LedgerInfo ledgerInfo);

}
