package org.ivy.settlement.core.chain.consensus.sequence.store;

import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Optional;
import java.util.TreeMap;


/**
 * description:
 * @author carrot
 */
public class PendingVotes {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    private HashMap<ByteArrayWrapper, LedgerInfoWithSignatures> liDigest2Votes;

    private Optional<TimeoutCertificate> maybePartialTc;

    private Optional<TwoChainTimeoutCertificate> maybePartialTwoChainTc;

    private HashMap<ByteArrayWrapper, Vote> author2Vote;

    private boolean echoTimeout;

    public PendingVotes() {
        liDigest2Votes = new HashMap<>();
        maybePartialTc = Optional.empty();
        maybePartialTwoChainTc = Optional.empty();
        author2Vote = new HashMap<>();
        this.echoTimeout = false;
    }

    public VoteReceptionResult insertVote(Vote vote, ValidatorVerifier validatorVerifier) {
        var author = new ByteArrayWrapper(ByteUtil.copyFrom(vote.getAuthor()));
        var round = vote.getVoteData().getProposed().getRound();
        var liDigest = ByteUtil.copyFrom(vote.getLedgerInfo().getHash());


        //1. Has the author already voted for this round?
        var previouslySeenVote = this.author2Vote.get(author);
        if (previouslySeenVote != null) {
            // is it the same vote?
            if (Arrays.equals(liDigest, previouslySeenVote.getLedgerInfo().getHash())) {
                // we've already seen an equivalent vote before
                boolean newTimeoutVote = vote.isTwoChainTimeout() && !previouslySeenVote.isTwoChainTimeout();
                if (!newTimeoutVote) {
                    // it's not a new timeout vote
                    return VoteReceptionResult.ofDuplicateVote();
                }
            } else {
                // we have seen a different vote for the same round
                logger.error("Validator {} sent two different votes for the same round {}!",
                        Hex.toHexString(author.getData()),
                        round);
                return VoteReceptionResult.ofEquivocateVote();
            }
        }

        // 2. Store new vote (or update, in case it's a new timeout vote)
        this.author2Vote.put(author, vote);

        // 3. Let's check if we can create a QC
        var liDigestWrapper = new ByteArrayWrapper(vote.getLedgerInfo().getHash());
        var liWithSig = liDigest2Votes.get(liDigestWrapper);
        if (liWithSig == null) {
            //logger.debug("aggregateQc build LedgerInfoWithSignatures:" + liWithSig);
            liWithSig = LedgerInfoWithSignatures.build(vote.getLedgerInfo(), new TreeMap<>());
            liDigest2Votes.put(liDigestWrapper, liWithSig);
        }

        liWithSig.addSignature(ByteUtil.copyFrom(vote.getAuthor()), vote.getSignature());
        // check if we have enough signatures to create a QC
        var verifyResult = validatorVerifier.checkVotingPower(liWithSig.getSignatures().keySet());
        //logger.debug("current validatorVerifier info:" + validatorVerifier);
        long votingPower;
        switch (verifyResult.getStatus()) {
            case Success:
                liWithSig.reEncode();
                return VoteReceptionResult.ofNewQuorumCertificate(QuorumCert.build(vote.getVoteData(), liWithSig));
            case TooLittleVotingPower:
                votingPower = ((Pair<Long, Long>) verifyResult.getResult()).getLeft();
                break;
            default:
                logger.error("vote received from author[{}] could not be added: {}", Hex.toHexString(author.getData()), verifyResult.getStatus());
                //System.exit(1);
                return VoteReceptionResult.ofErrorAddingVote(verifyResult);
        }

        // 4
        // 4.1We couldn't form a QC, let's check if we can create a TC
//        if (vote.isTimeout()) {
//
//            // if no partial TC exist, create one
//            if (maybePartialTc.isEmpty()) {
//                maybePartialTc = Optional.of(new TimeoutCertificate(vote.getTimeout(), new TreeMap<>()));
//            }
//
//            // add the timeout signature
//            maybePartialTc.get().addSignature(ByteUtil.copyFrom(vote.getAuthor()), vote.getTimeoutSignature().get());
//
//            // did the TC reach a threshold?
//            verifyResult = validatorVerifier.checkVotingPower(maybePartialTc.get().getSignatures().keySet());
//            switch (verifyResult.getStatus()) {
//                case Success -> {
//                    return VoteReceptionResult.ofNewTimeoutCertificate(maybePartialTc.get());
//                }
//                case TooLittleVotingPower -> {
//                }
//                default -> {
//                    logger.error("MUST_FIX: Unexpected verification error, vote = {}", vote);
//                    System.exit(1);
//                    return VoteReceptionResult.ofErrorAddingVote(verifyResult);
//                }
//            }
//        }

        //4.2 process two chain timeout

        if (vote.isTwoChainTimeout()) {
            var twoChainTimeout = vote.getTwoChainTimeout().get().first.copy();
            var signature = vote.getTwoChainTimeout().get().second.copy();

            if (maybePartialTwoChainTc.isEmpty()) {
                maybePartialTwoChainTc = Optional.of(new TwoChainTimeoutCertificate(twoChainTimeout));
            }

            maybePartialTwoChainTc.get().add(new ByteArrayWrapper(ByteUtil.copyFrom(vote.getAuthor())), twoChainTimeout, signature);
            verifyResult = validatorVerifier.checkVotingPower(maybePartialTwoChainTc.get().signers());
            switch (verifyResult.getStatus()) {
                case Success -> {
                    return VoteReceptionResult.ofNewTwoChainTimeoutCertificate(maybePartialTwoChainTc.get());
                }
                case TooLittleVotingPower -> {
                    var currentVotingPower = ((Pair<Long, Long>) verifyResult.getResult()).getLeft();
                    if (!this.echoTimeout) {
                        var fPlusOne = validatorVerifier.getTotalVotingPower() - validatorVerifier.getQuorumVotingPower() + 1;
                        if (currentVotingPower >= fPlusOne) {
                            this.echoTimeout = true;
                            return VoteReceptionResult.ofEchoTimeout(currentVotingPower);
                        }
                    }
                }
                default -> {
                    logger.error("MUST_FIX: 2-chain Unexpected verification error, vote = {}", vote);
                    System.exit(1);
                    return VoteReceptionResult.ofErrorAddingVote(verifyResult);
                }
            }

        }

        // 5. No QC (or TC) could be formed, return the QC's voting power
        return VoteReceptionResult.ofVoteAdded(votingPower);
    }
}
