package org.ivy.settlement.core.chain.ledger.model;

import org.ivy.settlement.ethereum.model.settlement.FastPathBlock;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.crypto.HashUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.ethereum.model.settlement.SettlementBlockInfos;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.infrastructure.datasource.model.Persistable;
import org.spongycastle.util.encoders.Hex;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.generated.Int64;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Map;

/**
 * description:
 * @author carrot
 */
public class Block extends Persistable {

    //event id, after execute transaction ,it become include receipts root hash
    private byte[] hash;

    private byte[] parentHash;

    private long epoch;

    //递增
    private long number;

    //空快不变
    private long height;

    /* A scalar value equal to the reasonable output of Unix's time()
     * at this block's inception */
    private long timestamp;

    SettlementChainOffsets settlementChainOffsets;

    SettlementBlockInfos settlementBlockInfos;

    //Transient
    BlockSign blockSign;

    private byte[] contentRoot;
    private byte[] extendRoot;


    public static Block buildWithoutDecode(byte[] rawData) {
        var block = new Block(null);
        block.rlpEncoded = rawData;
        return block;
    }

    public Block(byte[] rawData) {
        super(rawData);
    }

    public Block(byte[] parentHash, long epoch, long number, long height,
                 long timestamp,
                 SettlementChainOffsets settlementChainOffsets,
                 SettlementBlockInfos settlementBlockInfos) {
        super(null);

        this.parentHash = parentHash;
        this.epoch = epoch;
        this.number = number;
        this.height = height;
        this.timestamp = timestamp;
        this.settlementChainOffsets = settlementChainOffsets;
        this.settlementBlockInfos = settlementBlockInfos;
        this.rlpEncoded = rlpEncoded();
        this.contentRoot = calContentRoot();
        this.extendRoot = calExtendRoot();
        this.hash = generateHash();

    }

    private byte[] generateHash() {
        return HashUtil.sha3Dynamic(parentHash, Numeric.hexStringToByteArray(TypeEncoder.encodePacked(new Int64(this.height))), this.contentRoot, this.extendRoot);
    }

    private byte[] calExtendRoot() {
        return new byte[0];
    }

    private byte[] calContentRoot() {
        return new byte[0];
    }

    public byte[] getHash() {
        return hash;
    }

    public byte[] getEventId() {
        return hash;
    }

    public long getEpoch() {
        return epoch;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public long getNumber() {
        return number;
    }

    public long getHeight() {
        return height;
    }

    public SettlementChainOffsets getCrossChainOffsets() {
        return settlementChainOffsets;
    }

    public SettlementBlockInfos getSettlementBlockInfos() {
        return settlementBlockInfos;
    }


    public void recordCommitSign(Map<ByteArrayWrapper, Signature> signatures) {
        this.blockSign = new BlockSign(this.epoch, this.number, this.hash, signatures);
    }

    public BlockSign getBlockSign() {
        return blockSign;
    }

    protected byte[] rlpEncoded() {
        byte[][] encode = new byte[7][];
        encode[0] = RLP.encodeElement(this.parentHash);
        encode[1] = RLP.encodeBigInteger(BigInteger.valueOf(this.epoch));
        encode[2] = RLP.encodeBigInteger(BigInteger.valueOf(this.number));
        encode[3] = RLP.encodeBigInteger(BigInteger.valueOf(this.height));
        encode[4] = RLP.encodeBigInteger(BigInteger.valueOf(this.timestamp));
        encode[5] = this.settlementChainOffsets.getEncoded();
        encode[6] = this.settlementBlockInfos.getEncoded();
        return RLP.encodeList(encode);

    }

    protected void rlpDecoded() {
        RLPList params = RLP.decode2(rlpEncoded);
        RLPList block = (RLPList) params.get(0);
        this.parentHash = block.get(0).getRLPData();
        this.epoch = ByteUtil.byteArrayToLong(block.get(1).getRLPData());
        this.number = ByteUtil.byteArrayToLong(block.get(2).getRLPData());
        this.height = ByteUtil.byteArrayToLong(block.get(3).getRLPData());
        this.timestamp = ByteUtil.byteArrayToLong(block.get(4).getRLPData());
        this.settlementChainOffsets = new SettlementChainOffsets(block.get(5).getRLPData());
        this.settlementBlockInfos = new SettlementBlockInfos(block.get(6).getRLPData());
        this.hash = HashUtil.sha3(rlpEncoded);
    }

    public FastPathBlock exportFastPathBlock() {
        return new FastPathBlock(this.parentHash, this.height, this.settlementBlockInfos, this.extendRoot, this.blockSign.getSignatures());
    }

    @Override
    public String toString() {
        return "Block{" +
                "hash=" + Hex.toHexString(hash) +
                ", eventId=" + Hex.toHexString(parentHash) +
                ", epoch=" + epoch +
                ", number=" + number +
                ", height=" + height +
                ", timestamp=" + timestamp +
                ", crossChainOffsets=" + settlementChainOffsets +
                ", settlementBlockInfos=" + settlementBlockInfos +
                '}';
    }
}
