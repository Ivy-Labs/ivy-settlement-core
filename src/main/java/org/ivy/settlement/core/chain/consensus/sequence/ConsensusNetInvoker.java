package org.ivy.settlement.core.chain.consensus.sequence;

import io.libp2p.core.peer.PeerId;
import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecurePublicKey;
import org.ivy.settlement.core.chain.consensus.sequence.executor.ConsensusEventExecutor;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.consensus.sequence.model.ConsensusMsg;
import org.ivy.settlement.core.chain.network.NetInvoker;
import org.ivy.settlement.core.chain.network.protocols.base.Message;

import java.util.HashSet;
import java.util.List;

import static io.libp2p.core.crypto.keys.Secp256k1.unmarshalSecp256k1PublicKey;

/**
 * description:
 * @author carrot
 */
public class ConsensusNetInvoker {

    NetInvoker netInvoker;

    PeerId peerId;

    public ConsensusNetInvoker(NetInvoker netInvoker, PeerId peerId) {
        this.netInvoker = netInvoker;
        this.peerId = peerId;
    }

    public void directSend(PeerId to, ConsensusMsg message) {
        if (to.equals(this.peerId)) {
            message.setNodeId(this.peerId.bytes);
            ChainedBFT.putConsensusMsg(message);
        } else {
            this.netInvoker.directSend(List.of(to), message);
        }
    }

    // default timeout 3 sec
    public Message rpcSend(PeerId to, ConsensusMsg message) {
        return this.netInvoker.rpcSend(to, message);
    }

    public Message rpcSend(PeerId to, ConsensusMsg message, long timeout) {
        return this.netInvoker.rpcSend(to, message, timeout);
    }

    public void rpcResponse(PeerId to, ConsensusMsg responseMsg) {
        this.netInvoker.rpcResponse(to, responseMsg);
    }

    public void broadcast(ConsensusMsg message, boolean includeSelf) {
        if (includeSelf) {
            message.setNodeId(this.peerId.bytes);
            ChainedBFT.putConsensusMsg(message); // self
        }
        this.netInvoker.broadcast(message);
    }

    public void updateEligibleNodes(EpochState epochState, ConsensusEventExecutor consensusEventExecutor) {
        var validators = epochState.getValidatorVerifier();
        var validNodes = new HashSet<PeerId>();
        for (var entry : validators.getPk2ValidatorInfo().entrySet()) {
            var peerId = PeerId.fromPubKey(unmarshalSecp256k1PublicKey(SecurePublicKey.generate(entry.getKey().getData()).rawPublicKeyBytes));
            validNodes.add(peerId);
        }
        this.netInvoker.updateEligibleNodes(validNodes);
    }
}
