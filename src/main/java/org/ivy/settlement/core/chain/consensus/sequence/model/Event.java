package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.bytes.FastByteComparisons;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochStateHolder;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.core.chain.ledger.model.crypto.ValidatorSigner;
import org.ivy.settlement.infrastructure.datasource.model.Keyable;
import org.ivy.settlement.infrastructure.datasource.model.Persistable;
import org.spongycastle.util.encoders.Hex;

import java.util.*;

/**
 * description:
 * @author carrot
 */
public class Event extends Persistable {

    //equals EventData->getHash()
    private byte[] id;

    private EventData eventData;

    private Optional<Signature> signature;

    //@Transient
    private Map<Keyable, byte[]> deltaState;

    public Event(){super(null);}

    public Event(byte[] encode){super(encode);}

    public static  Event build(byte[] id, EventData eventData, Map<Keyable, byte[]> deltaState, Optional<Signature> signature) {
        Event event = new Event();
        event.id = id;
        event.eventData = eventData;
        event.signature = signature;
        event.deltaState = deltaState;
        event.rlpEncoded = event.rlpEncoded();
        return event;
    }

//    public static Event buildGenesisEvent() {
//        return buildGenesisEventFromLedgerInfo(LedgerInfo.buildGenesis(Constants.EMPTY_HASH_BYTES, null));
//    }

    public static Event buildGenesisEventFromLedgerInfo(LedgerInfo ledgerInfo, SettlementChainOffsets latestOffsets) {
        var eventData = EventData.buildGenesisFromLedgerInfo(ledgerInfo, latestOffsets);
        return build(eventData.getHash(), eventData, null, Optional.empty());
    }

    public static Event buildEmptyEvent(long round, QuorumCert qc, SettlementChainOffsets latestOffsets) {
        var eventData = EventData.buildEmpty(round, qc, latestOffsets);
        return build(eventData.getHash(), eventData, null, Optional.empty());
    }

//    public static  Event buildProposalEvent(ConsensusPayload payload, long round, long timestamp, QuorumCert quorumCert, ValidatorSigner signer) {
//        EventData eventData = EventData.buildProposal(payload, signer.getAuthor(), round, timestamp, quorumCert);
//        return buildProposalFromEventData(eventData, signer);
//    }

    public static  Event buildProposalFromEventData(EventData eventData, ValidatorSigner signer) {
        var id = eventData.getHash();
        var signature = signer.signMessage(id);
        //当生成共识Event作为签名时，无需写入deltaState。
        return  build(id, eventData, null, signature);
    }

    public EventInfo buildEventInfo(byte[] executedStateId, long number, EpochStateHolder newCurrentEpochState, EpochStateHolder newNextEpochState) {
        return EventInfo.build(getEpoch(), getRound(), getId(), executedStateId, number, getTimestamp(), newCurrentEpochState, newNextEpochState);
    }

    public EventInfoWithSignatures buildEventInfoWithSignatures(byte[] executedStateId, long number, EpochStateHolder newCurrentEpoch, EpochStateHolder newNextEpoch, TreeMap<ByteArrayWrapper, Signature> signatures) {
        return EventInfoWithSignatures.build(getEpoch(), getRound(), getId(), executedStateId, number, getTimestamp(), newCurrentEpoch, newNextEpoch, signatures);
    }

    public EventData getEventData() {
        return eventData;
    }

    public Optional<byte[]> getAuthor() {
        return this.eventData.getAuthor();
    }

    public long getEpoch() {
        return this.eventData.getEpoch();
    }

    public byte[] getId() {
        return id;
    }

    public boolean isParentOf(Event event) {
        return FastByteComparisons.equal(event.getId(), this.id);
    }

    public byte[] getParentId() {
        return this.eventData.getQuorumCert().getCertifiedEvent().getId();
    }

    public ConsensusPayload getPayload() {
        return this.eventData.getPayload();
    }

    public boolean hasGlobalEvents() {
        return !eventData.getGlobalEvent().isEmpty();
    }



    public QuorumCert getQuorumCert() {
        return this.eventData.getQuorumCert();
    }

    public long getRound() {
        return this.eventData.getRound();
    }

    public Optional<Signature> getSignature() {
        return signature;
    }

    public long getTimestamp() {
        return this.eventData.getTimestamp();
    }

    public long getEventNumber() {return this.eventData.getNumber(); }

    public boolean isGenesisEvent() {
        return this.eventData.getEventType() == EventData.EventType.GENESIS;
    }

    public boolean isEmptyEvent() {
        return this.eventData.getEventType() == EventData.EventType.EMPTY_EVENT;
    }


    public ProcessResult<Void> validateSignatures(ValidatorVerifier verifier) {
        switch (eventData.getEventType()) {
            case EventData.EventType.GENESIS:
                return ProcessResult.ofError("We should not accept genesis from others");
            case EventData.EventType.EMPTY_EVENT:
                return getQuorumCert().verify(verifier);
            case EventData.EventType.PROPOSAL:
                {
                    if(signature.isEmpty()) return ProcessResult.ofError("Missing signature in Proposal");
                    var verifyResult = verifier.verifySignature(new ByteArrayWrapper(this.getAuthor().get()), id, signature.get());
                    if (!verifyResult.isSuccess()) {
                        return ProcessResult.ofError("verifier.verify error:" + verifyResult);
                    }
                    return this.getQuorumCert().verify(verifier);
                }
            default:
                return ProcessResult.ofError("un know event type");
        }
    }

    public ProcessResult<Void> verifyWellFormed() {
        if (isGenesisEvent()) {
            return ProcessResult.ofError("We must not accept genesis from others");
        }

        EventInfo parent = getQuorumCert().getCertifiedEvent();

        if (getRound() <= parent.getRound()) {
            return ProcessResult.ofError("event must have a greater round than parent's event");
        }

        if (getEpoch() != parent.getEpoch()) {
            return ProcessResult.ofError("event's parent should be in the same epoch");
        }

        if (parent.hasReconfiguration()) {
            if (!eventData.getGlobalEvent().isEmpty() || !eventData.getPayload().isEmpty()) {
                return ProcessResult.ofError("Reconfiguration suffix should not carry payload");
            }
        }

        if (isEmptyEvent() || parent.hasReconfiguration()) {
            if (getTimestamp() != parent.getTimestamp()) {
                return ProcessResult.ofError("empty/reconfig suffix block must have same timestamp as parent");
            }
        } else {
            if (getTimestamp() <= parent.getTimestamp()) {
                return ProcessResult.ofError("event must have strictly increasing timestamps");
            }
        }

        if (getQuorumCert().isEpochChange()) {
            return ProcessResult.ofError("Event cannot be proposed in an epoch that has Epoch Change");
        }

        if (!Arrays.equals(id, eventData.getHash())) {
            return ProcessResult.ofError("event id mismatch the hash");
        }

        return ProcessResult.SUCCESSFUL;
    }

    @Override
    protected byte[] rlpEncoded() {
        var id = RLP.encodeElement(this.id);
        var eventData = this.eventData.getEncoded();
        var signature = this.signature.isPresent()?RLP.encodeElement(this.signature.get().getSig()): ByteUtil.EMPTY_BYTE_ARRAY;
        return RLP.encodeList(id, eventData, signature);
    }

    @Override
    protected void rlpDecoded() {
        var rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.id = rlpDecode.get(0).getRLPData();
        this.eventData = new EventData(rlpDecode.get(1).getRLPData());
        if (rlpDecode.size() > 2) {
            this.signature = Optional.of(new Signature(rlpDecode.get(2).getRLPData()));
        } else {
            this.signature = Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "Event{" +
                "id=" + Hex.toHexString(id) +
                ", " + eventData +
                '}';
    }

    public void clear() {
        //id = null;
        eventData.clear();
        //signature = null;
    }
}
