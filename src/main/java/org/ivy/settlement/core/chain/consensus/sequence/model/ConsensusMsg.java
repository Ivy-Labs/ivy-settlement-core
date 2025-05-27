package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.network.protocols.base.Message;
import org.ivy.settlement.core.chain.network.protocols.base.MessageType;

/**
 * description:
 * @author carrot
 */
public class ConsensusMsg extends Message {

    protected long epoch;

    protected ConsensusMsg(byte[] encode) {
        super(encode);
    }

    public ConsensusMsg(byte type, byte code, byte remoteType, long rpcId, byte[] nodeId, byte[] encoded) {
        super(type, code, remoteType, rpcId, nodeId, encoded);
    }

    public long getEpoch() {
        return epoch;
    }

    public byte getType() {
        return MessageType.CONSENSUS.getType();
    }

    public  ConsensusCommand getCommand() {
        return ConsensusCommand.fromByte(getCode());
    }

    public ProcessResult<Void> verify(ValidatorVerifier verifier) {
        return ProcessResult.SUCCESSFUL;
    }
}
