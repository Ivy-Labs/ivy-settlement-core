package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.infrastructure.datasource.model.Keyable;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * description:
 * @author carrot
 */
public class ExecutedEvent {

    Event event;

    ExecutedEventOutput executeOutput;

    TreeMap<ByteArrayWrapper, Signature> signatures;

    public ExecutedEvent(Event event, ExecutedEventOutput executeOutput) {
        this.event = event;
        this.executeOutput = executeOutput;
    }

    public Event getEvent() {
        return event;
    }

    public ExecutedEventOutput getExecutedEventOutput() { return executeOutput; }

    public Map<Keyable.DefaultKeyable, byte[]> getStateOutput() { return executeOutput.output; }

    public long getEventNumber() {
        return executeOutput.eventNumber;
    }

    public byte[] getStateRoot() {
        return executeOutput.stateRoot;
    }

    public long getEpoch() {
        return event.getEpoch();
    }

    public byte[] getId() {
        return event.getId();
    }

    public byte[] getParentId() {
        return event.getQuorumCert().getCertifiedEvent().getId();
    }

    public Optional<byte[]> getAuthor() {
        return event.getAuthor();
    }

//    public ConsensusPayload getPayload() {
//        return event.getPayload();
//    }

    public long getTimestamp() {
        return event.getTimestamp();
    }

    public long getRound() {
        return event.getRound();
    }

    public EventInfo getEventInfo() {
        return event.buildEventInfo(executeOutput.stateRoot, executeOutput.eventNumber, executeOutput.newCurrentEpoch, executeOutput.newNextEpoch);
    }

    public EventInfoWithSignatures getEventInfoWithSignatures() {
        return event.buildEventInfoWithSignatures(executeOutput.stateRoot, executeOutput.eventNumber, executeOutput.newCurrentEpoch, executeOutput.newNextEpoch, signatures);
    }

    public TreeMap<ByteArrayWrapper, Signature> getSignatures() {
        return signatures;
    }

    public void setSignatures(TreeMap<ByteArrayWrapper, Signature> signatures) {
        this.signatures = signatures;
    }

    public void clear() {
        event = null;
        //event.clear();
//        executeOutput.clear();
//        signatures = null;
        //signatures.clear();
    }

    @Override
    public String toString() {
        return "ExecutedEvent{" +
                "event=" + event +
                '}';
    }
}
