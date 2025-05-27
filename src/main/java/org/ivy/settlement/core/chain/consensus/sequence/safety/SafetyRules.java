package org.ivy.settlement.core.chain.consensus.sequence.safety;

import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecureKey;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.consensus.sequence.store.PersistentSafetyStore;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.ivy.settlement.core.chain.ledger.model.crypto.ValidatorSigner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * description:
 * @author carrot
 */
public class SafetyRules {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    PersistentSafetyStore safetyStorage;

    ValidatorSigner signer;

    EpochState epochState;

    ByteArrayWrapper authorWrapper;

    final private ReentrantReadWriteLock safetyRulesLock = new ReentrantReadWriteLock();

    public SafetyRules(SecureKey secureKey) {
        signer = new ValidatorSigner(secureKey);
        authorWrapper = new ByteArrayWrapper(signer.getAuthor());
        safetyStorage = new PersistentSafetyStore(true);
    }

    public void initialize(EpochState epochState) {
        try {
            this.safetyRulesLock.writeLock().lock();
            logger.debug("start new epoch:{}", epochState);
            startNewEpoch(epochState);
        } finally {
            this.safetyRulesLock.writeLock().unlock();
        }
    }

    private void startNewEpoch(EpochState epochState) {
        this.epochState = epochState;
        long currentEpoch = this.safetyStorage.getEpoch();
        if (currentEpoch < epochState.getEpoch()) {
            // This is ordered specifically to avoid configuration issues:
            // * First set the waypoint to lock in the minimum restarting point,
            // * set the round information,
            // * finally, set the epoch information because once the epoch is set, this `if`
            // statement cannot be re-entered.
            safetyStorage.setEpoch(epochState.getEpoch());
            safetyStorage.setOneChainRound(0);
            safetyStorage.setLastVotedRound(0);
            safetyStorage.setPreferredRound(0);
        }
    }

    public byte[] getAuthor() {
        return this.signer.getAuthor();
    }

    public ByteArrayWrapper getAutorWrapper() {
        return authorWrapper;
    }

    //当生成共识Event作为签名时，无需写入deltaState。
    public Event signProposal(EventData eventData) {
        try {
            this.safetyRulesLock.writeLock().lock();

            // verify author
            var authorOp = eventData.getAuthor();
            if (authorOp.isEmpty()) {
                logger.debug("signProposal, No author found in the proposal!");
                return null;
            }
            if (!Arrays.equals(authorOp.get(), this.signer.getAuthor())) {
                logger.debug("signProposal, Proposal author is not validator signer!");
                return null;
            }

            //verify epoch
            if (eventData.getEpoch() != safetyStorage.getEpoch()) {
                logger.error("signProposal, Incorrect Epoch!, current event data epoch[{}], safetyStorage epoch[{}]", eventData.getEpoch(), safetyStorage.getEpoch());
                return null;
            }

            // last vote round
            if (eventData.getRound() <= safetyStorage.getLastVotedRound()) {
                logger.debug("Block round is older than last_voted_round ({} <= {})", eventData.getRound(), safetyStorage.getLastVotedRound());
                return null;
            }

            // verify qc
            ProcessResult<Void> verifyProcessResult = verifyQC(eventData.getQuorumCert());
            if (!verifyProcessResult.isSuccess()) {
                logger.debug("signProposal, verifyQC fail!");
                return null;
            }

            // compare Preferred Round
            ProcessResult<Void> checkRes = checkAndUpdatePreferredRound(eventData.getQuorumCert());
            if (!checkRes.isSuccess()) {
                logger.debug("signProposal, checkAndUpdatePreferredRound error.{}", checkRes.getErrMsg());
                return null;
            }

            return Event.buildProposalFromEventData(eventData, signer);
        } finally {
            this.safetyRulesLock.writeLock().unlock();
        }
    }
    //===============================================================

    public Vote constructAndSignVoteTwoChain(VoteProposal voteProposal, Optional<TwoChainTimeoutCertificate> timeoutCert) {
        try {
            this.safetyRulesLock.writeLock().lock();
            //logger.debug("Incoming vote proposal to signECDSA.");

            Event proposalEvent = voteProposal.getEvent();

            if (!verifyProposal(voteProposal)) return null;

            if (timeoutCert.isPresent()) {
                var tcVerifyRes = timeoutCert.get().verify(this.epochState.getValidatorVerifier());
                if (!tcVerifyRes.isSuccess()) {
                    logger.warn("invalid timeout certificate:{}", tcVerifyRes.getErrMsg());
                    return null;
                }
            }

            //if already voted on this round, send back the previous vote
            //note: this needs to happen after verifying the epoch as we just check the round here
            if (this.safetyStorage.getLastVote().isPresent()) {
                if (this.safetyStorage.getLastVote().get().getVoteData().getProposed().getRound() == proposalEvent.getRound()) {
                    return this.safetyStorage.getLastVote().get();
                }
            }

            // two voting rules
            if (!safeToVote(proposalEvent, timeoutCert)) return null;


            if (proposalEvent.getRound() <= safetyStorage.getLastVotedRound()) {
                logger.debug("constructAndSignVote error vote round: proposalEventRound[{}] , lastVotedRound[{}]", proposalEvent.getRound(), safetyStorage.getLastVotedRound());
                return null;
            }
            safetyStorage.setLastVotedRound(proposalEvent.getRound());

            //record 1&2-chain data
            var oneChain = proposalEvent.getQuorumCert().getCertifiedEvent().getRound();
            if (oneChain > this.safetyStorage.getOneChainRound()) {
                this.safetyStorage.setOneChainRound(oneChain);
            }

            var twoChain = proposalEvent.getQuorumCert().getParentEvent().getRound();
            if (twoChain > this.safetyStorage.getPreferredRound()) {
                this.safetyStorage.setPreferredRound(twoChain);
            }

            //construct and sign vote
            var voteData = VoteData.build(proposalEvent.buildEventInfo(voteProposal.getStateRoot(), voteProposal.getNumber(), voteProposal.getNewCurrentEpochState(), voteProposal.getNewNextEpochState()), proposalEvent.getQuorumCert().getCertifiedEvent());
            var vote = Vote.build(voteData, signer.getAuthor(), constructLedgerInfo2Chain(proposalEvent, voteData.getHash()), signer);
            this.safetyStorage.setLastVote(Optional.of(vote));

            return vote;
        } finally {
            this.safetyRulesLock.writeLock().unlock();
        }
    }

    private boolean verifyProposal(VoteProposal voteProposal) {
        var proposalEvent = voteProposal.getEvent();

        if (proposalEvent.getEpoch() != safetyStorage.getEpoch()) {
            logger.warn("constructAndSignVote Incorrect Epoch!");
            return false;
        }

        ProcessResult<Void> verifyRes = verifyQC(proposalEvent.getQuorumCert());
        if (!verifyRes.isSuccess()) {
            logger.warn("constructAndSignVote, verifyQC error:{}", verifyRes.getErrMsg());
            return false;
        }

        var eventVerifySignRes = proposalEvent.validateSignatures(this.epochState.getValidatorVerifier());
        if (!eventVerifySignRes.isSuccess()) {
            logger.warn("constructAndSignVote, verify event error:{}", eventVerifySignRes.getErrMsg());
            return false;
        }

        var verifyFormedRes = proposalEvent.verifyWellFormed();
        if (!verifyFormedRes.isSuccess()) {
            logger.warn("constructAndSignVote, verify event formed error:{}", verifyFormedRes.getErrMsg());
            return false;
        }
        return true;
    }


    private boolean safeToVote(Event event, Optional<TwoChainTimeoutCertificate> mayBeTc) {
        var round = event.getRound();
        var qcRound = event.getQuorumCert().getCertifiedEvent().getRound();
        var tcRound = mayBeTc.map(TwoChainTimeoutCertificate::getRound).orElse(0L);
        var hqcRound = mayBeTc.map(TwoChainTimeoutCertificate::highestHqcRound).orElse(0L);

        if (round == nextRound(qcRound)
                || (round == nextRound(tcRound) && qcRound >= hqcRound)) {
            return true;
        } else {
            return false;
        }
    }


    /**
     * Produces a LedgerInfo that either commits an event based upon the 2-chain commit rule
     * or an empty LedgerInfo for no commit. The 2-chain commit rule is: E0 (as well as its
     * prefix) can be committed if there exist certified events B1 satisfy:
     * 1) E0 <- E1 <-
     * 2) round(E0) + 1 = round(E1)
     */
    private LedgerInfo constructLedgerInfo2Chain(Event proposalEvent, byte[] consensusDataHash) {
        long event1 = proposalEvent.getRound();
        long event0 = proposalEvent.getQuorumCert().getCertifiedEvent().getRound();

        boolean commit = nextRound(event0) == event1;
        var commitEvent = commit ? proposalEvent.getQuorumCert().getCertifiedEvent() : EventInfo.empty();
        return LedgerInfo.build(commitEvent, consensusDataHash);
    }

    private static long nextRound(long round) {
        return round + 1;
    }
    //===============================================================

    public Vote constructAndSignVote(VoteProposal voteProposal) {
        try {
            this.safetyRulesLock.writeLock().lock();
            //logger.debug("Incoming vote proposal to signECDSA.");

            Event proposalEvent = voteProposal.getEvent();

            // verify epoch
            if (proposalEvent.getEpoch() != safetyStorage.getEpoch()) {
                logger.debug("constructAndSignVote Incorrect Epoch!");
                return null;
            }

            ProcessResult<Void> verifyRes = verifyQC(proposalEvent.getQuorumCert());
            if (!verifyRes.isSuccess()) {
                logger.warn("constructAndSignVote, verifyQC error.{}", verifyRes.getErrMsg());
                return null;
            }

            ProcessResult<Void> checkRes = checkAndUpdatePreferredRound(proposalEvent.getQuorumCert());
            if (!checkRes.isSuccess()) {
                logger.warn("constructAndSignVote, checkAndUpdatePreferredRound error.{}", checkRes.getErrMsg());
                return null;
            }

            // verify and update last vote round
            if (proposalEvent.getRound() <= safetyStorage.getLastVotedRound()) {
                logger.debug("constructAndSignVote error vote round: proposalEventRound[{}] , lastVotedRound[{}]", proposalEvent.getRound(), safetyStorage.getLastVotedRound());
                return null;
            }
            safetyStorage.setLastVotedRound(proposalEvent.getRound());

            VoteData voteData = VoteData.build(proposalEvent.buildEventInfo(voteProposal.getStateRoot(), voteProposal.getNumber(), voteProposal.getNewCurrentEpochState(), voteProposal.getNewNextEpochState()), proposalEvent.getQuorumCert().getCertifiedEvent());
            return Vote.build(voteData, signer.getAuthor(), constructLedgerInfo(proposalEvent, voteData.getHash()), signer);
        } finally {
            this.safetyRulesLock.writeLock().unlock();
        }
    }

   /**
    * Produces a LedgerInfo that either commits a event based upon the 3-chain commit rule
    * or an empty LedgerInfo for no commit. The 3-chain commit rule is: E0 (as well as its
    * prefix) can be committed if there exist certified events B1 and E2 that satisfy:
    * 1) E0 <- E1 <- E2 <--
    * 2) round(E0) + 1 = round(E1), and
    * 3) round(E1) + 1 = round(E2).
    */
    private LedgerInfo constructLedgerInfo(Event proposalEvent, byte[] consensusDataHash) {
        long event2 = proposalEvent.getRound();
        long event1 = proposalEvent.getQuorumCert().getCertifiedEvent().getRound();
        long event0 = proposalEvent.getQuorumCert().getParentEvent().getRound();

        boolean commit = event0 + 1 == event1 && event1 + 1 == event2;
        if (commit) {
            return LedgerInfo.build(proposalEvent.getQuorumCert().getParentEvent(), consensusDataHash);
        } else {
            return LedgerInfo.build(EventInfo.empty(), consensusDataHash);
        }
    }

    public Signature signTimeout(Timeout timeout) {
        try {
            this.safetyRulesLock.writeLock().lock();

            //verify epoch
            if (timeout.getEpoch() != safetyStorage.getEpoch()) {
                logger.debug("signTimeout, Incorrect Epoch!");
                return null;
            }

            // compare Preferred Round
            if (timeout.getRound() <= safetyStorage.getPreferredRound()) {
                logger.debug("timeout round does not match preferred round {} < {}", timeout.getRound(),
                        safetyStorage.getPreferredRound());
                return null;
            }

            // compare last vote Round
            if (timeout.getRound() < safetyStorage.getLastVotedRound()) {
                logger.debug("timeout round does not match last vote round {} < {}", timeout.getRound(),
                        safetyStorage.getLastVotedRound());
                return null;
            }

            if (timeout.getRound() > safetyStorage.getLastVotedRound()) {
                safetyStorage.setLastVotedRound(timeout.getRound());
            }

            return timeout.sign(signer);
        } finally {
            this.safetyRulesLock.writeLock().unlock();
        }
    }

    public Signature signTwoChainTimeoutWithQc(TwoChainTimeout timeout, Optional<TwoChainTimeoutCertificate> timeoutCertificate) {
        try {
            this.safetyRulesLock.writeLock().lock();

            //verify epoch
            if (timeout.getEpoch() != safetyStorage.getEpoch()) {
                logger.debug("signTimeout, Incorrect Epoch!");
                return null;
            }

            var timeoutVerifyRes = timeout.verify(this.epochState.getValidatorVerifier());;
            if (!timeoutVerifyRes.isSuccess()) {
                logger.warn("signTwoChainTimeoutWithQc verify timeout error:{}", timeoutVerifyRes.getErrMsg());
                return null;
            }

            //verify tc
            if (timeoutCertificate.isPresent()) {
                var verifyTcRes = timeoutCertificate.get().verify(this.epochState.getValidatorVerifier());;
                if (!verifyTcRes.isSuccess()) {
                    logger.warn("signTwoChainTimeoutWithQc verify tc error:{}", verifyTcRes.getErrMsg());
                    return null;
                }
            }

            if (!safeToTimeout(timeout, timeoutCertificate)) return null;

            // compare last vote Round
            if (timeout.getRound() < safetyStorage.getLastVotedRound()) {
                logger.debug("timeout round does not match last vote round {} < {}", timeout.getRound(),
                        safetyStorage.getLastVotedRound());
                return null;
            }

            if (timeout.getRound() > safetyStorage.getLastVotedRound()) {
                safetyStorage.setLastVotedRound(timeout.getRound());
            }

            return timeout.sign(signer);
        } finally {
            this.safetyRulesLock.writeLock().unlock();
        }
    }


    private boolean safeToTimeout(TwoChainTimeout timeout, Optional<TwoChainTimeoutCertificate> maybeTc) {
        var round = timeout.getRound();
        var qcRound = timeout.hqcRound();
        var tcRound = maybeTc.map(TwoChainTimeoutCertificate::getRound).orElse(0L);
        if ((round == nextRound(qcRound) || round == nextRound(tcRound))
                && qcRound >= this.safetyStorage.getOneChainRound()) {
            return true;
        }
        logger.warn("not safe to timeout, round[{}], qcRound[{}], tcRound[{}], safety date one chain round[{}]", round, qcRound, tcRound, this.safetyStorage.getOneChainRound());
        return false;
    }

    private ProcessResult<Void> checkAndUpdatePreferredRound(QuorumCert quorumCert) {
        var preferredRound = this.safetyStorage.getPreferredRound();
        var oneChainRound = quorumCert.getCertifiedEvent().getRound();
        var twoChainRound = quorumCert.getParentEvent().getRound();

        if (oneChainRound < preferredRound) {
            logger.debug( "QC round does not match preferred round {} < {}", oneChainRound, preferredRound);
            return ProcessResult.ofError("ProposalRoundLowerThenPreferredBlock error!");
        }

        if (twoChainRound > preferredRound) {
            this.safetyStorage.setPreferredRound(twoChainRound);
        }

        return ProcessResult.SUCCESSFUL;
    }

    private ProcessResult<Void> verifyQC(QuorumCert qc) {
        if (this.epochState == null) {
            return ProcessResult.ofError("SafetyRules.epochState NotInitialized!");
        }

        return qc.verify(this.epochState.getValidatorVerifier());
    }
}
