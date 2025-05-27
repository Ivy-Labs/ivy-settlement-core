package org.ivy.settlement.core.chain.consensus.fastpath.model;

import org.ivy.settlement.core.chain.ledger.model.Block;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.spongycastle.util.encoders.Hex;

/**
 * description:
 * @author carrot
 */
public class LocalBlockSignMsg extends FastPathMsg {

    public Block block;

    public byte[] publicKey;

    public Signature signature;


    public LocalBlockSignMsg(Block block, byte[] publicKey, Signature signature) {
        super(null);
        this.block = block;
        this.publicKey = publicKey;
        this.signature = signature;
    }

    @Override
    public byte getCode() {
        return FastPathCommand.LOCAL_BLOCK_SIGN.getCode();
    }

    @Override
    public FastPathCommand getCommand() {
        return FastPathCommand.LOCAL_BLOCK_SIGN;
    }

    @Override
    public String toString() {
        return "LocalBlockSignMsg{" +
                "epoch=" + block.getEpoch() +
                ", number=" + block.getNumber() +
                ", hash=" + Hex.toHexString(block.getHash()) +
                '}';
    }

    @Override
    public void releaseReference() {
        this.block = null;
        this.publicKey = null;
        this.signature = null;
    }
}
