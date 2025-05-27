package org.ivy.settlement.core.chain.consensus.fastpath;

import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecureKey;
import org.ivy.settlement.infrastructure.string.StringUtils;
import org.ivy.settlement.infrastructure.async.IrisSettlementWorker;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.consensus.fastpath.model.LocalBlockSignMsg;
import org.ivy.settlement.core.chain.ledger.StateLedger;
import org.ivy.settlement.core.chain.ledger.model.Block;
import org.ivy.settlement.core.chain.ledger.model.BlockSign;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.core.chain.network.NetInvoker;
import org.ivy.settlement.core.chain.sync.SettlementChainsSyncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;

/**
 * description:
 * @author carrot
 */
public class SettlementFastPathCommitter {

    static final Logger logger = LoggerFactory.getLogger("fast_path");

    public static final ArrayBlockingQueue<Pair<Block, CountDownLatch>> TO_BE_CHECK_BLOCK_QUEUE = new ArrayBlockingQueue<>(512);

    StateLedger stateLedger;

    NetInvoker netInvoker;

    FastPathCommitCoordinator coordinator;

    volatile BlockCheckContext currentBlockCheckContext;

    SecureKey secureKey;

    ReentrantLock commitAwaitLock;
    Map<Long, Pair<Block, CountDownLatch>> commitAwaitConditions;

    Map<Long, Block> commitBlocks;

    Map<Long, BlockSign> commitBlockSigns;

    boolean test;

    int maxCommitBlockCache;

    SettlementBlobGenerator settlementBlobGenerator;

    public SettlementFastPathCommitter(IrisCoreSystemConfig irisCoreSystemConfig, StateLedger stateLedger, SettlementBlobGenerator settlementBlobGenerator, SettlementChainsSyncer settlementChainsSyncer, NetInvoker netInvoker, boolean test) {
        this.stateLedger = stateLedger;
        this.netInvoker = netInvoker;
        this.secureKey = irisCoreSystemConfig.getMyKey();
        this.coordinator = new FastPathCommitCoordinator(this, new BlockSyncCoordinator(stateLedger, settlementChainsSyncer, netInvoker), irisCoreSystemConfig.getFastPathCheckTimeoutMS());
        this.commitAwaitLock = new ReentrantLock(false);
        this.commitAwaitConditions = new HashMap<>();
        this.commitBlockSigns = new ConcurrentHashMap<>();
        this.commitBlocks = new ConcurrentHashMap<>();
        var commitCallback = new BiConsumer<BlockCheckContext, BlockCheckContext.BlockCommitType>() {
            @Override
            public void accept(BlockCheckContext blockCheckContext, BlockCheckContext.BlockCommitType commitType) {
                try {
                    commitAwaitLock.lock();
                    var awaitPair = SettlementFastPathCommitter.this.commitAwaitConditions.remove(blockCheckContext.getCurrentCheckNumber());
                    if (awaitPair == null) {
                        logger.info("commit callback wait condition for block number[{}] is not exist!", blockCheckContext.getCurrentCheckNumber());
                        var block = SettlementFastPathCommitter.this.stateLedger.getBlockByNumber(blockCheckContext.getCurrentCheckNumber());
                        SettlementFastPathCommitter.this.settlementBlobGenerator.accumulate(block);
                    } else {
                        var block = awaitPair.getLeft();
                        if (commitType == BlockCheckContext.BlockCommitType.NORMAL) {
                            block.recordCommitSign(blockCheckContext.getSameHashSigns());
                            SettlementFastPathCommitter.this.doCommitBlock(block);
                        } else if (commitType == BlockCheckContext.BlockCommitType.SYNC) {
                            logger.info("sync commit call back for block number:{}", block.getNumber());
                            SettlementFastPathCommitter.this.settlementBlobGenerator.accumulate(block);
                        }
                        awaitPair.getRight().countDown();
                    }
                } catch (Exception e) {
                    logger.warn("commit call back error!", e);
                } finally {
                    commitAwaitLock.unlock();
                }
            }
        };
        this.currentBlockCheckContext = new BlockCheckContext(commitCallback, this.stateLedger.getLatestBeExecutedNum() + 1);
        this.test = test;
        this.maxCommitBlockCache = irisCoreSystemConfig.getFastPathMaxCommitBlockInMemory();
        this.settlementBlobGenerator = settlementBlobGenerator;
    }

    public void start() {
        new IrisSettlementWorker("fast_path_committer_thread") {

            @Override
            protected void beforeLoop() {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    logger.error("fast_path_committer_thread beforeLoop sleep error!", e);
                }
            }

            @Override
            protected void doWork() throws Exception {
                var executedContext = TO_BE_CHECK_BLOCK_QUEUE.take();
                var exeBlock = executedContext.getKey();
                var finishCondition = executedContext.getValue();
                awaitAsyncCheckSeq(exeBlock, finishCondition);
            }

            @Override
            protected void doException(Throwable e) {
                super.doException(e);
            }
        }.start();

        this.coordinator.start();
    }



    private void awaitAsyncCheckSeq(Block exeBlock, CountDownLatch finishCommitCondition) throws InterruptedException {
        try {
            this.commitAwaitLock.lock();
            if (exeBlock.getNumber() <= this.stateLedger.getLatestBeExecutedNum()) {
                logger.info("latest be executed block is [{}], current executed block is [{}], ignore!", stateLedger.getLatestBeExecutedNum(), exeBlock.getNumber());
                finishCommitCondition.countDown();
                return;
            }
            this.commitAwaitConditions.put(exeBlock.getNumber(), Pair.of(exeBlock, finishCommitCondition));
        } finally {
            this.commitAwaitLock.unlock();
        }
        this.coordinator.fastPathMsgQueue.put(new LocalBlockSignMsg(exeBlock, secureKey.getPubKey(), new Signature(secureKey.sign(exeBlock.getHash()))));
        finishCommitCondition.await();
    }

    private void doCommitBlock(Block commitBlock) {
        if (commitBlock.getNumber() <= this.stateLedger.getLatestBeExecutedNum()) {
            //it may occur when sync condition!
            logger.info("commit block[{}] has do commit, ignore!", commitBlock.getNumber());
            return;
        }

        var blockSign = commitBlock.getBlockSign();
        if (blockSign == null) {
            logger.error("un except condition for blockSign is null when do commit block[{}]", commitBlock.getNumber());
            throw new RuntimeException(StringUtils.format("un except condition for blockSign is null when do commit block[{}]", commitBlock.getNumber()));
        }

        this.stateLedger.persistBlock(commitBlock, blockSign);
        this.settlementBlobGenerator.accumulate(commitBlock);
        //commitNotify(commitBlock);
        this.commitBlocks.put(commitBlock.getNumber(), commitBlock);
        this.commitBlockSigns.put(commitBlock.getNumber(), blockSign);
        this.commitBlocks.remove(commitBlock.getNumber() - this.maxCommitBlockCache);
        this.commitBlockSigns.remove(commitBlock.getNumber() - this.maxCommitBlockCache);

        long end = System.currentTimeMillis();
        logger.info("all chain trace finish all consensus block[{}-{}],total use [{}ms]",
                commitBlock.getNumber(),
                Hex.toHexString(commitBlock.getHash()),
                (end - commitBlock.getTimestamp()));
    }

    public BlockSign getCommitBlockSign(long number) {
        var sign = this.commitBlockSigns.get(number);
        if (sign == null) {
            sign = this.stateLedger.getBlockSignByNumber(number);
        }
        return sign;
    }

    public static void addToStateCheck(Block block, CountDownLatch finishCondition) {
        try {
            TO_BE_CHECK_BLOCK_QUEUE.put(Pair.of(block, finishCondition));
        } catch (Exception e) {
            logger.warn("addToStateCheck error!", e);
            throw new RuntimeException(e);
        }
    }
}
