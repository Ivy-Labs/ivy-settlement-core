package org.ivy.settlement.core.chain.network.protocols;

import org.ivy.settlement.core.chain.network.protocols.base.Message;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * description:
 * @author carrot
 */
public class MessageDuplexDispatcher {

    private static BlockingQueue<Message> consensusMsgQueue = null;

    private static BlockingQueue<Message> layer2StateSyncMsgQueue = null;

    private static BlockingQueue<Message> fastPathMsgQueue = null;
    private static BlockingQueue<Message> slowPathMsgQueue = null;

    static {
        consensusMsgQueue = new ArrayBlockingQueue<>(10000);
        layer2StateSyncMsgQueue = new ArrayBlockingQueue<>(10000);
        fastPathMsgQueue = new ArrayBlockingQueue<>(10000);
        slowPathMsgQueue = new ArrayBlockingQueue<>(10000);

    }


    public static void putConsensusMsg(Message consensusMsg) {
        try {
            consensusMsgQueue.put(consensusMsg);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Message getConsensusMsg() {
        Message message = null;
        try {
            return consensusMsgQueue.take();
        } catch (InterruptedException e) {
        }
        return message;
    }

    public static void putLayer2StateSyncMsg(Message layer2StateSyncMsg) {
        try {
            layer2StateSyncMsgQueue.put(layer2StateSyncMsg);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Message getLayer2StateSyncMsg() {
        Message message = null;
        try {
            return layer2StateSyncMsgQueue.take();
        } catch (InterruptedException e) {

        }
        return message;
    }

    public static void putFastPathMsg(Message fastPathMsg) {
        try {
            fastPathMsgQueue.put(fastPathMsg);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Message getFastPathMsg() {
        try {
            return fastPathMsgQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static void putSlowPathMsg(Message slowPathMsg) {
        try {
            slowPathMsgQueue.put(slowPathMsg);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Message getSlowPathMsg() {
        try {
            return slowPathMsgQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
