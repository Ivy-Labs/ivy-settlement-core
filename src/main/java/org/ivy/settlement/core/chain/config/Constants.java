package org.ivy.settlement.core.chain.config;


import java.math.BigInteger;

/**
 * Describes different constants specific for a blockchain
 *
 * Created by Anton Nashatyrev on 25.02.2016.
 */
public class Constants {

    public static final byte[] EMPTY_HASH_BYTES = new byte[32];


    private static final BigInteger SECP256K1N = new BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16);

//    private static final int LONGEST_CHAIN = 192;

    public BigInteger getInitialNonce() {
        return BigInteger.ZERO;
    }

    public int getMAX_CONTRACT_SZIE() { return Integer.MAX_VALUE; }

    /**
     * Introduced in the Homestead release
     */
    public boolean createEmptyContractOnOOG() {
        return true;
    }

    /**
     * New DELEGATECALL opcode introduced in the Homestead release. Before Homestead this opcode should generate
     * exception
     */
    public boolean hasDelegateCallOpcode() {return false; }

    /**
     * Introduced in the Homestead release
     */
    public static BigInteger getSECP256K1N() {
        return SECP256K1N;
    }

//    public static int getLONGEST_CHAIN() {
//        return LONGEST_CHAIN;
//    }
}
