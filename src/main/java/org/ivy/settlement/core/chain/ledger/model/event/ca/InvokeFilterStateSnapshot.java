package org.ivy.settlement.core.chain.ledger.model.event.ca;

import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.core.chain.ledger.model.event.GlobalEventCommand;

/**
 * description:
 * @author carrot
 */
public class InvokeFilterStateSnapshot extends CandidateStateSnapshot {

    int placeHolder;

    public InvokeFilterStateSnapshot() {
        super(null);
        this.placeHolder = 1;
        this.rlpEncoded = rlpEncoded();
    }

    @Override
    public GlobalEventCommand getCurrentCommand() {
        return GlobalEventCommand.INVOKE_FILTER;
    }

    @Override
    protected byte[] rlpEncoded() {
        return RLP.encodeInt(1);
    }

    @Override
    protected void rlpDecoded() {
        this.placeHolder = 1;
    }
}
