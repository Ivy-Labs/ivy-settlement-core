package org.ivy.settlement.core.chain.ledger.model;

import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.ivy.settlement.ethereum.model.settlement.SettlementBlockInfos;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.ivy.settlement.infrastructure.crypto.HashUtil.EMPTY_DATA_HASH;

public class Genesis extends Block {

    private Map<ByteArrayWrapper, PremineAccount> premine = new HashMap<>();


    private short maxShardingNum;
    private short shardingNum;

    public Genesis(long number,
                   long timestamp, short maxShardingNum, short shardingNum, long mainChainStartNumber, Map<String, Long> followerChainOffsets) {
        super(EMPTY_DATA_HASH, 0,
                number, number, timestamp, SettlementChainOffsets.genesisBuild(mainChainStartNumber, followerChainOffsets), new SettlementBlockInfos(new TreeMap<>()));
        this.maxShardingNum = maxShardingNum;
        this.shardingNum = shardingNum;
    }


    public short getShardingNum() {
        return shardingNum;
    }

    public Map<ByteArrayWrapper, PremineAccount> getPremine() {
        return premine;
    }

    public void setPremine(Map<ByteArrayWrapper, PremineAccount> premine) {
        this.premine = premine;
    }

    public void addPremine(ByteArrayWrapper address, AccountState accountState) {
        premine.put(address, new PremineAccount(accountState));
    }

    public Block asBlock() {
        return new Block(super.getEventId(), 1, super.getNumber(), super.getNumber(), super.getTimestamp(), super.getCrossChainOffsets(), super.getSettlementBlockInfos());
    }

    /**
     * Used to keep addition fields.
     */
    public static class PremineAccount {

        public byte[] code;

        public AccountState accountState;

        public byte[] getStateRoot() {
            return accountState.getStateRoot();
        }

        public PremineAccount(AccountState accountState) {
            this.accountState = accountState;
        }

        public PremineAccount() {
        }
    }
}
