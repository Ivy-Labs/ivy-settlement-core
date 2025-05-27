package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.crypto.CryptoHash;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.crypto.HashUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.core.chain.ledger.model.crypto.ValidatorSigner;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

/**
 * description:
 * @author carrot
 */
public class Timeout extends RLPModel implements CryptoHash {

    private long epoch;

    private long round;

    byte[] transientHash;

    private Timeout(){super(null);}

    public Timeout(byte[] encode) {super(encode);}

    public static Timeout build(long epoch, long round) {
        Timeout timeout = new Timeout();
        timeout.epoch = epoch;
        timeout.round = round;
        timeout.rlpEncoded = timeout.rlpEncoded();
        timeout.transientHash = HashUtil.sha3(timeout.rlpEncoded);
        return timeout;
    }

    public long getEpoch() {
        return epoch;
    }

    public long getRound() {
        return round;
    }

    @Override
    public byte[] getHash() {
        return transientHash;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] epoch = RLP.encodeBigInteger(BigInteger.valueOf(this.epoch));
        byte[] round = RLP.encodeBigInteger(BigInteger.valueOf(this.round));
        return RLP.encodeList(epoch, round);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.epoch = ByteUtil.byteArrayToLong(rlpDecode.get(0).getRLPData());
        this.round = ByteUtil.byteArrayToLong(rlpDecode.get(1).getRLPData());
        this.transientHash = HashUtil.sha3(this.rlpEncoded);
    }

    @Override
    public String toString() {
        return "Timeout{" +
                "epoch=" + epoch +
                ", round=" + round +
                ", transientHash=" + Hex.toHexString(transientHash) +
                '}';
    }

    public Signature sign(ValidatorSigner signer) {
        return signer.signMessage(getHash()).get();
    }
}
