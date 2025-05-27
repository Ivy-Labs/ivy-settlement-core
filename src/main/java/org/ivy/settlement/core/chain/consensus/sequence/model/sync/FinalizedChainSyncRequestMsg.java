package org.ivy.settlement.core.chain.consensus.sequence.model.sync;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;

import java.math.BigInteger;

/**
 * description:
 * @author carrot
 */
public class FinalizedChainSyncRequestMsg extends FinalizedChainSyncMsg {

    long startEventNumber;

    //long exceptSyncNum;

    public FinalizedChainSyncRequestMsg(byte[] encode) {
        super(encode);
    }

    public FinalizedChainSyncRequestMsg(long startEventNumber) {
        super(null);
        this.startEventNumber = startEventNumber;
        this.rlpEncoded = rlpEncoded();
    }


    public long getStartEventNumber() {
        return startEventNumber;
    }


    @Override
    protected byte[] rlpEncoded() {
        byte[] startEventNumber = RLP.encodeBigInteger(BigInteger.valueOf(this.startEventNumber));
        return RLP.encodeList(startEventNumber);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.startEventNumber = ByteUtil.byteArrayToLong(rlpDecode.get(0).getRLPData());
    }

    @Override
    public byte getCode() {
        return FinalizedChainSyncCommand.LAYER2_STATE_SYNC_REQUEST.getCode();
    }

    @Override
    public FinalizedChainSyncCommand getCommand() {
        return FinalizedChainSyncCommand.LAYER2_STATE_SYNC_REQUEST;
    }
}
