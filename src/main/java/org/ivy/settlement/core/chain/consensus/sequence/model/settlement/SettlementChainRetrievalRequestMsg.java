package org.ivy.settlement.core.chain.consensus.sequence.model.settlement;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.sequence.model.ConsensusCommand;
import org.ivy.settlement.core.chain.consensus.sequence.model.ConsensusMsg;

import java.math.BigInteger;

/**
 * description:
 * @author carrot
 */
public class SettlementChainRetrievalRequestMsg extends ConsensusMsg {

    int chain;

    long startHeight;

    public SettlementChainRetrievalRequestMsg(byte[] encode) {
        super(encode);
    }

    public SettlementChainRetrievalRequestMsg(long startHeight) {
        super(null);
        this.startHeight = startHeight;
        this.rlpEncoded = rlpEncoded();
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] startHeight = RLP.encodeBigInteger(BigInteger.valueOf(this.startHeight));
        return RLP.encodeList(startHeight);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.startHeight = ByteUtil.byteArrayToLong(rlpDecode.get(0).getRLPData());
    }

    public int getChain() {
        return SettlementChainOffsets.MAIN_CHAIN_CODE;
    }

    public long getStartHeight() {
        return startHeight;
    }

    @Override
    public byte getCode() {
        return ConsensusCommand.CROSS_CHAIN_RETRIEVAL_REQ.getCode();
    }

    @Override
    public ConsensusCommand getCommand() {
        return ConsensusCommand.CROSS_CHAIN_RETRIEVAL_REQ;
    }
}
