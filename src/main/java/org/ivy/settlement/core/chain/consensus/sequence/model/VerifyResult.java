package org.ivy.settlement.core.chain.consensus.sequence.model;



import org.apache.commons.lang3.tuple.Pair;

import static org.ivy.settlement.core.chain.consensus.sequence.model.VerifyResult.VerifyStatus.*;

/**
 * description:
 * @author carrot
 */
public class VerifyResult<T> {

    final VerifyStatus verifyStatus;

    final T result;

    private VerifyResult(VerifyStatus verifyStatus, T result) {
        this.verifyStatus = verifyStatus;
        this.result = result;
    }

    public boolean isSuccess() {
        return this.verifyStatus == VerifyStatus.Success;
    }

    public VerifyStatus getStatus() {
        return verifyStatus;
    }

    public T getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "VerifyResult{" +
                "verifyStatus=" + verifyStatus +
                ", result=" + result +
                '}';
    }

    public enum VerifyStatus {
        //
        Success,
        /// The author for this signature is unknown by this validator.
        UnknownAuthor,
        // long voting_power
        // long quorum_voting_power,
        TooLittleVotingPower,
        // int num_of_signatures
        // int num_of_authors,
        TooManySignatures,
        /// The signature does not match the hash.
        InvalidSignature;
    }

    public static VerifyResult<Void> ofSuccess() {
        return new VerifyResult(Success, null);
    }


    public static VerifyResult<Void> ofUnknownAuthor() {
        return new VerifyResult(UnknownAuthor, null);
    }

    public static VerifyResult<Pair<Long, Long>> ofTooLittleVotingPower(Pair<Long, Long> result) {
        return new VerifyResult(TooLittleVotingPower, result);
    }

    public static VerifyResult<Pair<Integer, Integer>> ofTooManySignatures(Pair<Integer, Integer> result) {
        return new VerifyResult(TooManySignatures, result);
    }

    public static VerifyResult<Void> ofInvalidSignature() {
        return new VerifyResult(InvalidSignature, null);
    }
}
