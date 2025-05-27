
package org.ivy.settlement.core.chain.consensus.sequence.store;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.ivy.settlement.infrastructure.crypto.VerifyingKey;
import org.ivy.settlement.infrastructure.collections.BlockingPutHashMap;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.core.chain.config.Constants;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochStateHolder;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementMainChainConfig;
import org.ivy.settlement.core.chain.ledger.model.ValidatorPublicKeyInfo;
import org.ivy.settlement.infrastructure.datasource.model.DefaultValueable;
import org.ivy.settlement.infrastructure.datasource.model.Keyable;
import org.ivy.settlement.infrastructure.datasource.model.Persistable;
import org.ivy.settlement.infrastructure.datasource.AbstractDbSource;
import org.ivy.settlement.infrastructure.datasource.DbSettings;
import org.ivy.settlement.infrastructure.datasource.inmem.CacheDbSource;
import org.ivy.settlement.infrastructure.datasource.rocksdb.RocksDbSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.*;
import java.util.stream.Collectors;

import static org.ivy.settlement.infrastructure.datasource.rocksdb.RocksDbSource.DB_MAX_RETRY_TIME;

/**
 * description:
 * @author carrot
 */
public class ConsensusChainStore {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    private static final Map<String, Class<? extends Persistable>> COLUMN_FAMILIES = new HashMap() {{
        put("quorum_cert", QuorumCert.class);
        put("event", Event.class);

        put("epoch_state", EpochState.class);
        put("event_date", EventData.class);
        put("event_date_with_signatures", EventInfoWithSignatures.class);
        put("single_entry", DefaultValueable.class);
        put("global_state", Keyable.DefaultKeyable.class);
    }};

    static Keyable LatestLedgerInfo = Keyable.ofDefault("LatestLedgerInfo".getBytes());

    static Keyable LatestOutput = Keyable.ofDefault("LatestOutput".getBytes());

    static Keyable LatestCrossChainOffsets = Keyable.ofDefault("LatestCrossChainOffsets".getBytes());

    static final int MAX_RETRY_TIME = 10;

    int maxCommitEventNumInMemory;

    public final AbstractDbSource db;

    IrisCoreSystemConfig irisCoreSystemConfig;

    volatile LatestLedger cacheLatestLedger;

    BlockingPutHashMap<Long, EventData> commitEventCache;


    public final boolean test;


    public ConsensusChainStore(IrisCoreSystemConfig irisCoreSystemConfig, boolean test) {
        this.irisCoreSystemConfig = irisCoreSystemConfig;
        this.maxCommitEventNumInMemory = irisCoreSystemConfig.getMaxCommitEventNumInMemory();
        this.commitEventCache = new BlockingPutHashMap<>(maxCommitEventNumInMemory);
        this.test = test;
        if (test) {
            db = new CacheDbSource();
        } else {
            db = new RocksDbSource("consensus_chain", COLUMN_FAMILIES, irisCoreSystemConfig.dbPath(), DbSettings.newInstance().withMaxOpenFiles(irisCoreSystemConfig.getConsensusChainMaxOpenFiles()).withMaxThreads(irisCoreSystemConfig.getConsensusChainMaxThreads()).withWriteBufferSize(irisCoreSystemConfig.getConsensusChainWriteBufferSize()).withBloomFilterFlag(irisCoreSystemConfig.getConsensusChainBloomFilterFlag()));
        }
        initLedger();
    }

    private void initLedger() {
        var liInfoBytes = db.getRaw(DefaultValueable.class, LatestLedgerInfo);
        if (liInfoBytes == null) {

            // genesis init
            var validatorPublicKeyInfos = new ArrayList<ValidatorPublicKeyInfo>();
            for (var entry : this.irisCoreSystemConfig.getGenesisJson().getValidatorVerifiers().entrySet()) {
                var publicKey = Hex.decode(entry.getKey());
                var validatorInfo = entry.getValue();
                validatorPublicKeyInfos.add(new ValidatorPublicKeyInfo(validatorInfo.slotIndex, validatorInfo.consensusVotingPower, new VerifyingKey(publicKey)));
            }
            var validatorVerifier = ValidatorVerifier.convertFrom(validatorPublicKeyInfos);

            var genesisSettlementConfig = this.irisCoreSystemConfig.getGenesisJson().getGenesisSettlementConfig();

            var extendInfo = new ExtendInfo(new SettlementMainChainConfig(genesisSettlementConfig.mainChainStartNumber, genesisSettlementConfig.epochChangeInterval, genesisSettlementConfig.emptyBlockTimes, genesisSettlementConfig.accumulateSize, genesisSettlementConfig.onChainTimeoutInterval));
            var newEpochState = new EpochState(1, 1, validatorVerifier, extendInfo);
            var newNextEpochState = new EpochState(2, 2, validatorVerifier, extendInfo);
            var executedEventOutput = new ExecutedEventOutput(new HashMap<>(), 0, Constants.EMPTY_HASH_BYTES, new EpochStateHolder(newEpochState), new EpochStateHolder(newNextEpochState));
            var eventInfo = EventInfo.build(0, 0, Constants.EMPTY_HASH_BYTES, executedEventOutput.getStateRoot(), 0, 0, new EpochStateHolder(newEpochState), new EpochStateHolder(newNextEpochState));
            var crossChainOffsets = SettlementChainOffsets.genesisBuild(genesisSettlementConfig.mainChainStartNumber, genesisSettlementConfig.followerChainOffsets);

            var ledgerInfo = LedgerInfo.build(eventInfo, eventInfo.getHash());
            var ledgerInfoWithSignatures = LedgerInfoWithSignatures.build(ledgerInfo, new TreeMap<>());
            this.cacheLatestLedger = new LatestLedger();
//            resetCrossChainOffsets(crossChainOffsets);
            commit(new ArrayList<>(), new ArrayList<>(), executedEventOutput, crossChainOffsets, ledgerInfoWithSignatures);
            logger.debug("init genesis ledger:" + cacheLatestLedger);
        } else {
            var latestLedger = new LedgerInfoWithSignatures(liInfoBytes);
            var latestEpoch = latestLedger.getLedgerInfo().getNewCurrentEpochState().isPresent() ? latestLedger.getLedgerInfo().getEpoch() + 1 : latestLedger.getLedgerInfo().getEpoch();
            logger.info("will start recovery from epoch:{}", latestEpoch);
            var currentEpochState = new EpochState(db.getRaw(EpochState.class, Keyable.ofDefault(ByteUtil.longToBytes(latestEpoch))));
            var nextEpochState = new EpochState(db.getRaw(EpochState.class, Keyable.ofDefault(ByteUtil.longToBytes(latestEpoch + 1))));
            var executedEventOutput = new ExecutedEventOutput(db.getRaw(DefaultValueable.class, LatestOutput), latestLedger.getLedgerInfo().getNewCurrentEpochState(), latestLedger.getLedgerInfo().getNewNextEpochState());
            var crossChainOffsets = new SettlementChainOffsets(db.getRaw(DefaultValueable.class, LatestCrossChainOffsets));
            this.cacheLatestLedger = new LatestLedger(latestLedger, executedEventOutput, currentEpochState, nextEpochState, crossChainOffsets);
            logger.info("init ledger:" + cacheLatestLedger);
        }
    }

    public void commit(List<EventData> eventDates, List<EventInfoWithSignatures> eventInfoWithSignatures, ExecutedEventOutput latestOutput, SettlementChainOffsets latestSettlementChainOffsets, LedgerInfoWithSignatures latestLedgerInfo) {
        var saveBatch = new ArrayList<Pair<Keyable, Persistable>>(eventDates.size() + latestOutput.getOutput().size() + 2);
        for (var eventData : eventDates) {
            saveBatch.add(Pair.of(Keyable.ofDefault(ByteUtil.longToBytes(eventData.getNumber())), eventData));
        }

        for (var infoWithSignatures : eventInfoWithSignatures) {
            saveBatch.add(Pair.of(Keyable.ofDefault(ByteUtil.longToBytes(infoWithSignatures.getNumber())), infoWithSignatures));
        }

        for (var entry : latestOutput.getOutput().entrySet()) {
            saveBatch.add(Pair.of(entry.getKey(), new DefaultValueable(entry.getValue())));
        }

        var newCurrentEpoch = latestOutput.getNewCurrentEpochState();
        if (newCurrentEpoch.isPresent()) {
            logger.info("will save new current epoch[{}]", newCurrentEpoch.get());
            saveBatch.add(Pair.of(Keyable.ofDefault(ByteUtil.longToBytes(newCurrentEpoch.get().getEpoch())), newCurrentEpoch.get()));
        }

        var newNextEpoch = latestOutput.getNewNextEpochState();
        if (newNextEpoch.isPresent()) {
            logger.info("will save new next epoch[{}]", newNextEpoch.get());
            saveBatch.add(Pair.of(Keyable.ofDefault(ByteUtil.longToBytes(newNextEpoch.get().getEpoch())), newNextEpoch.get()));
        }

        saveBatch.add(Pair.of(LatestLedgerInfo, new DefaultValueable(latestLedgerInfo.getEncoded())));
        saveBatch.add(Pair.of(LatestOutput, new DefaultValueable(latestOutput.getEncoded())));
        saveBatch.add(Pair.of(LatestCrossChainOffsets, new DefaultValueable(latestSettlementChainOffsets.getEncoded())));

        for (var i = 0; i < DB_MAX_RETRY_TIME; i++) {
            try {
                updateCommitEventCache(eventDates);
                db.updateBatch(saveBatch);
                cacheLatestLedger.reset(latestLedgerInfo, latestOutput, latestSettlementChainOffsets);
                logger.info("do commit {}:", latestLedgerInfo);
                return;
            } catch (Exception e) {
                logger.error("consensusChainStore commit error!", e);
                //e.printStackTrace();
                if (i == DB_MAX_RETRY_TIME - 1) {
                    System.exit(0);
                }
            }
        }
    }

    private void updateCommitEventCache(List<EventData> eventDates) {
        for (EventData eventData : eventDates) {
            commitEventCache.put(eventData.getNumber(), eventData);
            //logger.info("put commit event[{}] cache success!", eventData.getNumber());
        }
    }

    /**
     * read the LedgerInfoWithSignatures from consensus commit event LedgerInfoWithSignatures which persist in db
     *
     * @return
     */
    public LatestLedger getLatestLedger() {
        return cacheLatestLedger;
    }

    //for speed up, eventData must decode when use
    public Triple<SettlementChainOffsets, List<EventData>, List<EventInfoWithSignatures>> getEventDates(long startEventNumber, long endEventNumber) {
        if (endEventNumber >= cacheLatestLedger.getLatestLedgerInfo().getLedgerInfo().getNumber()) {
            endEventNumber = cacheLatestLedger.getLatestLedgerInfo().getLedgerInfo().getNumber();
        }

        var latestOffsets = new EventData(db.getRaw(EventData.class, Keyable.ofDefault(ByteUtil.longToBytes(endEventNumber)))).getPayload().getCrossChainOffsets();


        var totalNum = (int) (endEventNumber - startEventNumber + 1);
        var batchKeys = new ArrayList<byte[]>(totalNum);
        for (long i = startEventNumber; i <= endEventNumber; i++) {
            batchKeys.add(ByteUtil.longToBytes(i));
        }

        var eventDatesRes = db.batchGetRaw(EventData.class, batchKeys);
        var eventDates = new ArrayList<EventData>(totalNum);
        for (var eventData : eventDatesRes) {
            eventDates.add(EventData.buildWithoutDecode(eventData));
        }

        var eventInfosRes = db.batchGetRaw(EventInfoWithSignatures.class, batchKeys);
        var eventInfoWithSignatures = new ArrayList<EventInfoWithSignatures>(totalNum);
        for (var evenInfo : eventInfosRes) {
            eventInfoWithSignatures.add(EventInfoWithSignatures.buildWithoutDecode(evenInfo));
        }
        return Triple.of(latestOffsets, eventDates, eventInfoWithSignatures);
    }

    public EventData getEventData(long number, boolean fullDecode, boolean removeCache) {
        EventData result;

        if (removeCache) {
            result = this.commitEventCache.remove(number);
        } else {
            result = this.commitEventCache.get(number);
        }


        if (result == null) {
            var eventDataBytes = db.getRaw(EventData.class, Keyable.ofDefault(ByteUtil.longToBytes(number)));
            if (eventDataBytes == null) {
                //logger.debug("[{}]getEventData[{}] was not exist, cost[{}] ", threadName, number, (System.currentTimeMillis() - start));
                return null;
            }
            if (fullDecode) {
                result = new EventData(eventDataBytes);
            } else {
                result = EventData.buildWithoutDecode(eventDataBytes);
            }
        }

        return result;
    }

    public EpochState getEpochState(long epoch) {
        var raw = db.getRaw(EpochState.class, Keyable.ofDefault(ByteUtil.longToBytes(epoch)));
        if (raw == null) return null;
        return new EpochState(raw);
    }


    //===============================================================================================

    static Keyable LastVoteMsg = Keyable.ofDefault("LastVoteMsg".getBytes());
    public void saveLastVoteMsg(Vote lastVote) {
        db.put(LastVoteMsg, new DefaultValueable(lastVote.getEncoded()));
    }

    public Optional<Vote> getLastVoteMsg() {
        byte[] raw = db.getRaw(DefaultValueable.class, LastVoteMsg);
        if (raw == null) {
            return Optional.empty();
        } else {
            return Optional.of(new Vote(raw));
        }
    }

    public void deleteLastVoteMsg() {
        db.put(LastVoteMsg, new DefaultValueable(null));
    }

    static Keyable HighestTimeoutCertificate = Keyable.ofDefault("HighestTimeoutCertificate".getBytes());
    public void saveHighestTimeoutCertificate(TimeoutCertificate highestTimeoutCertificate) {
        db.put(HighestTimeoutCertificate, new DefaultValueable(highestTimeoutCertificate.getEncoded()));
    }

    public Optional<TimeoutCertificate> getHighestTimeoutCertificate() {
        byte[] raw = db.getRaw(DefaultValueable.class, HighestTimeoutCertificate);
        if (raw == null) {
            return Optional.empty();
        } else {
            return Optional.of(new TimeoutCertificate(raw));
        }
    }

    public void deleteHighestTimeoutCertificate() {
        db.put(HighestTimeoutCertificate, new DefaultValueable(null));
    }

    static Keyable HighestTwoChainTimeoutCertificate = Keyable.ofDefault("HighestTwoChainTimeoutCertificate".getBytes());
    public void saveHighestTwoChainTimeoutCertificate(TwoChainTimeoutCertificate twoChainTimeoutCertificate) {
        db.put(HighestTwoChainTimeoutCertificate, new DefaultValueable(twoChainTimeoutCertificate.getEncoded()));
    }

    public Optional<TwoChainTimeoutCertificate> getHighestTwoChainTimeoutCertificate() {
        byte[] raw = db.getRaw(DefaultValueable.class, HighestTwoChainTimeoutCertificate);
        if (raw == null) {
            return Optional.empty();
        } else {
            return Optional.of(new TwoChainTimeoutCertificate(raw));
        }
    }

    public void deleteHighestTwoChainTimeoutCertificate() {
        db.put(HighestTwoChainTimeoutCertificate, new DefaultValueable(null));
    }

    public void saveEventsAndQuorumCertificates(List<Event> events, List<QuorumCert> qcs) {
        List<Pair<Keyable, Persistable>> saveBatch = new ArrayList<>(events.size() + qcs.size());
        for (Event event: events) {
            saveBatch.add(Pair.of(Keyable.ofDefault(event.getId()), event));
        }

        for (QuorumCert qc: qcs) {
            saveBatch.add(Pair.of(Keyable.ofDefault(qc.getCertifiedEvent().getId()), qc));
        }

        db.updateBatch(saveBatch);
    }

    public void deleteEventsAndQuorumCertificates(List<byte[]> eventIds) {
        List<Pair<Keyable, Persistable>> saveBatch = new ArrayList<>(eventIds.size() * 2);
        for (byte[] eventId: eventIds) {
            Keyable keyable = Keyable.ofDefault(eventId);
            saveBatch.add(Pair.of(keyable, new Event()));
            saveBatch.add(Pair.of(keyable, new QuorumCert()));
        }
        db.updateBatch(saveBatch);
    }

    //get all consensus events
    public List<Event> getAllEvents() {
        return db.getAll(Event.class).stream().map(persistable -> (Event) persistable).collect(Collectors.toList());
    }

    public List<QuorumCert> getAllQuorumCerts() {
        return db.getAll(QuorumCert.class).stream().map(persistable -> (QuorumCert) persistable).collect(Collectors.toList());
    }
}
