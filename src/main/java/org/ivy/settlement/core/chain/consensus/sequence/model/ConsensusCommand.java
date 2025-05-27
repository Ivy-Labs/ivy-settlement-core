package org.ivy.settlement.core.chain.consensus.sequence.model;

import java.util.HashMap;
import java.util.Map;

/**
 * description:
 * @author carrot
 */
public enum ConsensusCommand {
    PROPOSAL((byte)0),
    VOTE((byte)1),
    HOTSTUFF_CHAIN_SYNC((byte)2),
    LOCAL_TIMEOUT((byte)3),
    EVENT_RETRIEVAL_REQ((byte)4),
    EVENT_RETRIEVAL_RESP((byte)5),
    LATEST_LEDGER_REQ((byte)6),
    LATEST_LEDGER_RESP((byte)7),
    EPOCH_CHANGE((byte)8),
    EPOCH_RETRIEVAL((byte)9),
    CROSS_CHAIN_RETRIEVAL_REQ((byte)10),
    CROSS_CHAIN_RETRIEVAL_RESP((byte)11);

    private static final Map<Byte, ConsensusCommand> byteToCommandMap = new HashMap<>();

    static {
        for (ConsensusCommand consensusCommand : ConsensusCommand.values()) {
            byteToCommandMap.put(consensusCommand.code, consensusCommand);
        }
    }

    private byte code;

    ConsensusCommand(byte code) {
        this.code = code;
    }

    public static ConsensusCommand fromByte(byte code) {
        return byteToCommandMap.get(code);
    }

    public byte getCode() {
        return code;
    }
}
