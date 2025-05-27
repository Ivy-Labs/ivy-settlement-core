package org.ivy.settlement.core.chain.consensus.sequence.liveness;

import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;

import java.math.BigInteger;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * description:
 * @author carrot
 */
public class EpochStateHolder extends RLPModel {

    public static final EpochStateHolder EMPTY_EPOCH_STATE_HOLDER = new EpochStateHolder();

    EpochState epochState;

    public EpochStateHolder(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    public EpochStateHolder(EpochState epochState) {
        super(null);
        this.epochState = epochState;
        this.rlpEncoded = rlpEncoded();
    }

    private EpochStateHolder() {
        super(null);
        this.epochState = null;
        this.rlpEncoded = rlpEncoded();
    }

    public EpochState get() {
        if (this.epochState == null) {
            throw new NoSuchElementException("No epochState present");
        }
        return this.epochState;
    }

    public boolean isPresent() {
        return this.epochState != null;
    }

    @Override
    protected byte[] rlpEncoded() {
        var size = 1 + (this.epochState != null ? 1 : 0);
        byte[][] encode = new byte[size][];
        encode[0] = RLP.encodeBigInteger(BigInteger.valueOf(size));
        if (this.epochState != null) {
            encode[1] = this.epochState.rlpEncoded();
        }
        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(this.rlpEncoded).get(0);
        if (rlpDecode.size() == 2) {
            this.epochState = new EpochState(rlpDecode.get(1).getRLPData());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EpochStateHolder that = (EpochStateHolder) o;
        return Objects.equals(epochState, that.epochState);
    }

    @Override
    public int hashCode() {
        return Objects.hash(epochState);
    }

    @Override
    public String toString() {
        return "EpochStateHolder{" +
                "epochState=" + (epochState != null ? epochState.toString() : "empty") +
                '}';
    }
}
