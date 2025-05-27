package org.ivy.settlement.core.chain.ledger.model.genesis;

/**
 * Created by Anton on 03.03.2017.
 */
public class GenesisConfig {
    public Integer homesteadBlock;
    public Integer daoForkBlock;
    public Integer eip150Block;
    public Integer eip155Block;
    public boolean daoForkSupport;
    public Integer eip158Block;
    public Integer byzantiumBlock;
    public Integer constantinopleBlock;
    public Integer petersburgBlock;
    public Integer chainId;

    // EthereumJ private options

    public static class HashValidator {
        public long number;
        public String hash;
    }

}
