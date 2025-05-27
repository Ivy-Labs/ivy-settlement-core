package org.ivy.settlement.core.chain.ledger.model.event.ca;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.event.GlobalEventCommand;

/**
 * description:
 * @author carrot
 */
public class VoteFilterCandidateEvent extends VoteEvent {

    CaContractCode caContractCode;

    public VoteFilterCandidateEvent(byte[] encode) {
        super(encode);
    }

    public VoteFilterCandidateEvent(int voteType, int processType, byte[] proposalId, CaContractCode caContractCode) {
        super(null);
        this.voteType = voteType;
        this.processType = processType;
        this.proposalId = proposalId;
        this.caContractCode = caContractCode;
        this.rlpEncoded = rlpEncoded();
    }

    @Override
    public GlobalEventCommand getEventCommand() {
        return GlobalEventCommand.VOTE_FILTER_CANDIDATE;
    }


    public CaContractCode getCaContractCode() {
        return caContractCode;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] voteType = RLP.encodeInt(this.voteType);
        byte[] processType = RLP.encodeInt(this.processType);
        byte[] proposalId = RLP.encodeElement(this.proposalId);
        byte[] voteCommitteeAddr = this.caContractCode.getEncoded();
        return RLP.encodeList(voteType, processType, proposalId, voteCommitteeAddr);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpInfo = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.voteType = ByteUtil.byteArrayToInt(rlpInfo.get(0).getRLPData());
        this.processType = ByteUtil.byteArrayToInt(rlpInfo.get(1).getRLPData());
        this.proposalId = ByteUtil.copyFrom(rlpInfo.get(2).getRLPData());
        this.caContractCode = new CaContractCode(ByteUtil.copyFrom(rlpInfo.get(3).getRLPData()));
    }
}
