package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;

import java.math.BigInteger;
import java.util.Optional;

/**
 * description:
 * @author carrot
 */
public class HotstuffChainSyncInfo extends ConsensusMsg {

    private QuorumCert highestQuorumCert;

    private QuorumCert highestCommitCert;

    //private Optional<TimeoutCertificate> highestTimeoutCert;

    private Optional<TwoChainTimeoutCertificate> highestTwoChainTimeoutCert;

    private HotstuffChainSyncInfo() {super(null);}

    public HotstuffChainSyncInfo(byte[] encode) {
        super(encode);
    }

//    public static HotstuffChainSyncInfo build(QuorumCert highestQuorumCert, QuorumCert highestCommitCert, Optional<TimeoutCertificate> highestTimeoutCert) {
//        HotstuffChainSyncInfo syncInfo = new HotstuffChainSyncInfo();
//        syncInfo.highestQuorumCert = highestQuorumCert;
//        syncInfo.highestCommitCert = highestCommitCert;
//        syncInfo.highestTimeoutCert = highestTimeoutCert;
//        syncInfo.epoch = highestQuorumCert.getCertifiedEvent().getEpoch();
//        syncInfo.rlpEncoded = syncInfo.rlpEncoded();
//        return syncInfo;
//    }

    public static HotstuffChainSyncInfo buildWithoutEncode(QuorumCert highestQuorumCert, QuorumCert highestCommitCert, Optional<TwoChainTimeoutCertificate> highestTwoChainTimeoutCert) {
        HotstuffChainSyncInfo syncInfo = new HotstuffChainSyncInfo();
        syncInfo.highestQuorumCert = highestQuorumCert;
        syncInfo.highestCommitCert = highestCommitCert;
        //syncInfo.highestTimeoutCert = highestTimeoutCert;
        syncInfo.highestTwoChainTimeoutCert = highestTwoChainTimeoutCert;
        syncInfo.epoch = highestQuorumCert.getCertifiedEvent().getEpoch();
        return syncInfo;
    }

    public QuorumCert getHighestQuorumCert() {
        return highestQuorumCert;
    }

    public QuorumCert getHighestCommitCert() {
        return highestCommitCert;
    }

//    public Optional<TimeoutCertificate> getHighestTimeoutCert() {
//        return highestTimeoutCert;
//    }

    public Optional<TwoChainTimeoutCertificate> getHighestTwoChainTimeoutCert() {
        return highestTwoChainTimeoutCert;
    }

    public long getHCCRound() {
        return this.highestCommitCert.getCommitEvent().getRound();
    }

    public long getHQCRound() {
        return this.highestQuorumCert.getCertifiedEvent().getRound();
    }

    public long getHTCRound() {
        return this.highestTwoChainTimeoutCert.map(TwoChainTimeoutCertificate::getRound).orElse(0L);
    }

    public long getHighestRound() {
        return Math.max(getHQCRound(), getHTCRound());
    }

    public long getEpoch() {
        return this.highestQuorumCert.getCertifiedEvent().getEpoch();
    }

    public ProcessResult<Void> verify(ValidatorVerifier verifier) {
        long epoch = this.highestQuorumCert.getCertifiedEvent().getEpoch();

        if (epoch != this.highestCommitCert.getCertifiedEvent().getEpoch()) {
            return ProcessResult.ofError("Multi epoch in SyncInfo - HCC and HQC");
        }

        if (epoch != this.highestCommitCert.getCommitEvent().getEpoch()) {
            return ProcessResult.ofError("Multi epoch in SyncInfo - HCE and HQC");
        }

//        if (this.getHighestTimeoutCert().isPresent()) {
//            if (epoch != this.getHighestTimeoutCert().get().getEpoch()) {
//                return ProcessResult.ofError("Multi epoch in SyncInfo - TC and HQC");
//
//            }
//        }

        if (this.getHighestTwoChainTimeoutCert().isPresent()) {
            if (epoch != this.getHighestTwoChainTimeoutCert().get().getEpoch()) {
                return ProcessResult.ofError("Multi epoch in SyncInfo - TC and HQC");

            }
        }

        if (this.highestQuorumCert.getCertifiedEvent().getRound() < this.highestCommitCert.getCertifiedEvent().getRound()) {
            return ProcessResult.ofError("HQC has lower round than HCC");
        }

        if (this.highestCommitCert.getCertifiedEvent().getRound() < this.highestCommitCert.getCommitEvent().getRound()) {
            return ProcessResult.ofError("HOC has lower round than HLI");
        }

        if (this.highestCommitCert.getCommitEvent().equals(EventInfo.empty())) {
            return ProcessResult.ofError("HCC has no committed block");
        }


        ProcessResult<Void> verifyRes = this.highestQuorumCert.verify(verifier);
        if (!verifyRes.isSuccess()) {
            return verifyRes.appendErrorMsg("sync info, highestQuorumCert.verify error!");
        }

        //no need to verify genesis ledger info
        if (this.highestCommitCert.getCommitEvent().getRound() > 0) {
            verifyRes = this.highestCommitCert.verify(verifier);
            if (!verifyRes.isSuccess()) {
                return verifyRes.appendErrorMsg("sync info, highestCommitCert.verify error!");
            }
        }

//        if (this.highestTimeoutCert.isPresent()) {
//            verifyRes = this.highestTimeoutCert.get().verify(verifier);
//            if (!verifyRes.isSuccess()) {
//                return verifyRes.appendErrorMsg("sync info, highestTimeoutCert.verify error!");
//            }
//        }

        if (this.highestTwoChainTimeoutCert.isPresent()) {
            verifyRes = this.highestTwoChainTimeoutCert.get().verify(verifier);
            if (!verifyRes.isSuccess()) {
                return verifyRes.appendErrorMsg("sync info, highestTwoChainTimeoutCert.verify error!");
            }
        }

        return ProcessResult.SUCCESSFUL;
    }

    public boolean hasNewerCertificates(HotstuffChainSyncInfo syncInfo) {
        return this.getHQCRound() > syncInfo.getHQCRound()
                || this.getHTCRound() > syncInfo.getHTCRound()
                || this.getHCCRound() > syncInfo.getHCCRound();
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] epoch = RLP.encodeBigInteger(BigInteger.valueOf(this.epoch));
        byte[] highestQuorumCert = this.highestQuorumCert.getEncoded();
        byte[] highestCommitCert = this.highestCommitCert.getEncoded();


        //byte[] highestTimeoutCert = this.highestTimeoutCert.isPresent()? this.highestTimeoutCert.get().getEncoded(): ByteUtil.EMPTY_BYTE_ARRAY;
        byte[] highestTwoChainTimeoutCert = this.highestTwoChainTimeoutCert.isPresent()? this.highestTwoChainTimeoutCert.get().getEncoded(): ByteUtil.EMPTY_BYTE_ARRAY;
        return RLP.encodeList(epoch, highestQuorumCert, highestCommitCert, highestTwoChainTimeoutCert);
    }

    @Override
    protected void rlpDecoded() {
        RLPList decodeData = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.epoch = ByteUtil.byteArrayToLong(decodeData.get(0).getRLPData());
        this.highestQuorumCert = new QuorumCert(decodeData.get(1).getRLPData());
        this.highestCommitCert = new QuorumCert(decodeData.get(2).getRLPData());
//        if (decodeData.size() > 3) {
//            this.highestTimeoutCert = Optional.of(new TimeoutCertificate(decodeData.get(3).getRLPData()));
//        } else {
//            this.highestTimeoutCert = Optional.empty();
//        }

        if (decodeData.size() > 3) {
            this.highestTwoChainTimeoutCert = Optional.of(new TwoChainTimeoutCertificate(decodeData.get(3).getRLPData()));
        } else {
            this.highestTwoChainTimeoutCert = Optional.empty();
        }
    }

    @Override
    public byte getCode() {
        return ConsensusCommand.HOTSTUFF_CHAIN_SYNC.getCode();
    }

    @Override
    public ConsensusCommand getCommand() {
        return ConsensusCommand.HOTSTUFF_CHAIN_SYNC;
    }

    @Override
    public String toString() {
        return "SyncInfo{" +
                "hqc=" + highestQuorumCert +
                ", hcc=" + highestCommitCert +
                ", htc=" + highestTwoChainTimeoutCert +
                '}';
    }
}
