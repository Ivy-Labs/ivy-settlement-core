package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.rlp.RLP;

/**
 * description:
 * @author carrot
 */
public class LatestLedgerInfoRequestMsg extends ConsensusMsg {

    final static byte[] PLACEHODER = RLP.encodeList(new byte[]{1});

    public LatestLedgerInfoRequestMsg(byte[] encode) {
        super(encode);
    }

    public LatestLedgerInfoRequestMsg() {
        super(null);
        this.rlpEncoded = PLACEHODER;
    }

    protected byte[] rlpEncoded() {
        return PLACEHODER;
    }

    @Override
    protected void rlpDecoded() {
        // not need
    }

    @Override
    public byte getCode() {
        return ConsensusCommand.LATEST_LEDGER_REQ.getCode();
    }

    @Override
    public ConsensusCommand getCommand() {
        return ConsensusCommand.LATEST_LEDGER_REQ;
    }

    @Override
    public String toString() {
        return "LatestLedgerInfoRequestMsg{" +
                "epoch=" + epoch +
                ", nodeId=" + peerId +
                '}';
    }
}


