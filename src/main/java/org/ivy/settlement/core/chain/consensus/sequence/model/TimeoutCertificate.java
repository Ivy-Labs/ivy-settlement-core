package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.ivy.settlement.ethereum.model.settlement.Signature;

import java.util.TreeMap;

/**
 * description:
 * @author carrot
 */
public class TimeoutCertificate extends RLPModel {

    private Timeout timeout;

    private TreeMap<ByteArrayWrapper, Signature> signatures;

    public TimeoutCertificate(byte[] encode){super(encode);}

    public TimeoutCertificate(Timeout timeout, TreeMap<ByteArrayWrapper, Signature> signatures) {
        super(null);
        this.timeout = timeout;
        this.signatures = signatures;
        //this.rlpEncoded = rlpEncoded();
    }

    public long getEpoch() {
        return timeout.getEpoch();
    }

    public long getRound() {
        return timeout.getRound();
    }

    public TreeMap<ByteArrayWrapper, Signature> getSignatures() {
        return signatures;
    }

    public void addSignature(byte[] author, Signature signature) {
        //no need to re encode
        var keyAuthor = new ByteArrayWrapper(author);
        if (!this.signatures.containsKey(keyAuthor)) {
            this.signatures.put(keyAuthor, signature);
        }
    }

    public ProcessResult<Void> verify(ValidatorVerifier verifier) {
        var hash = this.timeout.getHash();
        return verifier.verifyAggregatedSignature(hash, this.signatures);
    }

    @Override
    protected byte[] rlpEncoded() {
        var encode = new byte[signatures.size() + 1][];
        encode[0] = this.timeout.getEncoded();
        int i = 1;
        for (var entry: signatures.entrySet()) {
            var key = RLP.encodeElement(entry.getKey().getData());
            var value = RLP.encodeElement(entry.getValue().getSig());
            encode[i] = RLP.encodeList(key, value);
            i++;
        }
        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        var params = RLP.decode2(rlpEncoded);
        var timeoutCertificate = (RLPList) params.get(0);
        this.timeout = new Timeout(timeoutCertificate.get(0).getRLPData());

        var signatures = new TreeMap<ByteArrayWrapper, Signature>();
        for (var i = 1; i < timeoutCertificate.size(); i++) {
            var kvBytes = (RLPList) RLP.decode2(timeoutCertificate.get(i).getRLPData()).get(0);
            signatures.put(new ByteArrayWrapper(kvBytes.get(0).getRLPData()), new Signature(kvBytes.get(1).getRLPData()));
        }
        this.signatures = signatures;
    }

    @Override
    public String toString() {
        return "TimeoutCertificate{" +
                "timeout=" + timeout +
                ", signatures=" + signatures +
                '}';
    }
}
