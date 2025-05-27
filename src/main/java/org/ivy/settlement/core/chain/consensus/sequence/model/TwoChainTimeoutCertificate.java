package org.ivy.settlement.core.chain.consensus.sequence.model;

import io.libp2p.core.utils.Pair;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.crypto.HashUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.ivy.settlement.ethereum.model.settlement.Signature;

import java.math.BigInteger;
import java.util.Set;
import java.util.TreeMap;

/**
 * description:
 * @author carrot
 */
public class TwoChainTimeoutCertificate extends RLPModel {

    TwoChainTimeout twoChainTimeout;

    private TreeMap<ByteArrayWrapper, Pair<Long, Signature>> signatures;

    public TwoChainTimeoutCertificate(byte[] encode) {
        super(encode);
    }

    public TwoChainTimeoutCertificate(TwoChainTimeout twoChainTimeout) {
        super(null);
        this.twoChainTimeout = twoChainTimeout;
        this.signatures = new TreeMap<>();
    }

    public ProcessResult<Void> verify(ValidatorVerifier verifiers) {
        var qcVerifyRes = this.twoChainTimeout.verify(verifiers);

        if (!qcVerifyRes.isSuccess()) return qcVerifyRes;
        var sigVerifyRes = verifiers.checkVotingPower(this.signatures.keySet());
        if (!sigVerifyRes.isSuccess()) {
            return ProcessResult.ofError(sigVerifyRes.toString());
        }

        long signedRound = 0;
        for (var entry : this.signatures.entrySet()) {
            var author = entry.getKey();
            var qcRound = entry.getValue().first;
            var signature = entry.getValue().second;

            var sigHash = HashUtil.sha3Dynamic(RLP.encodeBigInteger(BigInteger.valueOf(this.twoChainTimeout.getEpoch())),
                    RLP.encodeBigInteger(BigInteger.valueOf(this.twoChainTimeout.getRound())),
                    RLP.encodeBigInteger(BigInteger.valueOf(qcRound)));

            sigVerifyRes = verifiers.verifySignature(author, sigHash, signature);
            if (!sigVerifyRes.isSuccess()) {
                return ProcessResult.ofError("failed to verify" + author + "'s timeout sign!");
            }
            signedRound = Math.max(signedRound, qcRound);
        }

        if (this.twoChainTimeout.hqcRound() != signedRound) {
            return ProcessResult.ofError(String.format("Inconsistent hqc round, qc has round %d, highest signed round %d", this.twoChainTimeout.hqcRound(), signedRound));
        }

        return qcVerifyRes;
    }

    public void add(ByteArrayWrapper author, TwoChainTimeout timeout, Signature signature) {
        var hqcRound = timeout.hqcRound();
        if (hqcRound > this.twoChainTimeout.hqcRound()) {
            this.twoChainTimeout = timeout;
        }
        this.signatures.put(author, Pair.of(hqcRound, signature));
    }

    public Set<ByteArrayWrapper> signers() {
        return this.signatures.keySet();
    }

    public long getEpoch() {
        return this.twoChainTimeout.getEpoch();
    }

    public long getRound() {
        return this.twoChainTimeout.getRound();
    }


    public long highestHqcRound() {
        return this.twoChainTimeout.hqcRound();
    }


    @Override
    protected byte[] rlpEncoded() {
        var encode = new byte[signatures.size() + 1][];
        encode[0] = this.twoChainTimeout.getEncoded();
        var i = 1;
        for (var entry : signatures.entrySet()) {
            var author = RLP.encodeElement(entry.getKey().getData());
            var round = RLP.encodeBigInteger(BigInteger.valueOf(entry.getValue().first));
            var sig = RLP.encodeElement(entry.getValue().second.getSig());
            encode[i] = RLP.encodeList(author, round, sig);
            i++;
        }
        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        var params = RLP.decode2(rlpEncoded);
        var timeoutCertificate = (RLPList) params.get(0);
        this.twoChainTimeout = new TwoChainTimeout(timeoutCertificate.get(0).getRLPData());

        var signatures = new TreeMap<ByteArrayWrapper, Pair<Long, Signature>>();
        for (var i = 1; i < timeoutCertificate.size(); i++) {
            var kvBytes = (RLPList) RLP.decode2(timeoutCertificate.get(i).getRLPData()).get(0);
            signatures.put(new ByteArrayWrapper(kvBytes.get(0).getRLPData()), Pair.of(ByteUtil.byteArrayToLong(kvBytes.get(1).getRLPData()), new Signature(kvBytes.get(2).getRLPData())));
        }
        this.signatures = signatures;
    }

    @Override
    public String toString() {
        return "TwoChainTimeoutCertificate{" +
                "twoChainTimeout=" + twoChainTimeout +
                ", signatures=" + signatures +
                '}';
    }
}
