package org.ivy.settlement.core.chain.network.protocols.base;

import io.libp2p.core.peer.PeerId;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * description:
 * @author carrot
 */
public class Message extends RLPModel {

    protected static final Logger logger = LoggerFactory.getLogger("network");

    private static AtomicLong messageId = new AtomicLong(0);

    /**
     * @MessageType, 消息类型，共识/p2p/分片
     */
    protected byte type;

    /**
     * 命令码
     */
    protected byte code;

    //protected byte[] encoded;

    // 0:direct request, 1:rpc_request 2:rpc_response;
    protected byte remoteType;

    //for invoke rpc
    protected long rpcId = -1;

    // for invoke channel
    protected PeerId peerId;

    public Message() {
        super(null);
        this.type = getType();
        this.code = getCode();
        this.rpcId = -1;
    }

    public Message(byte[] encoded) {
        super(encoded);
        this.type = getType();
        this.code = getCode();
        this.rpcId = -1;
        //this.encoded = encoded;
    }

    @Override
    protected byte[] rlpEncoded() {
        return rlpEncoded;
    }

    @Override
    protected void rlpDecoded() {
        // do nothing
    }

    public Message(byte type, byte code, byte remoteType, long rpcId,  byte[] nodeId, byte[] encoded) {
        super(encoded);
        this.type = type;
        this.code = code;
        this.remoteType = remoteType;
        this.peerId = new PeerId(nodeId);
        this.rpcId = rpcId;
        //this.encoded = encoded;
    }

    public byte getType() {
        return type;
    }

    public MessageType getMessageType() {
        return MessageType.fromByte(this.getType());
    }

    public byte getCode() {
        return code;
    }

    public byte[] getNodeId() {
        return peerId != null? peerId.bytes : null;
    }

    public PeerId getPeerId() {
        return this.peerId;
    }

    public void setNodeId(byte[] nodeId) {
        this.peerId = new PeerId(ByteUtil.copyFrom(nodeId));
    }

    public long getRpcId() {
        return rpcId;
    }

    public void setRpcId() {
        if (this.rpcId == -1) {
            this.rpcId = messageId.getAndIncrement();
        }
    }

    public void setRpcId(long rpcId) {
        if (this.rpcId == -1) {
            this.rpcId = rpcId;
        }
    }

    public boolean isRpcMsg() {
        return this.remoteType == RemotingMessageType.RPC_REQUEST_MESSAGE.getType();
    }

    public byte getRemoteType() {
        return remoteType;
    }

    public void setRemoteType(byte remoteType) {
        this.remoteType = remoteType;
    }

    public RemotingMessageType getRemotingMessageType() {
        return RemotingMessageType.fromByte(this.getRemoteType());
    }

    @Override
    public String toString() {
        return "Message{" +
                "type=" + type +
                ", code=" + code +
                ", remoteType=" + remoteType +
                ", rpcId=" + rpcId +
                '}';
    }
}
