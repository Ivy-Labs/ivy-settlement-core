package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;

import java.util.ArrayList;
import java.util.List;

/**
 * description:
 * @author carrot
 */
public class EventRetrievalResponseMsg extends ConsensusMsg {


    RetrievalStatus status;

    //event num descending
    List<Event> events;

    public EventRetrievalResponseMsg(byte[] encode) {
        super(encode);
    }

    public EventRetrievalResponseMsg(RetrievalStatus status, List<Event> events) {
        super(null);
        this.events = events;
        this.status = status;
        this.rlpEncoded = rlpEncoded();
    }

    public List<Event> getEvents() {
        return events;
    }

    public RetrievalStatus getStatus() {
        return status;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[][] encode = new byte[events.size() + 1][];
        encode[0] = RLP.encodeInt(status.ordinal());
        int i = 1;
        for (Event event: events) {
            encode[i] = event.getEncoded();
            i++;
        }
        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        RLPList params = RLP.decode2(rlpEncoded);
        RLPList retrievalRes = (RLPList) params.get(0);
        this.status = RetrievalStatus.convertFromOrdinal(ByteUtil.byteArrayToInt(retrievalRes.get(0).getRLPData()));

        List<Event> events = new ArrayList<>();
        for (int i = 1; i < retrievalRes.size(); i++) {
            events.add(new Event(retrievalRes.get(i).getRLPData()));
        }
        this.events = events;
    }


    @Override
    public byte getCode() {
        return ConsensusCommand.EVENT_RETRIEVAL_RESP.getCode();
    }

    @Override
    public ConsensusCommand getCommand() {
        return ConsensusCommand.EVENT_RETRIEVAL_RESP;
    }

    @Override
    public String toString() {
        return "EventRetrievalResponseMsg{" +
                "status=" + status +
                ", events size =" + events.size() +
                '}';
    }
}
