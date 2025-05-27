package org.ivy.settlement.core.chain.consensus.fastpath.model;


import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;

import java.math.BigInteger;

/**
 * description:
 * @author carrot
 */
public class GetCommitBlockRequestMsg extends FastPathMsg {

    private long startNumber;

    private long endNumber;

    public GetCommitBlockRequestMsg(long startNumber, long endNumber) {
        super(null);
        this.startNumber = startNumber;
        this.endNumber = endNumber;
        this.rlpEncoded = rlpEncoded();
    }

    public GetCommitBlockRequestMsg(byte[] encode) {
        super(encode);
    }

    public long getStartNumber() {
        return startNumber;
    }

    public long getEndNumber() {
        return endNumber;
    }

    @Override
    protected byte[] rlpEncoded() {
        var startNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.startNumber));
        var endNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.endNumber));
        return RLP.encodeList(startNumber, endNumber);
    }

    @Override
    protected void rlpDecoded() {
        var rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.startNumber = ByteUtil.byteArrayToLong(rlpDecode.get(0).getRLPData());
        this.endNumber = ByteUtil.byteArrayToLong(rlpDecode.get(1).getRLPData());
    }

    @Override
    public byte getCode() {
        return FastPathCommand.GET_COMMIT_BLOCK_REQ.getCode();
    }

    @Override
    public FastPathCommand getCommand() {
        return FastPathCommand.GET_COMMIT_BLOCK_REQ;
    }

    @Override
    public String toString() {
        return "GetCommitBlockRequestMsg{" +
                "startNumber=" + startNumber +
                ", endNumber=" + endNumber +
                '}';
    }
}
