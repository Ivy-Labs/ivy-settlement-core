package org.ivy.settlement.core.chain.consensus.sequence.safety.settlement;

/**
 * description:
 * @author carrot
 */
public enum IvyBlockConfirmStatus {

    SUCCESS,

    // must try sending fail IvyBlock again,due to out of gas and so on...
    FAIL,

    // epoch change
    RETRY,

    //the ivyBlock is not confirm
    UN_KNOW;

    static IvyBlockConfirmStatus convertFromOrdinal(int ordinal) {
        if (ordinal == 0) {
            return SUCCESS;
        } else if (ordinal == 1) {
            return FAIL;
        } else if (ordinal == 2) {
            return RETRY;
        } else {
            throw new RuntimeException("ordinal not exit!");
        }
    }


}
