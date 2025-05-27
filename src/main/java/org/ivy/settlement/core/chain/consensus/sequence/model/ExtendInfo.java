package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementMainChainConfig;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;

import java.util.Objects;

/**
 * description:
 * @author carrot
 */
public class ExtendInfo extends RLPModel {

    SettlementMainChainConfig settlementMainChainConfig;

    public ExtendInfo(SettlementMainChainConfig settlementMainChainConfig) {
        super(null);
        this.settlementMainChainConfig = settlementMainChainConfig;
        this.rlpEncoded = rlpEncoded();
    }

    public ExtendInfo(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    public long calculateEpoch(long height) {
        return this.settlementMainChainConfig.calculateEpoch(height);
    }

    public long calculateEpochBoundaryHeight(long height) {
        return this.settlementMainChainConfig.calculateEpochBoundaryHeight(height);
    }

    public boolean isEpochBoundary(long height) {
        return this.settlementMainChainConfig.isEpochBoundary(height);
    }

    public long getMainChainGenesisHeight() {
        return this.settlementMainChainConfig.getMainChainGenesisHeight();
    }

    public int getEmptyBlockTimes() {
        return this.settlementMainChainConfig.getEmptyBlockTimes();
    }

    public int getAccumulateSize() {
        return this.settlementMainChainConfig.getAccumulateSize();
    }

    public int getOnChainTimeoutInterval() {
        return this.settlementMainChainConfig.getAccumulateSize();
    }

    @Override
    protected byte[] rlpEncoded() {
        var chainConfigEncoded = this.settlementMainChainConfig.getEncoded();
        return RLP.encodeList(chainConfigEncoded);
    }

    @Override
    protected void rlpDecoded() {
        var decodeData = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.settlementMainChainConfig = new SettlementMainChainConfig(decodeData.get(0).getRLPData());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ExtendInfo that = (ExtendInfo) o;
        return Objects.equals(settlementMainChainConfig, that.settlementMainChainConfig);
    }

    @Override
    public int hashCode() {
        return Objects.hash(settlementMainChainConfig);
    }
}
