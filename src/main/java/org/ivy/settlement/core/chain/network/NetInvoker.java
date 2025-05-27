package org.ivy.settlement.core.chain.network;

import io.libp2p.core.peer.PeerId;
import org.ivy.settlement.core.chain.network.protocols.base.Message;

import java.util.List;
import java.util.Set;

/**
 * description:
 * @author carrot
 */
public interface NetInvoker {

    void directSend(List<PeerId> receiveNodes, Message message);

    void rpcResponse(PeerId to, Message message);

    // default timeout 3 sec
    default Message rpcSend(PeerId to, Message message) {
        return this.rpcSend(to, message, 3000);
    }

    Message rpcSend(PeerId to, Message message, long timeout);

    void broadcast(Message message);

    void updateEligibleNodes(Set<PeerId> validatorNodes);

    void start();

    void stop();
}
