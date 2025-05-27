package org.ivy.settlement.core.chain.consensus.slowpath.model;

import org.ivy.settlement.core.chain.consensus.fastpath.model.FastPathCommand;

/**
 * description:
 * @author carrot
 */
public class BatchCheckTimeoutMsg extends SlowPathMsg {

    public final long signEpoch;

    public final long number;

    public BatchCheckTimeoutMsg(long signEpoch, long number) {
        super(null);
        this.signEpoch = signEpoch;
        this.number = number;
    }

    @Override
    public byte getCode() {
        return FastPathCommand.BLOCK_SIGN_TIMEOUT.getCode();
    }

    @Override
    public SlowPathCommand getCommand() {
        return SlowPathCommand.BLOB_SIGN_TIMEOUT;
    }
}
