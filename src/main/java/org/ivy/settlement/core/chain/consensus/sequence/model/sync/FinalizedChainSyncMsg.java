package org.ivy.settlement.core.chain.consensus.sequence.model.sync;

import org.ivy.settlement.core.chain.network.protocols.base.Message;
import org.ivy.settlement.core.chain.network.protocols.base.MessageType;

/**
 * description:
 * @author carrot
 */
public class FinalizedChainSyncMsg extends Message {

    protected FinalizedChainSyncMsg(byte[] encode) {
        super(encode);
    }

    public byte getType() {
        return MessageType.LAYER_2_STATE_SYNC.getType();
    }

    public FinalizedChainSyncCommand getCommand() {
        return FinalizedChainSyncCommand.fromByte(getCode());
    }
}
