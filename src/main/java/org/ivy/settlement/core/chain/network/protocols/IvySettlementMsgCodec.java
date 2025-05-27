package org.ivy.settlement.core.chain.network.protocols;

import com.google.protobuf.ByteString;
import com.google.protobuf.BytesValue;
import io.libp2p.core.peer.PeerId;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.core.chain.network.protocols.base.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.xerial.snappy.Snappy;


/**
 * description:
 * @author carrot
 */
public class IvySettlementMsgCodec {

    private static final Logger logger = LoggerFactory.getLogger("network");

    static final int MAX_COMPRESS_SIZE = 1024 * 1024 * 64; // 64m

    //static final int HEAD_LENGTH = 32 + 64;
    static final int HEAD_LENGTH = 4 + 1 + 1 + 1 + 8 + 1 + 8;

    public static ByteString encode(Message msg) {
        var compress = msg.getEncoded().length > MAX_COMPRESS_SIZE;
        var content = msg.getEncoded();
//        if (compress) {
//            try {
//                content = Snappy.compress(msg.getEncoded());
//            } catch (Exception e) {
//                throw new RuntimeException("IvyCrossMsgCodec compress error:" + e.getMessage());
//            }
//
//        } else {
//            content = msg.getEncoded();
//        }

        var contentSize = content.length;


        var totalLength = 0;
        totalLength += HEAD_LENGTH;
        totalLength += contentSize;

        var msgBuffer = PooledByteBufAllocator.DEFAULT.heapBuffer(totalLength, totalLength);

        encodeHeader(msg, compress, contentSize, msgBuffer);
        msgBuffer.writeBytes(content);
        var bs = ByteString.copyFrom(msgBuffer.nioBuffer());
        msgBuffer.release();
        return bs;
    }

    private static void encodeHeader(Message msg, boolean compress, int contentSize, ByteBuf input) {
        var headBuffer = new byte[HEAD_LENGTH];
        headBuffer[0] = (byte) (contentSize >> 24);
        headBuffer[1] = (byte) (contentSize >> 16);
        headBuffer[2] = (byte) (contentSize >> 8);
        headBuffer[3] = (byte) (contentSize);

        headBuffer[4] = msg.getType();
        headBuffer[5] = msg.getCode();
        headBuffer[6] = msg.getRemoteType();

        var msgId = msg.getRpcId();
        headBuffer[7] = (byte) (msgId >> 56);
        headBuffer[8] = (byte) (msgId >> 48);
        headBuffer[9] = (byte) (msgId >> 40);
        headBuffer[10] = (byte) (msgId >> 32);
        headBuffer[11] = (byte) (msgId >> 24);
        headBuffer[12] = (byte) (msgId >> 16);
        headBuffer[13] = (byte) (msgId >> 8);
        headBuffer[14] = (byte) (msgId);
        headBuffer[15] = (byte) (compress ? 1 : 0);

        var frameCreateTime = System.currentTimeMillis();
        headBuffer[16] = (byte) (frameCreateTime >> 56);
        headBuffer[17] = (byte) (frameCreateTime >> 48);
        headBuffer[18] = (byte) (frameCreateTime >> 40);
        headBuffer[19] = (byte) (frameCreateTime >> 32);
        headBuffer[20] = (byte) (frameCreateTime >> 24);
        headBuffer[21] = (byte) (frameCreateTime >> 16);
        headBuffer[22] = (byte) (frameCreateTime >> 8);
        headBuffer[23] = (byte) (frameCreateTime);
        input.writeBytes(headBuffer);
    }

    public static Message decode(PeerId peerId, BytesValue bv) {
        return decode(peerId, bv.getValue());
    }

    public static Message decode(PeerId peerId, ByteString msgBuffer) {
        int contentSize = msgBuffer.byteAt(0) & 0xFF;
        contentSize = (contentSize << 8) + (msgBuffer.byteAt(1) & 0xFF);
        contentSize = (contentSize << 8) + (msgBuffer.byteAt(2) & 0xFF);
        contentSize = (contentSize << 8) + (msgBuffer.byteAt(3) & 0xFF);

        byte type = msgBuffer.byteAt(4);
        byte code = msgBuffer.byteAt(5);
        byte remoteType = msgBuffer.byteAt(6);

        long msgId = msgBuffer.byteAt(7) & 0xFF;
        msgId = (msgId << 8) + (msgBuffer.byteAt(8) & 0xFF);
        msgId = (msgId << 8) + (msgBuffer.byteAt(9) & 0xFF);
        msgId = (msgId << 8) + (msgBuffer.byteAt(10) & 0xFF);
        msgId = (msgId << 8) + (msgBuffer.byteAt(11) & 0xFF);
        msgId = (msgId << 8) + (msgBuffer.byteAt(12) & 0xFF);
        msgId = (msgId << 8) + (msgBuffer.byteAt(13) & 0xFF);
        msgId = (msgId << 8) + (msgBuffer.byteAt(14) & 0xFF);

        boolean compress = (msgBuffer.byteAt(15) == 1);

        long frameCreateTime = msgBuffer.byteAt(16) & 0xFF;
        frameCreateTime = (frameCreateTime << 8) + (msgBuffer.byteAt(17) & 0xFF);
        frameCreateTime = (frameCreateTime << 8) + (msgBuffer.byteAt(18) & 0xFF);
        frameCreateTime = (frameCreateTime << 8) + (msgBuffer.byteAt(19) & 0xFF);
        frameCreateTime = (frameCreateTime << 8) + (msgBuffer.byteAt(20) & 0xFF);
        frameCreateTime = (frameCreateTime << 8) + (msgBuffer.byteAt(21) & 0xFF);
        frameCreateTime = (frameCreateTime << 8) + (msgBuffer.byteAt(22) & 0xFF);
        frameCreateTime = (frameCreateTime << 8) + (msgBuffer.byteAt(23) & 0xFF);

        var content = new byte[contentSize];
        msgBuffer.copyTo(content, HEAD_LENGTH, 0, contentSize);
//        if (compress) {
//            logger.debug("receive msg need compress!");
//            try {
//                content = Snappy.uncompress(content);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        }
        return new Message(type, code, remoteType, msgId, ByteUtil.copyFrom(peerId.bytes), content);
    }
}
