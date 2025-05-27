package org.ivy.settlement.core.chain.consensus.sequence.model.settlement;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;


/**
 * description:
 * @author carrot
 * @since 2024-03-26
 */
public class SettlementChainBlock extends RLPModel {

    //blockHeader's Serializable

    static final int CODEC_OFFSET = 3;

    long blockHeight;

    byte[] blockHash;

    byte[] blockHeader;

    //all receipts
    List<byte[]> receipts;

    public SettlementChainBlock(long blockHeight, byte[] blockHash, byte[] blockHeader, List<byte[]> receipts) {
        super(null);
        this.blockHeight = blockHeight;
        this.blockHash = blockHash;
        this.blockHeader = blockHeader;
        this.receipts = receipts;
        this.rlpEncoded = rlpEncoded();
    }

    public SettlementChainBlock(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    @Override
    protected byte[] rlpEncoded() {
        var encode = new byte[CODEC_OFFSET + receipts.size()][];
        encode[0] = RLP.encodeBigInteger(BigInteger.valueOf(this.blockHeight));;
        encode[1] = RLP.encodeElement(blockHash);
        encode[2] = RLP.encodeElement(blockHeader);
        var i = CODEC_OFFSET;
        for (var receipt: receipts) {
            encode[i] = RLP.encodeElement(receipt);
            i++;
        }
        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        var params = RLP.decode2(rlpEncoded);
        var blockDecode = (RLPList) params.get(0);
        this.blockHeight = ByteUtil.byteArrayToLong(blockDecode.get(0).getRLPData());
        this.blockHash =  blockDecode.get(1).getRLPData();
        this.blockHeader =  blockDecode.get(2).getRLPData();
        var receipts = new ArrayList<byte[]>();
        for (var i = CODEC_OFFSET; i < blockDecode.size(); i++) {
            receipts.add(blockDecode.get(i).getRLPData());
        }
        this.receipts = receipts;
    }


    public long getBlockHeight() {
        return blockHeight;
    }

    public byte[] getBlockHash() {
        return blockHash;
    }

    public byte[] getBlockHeader() {
        return blockHeader;
    }

    public List<byte[]> getReceipts() {
        return receipts;
    }

    @Override
    public String toString() {
        return "ccb{" +
                "blockHeight=" + blockHeight +
                ", blockHash=" + Hex.toHexString(blockHash) +
                ", receipts size=" + receipts.size() +
                '}';
    }
}
