package org.ivy.settlement.core.chain.ledger.model.event;


import java.util.HashMap;
import java.util.Map;

/**
 * description:
 * @author carrot
 */
public enum GlobalEventCommand {
    PLACEHOLDER_EMPTY((byte) 0),
    VOTE_COMMITTEE_CANDIDATE((byte) 1),
    VOTE_NODE_CANDIDATE((byte) 2),
    VOTE_FILTER_CANDIDATE((byte) 3),
    VOTE_NODE_BLACKLIST_CANDIDATE((byte) 4),
    PROCESS_OPERATIONS_STAFF((byte) 5),
    INVOKE_FILTER((byte) 6),
    CROSS_MAIN_CHAIN_EVENT((byte) 7);


    private static final Map<Byte, GlobalEventCommand> byteToCommandMap = new HashMap<>();

    static {
        for (GlobalEventCommand globalEventCommand : GlobalEventCommand.values()) {
            byteToCommandMap.put(globalEventCommand.code, globalEventCommand);
        }
    }

    private final byte code;

    GlobalEventCommand(byte code) {
        this.code = code;
    }

    public static GlobalEventCommand fromByte(byte code) {
        return byteToCommandMap.get(code);
    }

    public byte getCode() {
        return code;
    }
}
