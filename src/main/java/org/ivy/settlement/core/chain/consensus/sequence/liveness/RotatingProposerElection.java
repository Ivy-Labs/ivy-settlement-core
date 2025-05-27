package org.ivy.settlement.core.chain.consensus.sequence.liveness;

import io.libp2p.core.peer.PeerId;
import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecurePublicKey;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.libp2p.core.crypto.keys.Secp256k1.unmarshalSecp256k1PublicKey;

/**
 * description:
 * @author carrot
 */
public class RotatingProposerElection extends ProposerElection {

    List<Pair<byte[] /* public key */, PeerId /* nodeId */>> proposerInfos;

    private Map<ByteArrayWrapper, PeerId> pk2NodeMap;

    int contiguousRounds;

    public RotatingProposerElection(List<byte[]> proposers, int contiguousRounds) {
        proposerInfos = new ArrayList<>(proposers.size());
        pk2NodeMap = new HashMap<>(proposers.size());
        for (byte[] publicKey: proposers) {
            byte[] newPk = ByteUtil.copyFrom(publicKey);
            PeerId peerId = PeerId.fromPubKey(unmarshalSecp256k1PublicKey(SecurePublicKey.generate(newPk).rawPublicKeyBytes));
            proposerInfos.add(Pair.of(newPk, peerId));
            pk2NodeMap.put(new ByteArrayWrapper(newPk), peerId);
        }
        this.contiguousRounds = contiguousRounds;
    }

    @Override
    public Pair<byte[] /* public key */, PeerId /* nodeId */> getValidProposer(long round) {
        var proposalIndex = (int) ((round / contiguousRounds) % proposerInfos.size());
        return this.proposerInfos.get(proposalIndex);
    }

    @Override
    public PeerId getNodeId(ByteArrayWrapper pk) {
        return pk2NodeMap.get(pk);
    }
}
