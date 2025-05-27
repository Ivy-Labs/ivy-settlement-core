package org.ivy.settlement.core.chain.ledger.model;

import org.ivy.settlement.infrastructure.crypto.VerifyingKey;
import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecurePublicKey;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.sequence.model.VerifyResult;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Objects;

/**
 * description:
 * @author carrot
 */
public class ValidatorPublicKeyInfo extends RLPModel {

    private int slotIndex;

    long consensusVotingPower;

    // This key can validate messages sent from this validator, can convert to accountAddress
    VerifyingKey consensusPublicKey;

    public ValidatorPublicKeyInfo(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    public ValidatorPublicKeyInfo() {
        super(null);
    }

    public ValidatorPublicKeyInfo(int slotIndex, long consensusVotingPower, VerifyingKey consensusPublicKey) {
        super(null);
        this.slotIndex = slotIndex;
        this.consensusVotingPower = consensusVotingPower;
        this.consensusPublicKey = consensusPublicKey;
        this.rlpEncoded = rlpEncoded();
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public byte[] getAccountAddress() {
        return this.consensusPublicKey.getKey();
    }

    public long getConsensusVotingPower() {
        return consensusVotingPower;
    }

    public VerifyingKey getConsensusPublicKey() {
        return consensusPublicKey;
    }

    public ValidatorPublicKeyInfo clone() {
        ValidatorPublicKeyInfo result = new ValidatorPublicKeyInfo(ByteUtil.copyFrom(this.rlpEncoded));
        return result;
    }

    public VerifyResult verifySignature(byte[] hash, Signature signature) {
        SecurePublicKey securePublicKey = getConsensusPublicKey().getSecurePublicKey();
        boolean res = securePublicKey.verify(hash, signature.getSig());
        if (!res) {
            return VerifyResult.ofInvalidSignature();
        }
        return VerifyResult.ofSuccess();
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] consensusVotingPower = RLP.encodeBigInteger(BigInteger.valueOf(this.consensusVotingPower));
        byte[] slotIndex = RLP.encodeInt(this.slotIndex);
        byte[] consensusPublicKey = RLP.encodeElement(this.consensusPublicKey.getKey());
        return RLP.encodeList(consensusVotingPower, slotIndex, consensusPublicKey);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.consensusVotingPower = ByteUtil.byteArrayToLong(rlpDecode.get(0).getRLPData());
        this.slotIndex = ByteUtil.byteArrayToInt(rlpDecode.get(1).getRLPData());
        this.consensusPublicKey = new VerifyingKey(rlpDecode.get(2).getRLPData());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidatorPublicKeyInfo that = (ValidatorPublicKeyInfo) o;
        return consensusVotingPower == that.consensusVotingPower &&
                slotIndex == that.slotIndex &&
                Objects.equals(consensusPublicKey, that.consensusPublicKey);
    }

    @Override
    public String toString() {
        return "VPI{" +
                "slotIndex=" + slotIndex +
                ", votingPower=" + consensusVotingPower +
                ", publicKey=" + Hex.toHexString(this.consensusPublicKey.getKey()) +
                '}';
    }
}
