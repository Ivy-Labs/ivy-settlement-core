package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.crypto.HashUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.core.chain.ledger.model.crypto.ValidatorSigner;

import java.math.BigInteger;

/**
 * description:
 * @author carrot
 */
public class TwoChainTimeout extends RLPModel {

    private long epoch;

    private long round;

    private QuorumCert quorumCert;

    private byte[] transientSignHash;

    private TwoChainTimeout() {
        super(null);
    }

    public TwoChainTimeout(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    public TwoChainTimeout(long epoch, long round, QuorumCert quorumCert) {
        super(null);
        this.epoch = epoch;
        this.round = round;
        this.quorumCert = quorumCert;
        this.rlpEncoded = rlpEncoded();
    }


    @Override
    protected byte[] rlpEncoded() {
        byte[] epoch = RLP.encodeBigInteger(BigInteger.valueOf(this.epoch));
        byte[] round = RLP.encodeBigInteger(BigInteger.valueOf(this.round));
        byte[] qc = this.quorumCert.rlpEncoded();
        return RLP.encodeList(epoch, round, qc);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.epoch = ByteUtil.byteArrayToLong(rlpDecode.get(0).getRLPData());
        this.round = ByteUtil.byteArrayToLong(rlpDecode.get(1).getRLPData());
        this.quorumCert = new QuorumCert(rlpDecode.get(2).getRLPData());
    }

    public long getEpoch() {
        return epoch;
    }

    public long getRound() {
        return round;
    }

    public byte[] getSignHash() {
        if (transientSignHash == null) {
            transientSignHash = HashUtil.sha3Dynamic(RLP.encodeBigInteger(BigInteger.valueOf(this.epoch)),
                    RLP.encodeBigInteger(BigInteger.valueOf(this.round)),
                    RLP.encodeBigInteger(BigInteger.valueOf(this.hqcRound())));
        }

        return transientSignHash;
    }

    public QuorumCert getQuorumCert() {
        return quorumCert;
    }


    public long hqcRound() {
        return this.quorumCert.getCertifiedEvent().round;
    }

    public ProcessResult<Void> verify(ValidatorVerifier verifiers) {
        if (this.hqcRound() >= this.getRound()) {
            throw new RuntimeException("Timeout round should be larger than the qc round");
        }
        return this.quorumCert.verify(verifiers);
    }

    public Signature sign(ValidatorSigner signer) {
        var sigHash = this.getSignHash();
        return signer.signMessage(sigHash).get();
    }

    public TwoChainTimeout copy() {
        return new TwoChainTimeout(ByteUtil.copyFrom(this.rlpEncoded));
    }

    @Override
    public String toString() {
        return "TwoChainTimeout{" +
                "epoch=" + epoch +
                ", round=" + round +
                ", quorumCert=" + quorumCert +
                '}';
    }


}
