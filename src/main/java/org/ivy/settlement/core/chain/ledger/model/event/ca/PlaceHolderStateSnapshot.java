package org.ivy.settlement.core.chain.ledger.model.event.ca;

import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.core.chain.ledger.model.event.GlobalEventCommand;

import java.util.Objects;

/**
 * description:
 * @author carrot
 */
public class PlaceHolderStateSnapshot extends CandidateStateSnapshot {

    int placeHolder;

    public PlaceHolderStateSnapshot() {
        super(null);
        this.placeHolder = 1;
        this.rlpEncoded = rlpEncoded();
    }

    @Override
    public GlobalEventCommand getCurrentCommand() {
        return GlobalEventCommand.PLACEHOLDER_EMPTY;
    }

    @Override
    protected byte[] rlpEncoded() {
        return RLP.encodeInt(1);
    }

    @Override
    protected void rlpDecoded() {
        this.placeHolder = 1;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaceHolderStateSnapshot that = (PlaceHolderStateSnapshot) o;
        return placeHolder == that.placeHolder;
    }

    @Override
    public int hashCode() {

        return Objects.hash(placeHolder);
    }
}
