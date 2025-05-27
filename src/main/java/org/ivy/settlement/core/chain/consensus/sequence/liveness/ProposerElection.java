package org.ivy.settlement.core.chain.consensus.sequence.liveness;

import io.libp2p.core.peer.PeerId;
import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.core.chain.consensus.sequence.model.Event;

import java.util.Arrays;

/**
 * description:
 * @author carrot
 */
public abstract class ProposerElection {

    public final static int MultipleOrderedProposers = 1;

    public final static int RotatingProposer = 2;

    public boolean isValidProposer(byte[] author, long round) {
        return Arrays.equals(author, getValidProposer(round).getLeft());
    }

    public abstract Pair<byte[] /* public key */, PeerId /* nodeId */> getValidProposer(long round);

    public boolean isValidProposer(Event event) {
        return event.getAuthor().isPresent() ?
                isValidProposer(event.getAuthor().get(), event.getRound()): false;
    }

    public abstract PeerId getNodeId(ByteArrayWrapper pk);
}
