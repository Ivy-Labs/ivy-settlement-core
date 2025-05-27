package org.ivy.settlement.core.chain.consensus.fastpath.model;

import java.util.HashMap;
import java.util.Map;

/**
 * description:
 * @author carrot
 */
public enum FastPathCommand {
    // used to initiate new sync
    LOCAL_BLOCK_SIGN((byte) 0),
    GET_BLOCK_SIGN_REQ((byte) 1),
    BLOCK_SIGN_RESP((byte) 2),
    BLOCK_SIGN_TIMEOUT((byte) 3),
    COMMIT_BLOCK_RESP((byte) 4),
    GET_COMMIT_BLOCK_REQ((byte) 5);


    private static final Map<Byte, FastPathCommand> byteToCommandMap = new HashMap<>();

    static {
        for (var consensusCommand : FastPathCommand.values()) {
            byteToCommandMap.put(consensusCommand.code, consensusCommand);
        }
    }

    private final byte code;

    FastPathCommand(byte code) {
        this.code = code;
    }

    public static FastPathCommand fromByte(byte code) {
        return byteToCommandMap.get(code);
    }

    public byte getCode() {
        return code;
    }
}
