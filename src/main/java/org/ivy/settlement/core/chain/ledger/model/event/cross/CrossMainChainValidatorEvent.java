package org.ivy.settlement.core.chain.ledger.model.event.cross;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.event.CommandEvent;
import org.ivy.settlement.core.chain.ledger.model.event.GlobalEventCommand;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static org.ivy.settlement.core.chain.ledger.model.event.GlobalEventCommand.CROSS_MAIN_CHAIN_EVENT;

/**
 * description:
 * @author carrot
 */
public class CrossMainChainValidatorEvent extends CommandEvent {

    public static final byte[] CA_NAME = "ivy_cross".getBytes(StandardCharsets.UTF_8);


    int processType;

    int slotIndex;

    byte[] id;

    long consensusVotingPower;

    public CrossMainChainValidatorEvent(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    @Override
    public GlobalEventCommand getEventCommand() {
        return CROSS_MAIN_CHAIN_EVENT;
    }

    public CrossMainChainValidatorEvent(int processType, int slotIndex, byte[] id, long consensusVotingPower) {
        super(null);
        this.processType = processType;
        this.slotIndex = slotIndex;
        this.id = id;
        this.consensusVotingPower = consensusVotingPower;
        this.rlpEncoded = rlpEncoded();
    }

    public int getProcessType() {
        return processType;
    }

    public byte[] getId() {
        return id;
    }

    public long getConsensusVotingPower() {
        return consensusVotingPower;
    }

    @Override
    protected byte[] rlpEncoded() {
        var voteType = RLP.encodeInt(this.processType);
        var id = RLP.encodeElement(this.id);
        var consensusVotingPower = RLP.encodeBigInteger(BigInteger.valueOf(this.consensusVotingPower));
        return RLP.encodeList(voteType, id, consensusVotingPower);
    }

    @Override
    protected void rlpDecoded() {
        var rlpInfo = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.processType = ByteUtil.byteArrayToInt(rlpInfo.get(0).getRLPData());
        this.id = rlpInfo.get(1).getRLPData();
        this.consensusVotingPower = ByteUtil.byteArrayToLong(rlpInfo.get(2).getRLPData());
    }
}
