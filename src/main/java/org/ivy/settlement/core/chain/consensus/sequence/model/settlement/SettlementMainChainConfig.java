package org.ivy.settlement.core.chain.consensus.sequence.model.settlement;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;

import java.math.BigInteger;
import java.util.Objects;

/**
 * description:
 * @author carrot
 * @since 2024-02-29
 */
public class SettlementMainChainConfig extends RLPModel {

    long mainChainGenesisHeight = 30;

    long epochChangeInterval = 100;

    private int emptyBlockTimes;

    private int accumulateSize;

    private int onChainTimeoutInterval;

    public SettlementMainChainConfig(long mainChainGenesisHeight, long epochChangeInterval, int emptyBlockTimes, int accumulateSize, int onChainTimeoutInterval) {
        super(null);
        this.mainChainGenesisHeight = mainChainGenesisHeight;
        this.epochChangeInterval = epochChangeInterval;
        this.emptyBlockTimes = emptyBlockTimes;
        this.accumulateSize = accumulateSize;
        this.onChainTimeoutInterval = onChainTimeoutInterval;
        this.rlpEncoded = rlpEncoded();
    }

    public SettlementMainChainConfig(byte[] rlpEncoded) {
        super(rlpEncoded);
    }


    public long getMainChainGenesisHeight() {
        return mainChainGenesisHeight;
    }

    public long calculateEpoch(long height) {
        return Math.floorDiv(height - mainChainGenesisHeight + 1, epochChangeInterval) + 1;
    }

    public long calculateEpochBoundaryHeight(long height) {
        if (isEpochBoundary(height)) return height;
        return mainChainGenesisHeight + (Math.floorDiv(height - mainChainGenesisHeight + 1, epochChangeInterval) + 1) * epochChangeInterval - 1;
    }

    public boolean isEpochBoundary(long height) {
        return (height - mainChainGenesisHeight + 1) % epochChangeInterval == 0;
    }

    public int getEmptyBlockTimes() {
        return emptyBlockTimes;
    }

    public int getAccumulateSize() {
        return accumulateSize;
    }

    public int getOnChainTimeoutInterval() {
        return onChainTimeoutInterval;
    }

    @Override
    protected byte[] rlpEncoded() {
        var mainChainGenesisHeight = RLP.encodeBigInteger(BigInteger.valueOf(this.mainChainGenesisHeight));
        var epochChangeInterval = RLP.encodeBigInteger(BigInteger.valueOf(this.epochChangeInterval));
        var emptyBlockTimes = RLP.encodeInt(this.emptyBlockTimes);
        var accumulateSize = RLP.encodeInt(this.accumulateSize);
        var onChainTimeoutInterval = RLP.encodeInt(this.onChainTimeoutInterval);
        return RLP.encodeList(mainChainGenesisHeight, epochChangeInterval, emptyBlockTimes, accumulateSize, onChainTimeoutInterval);
    }

    @Override
    protected void rlpDecoded() {
        var rlpEpochState = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.mainChainGenesisHeight = ByteUtil.byteArrayToLong(rlpEpochState.get(0).getRLPData());
        this.epochChangeInterval = ByteUtil.byteArrayToLong(rlpEpochState.get(1).getRLPData());
        this.emptyBlockTimes = ByteUtil.byteArrayToInt(rlpEpochState.get(2).getRLPData());
        this.accumulateSize = ByteUtil.byteArrayToInt(rlpEpochState.get(3).getRLPData());
        this.onChainTimeoutInterval = ByteUtil.byteArrayToInt(rlpEpochState.get(4).getRLPData());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SettlementMainChainConfig that = (SettlementMainChainConfig) o;
        return mainChainGenesisHeight == that.mainChainGenesisHeight && epochChangeInterval == that.epochChangeInterval && emptyBlockTimes == that.emptyBlockTimes && accumulateSize == that.accumulateSize && onChainTimeoutInterval == that.onChainTimeoutInterval;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mainChainGenesisHeight, epochChangeInterval, emptyBlockTimes, accumulateSize, onChainTimeoutInterval);
    }

    public static void main(String[] args) {
        var conf = new SettlementMainChainConfig(30, 100, 2, 5, 100);
        System.out.println(conf.calculateEpoch(128));//1
        System.out.println(conf.calculateEpoch(129));//2
        System.out.println(conf.calculateEpoch(130));//2
        System.out.println(conf.calculateEpoch(229));//3
        System.out.println(conf.calculateEpoch(328));//3
        System.out.println(conf.calculateEpoch(329));//4
        System.out.println(conf.calculateEpoch(330));//4
        System.out.println("==============================================");

        System.out.println(conf.calculateEpochBoundaryHeight(289));
        System.out.println(conf.calculateEpochBoundaryHeight(189));
        System.out.println(conf.calculateEpochBoundaryHeight(89));
        System.out.println(conf.calculateEpochBoundaryHeight(129));
        System.out.println(conf.calculateEpochBoundaryHeight(128));
    }
}
