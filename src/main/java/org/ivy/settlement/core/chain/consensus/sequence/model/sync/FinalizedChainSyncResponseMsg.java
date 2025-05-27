package org.ivy.settlement.core.chain.consensus.sequence.model.sync;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.sequence.model.EventData;
import org.ivy.settlement.core.chain.consensus.sequence.model.EventInfoWithSignatures;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;

import java.util.ArrayList;
import java.util.List;

/**
 * description:
 * @author carrot
 */
public class FinalizedChainSyncResponseMsg extends FinalizedChainSyncMsg {

    public enum CommitEventRetrievalStatus {
        // Successfully fill in the request.
        SUCCESSED,
        // Can not find the event corresponding to number.
        UN_EXCEPT_NUMBER;

        static CommitEventRetrievalStatus convertFromOrdinal(int ordinal) {
            if (ordinal == 0) {
                return SUCCESSED;
            } else if (ordinal == 1) {
                return UN_EXCEPT_NUMBER;
            } else {
                throw new RuntimeException("ordinal not exit!");
            }
        }
    }

    CommitEventRetrievalStatus status;

    SettlementChainOffsets latestOffsets;

    List<EventData> eventDatas;

    List<EventInfoWithSignatures> eventInfoWithSignatureses;

    public FinalizedChainSyncResponseMsg(byte[] encode) {
        super(encode);
    }

    public FinalizedChainSyncResponseMsg(CommitEventRetrievalStatus status, SettlementChainOffsets latestOffsets, List<EventData> eventDatas, List<EventInfoWithSignatures> eventInfoWithSignatureses) {
        super(null);
        this.status = status;
        this.latestOffsets = latestOffsets;
        this.eventDatas = eventDatas;
        this.eventInfoWithSignatureses = eventInfoWithSignatureses;
        this.rlpEncoded = rlpEncoded();
    }

    public CommitEventRetrievalStatus getStatus() {
        return status;
    }

    public SettlementChainOffsets getLatestOffsets() {
        return latestOffsets;
    }

    public List<EventData> getEventDatas() {
        return eventDatas;
    }

    public List<EventInfoWithSignatures> getEventInfoWithSignatureses() {
        return eventInfoWithSignatureses;
    }

    public boolean isSuccess() {
        return this.status == CommitEventRetrievalStatus.SUCCESSED;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[][] encode = new byte[eventDatas.size() + eventInfoWithSignatureses.size() + 3][];
        encode[0] = RLP.encodeInt(status.ordinal());
        encode[1] = this.latestOffsets.getEncoded();
        encode[2] = RLP.encodeInt(eventDatas.size());

        int i = 3;
        for (EventData event: eventDatas) {
            encode[i] = event.getEncoded();
            i++;
        }

        for (EventInfoWithSignatures eventInfoWithSignatures: eventInfoWithSignatureses) {
            encode[i] = eventInfoWithSignatures.getEncoded();
            i++;
        }

        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        RLPList params = RLP.decode2(rlpEncoded);
        RLPList resp = (RLPList) params.get(0);

        this.status = CommitEventRetrievalStatus.convertFromOrdinal(ByteUtil.byteArrayToInt(resp.get(0).getRLPData()));
        this.latestOffsets = new SettlementChainOffsets(resp.get(1).getRLPData());
        int size = ByteUtil.byteArrayToInt(resp.get(2).getRLPData());

        List<EventData> eventDatas = new ArrayList<>();
        int i = 3;
        int tempEventIndex = size + 3;
        for (; i < tempEventIndex; i++) {
            eventDatas.add(new EventData(resp.get(i).getRLPData()));
        }
        this.eventDatas = eventDatas;

        List<EventInfoWithSignatures> eventInfoWithSignatureses = new ArrayList<>();
        for (; i < resp.size(); i++) {
            eventInfoWithSignatureses.add(new EventInfoWithSignatures(resp.get(i).getRLPData()));
        }
        this.eventInfoWithSignatureses = eventInfoWithSignatureses;
    }

    @Override
    public byte getCode() {
        return FinalizedChainSyncCommand.LAYER2_STATE_SYNC_RESPONSE.getCode();
    }

    @Override
    public FinalizedChainSyncCommand getCommand() {
        return FinalizedChainSyncCommand.LAYER2_STATE_SYNC_RESPONSE;
    }
}
