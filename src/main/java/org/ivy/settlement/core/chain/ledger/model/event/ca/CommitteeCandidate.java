package org.ivy.settlement.core.chain.ledger.model.event.ca;

import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Objects;

/**
 * description:
 * @author carrot
 */
public class CommitteeCandidate extends RLPModel {

    int processType;

    byte[] proposalId;

    ByteArrayWrapper address;

    public CommitteeCandidate(byte[] encode) {
        super(encode);
    }

    public CommitteeCandidate(int processType, byte[] proposalId, ByteArrayWrapper address) {
        super(null);
        this.processType = processType;
        this.proposalId = proposalId;
        this.address = address;
        this.rlpEncoded = rlpEncoded();
    }

    public int getProcessType() {
        return processType;
    }

    public byte[] getProposalId() {
        return proposalId;
    }

    public ByteArrayWrapper getAddress() {
        return address;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] voteType = RLP.encodeInt(this.processType);
        byte[] proposalId = RLP.encodeElement(this.proposalId);
        byte[] address = RLP.encodeElement(this.address.getData());
        return RLP.encodeList(voteType, proposalId, address);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpInfo = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.processType = ByteUtil.byteArrayToInt(rlpInfo.get(0).getRLPData());
        this.proposalId = ByteUtil.copyFrom(rlpInfo.get(1).getRLPData());
        this.address = new ByteArrayWrapper(ByteUtil.copyFrom(rlpInfo.get(2).getRLPData()));

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CommitteeCandidate that = (CommitteeCandidate) o;
        return processType == that.processType &&
                Arrays.equals(proposalId, that.proposalId) &&
                Objects.equals(address, that.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(processType, proposalId, address);
    }

    @Override
    public String toString() {
        return "CommitteeCandidate{" +
                "processType=" + processType +
                "proposalId=" + Hex.toHexString(proposalId) +
                ", address=" + address +
                '}';
    }

    public static CommitteeCandidate convertFrom(VoteCommitteeCandidateEvent candidateEvent) {
        CommitteeCandidate committeeCandidate = new CommitteeCandidate(candidateEvent.getProcessType(), ByteUtil.copyFrom(candidateEvent.proposalId), new ByteArrayWrapper(ByteUtil.copyFrom(candidateEvent.getVoteCommitteeAddr())));
        return committeeCandidate;
    }
}
