package org.ivy.settlement.core.chain.consensus.slowpath.model;

import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Map;
import java.util.TreeMap;

/**
 * description:
 * @author carrot
 */
public class BatchSignResponseMsg extends SlowPathMsg {

    private long number;

    private byte[] hash;

    private Map<ByteArrayWrapper, Signature> signatures;

    public BatchSignResponseMsg(long number, byte[] hash, Map<ByteArrayWrapper, Signature> signatures) {
        super(null);
        this.number = number;
        this.hash = hash;
        this.signatures = new TreeMap<>(signatures);
        this.rlpEncoded = rlpEncoded();
    }

    public BatchSignResponseMsg(byte[] encode) {
        super(encode);
    }

    public long getNumber() {
        return number;
    }

    public byte[] getHash() {
        return hash;
    }

    public Map<ByteArrayWrapper, Signature> getSignatures() {
        return signatures;
    }

    @Override
    protected byte[] rlpEncoded() {
        var encode = new byte[2 + signatures.size()][];

        encode[0] = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        encode[1] = RLP.encodeElement(this.hash);
        var i = 2;
        for (var entry: signatures.entrySet()) {
            encode[i] =
                    RLP.encodeList(
                            RLP.encodeElement(entry.getKey().getData()),
                            RLP.encodeElement(entry.getValue().getSig())
                    );
            i++;
        }
        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        var params = RLP.decode2(rlpEncoded);
        var block = (RLPList) params.get(0);
        this.number = ByteUtil.byteArrayToLong(block.get(0).getRLPData());
        this.hash = block.get(1).getRLPData() == null? new byte[0]: block.get(1).getRLPData();
        var signatures = new TreeMap<ByteArrayWrapper, Signature>();
        for (var i = 2; i < block.size(); i++) {
            var kvBytes = (RLPList) RLP.decode2(block.get(i).getRLPData()).get(0);
            signatures.put(new ByteArrayWrapper(kvBytes.get(0).getRLPData()), new Signature(kvBytes.get(1).getRLPData()));
        }
        this.signatures = signatures;
    }

    @Override
    public byte getCode() {
        return SlowPathCommand.BLOB_SIGN_RESP.getCode();
    }

    @Override
    public SlowPathCommand getCommand() {
        return SlowPathCommand.BLOB_SIGN_RESP;
    }

    @Override
    public String toString() {
        return "BlobSignResponseMsg{" +
                "number=" + number +
                ", hash=" + Hex.toHexString(hash) +
                ", signatures size=" + signatures.size() +
                '}';
    }

    public void doRelease() {
        hash = null;
        signatures.clear();
        signatures = null;
    }
}
