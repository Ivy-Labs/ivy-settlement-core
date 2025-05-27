package org.ivy.settlement.core.chain.consensus.sequence.model.sync;

import java.util.HashMap;
import java.util.Map;

/**
 * description:
 * @author carrot
 */
public enum FinalizedChainSyncCommand {
    // used to initiate new sync
    LAYER2_STATE_SYNC_REQUEST((byte)0),

    LAYER2_STATE_SYNC_RESPONSE((byte)1),
    // used to notify about new txn commit
    COMMIT((byte)2),
    GET_STATE((byte)3),
    // used to generate epoch proof
    GET_EPOCH_PROOF((byte)4),
    // Receive a notification via a given channel when coordinator is initialized.
    WAIT_INITIALIZE((byte)5);

    private static final Map<Byte, FinalizedChainSyncCommand> byteToCommandMap = new HashMap<>();

    static {
        for (FinalizedChainSyncCommand consensusCommand : FinalizedChainSyncCommand.values()) {
            byteToCommandMap.put(consensusCommand.code, consensusCommand);
        }
    }

    private byte code;

    FinalizedChainSyncCommand(byte code) {
        this.code = code;
    }

    public static FinalizedChainSyncCommand fromByte(byte code) {
        return byteToCommandMap.get(code);
    }

    public byte getCode() {
        return code;
    }
}
