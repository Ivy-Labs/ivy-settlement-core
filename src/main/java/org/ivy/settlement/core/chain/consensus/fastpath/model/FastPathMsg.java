package org.ivy.settlement.core.chain.consensus.fastpath.model;


import org.ivy.settlement.core.chain.network.protocols.base.Message;
import org.ivy.settlement.core.chain.network.protocols.base.MessageType;

/**
 * description:
 * @author carrot
 */
public class FastPathMsg extends Message {

    protected FastPathMsg(byte[] encode) {
        super(encode);
    }

    public byte getType() {
        return MessageType.FAST_PATH.getType();
    }

    public FastPathCommand getCommand() {
        return FastPathCommand.fromByte(getCode());
    }

    public void releaseReference() {
    }
}
