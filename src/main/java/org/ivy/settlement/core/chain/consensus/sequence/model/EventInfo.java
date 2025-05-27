package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.crypto.CryptoHash;
import org.ivy.settlement.infrastructure.crypto.VerifyingKey;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.crypto.HashUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.config.Constants;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochStateHolder;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.ivy.settlement.core.chain.ledger.model.ValidatorPublicKeyInfo;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * description:
 * @author carrot
 */
public class EventInfo extends RLPModel implements CryptoHash {

    long epoch;

    long round;
    /**
     * The identifier (hash) of the event.
     */
    byte[] id;

    long timestamp;

    //event height
    long number;
    /**
     * The accumulator root hash after executing this event.
     */
    byte[] executedStateId;

    EpochStateHolder newCurrentEpochState;

    EpochStateHolder newNextEpochState;

    // consensus hash,  different from  id, it include executedStateId、nextEpochState and so on for consensus
    byte[] transientHash;

    private EventInfo() {
        super(null);
    }

    public EventInfo(byte[] encode) {
        super(encode);
    }

    public static EventInfo build(long epoch, long round, byte[] id, byte[] executedStateId, long number, long timestamp, EpochStateHolder newCurrentEpochState, EpochStateHolder newNextEpochState) {
        EventInfo info = new EventInfo();
        info.epoch = epoch;
        info.round = round;
        // equals Event.id or EventData->getHash()
        info.id = id;
        info.executedStateId = executedStateId;
        info.number = number;
        info.timestamp = timestamp;
        info.newCurrentEpochState = newCurrentEpochState;
        info.newNextEpochState = newNextEpochState;
        info.rlpEncoded = info.rlpEncoded();
        info.transientHash = HashUtil.sha3(info.rlpEncoded);
        return info;
    }

    public static EventInfo empty() {
        EventInfo info = new EventInfo();
        info.epoch = 0L;
        info.round = 0L;
        info.id = Constants.EMPTY_HASH_BYTES;
        info.executedStateId = Constants.EMPTY_HASH_BYTES;
        info.number = 0;
        info.timestamp = 0;
        info.newCurrentEpochState = EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER;
        info.newNextEpochState = EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER;
        info.rlpEncoded = info.rlpEncoded();
        info.transientHash = HashUtil.sha3(info.rlpEncoded);
        return info;
    }

    public long getEpoch() {
        return epoch;
    }

    public long getRound() {
        return round;
    }

    public long getNumber() {
        return number;
    }

    public byte[] getId() {
        return id;
    }

    public byte[] getExecutedStateId() {
        return executedStateId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean hasReconfiguration() {
        return this.newCurrentEpochState.isPresent();
    }

    public EpochStateHolder getNewCurrentEpochState() {
        return this.newCurrentEpochState;
    }

    public EpochStateHolder getNewNextEpochState() {
        return newNextEpochState;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventInfo eventInfo = (EventInfo) o;
        return epoch == eventInfo.epoch &&
                round == eventInfo.round &&
                timestamp == eventInfo.timestamp &&
                number == eventInfo.number &&
                Arrays.equals(id, eventInfo.id) &&
                Arrays.equals(executedStateId, eventInfo.executedStateId) &&
                Objects.equals(newCurrentEpochState, eventInfo.newCurrentEpochState) &&
                Objects.equals(newNextEpochState, eventInfo.newNextEpochState) ;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] epoch = RLP.encodeBigInteger(BigInteger.valueOf(this.epoch));
        byte[] round = RLP.encodeBigInteger(BigInteger.valueOf(this.round));
        byte[] id = RLP.encodeElement(this.id);
        byte[] timestamp = RLP.encodeBigInteger(BigInteger.valueOf(this.timestamp));
        byte[] number = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        byte[] executedStateId = RLP.encodeElement(this.executedStateId);
        byte[] newCurrentEpochState = this.newCurrentEpochState.getEncoded();
        byte[] newNextEpochState = this.newNextEpochState.getEncoded();
        return RLP.encodeList(epoch, round, id, timestamp, number, executedStateId, newCurrentEpochState, newNextEpochState);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.epoch = ByteUtil.byteArrayToLong(rlpDecode.get(0).getRLPData());
        this.round = ByteUtil.byteArrayToLong(rlpDecode.get(1).getRLPData());
        this.id = rlpDecode.get(2).getRLPData();
        this.timestamp = ByteUtil.byteArrayToLong(rlpDecode.get(3).getRLPData());
        this.number = ByteUtil.byteArrayToLong(rlpDecode.get(4).getRLPData());
        this.executedStateId = rlpDecode.get(5).getRLPData();
        this.newCurrentEpochState = new EpochStateHolder(rlpDecode.get(6).getRLPData());
        this.newNextEpochState = new EpochStateHolder(rlpDecode.get(7).getRLPData());
        this.transientHash = HashUtil.sha3(rlpEncoded);
    }

    @Override
    public byte[] getHash() {
        return transientHash;
    }

    @Override
    public String toString() {
        return "EventInfo{" +
                "epoch=" + epoch +
                ", round=" + round +
                ", number=" + number +
                ", id=" + Hex.toHexString(id) +
                ", timestamp=" + timestamp +
                ", executedStateId=" + Hex.toHexString(executedStateId) +
                ", newCurrentEpochState=" + newCurrentEpochState +
                ", newNextEpochState=" + newNextEpochState +
                ", transientHash=" + Hex.toHexString(transientHash) +
                '}';
    }

    public static void main(String[] args) {
        byte[] id = HashUtil.sha3(new byte[]{11, 22, 33});
        System.out.println(Hex.toHexString(id));
        byte[] exeId = HashUtil.sha3(new byte[]{1, 2, 3});
        System.out.println(Hex.toHexString(exeId));


        ValidatorPublicKeyInfo pk1 = new ValidatorPublicKeyInfo(1, 6, new VerifyingKey(id));
        ValidatorPublicKeyInfo pk2 = new ValidatorPublicKeyInfo(2, 7, new VerifyingKey(exeId));

        System.out.println(new ValidatorPublicKeyInfo(pk1.getEncoded()));

        ValidatorVerifier validatorVerifier = new ValidatorVerifier(Arrays.asList(pk1, pk2));
        System.out.println(new ValidatorVerifier(validatorVerifier.getEncoded()));


        EventInfo eventInfo1 = EventInfo.build(2, 1, id, exeId, 3, System.currentTimeMillis(), EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER, EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER);
        System.out.println(eventInfo1);
        EventInfo rlpEventInfo1 = new EventInfo(eventInfo1.rlpEncoded);
        System.out.println(rlpEventInfo1);

        EventInfo eventInfo2 = EventInfo.build(4, 6, id, exeId, 8, System.currentTimeMillis(), EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER, EpochStateHolder.EMPTY_EPOCH_STATE_HOLDER);
        System.out.println(eventInfo2);

        EventInfo rlpEventInfo2 = new EventInfo(eventInfo2.rlpEncoded);
        System.out.println(rlpEventInfo2);
    }

//    public void clear() {
//        this.id = null;
//        this.executedStateId = null;
//        this.nextEpochState = null;

//    }
}
