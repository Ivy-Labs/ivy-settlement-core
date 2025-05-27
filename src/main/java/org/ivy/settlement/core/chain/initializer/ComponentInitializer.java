/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.ivy.settlement.core.chain.initializer;

import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.consensus.sequence.ConsensusProvider;
import org.ivy.settlement.core.chain.consensus.sequence.store.ConsensusChainStore;
import org.ivy.settlement.core.chain.gateway.GatewayApiService;
import org.ivy.settlement.core.chain.ledger.StateLedger;
import org.ivy.settlement.core.chain.network.impl.NetInvokerImpl;
import org.ivy.settlement.core.chain.sync.impl.SettlementChainsSyncerMock;
import org.ivy.settlement.core.chain.txpool.TxnManager;

/**
 * description:
 * @author carrot
 */
public class ComponentInitializer {

    public static void init(IrisCoreSystemConfig irisCoreSystemConfig) {
        // network
        var netInvoker = new NetInvokerImpl(irisCoreSystemConfig);
        netInvoker.start();

        var settlementChainsSyncer = new SettlementChainsSyncerMock();

        //ledger
        var consensusChainStore = new ConsensusChainStore(irisCoreSystemConfig, false);
        var stateLedger = new StateLedger(irisCoreSystemConfig, consensusChainStore, false);

        //txnManager
        var txnManager = new TxnManager(irisCoreSystemConfig.getMaxPackSize(), irisCoreSystemConfig.getPoolLimit(), irisCoreSystemConfig.comingQueueSize(), irisCoreSystemConfig.dsCheck(), consensusChainStore);

        // layer2 consensus
        var consensusProvider = new ConsensusProvider(irisCoreSystemConfig, netInvoker, stateLedger, consensusChainStore, settlementChainsSyncer, txnManager);
        consensusProvider.start();

        //gateway
        var gatewayService = new GatewayApiService(irisCoreSystemConfig, stateLedger, txnManager);
        gatewayService.start();
    }
}
