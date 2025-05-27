package org.ivy.settlement.core.chain.txpool;

import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.ledger.model.eth.EthTransaction;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.core.chain.consensus.sequence.model.ConsensusPayload;
import org.ivy.settlement.core.chain.consensus.sequence.model.EventData;
import org.ivy.settlement.core.chain.ledger.model.event.GlobalEvent;
import org.ivy.settlement.core.chain.ledger.model.event.GlobalNodeEvent;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * description:
 * @author carrot
 */
public class TxnPool {

    private static final Logger logger = LoggerFactory.getLogger("tx-pool");

    LinkedHashMap<ByteArrayWrapper, GlobalNodeEvent> pendingEvents = new LinkedHashMap<>(8);

    Set<ByteArrayWrapper> pendingEventsCheck = new HashSet<>(1024 * 64);

    LinkedHashMap<ByteArrayWrapper, EthTransaction> pendingTxs;

    Set<ByteArrayWrapper> pendingTxsCheck;

    TxnManager txnManager;

    ReentrantLock lock = new ReentrantLock(true);

    Condition txsFullCondition = lock.newCondition();

    final int maxPackSize;

    final int poolLimit;

    public TxnPool(TxnManager txnManager, int poolLimit) {
        this.txnManager = txnManager;
        this.maxPackSize = txnManager.maxPackSize;
        this.poolLimit = poolLimit;
        this.pendingTxs = new LinkedHashMap<>(poolLimit);
        this.pendingTxsCheck = new HashSet<>(poolLimit);
        logger.info("TxnPool init success!");
    }

    public void doImportUnCommitEvents(EventData eventData) {
        try {
            lock.lock();
            for (GlobalNodeEvent globalNodeEvent: eventData.getGlobalEvent().getGlobalNodeEvents()) {
                logger.info("doImportUnCommitEvents[{}-{}] globalNodeEvent[{}]:", eventData.getNumber(), Hex.toHexString(eventData.getHash()), Hex.toHexString(globalNodeEvent.getHash()));
                pendingEvents.put(globalNodeEvent.getDsCheck(), globalNodeEvent);
            }

            for (EthTransaction tx: eventData.getPayload().getEthTransactions()) {
                pendingTxs.put(tx.getDsCheck(), tx);
            }

            if (logger.isDebugEnabled()) {
                for (EthTransaction tx: eventData.getPayload().getEthTransactions()) {
                    logger.debug("doImportUnCommitEvents tx[{}]:", Hex.toHexString(tx.getHash()));
                }
            }
            //}

            //this.txnManager.consensusChainStore.doubleSpendCheck.doReimport(eventDatas, this.pendingEvents, this.pendingTxs);

        } catch (Exception e) {
            logger.warn("doImport error! {}", ExceptionUtils.getStackTrace(e));
            //e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }

    public boolean proposalDSCheck(EventData eventData) {
        try {
            lock.lock();
            // todo :: check event data ds
            return true;

        } catch (Exception e) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void doRemoveCheck(EventData eventData) {
        try {
            lock.lock();
            //for (EventData eventData: eventDatas) {
            for (GlobalNodeEvent event: eventData.getGlobalEvent().getGlobalNodeEvents()) {
                logger.info("doCommit remove event:[{}-{}]", Hex.toHexString(eventData.getHash()), Hex.toHexString(event.getHash()));
                pendingEventsCheck.remove(event.getDsCheck());
                //pendingEvents.remove(event.getDsCheck());
            }

            for (EthTransaction tx: eventData.getPayload().getEthTransactions()) {
                pendingTxsCheck.remove(tx.getDsCheck());
                //pendingTxs.remove(tx.getDsCheck());
            }
            //}

//            if (logger.isDebugEnabled()) {
//                logger.debug("txpool do commit, pendingTxsCheck size[{}], pendingTxs size[{}]", pendingTxsCheck.size(), pendingTxs.size());
//            }

            //txsFullCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void doSyncCommit(EventData eventData) {
        try {
            lock.lock();
            //for (EventData eventData: eventDatas) {
            for (GlobalNodeEvent event: eventData.getGlobalEvent().getGlobalNodeEvents()) {
                logger.info("doSyncCommit remove event:[{}-{}]", Hex.toHexString(eventData.getHash()), Hex.toHexString(event.getHash()));
                pendingEventsCheck.remove(event.getDsCheck());
                pendingEvents.remove(event.getDsCheck());
            }

            for (EthTransaction tx: eventData.getPayload().getEthTransactions()) {
                pendingTxsCheck.remove(tx.getDsCheck());
                pendingTxs.remove(tx.getDsCheck());
            }
            //}

//            if (logger.isDebugEnabled()) {
//                logger.debug("txpool do commit, pendingTxsCheck size[{}], pendingTxs size[{}]", pendingTxsCheck.size(), pendingTxs.size());
//            }

            //txsFullCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void doRemove(EventData eventData) {
        try {
            lock.lock();
            //for (EventData eventData: eventDatas) {
            for (GlobalNodeEvent event: eventData.getGlobalEvent().getGlobalNodeEvents()) {
                pendingEventsCheck.add(event.getDsCheck());
                pendingEvents.remove(event.getDsCheck());
            }

            for (EthTransaction tx: eventData.getPayload().getEthTransactions()) {
                pendingTxsCheck.add(tx.getDsCheck());
                pendingTxs.remove(tx.getDsCheck());
            }
            //}
            //logger.debug("txpool do commit!");
            if (pendingTxsCheck.size() < poolLimit) {
                txsFullCondition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public Pair<GlobalEvent, ConsensusPayload> doPull(SettlementChainOffsets settlementChainOffsets, boolean stateConsistent) {
        try {

            lock.lock();
            if (!stateConsistent && pendingEvents.size() > 0) {
                return Pair.of(new GlobalEvent(), new ConsensusPayload(settlementChainOffsets, new EthTransaction[0]));
            }


            GlobalNodeEvent[] globalNodeEvents = new GlobalNodeEvent[pendingEvents.size()];
            Iterator<Map.Entry<ByteArrayWrapper, GlobalNodeEvent>> eventsIter = pendingEvents.entrySet().iterator();
            int count = 0;
            while (eventsIter.hasNext()) {
                globalNodeEvents[count] = eventsIter.next().getValue();
                eventsIter.remove();
                count++;
            }

            int max = Math.min(maxPackSize, pendingTxs.size());
            EthTransaction[] txs = new EthTransaction[max];
            Iterator<Map.Entry<ByteArrayWrapper, EthTransaction>> txsIter = pendingTxs.entrySet().iterator();
            for (int i = 0; i < max; i++) {
                txs[i] = txsIter.next().getValue();
                txsIter.remove();
            }

            if (pendingTxsCheck.size() < poolLimit) {
                txsFullCondition.signalAll();
            }

            return Pair.of(new GlobalEvent(globalNodeEvents), new ConsensusPayload(settlementChainOffsets, txs));
        } finally {
            lock.unlock();
        }
    }

    public long getLatestNumber() {
        return this.txnManager.getLatestNumber();
    }
}
