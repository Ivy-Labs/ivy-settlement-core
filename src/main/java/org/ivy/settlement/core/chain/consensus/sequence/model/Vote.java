package org.ivy.settlement.core.chain.consensus.sequence.model;

import io.libp2p.core.utils.Pair;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.core.chain.ledger.model.crypto.ValidatorSigner;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Optional;

/**
 * description:
 * @author carrot
 */
public class Vote extends RLPModel {

    private VoteData voteData;

    private byte[] author;

    private LedgerInfo ledgerInfo;

    private Signature signature;

    private Optional<Signature> timeoutSignature;

    private Optional<Pair<TwoChainTimeout, Signature>> twoChainTimeout;

    public Vote(){super(null);}

    public Vote(byte[] encode){super(encode);}

    public static Vote build(VoteData voteData, byte[] author, LedgerInfo ledgerInfo, ValidatorSigner validatorSigner) {
        //ledgerInfo.setConsensusDataHash(voteData.getHash());
        Vote vote = new Vote();
        vote.voteData = voteData;
        vote.author = author;
        vote.ledgerInfo = ledgerInfo;
        vote.signature = validatorSigner.signMessage(ledgerInfo.getHash()).get();
        vote.timeoutSignature = Optional.empty();
        vote.twoChainTimeout = Optional.empty();
        vote.rlpEncoded = vote.rlpEncoded();
        return vote;
    }

//    public void addTimeoutSignature(Signature signature) {
//        if (this.timeoutSignature.isPresent()) {
//            return;
//        }
//
//        this.timeoutSignature = Optional.of(signature);
//        this.rlpEncoded = rlpEncoded();
//    }

    public void addTwoChainTimeoutSignature(TwoChainTimeout twoChainTimeout, Signature signature) {
        if (this.twoChainTimeout.isPresent()) {
            return;
        }

        this.twoChainTimeout = Optional.of(Pair.of(twoChainTimeout, signature));
        this.rlpEncoded = rlpEncoded();
    }

    public VoteData getVoteData() {
        return voteData;
    }

    public byte[] getAuthor() {
        return author;
    }

    public LedgerInfo getLedgerInfo() {
        return ledgerInfo;
    }

    public Signature getSignature() {
        return signature;
    }

    public Optional<Signature> getTimeoutSignature() {
        return timeoutSignature;
    }

//    public Timeout getTimeout() {
//        return Timeout.build(voteData.getProposed().getEpoch(), voteData.getProposed().getRound());
//    }

    public TwoChainTimeout getTwoChainTimeout(QuorumCert qc) {
        return new TwoChainTimeout(voteData.getProposed().getEpoch(), voteData.getProposed().getRound(), qc);
    }


//    public boolean isTimeout() {
//        return timeoutSignature.isPresent();
//    }

    public boolean isTwoChainTimeout() {
        return twoChainTimeout.isPresent();
    }

    public Optional<Pair<TwoChainTimeout, Signature>> getTwoChainTimeout() {
        return twoChainTimeout;
    }

    public long getEpoch() {
        return voteData.getProposed().getEpoch();
    }

    public ProcessResult<Void> verify(ValidatorVerifier verifier) {
        if (!Arrays.equals(this.ledgerInfo.getConsensusDataHash(), voteData.getHash())) {
            return ProcessResult.ofError("Vote's hash mismatch with LedgerInfo");
        }

        var verifyRes = verifier.verifySignature(new ByteArrayWrapper(this.author), this.ledgerInfo.getHash(), this.signature);
        if (!verifyRes.isSuccess()) {
            return ProcessResult.ofError("Failed to verify Vote: " + verifyRes);
        }

        if (this.twoChainTimeout.isPresent()) {
            var timeout = this.twoChainTimeout.get().first;
            var signature = this.twoChainTimeout.get().second;
            if (timeout.getEpoch() != this.getEpoch() || timeout.getRound() != this.voteData.getProposed().getRound()) {
                return ProcessResult.ofError("2-chain timeout has different (epoch, round) than vote");
            }

            var timeoutVerifyRes = timeout.verify(verifier);
            if (!timeoutVerifyRes.isSuccess()) {
                return ProcessResult.ofError("Failed to verify Timeout:" + verifyRes);
            }

            verifyRes = verifier.verifySignature(new ByteArrayWrapper(this.author), timeout.getSignHash(), signature);
            if (!verifyRes.isSuccess()) {
                return ProcessResult.ofError("Failed to verify Timeout Vote: " + verifyRes);
            }
        }

        return this.voteData.verify();
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] voteData = this.voteData.getEncoded();
        byte[] author = RLP.encodeElement(this.author);
        byte[] ledgerInfo = this.ledgerInfo.getEncoded();
        byte[] signature = RLP.encodeElement(this.signature.getSig());

        if (this.twoChainTimeout.isPresent()) {
            var tc = this.twoChainTimeout.get().first.rlpEncoded();
            var tcSig = RLP.encodeElement(this.twoChainTimeout.get().second.getSig());
            return RLP.encodeList(voteData, author, ledgerInfo, signature, tc, tcSig);
        } else {
            return RLP.encodeList(voteData, author, ledgerInfo, signature);
        }
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.voteData = new VoteData(rlpDecode.get(0).getRLPData());
        this.author = rlpDecode.get(1).getRLPData();
        this.ledgerInfo = new LedgerInfo(rlpDecode.get(2).getRLPData());
        this.signature = new Signature(rlpDecode.get(3).getRLPData());
        this.timeoutSignature = Optional.empty();
        if (rlpDecode.size() > 4) {
            this.twoChainTimeout = Optional.of(Pair.of(new TwoChainTimeout(rlpDecode.get(4).getRLPData()), new Signature(rlpDecode.get(5).getRLPData())));
        } else {
            this.twoChainTimeout = Optional.empty();
        }
    }

    @Override
    public String toString() {
        return "Vote{" +
                "voteData=" + voteData +
                ", author=" + Hex.toHexString(author) +
                ", ledgerInfo=" + ledgerInfo +
                ", signature=" + signature +
                ", twoChainTimeout=" + twoChainTimeout +
                '}';
    }
}
