package org.ivy.settlement.core.chain.ledger.store;

import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.ethereum.model.settlement.FastPathBlocks;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.datasource.AbstractDbSource;
import org.ivy.settlement.infrastructure.datasource.DbSettings;
import org.ivy.settlement.infrastructure.datasource.inmem.CacheDbSource;
import org.ivy.settlement.infrastructure.datasource.model.DefaultValueable;
import org.ivy.settlement.infrastructure.datasource.model.Keyable;
import org.ivy.settlement.infrastructure.datasource.model.Persistable;
import org.ivy.settlement.infrastructure.datasource.rocksdb.RocksDbSource;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.ledger.model.BatchSign;
import org.ivy.settlement.core.chain.ledger.model.Block;
import org.ivy.settlement.core.chain.ledger.model.BlockSign;
import org.ivy.settlement.core.chain.ledger.model.SettlementBatch;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * description:
 * @author carrot
 */
public class LedgerStore {
    private static final Logger logger = LoggerFactory.getLogger("db");

    private static final Map<String, Class<? extends Persistable>> COLUMN_FAMILIES = new HashMap<>() {{
        put("single_entry", DefaultValueable.class);
        put("block", Block.class);
        put("block_sign", BlockSign.class);
        put("settlement_blob", SettlementBatch.class);
        put("blob_sign", BatchSign.class);
    }};

    static final Keyable LATEST_BE_EXECUTED_NUMBER = Keyable.ofDefault("LATEST_BE_EXECUTED_NUMBER".getBytes());
    static final Keyable LATEST_BE_EXECUTED_EPOCH = Keyable.ofDefault("LATEST_BE_EXECUTED_EPOCH".getBytes());


    static final Keyable LATEST_WILL_SIGNED_BLOB_NUMBER = Keyable.ofDefault("LATEST_WILL_SIGNED_BLOB_NUMBER".getBytes());
    static final Keyable LATEST_SIGNED_BLOB_NUMBER = Keyable.ofDefault("LATEST_SIGNED_BLOB_NUMBER".getBytes());
    static final Keyable LATEST_CONFIRM_BLOB_NUMBER = Keyable.ofDefault("LATEST_CONFIRM_BLOB_NUMBER".getBytes());


    //private static final LedgerSource cacheSource = new LedgerSource(true, SystemConfig.getDefault());

    private final AbstractDbSource db;

    private volatile long latestWillSignedBlobNum;

    private volatile long latestConfirmBlobNum;

    private volatile long latestBeExecutedNum;

    private volatile long latestBeExecutedEpoch;


    public LedgerStore(boolean test, IrisCoreSystemConfig irisCoreSystemConfig) {
        if (test) {
            db = new CacheDbSource();
        } else {
            db = new RocksDbSource("ledger", COLUMN_FAMILIES, irisCoreSystemConfig.dbPath(), DbSettings.newInstance().withMaxOpenFiles(irisCoreSystemConfig.getLedgerMaxOpenFiles()).withMaxThreads(irisCoreSystemConfig.getLedgerMaxThreads()).withWriteBufferSize(irisCoreSystemConfig.getLedgerWriteBufferSize()));

            byte[] latestWillSignedBlobNum = db.getRaw(DefaultValueable.class, LATEST_WILL_SIGNED_BLOB_NUMBER);
            byte[] latestConfirmBlobNum = db.getRaw(DefaultValueable.class, LATEST_CONFIRM_BLOB_NUMBER);
            byte[] latestExecuteNum = db.getRaw(DefaultValueable.class, LATEST_BE_EXECUTED_NUMBER);
            byte[] latestExecuteEpoch = db.getRaw(DefaultValueable.class, LATEST_BE_EXECUTED_EPOCH);
            if (latestConfirmBlobNum == null) {
                var genesisBlock = irisCoreSystemConfig.getGenesis().asBlock();
                var blockSign = new BlockSign(genesisBlock.getEpoch(), genesisBlock.getNumber(), genesisBlock.getHash(), new HashMap<>());
                doPersistBlock(genesisBlock, blockSign);

                var genesisBlob = new SettlementBatch(genesisBlock.getEpoch(), genesisBlock.getEpoch(), 0, genesisBlock.getNumber(), genesisBlock.getTimestamp(), new FastPathBlocks(new TreeMap<>()), Collections.EMPTY_LIST);
                genesisBlob.recordCommitSign(new HashMap<>());
                doPersistWillSignedBlob(genesisBlob);
                doPersistConfirmBlob(genesisBlob);
            } else {
                this.latestWillSignedBlobNum = ByteUtil.byteArrayToLong(latestWillSignedBlobNum);
                this.latestConfirmBlobNum = ByteUtil.byteArrayToLong(latestConfirmBlobNum);

                this.latestBeExecutedNum = ByteUtil.byteArrayToLong(latestExecuteNum);
                this.latestBeExecutedEpoch = ByteUtil.byteArrayToLong(latestExecuteEpoch);
            }
        }
    }

    public long getLatestBeExecutedNum() {
        return latestBeExecutedNum;
    }

    public long getLatestBeExecutedEpoch() {
        return latestBeExecutedEpoch;
    }

    public long getLatestWillSignedBlobNum() {
        return latestWillSignedBlobNum;
    }

    public long getLatestConfirmBlobNum() {
        return latestConfirmBlobNum;
    }

    public Block getBlockByNumber(long number) {
        byte[] dbValues = db.getRaw(Block.class, Keyable.ofDefault(ByteUtil.longToBytes(number)));
        if (dbValues == null) {
            return null;
        }
        return new Block(dbValues);
    }

    public SettlementBatch getBlobByNumber(long number) {
        byte[] dbValues = db.getRaw(SettlementBatch.class, Keyable.ofDefault(ByteUtil.longToBytes(number)));
        if (dbValues == null) {
            return null;
        }
        return new SettlementBatch(dbValues);
    }

    public List<Pair<Block, BlockSign>> getCommitBlocksByNumber(long start, long end) {

        var batchKeys = new ArrayList<byte[]>((int) (end - start + 1));
        for (long i = start; i <= end; i++) {
            batchKeys.add(ByteUtil.longToBytes(i));
        }

        var rawBlocks = db.batchGetRaw(Block.class, batchKeys);
        var blocks = new ArrayList<Block>(rawBlocks.size());
        for (var entry : rawBlocks) {
            blocks.add(Block.buildWithoutDecode(entry));
        }


        var rawBlockSigns = db.batchGetRaw(BlockSign.class, batchKeys);
        var blockSigns = new ArrayList<BlockSign>(rawBlockSigns.size());
        for (var entry : rawBlockSigns) {
            blockSigns.add(BlockSign.buildWithoutDecode(entry));
        }

        var res = new ArrayList<Pair<Block, BlockSign>>(blocks.size());
        for (var i = 0; i < blocks.size(); i++) {
            res.add(Pair.of(blocks.get(i), blockSigns.get(i)));
        }
        return res;
    }

    public BlockSign getBlockSignByNumber(long number) {
        byte[] dbValues = db.getRaw(BlockSign.class, Keyable.ofDefault(ByteUtil.longToBytes(number)));
        if (dbValues == null) {
            return null;
        }
        return new BlockSign(dbValues);
    }

    public BatchSign getBlobSignByNumber(long number) {
        byte[] dbValues = db.getRaw(BatchSign.class, Keyable.ofDefault(ByteUtil.longToBytes(number)));
        if (dbValues == null) {
            return null;
        }
        return new BatchSign(dbValues);
    }

    public void doPersistWillSignedBlob(SettlementBatch settlementBatch) {
        try (var writeOptions = new WriteOptions(); var writeBatch = db.writeBatchFactory.getInstance()) {
            var defaultValueHandle = db.clazz2HandleTable.get(DefaultValueable.class);
            writeBatch.put(defaultValueHandle, LATEST_WILL_SIGNED_BLOB_NUMBER.keyBytes(), ByteUtil.longToBytes(settlementBatch.getNumber()));

            var blockHandle = db.clazz2HandleTable.get(SettlementBatch.class);
            writeBatch.put(blockHandle, ByteUtil.longToBytes(settlementBatch.getNumber()), settlementBatch.getEncoded());


            for (int i = 0; i < 10; i++) {
                try {
                    db.db.write(writeOptions, writeBatch);
                    this.latestWillSignedBlobNum = settlementBatch.getNumber();
                    return;
                } catch (Exception e) {
                    logger.error("consensusChainStore commit error!", e);
                    if (i == 9) {
                        System.exit(0);
                    }
                }
            }
            //long start = System.currentTimeMillis();
        } catch (RocksDBException e) {
            logger.error("Error in writeBatch update on db ", e);
            throw new RuntimeException(e);
        }
    }

    public void doPersistConfirmBlob(SettlementBatch settlementBatch) {
        try (var writeOptions = new WriteOptions(); var writeBatch = db.writeBatchFactory.getInstance()) {
            var defaultValueHandle = db.clazz2HandleTable.get(DefaultValueable.class);
            writeBatch.put(defaultValueHandle, LATEST_CONFIRM_BLOB_NUMBER.keyBytes(), ByteUtil.longToBytes(settlementBatch.getNumber()));
            ColumnFamilyHandle blockSignHandle = db.clazz2HandleTable.get(BatchSign.class);
            writeBatch.put(blockSignHandle, ByteUtil.longToBytes(settlementBatch.getNumber()), settlementBatch.getBlobSign().getEncoded());

            for (int i = 0; i < 10; i++) {
                try {
                    db.db.write(writeOptions, writeBatch);
                    this.latestConfirmBlobNum = settlementBatch.getNumber();
                    return;
                } catch (Exception e) {
                    logger.error("consensusChainStore commit error!", e);
                    if (i == 9) {
                        System.exit(0);
                    }
                }
            }
            //long start = System.currentTimeMillis();
        } catch (RocksDBException e) {
            logger.error("Error in writeBatch update on db ", e);
            throw new RuntimeException(e);
        }
    }

    public void doPersistBlock(Block block, BlockSign blockSign) {
        logger.info("will do persist block[{}]!", block.getNumber());
        try (var writeOptions = new WriteOptions(); var writeBatch = db.writeBatchFactory.getInstance()) {
            var defaultValueHandle = db.clazz2HandleTable.get(DefaultValueable.class);
            writeBatch.put(defaultValueHandle, LATEST_BE_EXECUTED_NUMBER.keyBytes(), ByteUtil.longToBytes(block.getNumber()));
            writeBatch.put(defaultValueHandle, LATEST_BE_EXECUTED_EPOCH.keyBytes(), ByteUtil.longToBytes(block.getEpoch()));

            var blockHandle = db.clazz2HandleTable.get(Block.class);
            writeBatch.put(blockHandle, ByteUtil.longToBytes(block.getNumber()), block.getEncoded());

            if (blockSign != null) {
                ColumnFamilyHandle blockSignHandle = db.clazz2HandleTable.get(BlockSign.class);
                writeBatch.put(blockSignHandle, ByteUtil.longToBytes(block.getNumber()), blockSign.getEncoded());
            }

            for (int i = 0; i < 10; i++) {
                try {
                    db.db.write(writeOptions, writeBatch);
                    this.latestBeExecutedNum = block.getNumber();
                    this.latestBeExecutedEpoch = block.getEpoch();
                    //logger.info("persist block[{}] success!", this.latestBeExecutedNum);
                    return;
                } catch (Exception e) {
                    logger.error("consensusChainStore commit error!", e);
                    if (i == 9) {
                        System.exit(0);
                    }
                }
            }
            //long start = System.currentTimeMillis();
        } catch (RocksDBException e) {
            logger.error("Error in writeBatch update on db ", e);
            throw new RuntimeException(e);
        }
    }
}
