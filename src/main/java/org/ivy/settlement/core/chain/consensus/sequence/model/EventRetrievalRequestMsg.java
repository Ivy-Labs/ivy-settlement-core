package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;

import java.math.BigInteger;

/**
 * description:
 * @author carrot
 */
public class EventRetrievalRequestMsg extends ConsensusMsg {

    byte[] eventId;

    int eventNum;

    public EventRetrievalRequestMsg(byte[] encode) {
        super(encode);
    }

    public EventRetrievalRequestMsg(byte[] eventId, int eventNum) {
        super(null);
        this.eventId = eventId;
        this.eventNum = eventNum;
        this.rlpEncoded = rlpEncoded();
    }

    public byte[] getEventId() {
        return eventId;
    }

    public int getEventNum() {
        return eventNum;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] epoch = RLP.encodeBigInteger(BigInteger.valueOf(this.epoch));
        byte[] eventId = RLP.encodeElement(this.eventId);
        byte[] eventNum = RLP.encodeInt(this.eventNum);
        return RLP.encodeList(epoch, eventId, eventNum);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.epoch = ByteUtil.byteArrayToLong(rlpDecode.get(0).getRLPData());
        this.eventId = rlpDecode.get(1).getRLPData();
        this.eventNum = ByteUtil.byteArrayToInt(rlpDecode.get(2).getRLPData());
    }

    @Override
    public byte getCode() {
        return ConsensusCommand.EVENT_RETRIEVAL_REQ.getCode();
    }

    @Override
    public ConsensusCommand getCommand() {
        return ConsensusCommand.EVENT_RETRIEVAL_REQ;
    }
}
