package org.ivy.settlement.core.chain.ledger.model.event;

import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.ivy.settlement.core.chain.ledger.model.event.ca.*;
import org.ivy.settlement.core.chain.ledger.model.event.cross.CrossMainChainValidatorEvent;
import org.ivy.settlement.core.chain.ledger.model.event.ca.*;

/**
 * description:
 * @author carrot
 */
public abstract class CommandEvent extends RLPModel {

    static CommandEvent build(byte code, byte[] data) {
        CommandEvent result;
        GlobalEventCommand globalEventCommand = GlobalEventCommand.fromByte(code);
        switch (globalEventCommand) {
            case VOTE_COMMITTEE_CANDIDATE:
                result = new VoteCommitteeCandidateEvent(data);
                break;
            case VOTE_NODE_CANDIDATE:
                result = new VoteNodeCandidateEvent(data);
                break;
            case VOTE_FILTER_CANDIDATE:
                result = new VoteFilterCandidateEvent(data);
                break;
            case VOTE_NODE_BLACKLIST_CANDIDATE:
                result = new VoteNodeBlackListCandidateEvent(data);
                break;
            case PROCESS_OPERATIONS_STAFF:
                result = new ProcessOperationsStaffCandidateEvent(data);
                break;
            case INVOKE_FILTER:
                result = new InvokeFilterEvent(data);
                break;
            case CROSS_MAIN_CHAIN_EVENT:
                result = new CrossMainChainValidatorEvent(data);
                break;
            default:
                throw new RuntimeException("un except CommandEvent type!");

        }
        return result;
    }

    public CommandEvent(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    public abstract GlobalEventCommand getEventCommand();
}
