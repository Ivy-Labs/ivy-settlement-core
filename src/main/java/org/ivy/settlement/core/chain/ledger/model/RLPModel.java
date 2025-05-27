package org.ivy.settlement.core.chain.ledger.model;

/**
 * description:
 * @author carrot
 */
public abstract class RLPModel {

    protected byte[] rlpEncoded;

    public RLPModel(byte[] rlpEncoded) {
        if (rlpEncoded == null) return;
        this.rlpEncoded = rlpEncoded;
        rlpDecoded();
    }

    protected abstract byte[] rlpEncoded();

    protected abstract void rlpDecoded();

    public final byte[] getEncoded() {
        if (rlpEncoded != null) return this.rlpEncoded;
        this.rlpEncoded = rlpEncoded();
        return this.rlpEncoded;
    }
}
