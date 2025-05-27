package org.ivy.settlement.core.chain.consensus.slowpath.model;

import java.util.HashMap;
import java.util.Map;

/**
 * description:
 * @author carrot
 */
public enum SlowPathCommand {
    // used to initiate new sync
    LOCAL_BLOB_SIGN((byte) 0),
    GET_BLOB_SIGN_REQ((byte) 1),
    BLOB_SIGN_RESP((byte) 2),
    BLOB_SIGN_TIMEOUT((byte) 3),
    COMMIT_BLOB_RESP((byte) 4),
    GET_COMMIT_BLOB_REQ((byte) 5);


    private static final Map<Byte, SlowPathCommand> byteToCommandMap = new HashMap<>();

    static {
        for (var consensusCommand : SlowPathCommand.values()) {
            byteToCommandMap.put(consensusCommand.code, consensusCommand);
        }
    }

    private final byte code;

    SlowPathCommand(byte code) {
        this.code = code;
    }

    public static SlowPathCommand fromByte(byte code) {
        return byteToCommandMap.get(code);
    }

    public byte getCode() {
        return code;
    }
}
