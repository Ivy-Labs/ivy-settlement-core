package org.ivy.settlement.core.chain.consensus.sequence.liveness;

import org.ivy.settlement.infrastructure.anyhow.Assert;
import org.ivy.settlement.core.chain.consensus.sequence.model.ConsensusPayload;
import org.ivy.settlement.core.chain.consensus.sequence.model.Event;
import org.ivy.settlement.core.chain.consensus.sequence.model.EventData;
import org.ivy.settlement.core.chain.consensus.sequence.model.QuorumCert;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.consensus.sequence.safety.settlement.SettlementChainsVerifier;
import org.ivy.settlement.core.chain.consensus.sequence.store.EventTreeStore;
import org.ivy.settlement.core.chain.ledger.model.eth.EthTransaction;
import org.ivy.settlement.core.chain.ledger.model.event.GlobalEvent;
import org.ivy.settlement.core.chain.txpool.TxnManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * description:
 * @author carrot
 */
public class ProposalGenerator {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    private static final int MAX_EMPTY_PULL_COUNT = 50;

    // public key
    private byte[] author;

    private EventTreeStore eventTreeStore;

    private TxnManager txnManager;

    private SettlementChainsVerifier settlementChainsVerifier;


    private volatile Long lastRoundGenerated = 0L;


    public ProposalGenerator(byte[] author, EventTreeStore eventTreeStore, TxnManager txnManager,
                             SettlementChainsVerifier settlementChainsVerifier) {
        this.author = author;
        this.eventTreeStore = eventTreeStore;
        this.txnManager = txnManager;
        this.settlementChainsVerifier = settlementChainsVerifier;
    }

    public byte[] getAuthor() {
        return author;
    }

    public Event generateEmptyEvent(long round) {
        var hqc = ensureHighestQuorumCert(round);
        var latestOffsets = this.eventTreeStore.getEvent(hqc.getCertifiedEvent().getId()).getEvent().getPayload().getCrossChainOffsets();
        return Event.buildEmptyEvent(round, hqc, latestOffsets);
    }

    public EventData generateReConfigEmptySuffix(long round, QuorumCert hqc, SettlementChainOffsets parentOffsets) {
        return EventData.buildProposal(new GlobalEvent(), parentOffsets, new ConsensusPayload(parentOffsets, new EthTransaction[0]), this.author, round, hqc.getCertifiedEvent().getTimestamp(), hqc);
    }

    public EventData generateProposal(long round) {
        if (lastRoundGenerated < round) {
            this.lastRoundGenerated = round;
        } else {
            logger.warn("Already proposed in the round {}", round);
            throw new RuntimeException("Already proposed in the round " + round);
        }

        var hqc = ensureHighestQuorumCert(round);
        var parentCrossChainOffsets = this.eventTreeStore.getEvent(hqc.getCertifiedEvent().getId()).getEvent().getPayload().getCrossChainOffsets();

        if (hqc.getCertifiedEvent().hasReconfiguration()) {
            logger.info("generateProposal empty event for round[{}] hasReconfiguration", round);
            return generateReConfigEmptySuffix(round, hqc, parentCrossChainOffsets);
        }
        var parentTimestamp = hqc.getCertifiedEvent().getTimestamp();
        var createTimestamp = System.currentTimeMillis();
        createTimestamp = createTimestamp > parentTimestamp ? createTimestamp : parentTimestamp + 1;

        var crossChainOffsets = this.settlementChainsVerifier.generateCrossChainOffsets(parentCrossChainOffsets);
        //logger.info("round[{}] generate proposal current main offset:{}", round, crossChainOffsets.getMainChain());

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        return EventData.buildProposal(new GlobalEvent(), parentCrossChainOffsets, new ConsensusPayload(crossChainOffsets, new EthTransaction[0]), author, round, createTimestamp, hqc);
    }

    private QuorumCert ensureHighestQuorumCert(long round) {
        var hqc = this.eventTreeStore.getHighestQuorumCert();
        Assert.ensure(hqc.getCertifiedEvent().getRound() < round,
                "Given round {} is lower than hqc round {}", round, hqc.getCertifiedEvent().getRound());
        Assert.ensure(!hqc.isEpochChange(),
                "The epoch has already ended,a proposal is not allowed to generated");
        return hqc;
    }
}
