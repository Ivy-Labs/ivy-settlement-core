package org.ivy.settlement.core.chain.consensus.sequence.liveness;

import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.sequence.model.ExtendInfo;
import org.ivy.settlement.core.chain.consensus.sequence.model.LedgerInfo;
import org.ivy.settlement.core.chain.consensus.sequence.model.LedgerInfoWithSignatures;
import org.ivy.settlement.core.chain.consensus.sequence.model.ValidatorVerifier;
import org.ivy.settlement.core.chain.consensus.sequence.safety.Verifier;
import org.ivy.settlement.ethereum.model.event.VoterUpdateEvent;
import org.ivy.settlement.infrastructure.datasource.model.Persistable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

import static org.ivy.settlement.ethereum.model.event.VoterUpdateEvent.NODE_DEATH;
import static org.ivy.settlement.ethereum.model.event.VoterUpdateEvent.NODE_UPDATE;

/**
 * description:
 * @author carrot
 */
public class EpochState extends Persistable implements Verifier {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    long epoch;

    long ethEpoch;

    ValidatorVerifier validatorVerifier;

    ExtendInfo extendInfo;

    public EpochState(byte[] encoded) {
        super(encoded);
    }

    public EpochState(long ethEpoch, long epoch, ValidatorVerifier validatorVerifier, ExtendInfo extendInfo) {
        super(null);
        this.ethEpoch = ethEpoch;
        this.epoch = epoch;
        this.validatorVerifier = validatorVerifier;
        this.extendInfo = extendInfo;
        this.rlpEncoded = rlpEncoded();
    }


    public ValidatorVerifier getValidatorVerifier() {
        return this.validatorVerifier;
    }

    public long getMainChainGenesisHeight() {
        return this.extendInfo.getMainChainGenesisHeight();
    }

    public int getEmptyBlockTimes() {
        return this.extendInfo.getEmptyBlockTimes();
    }

    public int getAccumulateSize() {
        return this.extendInfo.getAccumulateSize();
    }

    public int getOnChainTimeoutInterval() {
        return this.extendInfo.getOnChainTimeoutInterval();
    }

    public ProcessResult<Void> verify(LedgerInfoWithSignatures ledgerInfo) {
        if (this.epoch != ledgerInfo.getLedgerInfo().getEpoch()) {
            ProcessResult.ofError(String.format("LedgerInfo has unexpected epoch [%d], expected [%d]", ledgerInfo.getLedgerInfo().getEpoch(), this.epoch));
        }

        return ledgerInfo.verifySignatures(this.validatorVerifier);
    }

    public boolean epochChangeVerificationRequired(long epoch) {
        return this.epoch < epoch;
    }

    public boolean isLedgerInfoStale(LedgerInfo ledgerInfo) {
        return ledgerInfo.getEpoch() < this.epoch;
    }

    public long getEpoch() {
        return epoch;
    }

    public long getEthEpoch() {
        return ethEpoch;
    }


    public List<byte[]> getOrderedPublishKeys() {
        return this.validatorVerifier.getOrderedPublishKeys();
    }

    public long calculateEpoch(long height) {
        return this.extendInfo.calculateEpoch(height);
    }

    public long calculateEpochBoundaryHeight(long height) {
        return this.extendInfo.calculateEpochBoundaryHeight(height);
    }

    public boolean isEpochBoundary(long height) {
        return this.extendInfo.isEpochBoundary(height);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EpochState that = (EpochState) o;
        return epoch == that.epoch && ethEpoch == that.ethEpoch && Objects.equals(validatorVerifier, that.validatorVerifier) && Objects.equals(extendInfo, that.extendInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(epoch, ethEpoch, validatorVerifier, extendInfo);
    }

    public void resetValidators(ValidatorVerifier validatorVerifier) {
        this.validatorVerifier = validatorVerifier;
        this.rlpEncoded = rlpEncoded();
    }


    public void updateValidators(List<VoterUpdateEvent> updateEvents) {
        var validEvents = updateEvents
                .stream()
                .filter(e -> {
                    if (e.getOpType() == NODE_DEATH) {
                        return this.ethEpoch == e.getOpEthEpoch() || this.ethEpoch == e.getOpEthEpoch() + 1;
                    } else if (e.getOpType() == NODE_UPDATE) {
                        return this.ethEpoch == e.getOpEthEpoch();
                    } else {
                        logger.error("update Epoch State, but meet un know event: {}", e);
                        return false;
                    }
                })
                .toList();
        if (validEvents.isEmpty()) return;
        this.validatorVerifier.updateVerifier(updateEvents, this.ethEpoch);
        this.rlpEncoded = rlpEncoded();
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] ehtEpoch = RLP.encodeBigInteger(BigInteger.valueOf(this.ethEpoch));
        byte[] epoch = RLP.encodeBigInteger(BigInteger.valueOf(this.epoch));
        byte[] validatorVerifier = this.validatorVerifier.getEncoded();
        byte[] extendInfo = this.extendInfo.getEncoded();
        return RLP.encodeList(ehtEpoch, epoch, validatorVerifier, extendInfo);
    }

    @Override
    protected void rlpDecoded() {
        var rlpEpochState = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.ethEpoch = ByteUtil.byteArrayToLong(rlpEpochState.get(0).getRLPData());
        this.epoch = ByteUtil.byteArrayToLong(rlpEpochState.get(1).getRLPData());
        this.validatorVerifier = new ValidatorVerifier(rlpEpochState.get(2).getRLPData());
        this.extendInfo = new ExtendInfo(rlpEpochState.get(3).getRLPData());
    }

    public EpochState copy() {
        if (this.rlpEncoded == null) {
            this.rlpEncoded = rlpEncoded();
        }
        return new EpochState(ByteUtil.copyFrom(this.rlpEncoded));
    }

    public EpochState copyForNext(boolean updateEthEpoch, boolean updateEpoch) {
        var ethEpoch = updateEthEpoch ? this.ethEpoch + 1 : this.ethEpoch;
        var epoch = updateEpoch ? this.epoch + 1 : this.epoch;
        return new EpochState(ethEpoch, epoch, new ValidatorVerifier(ByteUtil.copyFrom(this.validatorVerifier.getEncoded())), new ExtendInfo(ByteUtil.copyFrom(this.extendInfo.getEncoded())));
    }

    @Override
    public String toString() {
        return "EpochState{" +
                "epoch=" + epoch +
                ", ethEpoch=" + ethEpoch +
                ", validatorVerifier=" + validatorVerifier +
                ", extendInfo=" + extendInfo +
                '}';
    }
}
