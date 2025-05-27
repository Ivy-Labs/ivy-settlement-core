package org.ivy.settlement.core.chain.consensus.slowpath.model;


import org.ivy.settlement.core.chain.network.protocols.base.Message;
import org.ivy.settlement.core.chain.network.protocols.base.MessageType;

/**
 * description:
 * @author carrot
 */
public class SlowPathMsg extends Message {

    protected SlowPathMsg(byte[] encode) {
        super(encode);
    }

    public byte getType() {
        return MessageType.SLOW_PATH.getType();
    }

    public SlowPathCommand getCommand() {
        return SlowPathCommand.fromByte(getCode());
    }

    public void releaseReference() {
    }
}
