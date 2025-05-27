package org.ivy.settlement.core.chain.consensus.fastpath.model;


import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;

import java.math.BigInteger;

/**
 * description:
 * @author carrot
 */
public class GetBlockSignRequestMsg extends FastPathMsg {

    private long number;

    public GetBlockSignRequestMsg(long number) {
        super(null);
        this.number = number;
        this.rlpEncoded = rlpEncoded();
    }

    public GetBlockSignRequestMsg(byte[] encode) {
        super(encode);
    }

    public long getNumber() {
        return number;
    }

    @Override
    protected byte[] rlpEncoded() {
        var number = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        return RLP.encodeList(number);
    }

    @Override
    protected void rlpDecoded() {
        var rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.number = ByteUtil.byteArrayToLong(rlpDecode.get(0).getRLPData());
    }

    @Override
    public byte getCode() {
        return FastPathCommand.GET_BLOCK_SIGN_REQ.getCode();
    }

    @Override
    public FastPathCommand getCommand() {
        return FastPathCommand.GET_BLOCK_SIGN_REQ;
    }

    @Override
    public String toString() {
        return "GetBlockSignRequestMsg{" +
                "number=" + number +
                '}';
    }
}
