package org.ivy.settlement.core.chain.consensus.slowpath.model;

import org.ivy.settlement.core.chain.ledger.model.SettlementBatch;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.spongycastle.util.encoders.Hex;

/**
 * description:
 * @author carrot
 */
public class LocalBatchSignMsg extends SlowPathMsg {

    public long signEpoch;

    public SettlementBatch settlementBatch;

    public byte[] publicKey;

    public Signature signature;

    public LocalBatchSignMsg(long signEpoch, SettlementBatch settlementBatch, byte[] publicKey, Signature signature) {
        super(null);
        this.signEpoch = signEpoch;
        this.settlementBatch = settlementBatch;
        this.publicKey = publicKey;
        this.signature = signature;
    }

    @Override
    public byte getCode() {
        return SlowPathCommand.LOCAL_BLOB_SIGN.getCode();
    }

    @Override
    public SlowPathCommand getCommand() {
        return SlowPathCommand.LOCAL_BLOB_SIGN;
    }

    @Override
    public String toString() {
        return "LocalBlockSignMsg{" +
                "signEpoch=" + signEpoch +
                "epoch=" + settlementBatch.getBornEpoch() +
                ", number=" + settlementBatch.getEndBlockNumber() +
                ", hash=" + Hex.toHexString(settlementBatch.getHash()) +
                '}';
    }

    @Override
    public void releaseReference() {
        this.settlementBatch = null;
        this.publicKey = null;
        this.signature = null;
    }
}
