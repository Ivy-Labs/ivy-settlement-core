package org.ivy.settlement.core.chain.consensus.sequence.model;

/**
 * description:
 * @author carrot
 */
public enum RetrievalStatus {

    // Successfully fill in the request.
    SUCCESS(0),
    //need to continue retrieval.
    CONTINUE(1),
    // Can not find the object corresponding to id.
    NOT_FOUND(2),
    // Can not find enough target objs but find some.
    NOT_ENOUGH(3);

    final int code;

    RetrievalStatus(int code) {
        this.code = code;
    }

    public static RetrievalStatus convertFromOrdinal(int ordinal) {
        if (ordinal == 0) {
            return SUCCESS;
        } else if (ordinal == 1) {
            return CONTINUE;
        } else if (ordinal == 2) {
            return NOT_FOUND;
        } else if (ordinal == 3) {
            return NOT_ENOUGH;
        } else {
            throw new RuntimeException("ordinal not exit!");
        }
    }
}
