package org.ivy.settlement.core.chain.ledger.model;

import org.ivy.settlement.ethereum.model.settlement.FastPathBlocks;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.crypto.HashUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.infrastructure.datasource.model.Persistable;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * description:
 * @author carrot
 */
public class SettlementBatch extends Persistable {

    private final static int PKS_CODEC_OFFSET = 6;

    //event id, after execute transaction ,it become include receipts root hash
    private byte[] hash;

    private long bornEpoch;

    private long ethEpoch;

    private long number;

    private long endBlockNumber;

    /* the last block timestamp */
    private long timestamp;

    FastPathBlocks fastPathBlocks;

    List<ValidatorPublicKeyInfo> validatorPublicKeyInfos;

    //Transient
    BatchSign batchSign;

    public static SettlementBatch buildWithoutDecode(byte[] rawData) {
        var block = new SettlementBatch(null);
        block.rlpEncoded = rawData;
        return block;
    }

    public SettlementBatch(byte[] rawData) {
        super(rawData);
    }

    public SettlementBatch(long bornEpoch, long ethEpoch, long number, long endBlockNumber,
                           long timestamp,
                           FastPathBlocks fastPathBlocks, List<ValidatorPublicKeyInfo> validatorPublicKeyInfos) {
        super(null);
        this.bornEpoch = bornEpoch;
        this.ethEpoch = ethEpoch;
        this.number = number;
        this.endBlockNumber = endBlockNumber;
        this.timestamp = timestamp;
        this.fastPathBlocks = fastPathBlocks;
        this.validatorPublicKeyInfos = validatorPublicKeyInfos;
        this.rlpEncoded = rlpEncoded();
        this.hash = HashUtil.sha3(this.rlpEncoded);
    }

    public byte[] getHash() {
        return hash;
    }


    public long getBornEpoch() {
        return bornEpoch;
    }


    private void resetOnChainEpoch(long onChainEpoch) {
        this.ethEpoch = onChainEpoch;
        this.rlpEncoded = rlpEncoded();
        this.hash = HashUtil.sha3(rlpEncoded);
    }

    public long getEthEpoch() {
        return ethEpoch;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public long getNumber() {
        return number;
    }

    public long getEndBlockNumber() {
        return endBlockNumber;
    }

    public FastPathBlocks getFastPathBlocks() {
        return this.fastPathBlocks;
    }

    public List<ValidatorPublicKeyInfo> getValidatorPublicKeyInfos() {
        return validatorPublicKeyInfos;
    }

    public void recordCommitSign(Map<ByteArrayWrapper, Signature> signatures) {
        this.batchSign = new BatchSign(this.bornEpoch, this.endBlockNumber, this.hash, signatures);
    }

    public BatchSign getBlobSign() {
        return batchSign;
    }

    protected byte[] rlpEncoded() {
        byte[][] encode = new byte[PKS_CODEC_OFFSET + this.validatorPublicKeyInfos.size()][];
        encode[0] = RLP.encodeBigInteger(BigInteger.valueOf(this.bornEpoch));
        encode[1] = RLP.encodeBigInteger(BigInteger.valueOf(this.ethEpoch));
        encode[2] = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        encode[3] = RLP.encodeBigInteger(BigInteger.valueOf(this.endBlockNumber));
        encode[4] = RLP.encodeBigInteger(BigInteger.valueOf(this.timestamp));
        encode[5] = this.fastPathBlocks.getEncoded();
        for (var i = 0; i < this.validatorPublicKeyInfos.size(); i++) {
            encode[i + PKS_CODEC_OFFSET] = this.validatorPublicKeyInfos.get(i).rlpEncoded();
        }
        return RLP.encodeList(encode);
    }

    protected void rlpDecoded() {
        RLPList params = RLP.decode2(rlpEncoded);
        RLPList block = (RLPList) params.get(0);
        this.bornEpoch = ByteUtil.byteArrayToLong(block.get(0).getRLPData());
        this.ethEpoch = ByteUtil.byteArrayToLong(block.get(1).getRLPData());
        this.number = ByteUtil.byteArrayToLong(block.get(2).getRLPData());
        this.endBlockNumber = ByteUtil.byteArrayToLong(block.get(3).getRLPData());
        this.timestamp = ByteUtil.byteArrayToLong(block.get(4).getRLPData());
        this.fastPathBlocks = new FastPathBlocks(block.get(5).getRLPData());
        this.validatorPublicKeyInfos = new ArrayList<>(block.size() - PKS_CODEC_OFFSET);
        for (var i = PKS_CODEC_OFFSET; i < block.size(); i++) {
            this.validatorPublicKeyInfos.add(new ValidatorPublicKeyInfo(block.get(i).getRLPData()));
        }
        this.hash = HashUtil.sha3(rlpEncoded);
    }


    @Override
    public String toString() {
        return "SettlementBlob{" +
                "hash=" + Hex.toHexString(hash) +
                ", bornEpoch=" + bornEpoch +
                ", onChainEpoch=" + ethEpoch +
                ", number=" + endBlockNumber +
                ", timestamp=" + timestamp +
                ", fastPathBlocks=" + fastPathBlocks +
                ", validatorPublicKeyInfos=" + validatorPublicKeyInfos +
                '}';
    }
}
