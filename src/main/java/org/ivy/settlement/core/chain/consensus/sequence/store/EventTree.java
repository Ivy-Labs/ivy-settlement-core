package org.ivy.settlement.core.chain.consensus.sequence.store;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.infrastructure.anyhow.Assert;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.txpool.TxnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.*;

/**
 * description:
 * @author carrot
 */
public class EventTree {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    protected static class LinkableEvent {

        public LinkableEvent(ExecutedEvent executedEvent) {
            this.executedEvent = executedEvent;
            this.children = new HashSet<>();
        }

        ExecutedEvent executedEvent;

        Set<ByteArrayWrapper> children;

        boolean reimport = false;

        public void addChild(byte[] childEventId) {
            children.add(new ByteArrayWrapper(childEventId));
        }

        public byte[] getEventId() {
            return this.executedEvent.getId();
        }

        public ExecutedEvent getExecutedEvent() {
            return executedEvent;
        }

        public boolean hasReimport() {
            return reimport;
        }

        public void setImport() {
            this.reimport = true;
        }

        public void clear() {
//            children.clear();
            executedEvent.clear();
//            executedEvent = null;
//            children = null;
        }

        @Override
        public String toString() {
            return "LinkableEvent{" +
                    "self =" + Hex.toHexString(executedEvent.getId()) +
                    ", parent =" + Hex.toHexString(executedEvent.getParentId()) +
                    ", children=" + children +
                    '}';
        }
    }

    TxnManager txnManager;

    HashMap<ByteArrayWrapper, LinkableEvent> id2Event;

    //commit root id
    byte[] rootId;

    byte[] highestCertifiedEventId;

    QuorumCert highestQuorumCert;

    Optional<TimeoutCertificate> highestTimeoutCert;

    Optional<TwoChainTimeoutCertificate> highestTwoChainTimeoutCert;

    //highest_ordered_cert
    QuorumCert highestCommitCert;

    HashMap<ByteArrayWrapper, QuorumCert> id2QC;

    LinkedList<byte[]> prunedEventIds;

    // 这里需要依据系统的内存大小定，不能太大，如果太大
    // 并且每一个event的占用的内存太大的情况下，会应无法及时gc而导致oom。
    int max_pruned_events_in_mem;

    boolean reimportUnCommitEvent;

    public EventTree(TxnManager txnManager, ExecutedEvent rootEvent, QuorumCert rootQC, QuorumCert rootLeaderInfo, Optional<TimeoutCertificate> highestTimeoutCert, Optional<TwoChainTimeoutCertificate> highestTwoChainTc, int max_pruned_events_in_mem, boolean reimportUnCommitEvent) {
        Assert.ensure(Arrays.equals(rootEvent.getId(), rootLeaderInfo.getCommitEvent().getId()),
                "inconsistent root and ledger info");
        this.max_pruned_events_in_mem = Math.max(max_pruned_events_in_mem, 10);
        this.reimportUnCommitEvent = reimportUnCommitEvent;
        this.txnManager = txnManager;
        this.rootId = rootEvent.getId();
        this.highestCertifiedEventId = rootEvent.getId();

        this.id2Event = new HashMap<>();
        this.id2Event.put(new ByteArrayWrapper(rootEvent.getId()), new LinkableEvent(rootEvent));

        this.highestQuorumCert = rootQC;
        this.highestCommitCert = rootLeaderInfo;
        this.highestTimeoutCert = highestTimeoutCert;
        this.highestTwoChainTimeoutCert = highestTwoChainTc;

        this.id2QC = new HashMap<>();
        id2QC.put(new ByteArrayWrapper(rootQC.getCertifiedEvent().getId()), rootQC);

        prunedEventIds = new LinkedList<>();

        logger.info("event tree build success! max_pruned_events_in_mem:{}, reimportUnCommitEvent:{}", this.max_pruned_events_in_mem, this.reimportUnCommitEvent);
    }

    public QuorumCert getHighestQuorumCert() {
        return highestQuorumCert;
    }

    public Optional<TimeoutCertificate> getHighestTimeoutCert() {
        return highestTimeoutCert;
    }

    public Optional<TwoChainTimeoutCertificate> getHighestTwoChainTimeoutCert() {
        return highestTwoChainTimeoutCert;
    }

    public QuorumCert getHighestCommitCert() {
        return highestCommitCert;
    }

    public ExecutedEvent getHighestCertifiedEvent() {
        return getEvent(this.highestCertifiedEventId);
    }

    private LinkableEvent getLinkableEvent(byte[] eventId) {
        return id2Event.get(new ByteArrayWrapper(eventId));
    }

    public LinkableEvent getRootLinkableEvent() {
        return getLinkableEvent(rootId);
    }

    public ExecutedEvent getRootEvent() {
        return getEvent(this.rootId);
    }

    public ExecutedEvent getEvent(byte[] eventId) {
        var linkableEvent = getLinkableEvent(eventId);
        return linkableEvent == null? null: linkableEvent.executedEvent;
    }

    public void doRemoveCommitEvent(LinkedList<ByteArrayWrapper> orderCommitPathEvents) {
        var leftSize = orderCommitPathEvents.size() - this.max_pruned_events_in_mem;
        if (leftSize > 0) {
            for (var i = 0; i < leftSize; i++) {
                var id = orderCommitPathEvents.pollLast();
                var removeEvent = id2Event.remove(id);
                removeEvent.clear();
                // help gc
                QuorumCert quorumCert = id2QC.remove(id);
                //quorumCert.clear();
            }
        }
    }

    public void doRemoveUnCommitEvent(Set<ByteArrayWrapper> unCommitPathEvents) {
        if (CollectionUtils.isEmpty(unCommitPathEvents)) return;

        //Map<ByteArrayWrapper, EventData> unCommitEvents = new HashMap<>(8);
        for (var id: unCommitPathEvents) {
            var quorumCert = id2QC.remove(id);
            var removeEvent = id2Event.remove(id);
            var event = removeEvent.executedEvent.getEvent();
            removeEvent.clear();
        }
    }


    public boolean eventExists(byte[] eventId) {
        return id2Event.containsKey(new ByteArrayWrapper(eventId));
    }

    public void replaceTimeoutCert(TimeoutCertificate timeoutCertificate) {
        this.highestTimeoutCert = Optional.of(timeoutCertificate);
    }

    public void replaceTwoChainTimeoutCert(TwoChainTimeoutCertificate twoChainTimeoutCertificate) {
        this.highestTwoChainTimeoutCert = Optional.of(twoChainTimeoutCertificate);
    }

    public QuorumCert getQCForEvent(byte[] eventId) {
        return this.id2QC.get(new ByteArrayWrapper(eventId));
    }


    public Pair<QuorumCert, List<Event>> getThreeChainCommitPair() {
        var rootEvent = getRootEvent().getEvent();
        QuorumCert highestCommitCert = null;
        for (var quorumCert: id2QC.values()) {
            if (Arrays.equals(rootEvent.getId(), quorumCert.getCommitEvent().getId())) {
                highestCommitCert = quorumCert;
                break;
            }
        }

        var highestCommitCertOneEvent = getEvent(highestCommitCert.getCertifiedEvent().getId()).getEvent();
        var highestCommitCertTwoEvent = getEvent(highestCommitCert.getParentEvent().getId()).getEvent();
        return Pair.of(highestCommitCert, Arrays.asList(highestCommitCertOneEvent, highestCommitCertTwoEvent, rootEvent));
    }

    public Pair<QuorumCert, List<Event>> getTwoChainCommitPair() {
        var rootEvent = getRootEvent().getEvent();
        QuorumCert highestCommitCert = null;
        for (var quorumCert: id2QC.values()) {
            if (Arrays.equals(rootEvent.getId(), quorumCert.getCommitEvent().getId())) {
                highestCommitCert = quorumCert;
                break;
            }
        }

        var highestCommitCertOneEvent = getEvent(Objects.requireNonNull(highestCommitCert, "getTwoChainCommitPair for rootEvent has not qc").getCertifiedEvent().getId()).getEvent();
        return Pair.of(highestCommitCert, Arrays.asList(highestCommitCertOneEvent, rootEvent));
    }

    public ExecutedEvent insertEvent(ExecutedEvent event) {
        var existingEvent = this.getEvent(event.getId());
        if (existingEvent != null) {
            logger.debug("Already had event {} when trying to add another event {} for the same id", existingEvent, event);
            return existingEvent;
        }

        var parentLinkableEvent = getLinkableEvent(event.getParentId());

        Assert.ensure(parentLinkableEvent != null,
                "Parent event {} not found", Hex.toHexString(event.getParentId()));
        parentLinkableEvent.addChild(event.getId());

        var linkableEvent = new LinkableEvent(event);
        id2Event.put(new ByteArrayWrapper(event.getId()), linkableEvent);
        return event;
    }

    public void insertQC(QuorumCert qc) {
        var eventId = qc.getCertifiedEvent().getId();

        // Safety invariant: For any two quorum certificates qc1, qc2 in the block store,
        // qc1 == qc2 || qc1.round != qc2.round
        // The invariant is quadratic but can be maintained in linear time by the check
        // below.
        Assert.ensure(
                (this.id2QC.entrySet().stream()
                .allMatch(entry ->
                        Arrays.equals(entry.getValue().getLedgerInfoWithSignatures().getLedgerInfo().getConsensusDataHash(), qc.getLedgerInfoWithSignatures().getLedgerInfo().getConsensusDataHash())
                                || entry.getValue().getCertifiedEvent().getRound() != qc.getCertifiedEvent().getRound()
                        )
                ), "For any two quorum certificates qc1, qc2 in the block store to check not match!");

        var event = this.getEvent(eventId);

        Assert.ensure(event != null,
                "event {} not found", Hex.toHexString(eventId));

        if (event.getRound() > this.getHighestCertifiedEvent().getRound()) {
            this.highestCertifiedEventId = event.getId();
            this.highestQuorumCert = qc;
        }

        var eventIdWrapper = new ByteArrayWrapper(eventId);
        if (!this.id2QC.containsKey(eventIdWrapper)) {
            this.id2QC.put(new ByteArrayWrapper(eventId), qc);
        }

        if (this.highestCommitCert.getCommitEvent().getRound() < qc.getCommitEvent().getRound()) {
            this.highestCommitCert = qc;
        }
    }

    public Set<ByteArrayWrapper> findEventToPrune(byte[] nextRootId) {
        if (Arrays.equals(nextRootId, this.rootId)) {
            return Collections.emptySet();
        }

        var eventsPruned = new HashSet<ByteArrayWrapper>(id2Event.size());
        eventsPruned.addAll(id2Event.keySet());

        var eventsToBeKeep = new LinkedList<LinkableEvent>();

        eventsToBeKeep.add(getLinkableEvent(nextRootId));

        while (eventsToBeKeep.peek() != null) {
            var eventToKeep = eventsToBeKeep.pop();
            eventsPruned.remove(new ByteArrayWrapper(eventToKeep.executedEvent.getId()));
            var iterator = eventToKeep.children.iterator();

            while (iterator.hasNext()) {
                var child = iterator.next();
                eventsPruned.remove(child);

                //if (Arrays.equals(nextRootId, child.getData())) continue;

                eventsToBeKeep.add(this.getLinkableEvent(child.getData()));
            }

        }

        return eventsPruned;
    }



    public void processPrunedEvents(byte[] nextRootId, Set<ByteArrayWrapper> newlyPrunedEvents) {
        Assert.ensure(this.eventExists(rootId),
                "root event[{}] is not exist", Hex.toHexString(rootId));

        var unCommitPathEvents = newlyPrunedEvents;

        var orderCommitPathEvents = new LinkedList<ByteArrayWrapper>();


        var nextRootEvent = getLinkableEvent(nextRootId);
        var parentEvent = getLinkableEvent(nextRootEvent.executedEvent.getParentId());
        while (true) {
            if (parentEvent == null) {
                break;
            }

            var currentId = new ByteArrayWrapper(parentEvent.getEventId());
            orderCommitPathEvents.addLast(currentId);
            unCommitPathEvents.remove(currentId);

            if (Arrays.equals(parentEvent.getEventId(), parentEvent.executedEvent.getParentId())) {
                break;
            }

            parentEvent = getLinkableEvent(parentEvent.executedEvent.getParentId());
        }

        if (logger.isTraceEnabled()) {
            printTrace(nextRootId, orderCommitPathEvents, unCommitPathEvents);
        }

        doRemoveTxnPoolCommitEvent(nextRootId);
        // Update the next root
        this.rootId = nextRootId;

        this.doRemoveUnCommitEvent(unCommitPathEvents);
        this.doRemoveCommitEvent(orderCommitPathEvents);

    }

    private void doRemoveTxnPoolCommitEvent(byte[] nextRootId) {
        var nextRootEvent = getLinkableEvent(nextRootId);
        if (!nextRootEvent.executedEvent.getEvent().getEventData().allEmpty()) {
            this.txnManager.doRemoveCheck(nextRootEvent.executedEvent.getEvent().getEventData());
        }

        var parentEvent = getLinkableEvent(nextRootEvent.executedEvent.getParentId());
        while (parentEvent != null && !Arrays.equals(parentEvent.executedEvent.getId(), this.rootId)) {
            if (!parentEvent.executedEvent.getEvent().getEventData().allEmpty()) {
                this.txnManager.doRemoveCheck(parentEvent.executedEvent.getEvent().getEventData());
            }
            parentEvent = getLinkableEvent(parentEvent.executedEvent.getParentId());
        }
    }

    public List<ExecutedEvent> pathFromRoot(byte[] eventId) {
        var currentId = eventId;
        var result = new ArrayList<ExecutedEvent>(8);
        while (true) {
            var event = this.getEvent(currentId);
            if (event == null) {
                return Collections.emptyList();
            }

            if (event.getRound() <= this.getRootEvent().getRound()) break;

            currentId = event.getParentId();
            result.add(event);
        }

        // At this point cur_block.round() <= self.root.round()
        if (!Arrays.equals(rootId, currentId)) return Collections.emptyList();

        Collections.reverse(result);
        return result;
    }

    public Set<ByteArrayWrapper> getAllEventId() {
        return this.id2Event.keySet();
    }

    public int getLinkableEventsSize() {
        int result = 0;
        var toVisit = new LinkedList<LinkableEvent>();
        toVisit.push(getRootLinkableEvent());
        while (toVisit.peek() != null) {
            result++;
            var current = toVisit.pop();
            for (var childId: current.children) {
                toVisit.push(getLinkableEvent(childId.getData()));
            }
        }

        return result;
    }

    public int getChildLinkableEventsSize() {
        return this.getLinkableEventsSize() - 1;
    }

    public int getPrunedEventsInMemSize() {
        return this.prunedEventIds.size();
    }

    public void printTrace(byte[] nextRootId, LinkedList<ByteArrayWrapper> orderCommitPathEvents, Set<ByteArrayWrapper> unCommitPathEvents) {
        logger.trace("event trace trace, total countL[{}],  current rootId:[{}], nextRootId[{}]", id2Event.size(), Hex.toHexString(rootId), Hex.toHexString(nextRootId));
        for (LinkableEvent linkableEvent: id2Event.values()) {
            logger.trace("event trace trace, event tree element:[{}]", linkableEvent);
        }

        var orderCommitPathEventsContent = new StringBuilder("event trace trace, current commit event[");
        for (var commitId: orderCommitPathEvents)  {
            orderCommitPathEventsContent.append(commitId).append(",");
        }
        orderCommitPathEventsContent.append("]");
        logger.trace(orderCommitPathEventsContent.toString());

        var unCommitPathEventsContent = new StringBuilder("event trace trace, current un commit event[");
        for (var unCommitId: unCommitPathEvents)  {
            unCommitPathEventsContent.append(unCommitId).append(",");
        }
        unCommitPathEventsContent.append("]");
        logger.trace(unCommitPathEventsContent.toString());

    }
}
