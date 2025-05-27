package org.ivy.settlement.core.chain.consensus.sequence.model.settlement;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.core.chain.consensus.sequence.model.RetrievalStatus;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.sequence.model.ConsensusCommand;
import org.ivy.settlement.core.chain.consensus.sequence.model.ConsensusMsg;
import org.ivy.settlement.ethereum.model.BeaconBlockRecord;

import java.util.ArrayList;
import java.util.List;

/**
 * description:
 * @author carrot
 */
public class SettlementChainRetrievalResponseMsg extends ConsensusMsg {

    static final int CODEC_OFFSET = 1;

    RetrievalStatus status;


    List<BeaconBlockRecord> crossChinBlocks;

    public SettlementChainRetrievalResponseMsg(byte[] encode) {
        super(encode);
    }

    public SettlementChainRetrievalResponseMsg(RetrievalStatus status, List<BeaconBlockRecord> crossChinBlocks) {
        super(null);
        this.crossChinBlocks = crossChinBlocks;
        this.status = status;
        this.rlpEncoded = rlpEncoded();
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[][] encode = new byte[CODEC_OFFSET + crossChinBlocks.size()][];
        encode[0] = RLP.encodeInt(status.ordinal());
        int i = CODEC_OFFSET;
        for (var block: crossChinBlocks) {
            encode[i] = block.getEncoded();
            i++;
        }
        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        var params = RLP.decode2(rlpEncoded);
        var retrievalRes = (RLPList) params.get(0);
        this.status = RetrievalStatus.convertFromOrdinal(ByteUtil.byteArrayToInt(retrievalRes.get(0).getRLPData()));
        var crossChainBlocks = new ArrayList<BeaconBlockRecord>();
        for (var i = CODEC_OFFSET; i < retrievalRes.size(); i++) {
            crossChainBlocks.add(new BeaconBlockRecord(retrievalRes.get(i).getRLPData()));
        }
        this.crossChinBlocks = crossChainBlocks;
    }

    public RetrievalStatus getStatus() {
        return status;
    }


    public List<BeaconBlockRecord> getCrossChinBlocks() {
        return crossChinBlocks;
    }

    public long getLastHeight() {
        if (this.crossChinBlocks.isEmpty()) {
            throw new RuntimeException("[CrossChainRetrievalResponseMsg] current crossChinBlocks is empty!");
        }
        return this.crossChinBlocks.get(this.crossChinBlocks.size() - 1).getNumber();
    }

    @Override
    public byte getCode() {
        return ConsensusCommand.CROSS_CHAIN_RETRIEVAL_RESP.getCode();
    }

    @Override
    public ConsensusCommand getCommand() {
        return ConsensusCommand.CROSS_CHAIN_RETRIEVAL_RESP;
    }


    @Override
    public String toString() {
        return "CrossChainRetrievalResponseMsg{" +
                "status=" + status +
                ", crossChinBlocks=" + crossChinBlocks +
                '}';
    }
}
