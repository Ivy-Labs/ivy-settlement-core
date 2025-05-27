package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochStateHolder;

/**
 * description:
 * @author carrot
 */
public class VoteProposal {

    private Event event;

    private EpochStateHolder newCurrentEpochState;

    private EpochStateHolder newNextEpochState;

    private byte[] stateRoot;

    private long number;

    private VoteProposal() {}

    public static VoteProposal build(Event event, long number,  byte[] stateRoot, EpochStateHolder newCurrentEpochState, EpochStateHolder newNextEpochState) {
        VoteProposal voteProposal = new VoteProposal();
        voteProposal.event = event;
        voteProposal.number = number;
        voteProposal.stateRoot = stateRoot;
        voteProposal.newCurrentEpochState = newCurrentEpochState;
        voteProposal.newNextEpochState = newNextEpochState;
        return voteProposal;
    }

    public Event getEvent() {
        return event;
    }

    public byte[] getStateRoot() {
        return stateRoot;
    }

    public long getNumber() {
        return number;
    }

    public EpochStateHolder getNewCurrentEpochState() {
        return newCurrentEpochState;
    }

    public EpochStateHolder getNewNextEpochState() {
        return newNextEpochState;
    }
}
