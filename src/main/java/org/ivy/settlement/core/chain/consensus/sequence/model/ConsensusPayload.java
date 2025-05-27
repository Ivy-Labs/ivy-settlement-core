package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.ivy.settlement.core.chain.ledger.model.eth.EthTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * description:
 * @author carrot
 */
public class ConsensusPayload extends RLPModel {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    private static final int PRE_COUNT = 2;


    SettlementChainOffsets settlementChainOffsets;

    // for speed
    volatile EthTransaction[] ethTransactions;

    public ConsensusPayload(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    public ConsensusPayload(SettlementChainOffsets settlementChainOffsets, EthTransaction[] ethTransactions) {
        super(null);
        assert ethTransactions != null;
        this.settlementChainOffsets = settlementChainOffsets;
        this.ethTransactions = ethTransactions;
        this.rlpEncoded = rlpEncoded();
    }

    public EthTransaction[] getEthTransactions() {
        return ethTransactions;
    }



    public boolean isEmpty() {
        return ethTransactions.length == 0;
    }

    public SettlementChainOffsets getCrossChainOffsets() {
        return this.settlementChainOffsets;
    }

    @Override
    protected byte[] rlpEncoded() {
        int txsSize = this.ethTransactions.length;
        int totalSize = 1 + txsSize;

        byte[][] encode = new byte[totalSize][];
        encode[0] = this.settlementChainOffsets.getEncoded();

        var offset = 1;
        for (var i = 0; i < this.ethTransactions.length; i++) {
            encode[i + offset] = this.ethTransactions[i].getEncoded();
        }

        return RLP.encodeList(encode);
    }


    @Override
    protected void rlpDecoded() {
        RLPList payload = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.settlementChainOffsets = new SettlementChainOffsets(payload.get(0).getRLPData());

        var offset =  1;
        var txSize = payload.size() - 1;
        this.ethTransactions = new EthTransaction[txSize];
        for (var i = 0; i < txSize; i++) {
            this.ethTransactions[i] = new EthTransaction(payload.get(offset + i).getRLPData());
        }
    }


    @Override
    public String toString() {
        return "{" + settlementChainOffsets + "}";
    }

    public void clear() {
        this.ethTransactions = null;
    }
}


