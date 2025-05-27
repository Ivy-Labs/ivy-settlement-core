package org.ivy.settlement.core.chain.txpool;

import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecureKey;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.crypto.HashUtil;
import org.ivy.settlement.core.chain.ledger.model.eth.EthTransaction;
import org.ivy.settlement.infrastructure.async.IrisSettlementWorker;
import org.ivy.settlement.core.chain.ledger.model.event.GlobalNodeEvent;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * description:
 * @author carrot
 */
public class EventCollector {

    private static final Logger logger = LoggerFactory.getLogger("tx-pool");

    public static Object IN_COMING_MONITOR = new Object();

    public final LinkedList<GlobalNodeEvent> IN_COMING_EVENTS;

    public final ArrayBlockingQueue<EthTransaction[]> IN_COMING_TXS;

    public final TxnPool txnPool;

    public EventCollector(TxnPool txnPool, int comingQueueSize, boolean test) {
        this.txnPool = txnPool;
        IN_COMING_EVENTS = new LinkedList();
        IN_COMING_TXS = new ArrayBlockingQueue<>(comingQueueSize);
        if (!test) {
            start();
        }
        //
    }

    private void mockGenerateTransactions() {
        new Thread(() -> {

            while (true) {
                try {
                    Random random = new Random();
                    // auto import
                    int num = 10;
                    EthTransaction[] ethTransactions = new EthTransaction[num];
                    long currentTime = System.currentTimeMillis();
                    byte[] receiveAddress = HashUtil.sha3(ByteUtil.longToBytesNoLeadZeroes(currentTime));
                    //EthTransaction sameTx = new EthTransaction(sender, sender, 1, ByteUtil.longToBytesNoLeadZeroes(currentTime), ByteUtil.longToBytesNoLeadZeroes(currentTime + 3_000_000), receiveAddress, ByteUtil.longToBytesNoLeadZeroes(currentTime - 3_000_000), receiveAddress, new HashSet<>(Arrays.asList(new ByteArrayWrapper(("hehe" ).getBytes()))), sender, HashUtil.sha3(sender));
                    long latestNumber = this.txnPool.getLatestNumber();

                    for (int i = 0; i < num; i++) {
                        byte[] hash = HashUtil.sha3(ByteUtil.longToBytes(currentTime), ByteUtil.merge(ByteUtil.intToBytes(i), ByteUtil.longToBytes(random.nextInt())));
                        EthTransaction tx = new EthTransaction(
                                SecureKey.getInstance("ECDSA",1).getPubKey(),
                                ByteUtil.longToBytesNoLeadZeroes(currentTime + i),
                                latestNumber + 5,
                                ByteUtil.longToBytesNoLeadZeroes(currentTime - i),
                                ByteUtil.longToBytesNoLeadZeroes(currentTime + 3_000_000 + i),
                                receiveAddress,
                                ByteUtil.longToBytesNoLeadZeroes(currentTime - 3_000_000 - i),
                                receiveAddress,
                                new HashSet<>(Arrays.asList(new ByteArrayWrapper(("hehe" + i).getBytes()))),
                                hash
                        );
                        ethTransactions[i] = tx;
                    }

                    //ethTransactions.add(sameTx);
                    importPayload(ethTransactions, new ArrayList<>(0));
                    ethTransactions = null;
                    Thread.sleep(3000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "do_generate_thread").start();
    }

    public void start() {
        mockGenerateTransactions();

        new IrisSettlementWorker("do_import_thread") {

            @Override
            protected void beforeLoop() {
                try {
                    logger.info("do_import_thread start success!");
                    Thread.sleep(3000);
                } catch (InterruptedException e) {

                }
            }

            @Override
            protected void doWork() throws Exception {
                //logger.debug("EventCollector do import!");

                List<EthTransaction[]> ethTransactionArrays = new ArrayList<>(30);
                int i = 0;
                GlobalNodeEvent[] globalNodeEvents;
                //EthTransaction[] txs;
                synchronized (EventCollector.IN_COMING_MONITOR) {
                    globalNodeEvents = new GlobalNodeEvent[EventCollector.this.IN_COMING_EVENTS.size()];
                    for (i = 0; i < EventCollector.this.IN_COMING_EVENTS.size(); i++) {
                        globalNodeEvents[i] = EventCollector.this.IN_COMING_EVENTS.pop();
                    }


                }

                if (IN_COMING_TXS.size() > 0) {
                    EventCollector.this.IN_COMING_TXS.drainTo(ethTransactionArrays);
                } else {
                    Thread.sleep(1000);
                }

                //EventCollector.this.txnPool.doImport(globalNodeEvents, ethTransactionArrays);
            }
        }.start();
    }

    public boolean importPayload(EthTransaction[] ethTransactions, List<GlobalNodeEvent> globalNodeEvents) {
        if (!CollectionUtils.isEmpty(globalNodeEvents)) {
            synchronized (IN_COMING_MONITOR) {
                IN_COMING_EVENTS.addAll(globalNodeEvents);
                //if (IN_COMING_TXS.size() > MAX_IN_COMING_SIZE) return false;
                //IN_COMING_TXS.addAll(ethTransactions);
            }
        }

        if (ethTransactions.length != 0) {
            try {
                IN_COMING_TXS.put(ethTransactions);
            } catch (InterruptedException e) {

            }
        }
        return true;
    }
}
