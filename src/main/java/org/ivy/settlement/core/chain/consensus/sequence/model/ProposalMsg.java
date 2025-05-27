package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.Arrays;


/**
 * description:
 * @author carrot
 */
public class ProposalMsg extends ConsensusMsg {

    private Event proposal;

    private HotstuffChainSyncInfo hotstuffChainSyncInfo;

    private ProposalMsg() {super(null);}

    public ProposalMsg(byte[] encode) {super(encode);}

    public static  ProposalMsg build(Event proposal, HotstuffChainSyncInfo hotstuffChainSyncInfo) {
        ProposalMsg proposalMsg = new ProposalMsg();
        proposalMsg.epoch = proposal.getEpoch();
        proposalMsg.proposal = proposal;
        proposalMsg.hotstuffChainSyncInfo = hotstuffChainSyncInfo;
        proposalMsg.rlpEncoded = proposalMsg.rlpEncoded();
        return proposalMsg;
    }

    public Event getProposal() {
        return proposal;
    }

    public HotstuffChainSyncInfo getHotstuffChainSyncInfo() {
        return hotstuffChainSyncInfo;
    }

    public long getRound() {
        return this.proposal.getRound();
    }

    public byte[] getProposer() {
        assert proposal.getAuthor().isPresent();
        return this.proposal.getAuthor().get();
    }

    public ProcessResult<Void> verify(ValidatorVerifier verifier) {
        ProcessResult<Void> verifyRes = this.proposal.validateSignatures(verifier);
        if (!verifyRes.isSuccess()) return verifyRes;

        if (hotstuffChainSyncInfo.getHighestTwoChainTimeoutCert().isPresent()) {
            verifyRes = hotstuffChainSyncInfo.getHighestTwoChainTimeoutCert().get().verify(verifier);
            if (!verifyRes.isSuccess()) return verifyRes;
        }

        return verifyWellFormed();
    }

    public ProcessResult<Void> verifyWellFormed() {
        if (proposal.isEmptyEvent()) {
            return ProcessResult.ofError(String.format("Proposal %s for a empty event", proposal));
        }

        ProcessResult<Void> verifyRes = proposal.verifyWellFormed();
        if (!verifyRes.isSuccess()) {
            return verifyRes.appendErrorMsg("Fail to verify ProposalMsg's event");
        }

        if (proposal.getRound() <= 0) {
            return ProcessResult.ofError(String.format("Proposal %s has an incorrect round of 0", proposal));
        }

        if (proposal.getEpoch() != hotstuffChainSyncInfo.getEpoch()) {
            return ProcessResult.ofError("ProposalMsg has different epoch number from SyncInfo");
        }

        if (!Arrays.equals(proposal.getParentId(), hotstuffChainSyncInfo.getHighestQuorumCert().getCertifiedEvent().getId())) {
            return ProcessResult.ofError(String.format("Proposal HQC in SyncInfo certifies %s, but parent event id is %s", Hex.toHexString(hotstuffChainSyncInfo.getHighestQuorumCert().getCertifiedEvent().getId()), Hex.toHexString(proposal.getParentId())));
        }

        long previousRound = proposal.getRound() - 1;

        long highestCertifiedRound = Math.max(proposal.getQuorumCert().getCertifiedEvent().getRound(), hotstuffChainSyncInfo.getHTCRound());

        if (previousRound != highestCertifiedRound) {
            return ProcessResult.ofError(String.format("%s does not have a certified round %d", proposal, previousRound));
        }

        if (proposal.getAuthor().isEmpty()) {
            return ProcessResult.ofError(String.format("%s does not define an author", proposal));
        }

        return ProcessResult.SUCCESSFUL;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] epoch = RLP.encodeBigInteger(BigInteger.valueOf(this.epoch));
        byte[] proposal = this.proposal.getEncoded();
        byte[] hotstuffChainSyncInfo = this.hotstuffChainSyncInfo.getEncoded();
        return RLP.encodeList(epoch, proposal, hotstuffChainSyncInfo);
    }

    @Override
    protected void rlpDecoded() {
        RLPList decodeData = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.epoch = ByteUtil.byteArrayToLong(decodeData.get(0).getRLPData());
        this.proposal = new Event(decodeData.get(1).getRLPData());
        this.hotstuffChainSyncInfo = new HotstuffChainSyncInfo(decodeData.get(2).getRLPData());
    }

    @Override
    public byte getCode() {
        return ConsensusCommand.PROPOSAL.getCode();
    }

    @Override
    public ConsensusCommand getCommand() {
        return ConsensusCommand.PROPOSAL;
    }

    @Override
    public String toString() {
        return "ProposalMsg{" + proposal +
                ", " + hotstuffChainSyncInfo +
                '}';
    }
}


