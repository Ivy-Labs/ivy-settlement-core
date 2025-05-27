
package org.ivy.settlement.core.chain.ledger;

import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.consensus.sequence.model.EventData;
import org.ivy.settlement.core.chain.consensus.sequence.model.LatestLedger;
import org.ivy.settlement.core.chain.consensus.sequence.store.ConsensusChainStore;
import org.ivy.settlement.core.chain.ledger.model.BatchSign;
import org.ivy.settlement.core.chain.ledger.model.Block;
import org.ivy.settlement.core.chain.ledger.model.BlockSign;
import org.ivy.settlement.core.chain.ledger.model.SettlementBatch;
import org.ivy.settlement.core.chain.ledger.store.LedgerStore;

import java.util.List;

/**
 * description:
 * @author carrot
 */
public class StateLedger {

    final ConsensusChainStore consensusChainStore;

    final LedgerStore ledgerStore;

    //public final RepositoryRoot rootRepository;

    public StateLedger(IrisCoreSystemConfig irisCoreSystemConfig, ConsensusChainStore consensusChainStore, boolean test) {
        this.consensusChainStore = consensusChainStore;
        this.ledgerStore = new LedgerStore(false, irisCoreSystemConfig);
        //this.rootRepository = new RepositoryRoot(ledgerSource);
    }

    public LatestLedger getLatestLedger() {
        return consensusChainStore.getLatestLedger();
    }

    public EventData getEventData(long number) {
        return consensusChainStore.getEventData(number, true, true);
    }

    public EpochState getEpochState(long epoch) {
        return this.consensusChainStore.getEpochState(epoch);
    }

    public long getLatestConsensusNumber() {return consensusChainStore.getLatestLedger().getLatestNumber();}

    public long getLatestBeExecutedNum() {
        return ledgerStore.getLatestBeExecutedNum();
    }

    public long getLatestWillSignedBlobNum() {
        return ledgerStore.getLatestWillSignedBlobNum();
    }

    public long getLatestConfirmBlobNum() {
        return ledgerStore.getLatestConfirmBlobNum();
    }

    public Block getBlockByNumber(long number) {
        return this.ledgerStore.getBlockByNumber(number);
    }

    public BlockSign getBlockSignByNumber(long number) {
        return this.ledgerStore.getBlockSignByNumber(number);
    }

    public SettlementBatch getBlobByNumber(long number) {
        return this.ledgerStore.getBlobByNumber(number);
    }

    public BatchSign getBlobSignByNumber(long number) {
        return this.ledgerStore.getBlobSignByNumber(number);
    }

    public List<Pair<Block, BlockSign>> getCommitBlocksByNumber(long start, long end) {
        return this.ledgerStore.getCommitBlocksByNumber(start, end);
    }

    //============================================================================================
    public void persistBlock(Block block, BlockSign blockSign) {
        ledgerStore.doPersistBlock(block, blockSign);
    }

    public void doPersistWillSignedBlob(SettlementBatch settlementBatch) {
        ledgerStore.doPersistWillSignedBlob(settlementBatch);
    }

    public void doPersistConfirmBlob(SettlementBatch settlementBatch) {
        ledgerStore.doPersistConfirmBlob(settlementBatch);
    }
}
