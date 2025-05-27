package org.ivy.settlement.core.chain.consensus.slowpath.model;


import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;

import java.math.BigInteger;

/**
 * description:
 * @author carrot
 */
public class GetBatchSignRequestMsg extends SlowPathMsg {

    private long signEpoch;

    private long number;

    public GetBatchSignRequestMsg(long signEpoch, long number) {
        super(null);
        this.signEpoch = signEpoch;
        this.number = number;
        this.rlpEncoded = rlpEncoded();
    }

    public GetBatchSignRequestMsg(byte[] encode) {
        super(encode);
    }

    public long getSignEpoch() {
        return signEpoch;
    }

    public long getNumber() {
        return number;
    }

    @Override
    protected byte[] rlpEncoded() {
        var signEpoch = RLP.encodeBigInteger(BigInteger.valueOf(this.signEpoch));
        var number = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        return RLP.encodeList(signEpoch, number);
    }

    @Override
    protected void rlpDecoded() {
        var rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.signEpoch = ByteUtil.byteArrayToLong(rlpDecode.get(0).getRLPData());
        this.number = ByteUtil.byteArrayToLong(rlpDecode.get(1).getRLPData());
    }

    @Override
    public byte getCode() {
        return SlowPathCommand.GET_BLOB_SIGN_REQ.getCode();
    }

    @Override
    public SlowPathCommand getCommand() {
        return SlowPathCommand.GET_BLOB_SIGN_REQ;
    }

    @Override
    public String toString() {
        return "GetBlobSignRequestMsg{" +
                "signEpoch=" + signEpoch +
                "number=" + number +
                '}';
    }
}
