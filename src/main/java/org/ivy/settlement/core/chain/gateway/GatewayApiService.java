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
package org.ivy.settlement.core.chain.gateway;

import io.vertx.core.Vertx;
import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecureKey;
import org.ivy.settlement.infrastructure.crypto.HashUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.gateway.http.JsonRpcConfiguration;
import org.ivy.settlement.core.chain.gateway.http.JsonRpcHttpService;
import org.ivy.settlement.core.chain.gateway.http.health.HealthService;
import org.ivy.settlement.core.chain.gateway.http.jsonrpc.internal.methods.JsonRpcMethod;
import org.ivy.settlement.core.chain.gateway.method.IvyMainChainEventProcessor;
import org.ivy.settlement.core.chain.gateway.method.parameters.MainChainEventParameter;
import org.ivy.settlement.core.chain.ledger.StateLedger;
import org.ivy.settlement.core.chain.ledger.model.eth.EthTransaction;
import org.ivy.settlement.core.chain.ledger.model.event.GlobalNodeEvent;
import org.ivy.settlement.core.chain.ledger.model.event.cross.CrossMainChainValidatorEvent;
import org.ivy.settlement.core.chain.txpool.TxnManager;
import org.spongycastle.util.BigIntegers;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ivy.settlement.core.chain.ledger.model.event.GlobalEventCommand.CROSS_MAIN_CHAIN_EVENT;

public class GatewayApiService {


    IrisCoreSystemConfig irisCoreSystemConfig;

    StateLedger stateLedger;

    //BlockPublisher blockPublisher;

    TxnManager txnManager;

    JsonRpcHttpService jsonRpcHttpService;

    public GatewayApiService(IrisCoreSystemConfig irisCoreSystemConfig, StateLedger stateLedger, TxnManager txnManager) {
        this.irisCoreSystemConfig = irisCoreSystemConfig;
        this.stateLedger = stateLedger;
        this.txnManager = txnManager;
        this.jsonRpcHttpService = createHttpService(irisCoreSystemConfig);
    }


    private JsonRpcHttpService createHttpService(IrisCoreSystemConfig irisCoreSystemConfig) {
        JsonRpcConfiguration config = JsonRpcConfiguration.createDefault();


        var ipAndPort = irisCoreSystemConfig.gatewayRpcIpPort().split(":");
        var ip = ipAndPort[0];
        var port = Integer.parseInt(ipAndPort[1]);

        config.setHost(ip);
        config.setPort(port);

        Vertx vertx = Vertx.vertx();

        return new JsonRpcHttpService(
                vertx,
                null,
                config,
                null,
                buildRpcMethod(),
                HealthService.ALWAYS_HEALTHY,
                HealthService.ALWAYS_HEALTHY);
    }

    private Map<String, JsonRpcMethod> buildRpcMethod() {
        var res = new HashMap<String, JsonRpcMethod>();

        var ivyMainChainEventProcessor = new IvyMainChainEventProcessor(this);
        res.put(ivyMainChainEventProcessor.getName(), ivyMainChainEventProcessor);
        return res;
    }

    public void start() {
        this.jsonRpcHttpService.start().join();
        System.out.println("gateway:" + this.jsonRpcHttpService.url() + " start success!");
    }


    
    public String validateAndSubmitEth(String tx) {
        System.out.println(tx);
        return "validateAndSubmitEth";

    }


    
    public String getBlockByNumber(Long blockNumber) {
        System.out.println(blockNumber);
        return "getBlockByNumber";
    }

    
    public String getEthTransactionByHash(byte[] txHash) {
        return "peer.getPushManager().getTransactionByHash(txHash)";
    }

    
    public String getGlobalNodeEventByHash(byte[] txhash) {
        return "peer.getPushManager().getGlobalNodeEventByCache(txhash)";
    }

    
    public String getGlobalNodeEventReceiptByHash(byte[] txhash) {
        return "peer.getPushManager().getGlobalNodeEventReceipt(txhash)";
    }

    
    public String getGlobalNodeEventByHashByChain(byte[] txhash) {
        return "peer.getPushManager().getGlobalNodeEventByChain(txhash)";
    }

    
    public String getEpochState() {
        return "peer.getPushManager().getEpochState()";
    }

    
    public String processIvyMainChainEvent(MainChainEventParameter param) {
        System.out.println("gateway receive:" + param);

        var sk = SecureKey.getInstance("ETH256K1", 1);

        var pk = sk.getPubKey();
        var nonce = RLP.encodeString(Long.toString(System.currentTimeMillis()));
        var futureNum = 1;
        var event = new CrossMainChainValidatorEvent(param.getProcessType(), param.getSlotIndex(), param.getPublicKey(), param.getConsensusVotingPower());

        var hash = HashUtil.sha3Dynamic(pk, nonce, BigIntegers.asUnsignedByteArray(BigInteger.valueOf(futureNum)), BigIntegers.asUnsignedByteArray(BigInteger.valueOf(CROSS_MAIN_CHAIN_EVENT.getCode())), event.getEncoded());
        var sig = sk.sign(hash);
        GlobalNodeEvent gne = new GlobalNodeEvent(pk, nonce, futureNum, CROSS_MAIN_CHAIN_EVENT.getCode(), event.getEncoded(), sig);

        gne.verify(true);
        this.txnManager.eventCollector.importPayload(new EthTransaction[]{}, List.of(gne));
        return Hex.toHexString(hash);
    }

    
    public String getEthTransactionByHashByChain(byte[] txHash) {
        return Hex.toHexString(txHash);

    }

    
    public List<String> getEthTransactionReceiptsByHashesByChain(List<byte[]> txHashes) {
        return List.of("getEthTransactionReceiptsByHashesByChain test list");
    }

    
    public List<String> getEthTransactionsByHashes(List<byte[]> reqs) {
        return List.of("getEthTransactionsByHashes test list");

    }


    
    public Long getLatestConsensusNumber() {
        return 20L;
    }

    
    public Long getCurrentCommitRound() {
        return 10L;
    }

    
    public void validateAndSubmitGlobal(GlobalNodeEvent globalNodeEvent) {
        System.out.println(globalNodeEvent);
    }
}
