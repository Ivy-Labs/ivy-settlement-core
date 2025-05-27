package org.ivy.settlement.core.chain.consensus.sequence;

import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.consensus.fastpath.SettlementFastPathCommitter;
import org.ivy.settlement.core.chain.consensus.fastpath.impl.SettlementBlobGeneratorImpl;
import org.ivy.settlement.core.chain.consensus.sequence.executor.ConsensusEventExecutor;
import org.ivy.settlement.core.chain.consensus.sequence.safety.settlement.SettlementChainsVerifier;
import org.ivy.settlement.core.chain.consensus.sequence.store.ConsensusChainStore;
import org.ivy.settlement.core.chain.consensus.sequence.store.PersistentLivenessStore;
import org.ivy.settlement.core.chain.consensus.slowpath.SettlementSlowPathCommitter;
import org.ivy.settlement.core.chain.consensus.sequence.executor.GlobalExecutor;
import org.ivy.settlement.core.chain.consensus.slowpath.impl.SettlementBatchFinalityMock;
import org.ivy.settlement.core.chain.ledger.StateLedger;
import org.ivy.settlement.core.chain.network.NetInvoker;
import org.ivy.settlement.core.chain.sync.SettlementChainsSyncer;
import org.ivy.settlement.core.chain.txpool.TxnManager;

/**
 * description:
 * @author carrot
 */
public class ConsensusProvider {

    IrisCoreSystemConfig irisCoreSystemConfig;

    NetInvoker netInvoker;

    StateLedger stateLedger;

    ConsensusChainStore consensusChainStore;

    SettlementChainsSyncer settlementChainsSyncer;

    TxnManager txnManager;

    public ConsensusProvider(IrisCoreSystemConfig irisCoreSystemConfig, NetInvoker netInvoker, StateLedger stateLedger, ConsensusChainStore consensusChainStore, SettlementChainsSyncer settlementChainsSyncer, TxnManager txnManager) {
        this.irisCoreSystemConfig = irisCoreSystemConfig;
        this.netInvoker = netInvoker;
        this.stateLedger = stateLedger;
        this.consensusChainStore = consensusChainStore;
        this.settlementChainsSyncer = settlementChainsSyncer;
        this.txnManager = txnManager;
    }

    public void start() {
        var crossChainVerifier = new SettlementChainsVerifier(this.settlementChainsSyncer, this.consensusChainStore.getLatestLedger());

        var globalExecutor = new GlobalExecutor(this.stateLedger, this.consensusChainStore, this.settlementChainsSyncer);
        globalExecutor.start();

        var consensusEventExecutor = new ConsensusEventExecutor(this.consensusChainStore, crossChainVerifier, this.netInvoker, globalExecutor, this.txnManager);

        EpochManager epochManager = new EpochManager(
                new ConsensusNetInvoker(this.netInvoker, this.irisCoreSystemConfig.getPeerId()),
                consensusEventExecutor,
                this.txnManager,
                new PersistentLivenessStore(this.consensusChainStore),
                crossChainVerifier,
                this.irisCoreSystemConfig);

        new ChainedBFT(epochManager).start();

        //blob generator
        var settlementBlobGenerator = new SettlementBlobGeneratorImpl(this.stateLedger);
        settlementBlobGenerator.start();

        var settlementFastPathCommitter = new SettlementFastPathCommitter(this.irisCoreSystemConfig, this.stateLedger, settlementBlobGenerator, this.settlementChainsSyncer, this.netInvoker, false);
        settlementFastPathCommitter.start();

        var settlementSlowPathCommitter  = new SettlementSlowPathCommitter(this.irisCoreSystemConfig, this.stateLedger, settlementBlobGenerator, this.settlementChainsSyncer, new SettlementBatchFinalityMock(), this.netInvoker, false);
        settlementSlowPathCommitter.start();
    }
}
