package org.ivy.settlement.core.chain.consensus.sequence.executor;

import io.libp2p.core.peer.PeerId;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.consensus.sequence.safety.settlement.SettlementChainsVerifier;
import org.ivy.settlement.core.chain.consensus.sequence.store.ConsensusChainStore;
import org.ivy.settlement.core.chain.network.NetInvoker;
import org.ivy.settlement.core.chain.consensus.sequence.FinalizedChainSynchronizer;
import org.ivy.settlement.core.chain.txpool.TxnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * description:
 * @author carrot
 */
public class ConsensusEventExecutor {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    ConsensusChainStore consensusChainStore;

    GlobalExecutor globalExecutor;

    FinalizedChainSynchronizer finalizedChainSynchronizer;

    public ConsensusEventExecutor(ConsensusChainStore consensusChainStore, SettlementChainsVerifier settlementChainsVerifier, NetInvoker netInvoker, GlobalExecutor globalExecutor, TxnManager txnManager) {
        this.consensusChainStore = consensusChainStore;
        this.globalExecutor = globalExecutor;
        this.finalizedChainSynchronizer = new FinalizedChainSynchronizer(netInvoker, settlementChainsVerifier, consensusChainStore, globalExecutor, txnManager);
    }

    public ExecutedEventOutput execute(Event event, ExecutedEvent parent) {
        // execute the node event(register/ unregister),and the update the ExecutedEventOutput.validators
        // when event(register/ unregister), we should set ExecutedEventOutput.epochState, this will trigger epoch change
        var epochStateHolders = globalExecutor.doExecuteGlobalNodeEvents(event.getPayload().getCrossChainOffsets(), parent.getEvent().getPayload().getCrossChainOffsets());
        return new ExecutedEventOutput(new HashMap<>(), event.getEventNumber(), parent.getStateRoot(), epochStateHolders.first, epochStateHolders.second);
    }

    public void commit(List<ExecutedEvent> eventsToCommit, SettlementChainOffsets latestOffsets, LedgerInfoWithSignatures finalityProof) {

        var eventDatas = new ArrayList<EventData>(eventsToCommit.size());
        var eventInfoWithSignatures = new ArrayList<EventInfoWithSignatures>(eventsToCommit.size());
        for (var executedEvent : eventsToCommit) {
            if (executedEvent.getEvent().getEventData().isEmptyPayload() && executedEvent.getEvent().getEventData().getGlobalEvent().isEmpty())
                continue;

            var eventData = executedEvent.getEvent().getEventData();
            // wait state Consistent!
//            if (!eventData.getGlobalEvent().isEmpty()) {
//
//                while (!isStateConsistent()) {
//                    try {
//                        Thread.sleep(500);
//                    } catch (Exception e) {
//                    }
//                }
//
//            }
            eventDatas.add(executedEvent.getEvent().getEventData());
            eventInfoWithSignatures.add(executedEvent.getEventInfoWithSignatures());
        }

        var lastEventToCommit = eventsToCommit.get(eventsToCommit.size() - 1);
        var lastOutput = lastEventToCommit.getExecutedEventOutput();
        consensusChainStore.commit(eventDatas, eventInfoWithSignatures, lastOutput, latestOffsets, finalityProof);
    }

    public ProcessResult<Void> syncTo(LedgerInfoWithSignatures targetLatestLedgerInfo, PeerId syncPeer) {
        return finalizedChainSynchronizer.syncTo(targetLatestLedgerInfo, syncPeer);
    }

    public LatestLedger getLatestLedgerInfo() {
        return consensusChainStore.getLatestLedger();
    }

    public boolean isSyncing() {
        return finalizedChainSynchronizer.isSyncing();
    }

    public boolean isStateConsistent() {
        return true;
//        return
//                this.consensusChainStore.getLatestLedger().getLatestNumber()
//                == this.globalExecutor.getLatestExecuteNum();
    }
}
