package org.ivy.settlement.core.chain.network.impl;

import io.libp2p.core.peer.PeerId;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.network.NetInvoker;
import org.ivy.settlement.core.chain.network.protocols.base.Message;

import java.util.List;
import java.util.Set;

/**
 * description:
 * @author carrot
 */
public class NetInvokerImpl implements NetInvoker {

    public P2pNetworkManger p2pNetworkManger;

    public NetInvokerImpl(IrisCoreSystemConfig irisCoreSystemConfig) {
        this.p2pNetworkManger = new P2pNetworkManger(irisCoreSystemConfig);
    }

    @Override
    public void directSend(List<PeerId> receiveNodes, Message message) {
        this.p2pNetworkManger.directSend(receiveNodes, message);
    }

    @Override
    public void rpcResponse(PeerId to, Message message) {
        this.p2pNetworkManger.rpcResponse(to, message);
    }

    @Override
    public Message rpcSend(PeerId to, Message message, long timeout) {
        return this.p2pNetworkManger.rpcRequest(to, message, timeout);
    }

    @Override
    public void broadcast(Message message) {
        this.p2pNetworkManger.broadcast(message);
    }

    @Override
    public void updateEligibleNodes(Set<PeerId> validatorNodes) {
        this.p2pNetworkManger.updateEligibleNodes(validatorNodes);
    }

    @Override
    public void start() {
        this.p2pNetworkManger.start();
    }

    @Override
    public void stop() {
        this.p2pNetworkManger.stop();
    }
}
