package org.ivy.settlement.core.chain.ledger.model;


import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.bytes.FastByteComparisons;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.infrastructure.datasource.model.Persistable;

import java.math.BigInteger;

import static org.ivy.settlement.infrastructure.bytes.FastByteComparisons.equal;
import static org.ivy.settlement.infrastructure.crypto.HashUtil.EMPTY_DATA_HASH;
import static org.ivy.settlement.infrastructure.crypto.HashUtil.EMPTY_TRIE_HASH;
import static org.ivy.settlement.infrastructure.bytes.ByteUtil.toHexString;



public class AccountState extends Persistable {
    /* A value equal to the number of transactions sent
     * from this address, or, in the case of contract accounts,
     * the number of contract-creations made by this account */
    private BigInteger nonce;

    /* A scalar value equal to the number of Wei owned by this address */
    private BigInteger balance;

    /* A 256-bit getHash of the root node of a trie structure
     * that encodes the storage contents of the contract,
     * itself a simple mapping between byte arrays of size 32.
     * The getHash is formally denoted σ[a] s .
     *
     * Since I typically wish to refer not to the trie’s root getHash
     * but to the underlying set of key/value pairs stored within,
     * I define a convenient equivalence TRIE (σ[a] s ) ≡ σ[a] s .
     * It shall be understood that σ[a] s is not a ‘physical’ member
     * of the account and does not contribute to its later serialisation */
    private byte[] stateRoot;

    /* The getHash of the EVM code of this contract—this is the code
     * that gets executed should this address receive a message call;
     * it is immutable and thus, unlike all other fields, cannot be changed
     * after construction. All such code fragments are contained in
     * the state database under their corresponding hashes for later
     * retrieval */
    private byte[] codeHash;


    public AccountState(BigInteger nonce, BigInteger balance) {
        this(nonce, balance, EMPTY_TRIE_HASH, EMPTY_DATA_HASH);
    }

    public AccountState(BigInteger nonce, BigInteger balance, byte[] stateRoot, byte[] codeHash) {
        super(null);
        this.nonce = nonce;
        this.balance = balance;
        this.stateRoot = stateRoot == EMPTY_TRIE_HASH || equal(stateRoot, EMPTY_TRIE_HASH) ? EMPTY_TRIE_HASH : stateRoot;
        this.codeHash = codeHash == EMPTY_DATA_HASH || equal(codeHash, EMPTY_DATA_HASH) ? EMPTY_DATA_HASH : codeHash;
        this.rlpEncoded = rlpEncoded();
    }

    public AccountState(byte[] rlpData) {
        super(rlpData);
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] nonce = RLP.encodeBigInteger(this.nonce);
        byte[] balance = RLP.encodeBigInteger(this.balance);
        byte[] stateRoot = RLP.encodeElement(this.stateRoot);
        byte[] codeHash = RLP.encodeElement(this.codeHash);
        return RLP.encodeList(nonce, balance, stateRoot, codeHash);
    }

    @Override
    protected void rlpDecoded() {
        RLPList items = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.nonce = ByteUtil.bytesToBigInteger(items.get(0).getRLPData());
        this.balance = ByteUtil.bytesToBigInteger(items.get(1).getRLPData());
        this.stateRoot = items.get(2).getRLPData();
        this.codeHash = items.get(3).getRLPData();
    }

    public BigInteger getNonce() {
        return nonce;
    }

    public AccountState withNonce(BigInteger nonce) {
        return new AccountState(nonce, balance, stateRoot, codeHash);
    }

    public byte[] getStateRoot() {
        return stateRoot;
    }

    public AccountState withIncrementedNonce() {
        return new AccountState(nonce.add(BigInteger.ONE), balance, stateRoot, codeHash);
    }

    public byte[] getCodeHash() {
        return codeHash;
    }

    public AccountState withCodeHash(byte[] codeHash) {
        return new AccountState(nonce, balance, stateRoot, codeHash);
    }

    public BigInteger getBalance() {
        return balance;
    }

    public AccountState withBalanceIncrement(BigInteger value) {
        return new AccountState(nonce, balance.add(value), stateRoot, codeHash);
    }


    public boolean isEmpty() {
        return FastByteComparisons.equal(codeHash, EMPTY_DATA_HASH) &&
                BigInteger.ZERO.equals(balance) &&
                BigInteger.ZERO.equals(nonce);
    }


    public String toString() {
        String ret = "  Nonce: " + this.getNonce().toString() + "\n" +
                "  Balance: " + getBalance() + "\n" +
                "  State Root: " + toHexString(this.getStateRoot()) + "\n" +
                "  Code Hash: " + toHexString(this.getCodeHash());
        return ret;
    }
}
