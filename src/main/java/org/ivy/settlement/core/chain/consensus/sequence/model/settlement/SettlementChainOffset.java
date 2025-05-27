package org.ivy.settlement.core.chain.consensus.sequence.model.settlement;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;

import java.math.BigInteger;
import java.util.Objects;

/**
 * description:
 * @author carrot
 * @since 2024-03-26
 */
public class SettlementChainOffset extends RLPModel {

    public static SettlementChainOffset EMPTY_OFFSET = new SettlementChainOffset(0, 0, 0, -0);

    //0:join
    //1:quit
    int status;

    long comeIntoEffectHeight;

    int chain;

    long height;

    public SettlementChainOffset(int status, long comeIntoEffectHeight, int chain, long height) {
        super(null);
        this.status = status;
        this.comeIntoEffectHeight = comeIntoEffectHeight;
        this.chain = chain;
        this.height = height;
        this.rlpEncoded = rlpEncoded();
    }

    public SettlementChainOffset(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    public int getChain() {
        return chain;
    }

    public long getHeight() {
        return height;
    }

    public int getStatus() {
        return status;
    }

    public long getComeIntoEffectHeight() {
        return comeIntoEffectHeight;
    }

    public void setStatus(int status, long comeIntoEffectHeight) {
        this.status = status;
        this.comeIntoEffectHeight = comeIntoEffectHeight;
        markDirty();
    }

    public void setHeight(long height) {
        this.height = height;
        markDirty();
    }

    public SettlementChainOffset copy() {
        return new SettlementChainOffset(this.status, this.comeIntoEffectHeight, this.chain, this.height);
    }

    @Override
    protected byte[] rlpEncoded() {
        var status = RLP.encodeInt(this.status);
        var comeIntoEffectHeight = RLP.encodeBigInteger(BigInteger.valueOf(this.comeIntoEffectHeight));
        var chain = RLP.encodeInt(this.chain);
        var height = RLP.encodeBigInteger(BigInteger.valueOf(this.height));
        return RLP.encodeList(status, comeIntoEffectHeight, chain, height);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.status = ByteUtil.byteArrayToInt(rlpDecode.get(0).getRLPData());
        this.comeIntoEffectHeight = ByteUtil.byteArrayToLong(rlpDecode.get(1).getRLPData());
        this.chain = ByteUtil.byteArrayToInt(rlpDecode.get(2).getRLPData());
        this.height = ByteUtil.byteArrayToLong(rlpDecode.get(3).getRLPData());
    }

    private void markDirty() {
        this.rlpEncoded = null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SettlementChainOffset that = (SettlementChainOffset) o;
        return status == that.status && comeIntoEffectHeight == that.comeIntoEffectHeight && chain == that.chain && height == that.height;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, comeIntoEffectHeight, chain, height);
    }

    @Override
    public String toString() {
        return "cco{" +
                "status=" + status +
                ", comeIntoEffectHeight=" + comeIntoEffectHeight +
                ", chain=" + chain +
                ", height=" + height +
                '}';
    }
}
