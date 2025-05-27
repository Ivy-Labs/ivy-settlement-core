
package org.ivy.settlement.core.chain.ledger.model.eth;

import org.apache.commons.lang3.StringUtils;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPItem;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.infrastructure.datasource.model.Persistable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.ivy.settlement.infrastructure.bytes.ByteUtil.EMPTY_BYTE_ARRAY;
import static org.ivy.settlement.infrastructure.bytes.ByteUtil.toHexString;

/**
 * The ethTransaction receipt is a tuple of three items
 * comprising the ethTransaction, together with the post-ethTransaction state,
 * and the cumulative gas used in the block containing the ethTransaction receipt
 * as of immediately after the ethTransaction has happened,
 */
public class EthTransactionReceipt extends Persistable {

    private EthTransaction ethTransaction;

    private byte[] gasUsed;
    private byte[] executionResult;
    private String error;

    public EthTransactionReceipt(byte[] rlp) {
        super(rlp);
    }

    public EthTransactionReceipt(EthTransaction ethTransaction, long gasUsed, byte[] executionResult, String error) {
        super(null);
        this.ethTransaction = ethTransaction;
        this.gasUsed = BigInteger.valueOf(gasUsed).toByteArray();
        this.executionResult = executionResult;
        this.error = error == null ? "" : error;
        this.rlpEncoded = rlpEncoded();
    }

    public byte[] getGasUsed() {
        return gasUsed;
    }

    public byte[] getExecutionResult() {
        return executionResult;
    }

    public boolean isValid() {
        return ByteUtil.byteArrayToLong(gasUsed) > 0;
    }

    public boolean isSuccessful() {
        return error.isEmpty();
    }

    public byte[] getHashContent() {
        return ByteUtil.merge(gasUsed, executionResult, error.getBytes());
    }

    public String getError() {
        return error;
    }

    public boolean hashError() {
        return !StringUtils.isEmpty(error);
    }

    public void setEthTransaction(EthTransaction ethTransaction) {
        this.ethTransaction = ethTransaction;
    }

    public EthTransaction getEthTransaction() {
        if (ethTransaction == null)
            throw new NullPointerException("EthTransaction is not initialized. Use TransactionInfo and BlockStore to setup EthTransaction instance");
        return ethTransaction;
    }


    @Override
    protected byte[] rlpEncoded() {


        return RLP.encodeList(
                ethTransaction.getEncoded(),
                RLP.encodeElement(gasUsed), RLP.encodeElement(executionResult),
                RLP.encodeElement(error.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    protected void rlpDecoded() {
        RLPList params = RLP.decode2(this.rlpEncoded);
        RLPList receipt = (RLPList) params.get(0);

        RLPList transactionRLP = (RLPList) receipt.get(0);
        RLPItem gasUsedRLP = (RLPItem) receipt.get(1);
        RLPItem result = (RLPItem) receipt.get(2);

        ethTransaction = new EthTransaction(transactionRLP.getRLPData());
        gasUsed = gasUsedRLP.getRLPData();
        executionResult = (executionResult = result.getRLPData()) == null ? EMPTY_BYTE_ARRAY : executionResult;

        if (receipt.size() > 4) {
            byte[] errBytes = receipt.get(4).getRLPData();
            error = errBytes != null ? new String(errBytes, StandardCharsets.UTF_8) : "";
        }
    }

    @Override
    public String toString() {

        return "EthTransactionReceipt[" +
                "\n  , hash=" + toHexString(this.ethTransaction.getHash()) +
                "\n  , gasUsed=" + toHexString(gasUsed) +
                "\n  , error=" + error +
                "\n  , executionResult=" + toHexString(executionResult) +
                ']';
    }
}

