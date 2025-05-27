package org.ivy.settlement.core.chain.network.protocols.base;

import java.util.HashMap;
import java.util.Map;

/**
 * description:
 * @author carrot
 */
public enum MessageType {
    CONSENSUS((byte)1),
    LAYER_2_STATE_SYNC((byte)2),
    STATE_SHARDING((byte)3),
    FAST_PATH((byte)4),
    SLOW_PATH((byte)5);


    private byte type;

    private static final Map<Byte, MessageType> byteToTypeMap = new HashMap<>();

    static {
        for (MessageType messageType : MessageType.values()) {
            byteToTypeMap.put(messageType.type, messageType);
        }
    }

    private MessageType(byte type) {
        this.type = type;
    }


    public static MessageType fromByte(byte type) {
        return byteToTypeMap.get(type);
    }

    public byte getType() {
        return type;
    }
}
