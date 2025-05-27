package org.ivy.settlement.core.chain.consensus.slowpath;


import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.ledger.StateLedger;
import org.ivy.settlement.core.chain.ledger.model.SettlementBatch;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static org.ivy.settlement.infrastructure.anyhow.Assert.ensure;

/**
 * description:
 * @author carrot
 */
class BatchCheckContext {

    static final Logger logger = LoggerFactory.getLogger("slow_path");

    enum BlobCommitType {
        NORMAL,
        SYNC
    }

    enum BlobCheckStatus {
        VOTING_BLOB,
        COMMIT_BLOB
    }

    // pk to sign
    volatile Map<ByteArrayWrapper, Signature> sameHashSigns = new HashMap<>();

    BiConsumer<BatchCheckContext, BlobCommitType> commitCallback;

    volatile EpochState epochState;

    volatile long currentConsensusNumber;

    volatile byte[] currentConsensusHash;

    volatile BlobCheckStatus currentStatus;

    BatchCheckContext(BiConsumer<BatchCheckContext, BlobCommitType> commitCallback) {
        this.commitCallback = commitCallback;
        this.currentStatus = BlobCheckStatus.COMMIT_BLOB;
    }

    void reset(StateLedger stateLedger, long signEpoch, SettlementBatch settlementBatch) {
        this.currentConsensusNumber = settlementBatch.getNumber();
        this.currentConsensusHash = settlementBatch.getHash();
        var latestEpochState = stateLedger.getLatestLedger().getCurrentEpochState();
        if (latestEpochState.getEpoch() == signEpoch) {
            this.epochState = latestEpochState;
        } else {
            this.epochState = stateLedger.getEpochState(signEpoch);
        }
        ensure(this.epochState != null, "BlobCheckContext reset error, sign epoch {} is not exist!", signEpoch);
        this.currentStatus = BlobCheckStatus.VOTING_BLOB;
    }

    void checkSign(ByteArrayWrapper pk, long number,  byte[] hash, Signature sign) {
        if (this.currentStatus != BlobCheckStatus.VOTING_BLOB) {
            logger.info("slow path check sign un except status, is not VOTING_BLOB");
            return;
        };

        if (number != this.currentConsensusNumber) {
            logger.info("slow path check sign for number is {}, current check number is {}", number, this.currentConsensusNumber);
            return;
        }

        if (!this.epochState.getValidatorVerifier().containPublicKey(pk)) {
            logger.warn("slow path check sign, [{}] is not a validator in this epoch, ignore!", pk);
            return;
        }

        if (Arrays.equals(hash, this.currentConsensusHash)) {
            var signature = this.sameHashSigns.get(pk);
            if (signature == null) {
                var verifySignRes = epochState.getValidatorVerifier().verifySignature(pk, hash, sign);
                if (!verifySignRes.isSuccess()) {
                    logger.warn("node[{}] for blob [{}-[{}] verify sign error", pk, number, Hex.toHexString(hash));
                    return;
                }
                logger.info("will put node[{}] blob [{}]-[{}] sign", pk, number, Hex.toHexString(hash));
                sameHashSigns.put(pk, sign);
            } else {
                //repeat sign
                logger.info("receive repeat blob sign from [{}] blob[{}] sign", pk, number);
                return;
            }

            var verifyPowerRes = this.epochState.getValidatorVerifier().checkVotingPower(sameHashSigns.keySet());
            if (verifyPowerRes.isSuccess()) {
                this.commit(this.currentConsensusNumber);
            }
        } else {
            logger.error("un except blob hash, current check hash is [{}], but receive[{}]", Hex.toHexString(this.currentConsensusHash), Hex.toHexString(hash));
        }
    }

    void commit(long number) {
        if (number == this.currentConsensusNumber) {
            //common condition
            this.commitCallback.accept(this, BlobCommitType.NORMAL);
            this.currentStatus = BlobCheckStatus.COMMIT_BLOB;
            this.sameHashSigns.clear();
        } else {
            logger.info("context do commit for blob number:{}, but receive:{}, ignore", this.currentConsensusNumber, number);
        }
    }

    Map<ByteArrayWrapper, Signature> getSameHashSigns() {
        return sameHashSigns;
    }

    long getCurrentCheckNumber() {
        return this.currentConsensusNumber;
    }

    long getCurrentSignEpoch() {
        return this.epochState.getEpoch();
    }

    EpochState getEpochState() {
        return epochState;
    }

    byte[] getCurrentCheckHash() {
        return this.currentConsensusHash;
    }

    boolean isCommit() {
        return currentStatus == BlobCheckStatus.COMMIT_BLOB;
    }

    @Override
    public String toString() {
        return "BlobCheckContext{" +
                "currentCheckNumber=" + this.currentConsensusNumber +
                ", currentStatus=" + currentStatus +
                '}';
    }
}
