package org.ivy.settlement.core.chain.ledger.model.event.ca;

import org.ivy.settlement.core.chain.ledger.model.event.CommandEvent;

/**
 * description:
 * @author carrot
 */
public abstract class VoteEvent extends CommandEvent {

    //赞同/取消
    int voteType;

    //register/cancel
    int processType;

    byte[] proposalId;


    public VoteEvent(byte[] rlpEncoded) {
        super(rlpEncoded);
    }


    public int getVoteType() {
        return voteType;
    }

    public int getProcessType() {
        return processType;
    }

    public byte[] getProposalId() {
        return proposalId;
    }
}
