package org.ivy.settlement.core.chain.consensus.fastpath.model;

/**
 * description:
 * @author carrot
 */
public class BlockCheckTimeoutMsg extends FastPathMsg {

    public final long number;

    public BlockCheckTimeoutMsg(long number) {
        super(null);
        this.number = number;
    }

    @Override
    public byte getCode() {
        return FastPathCommand.BLOCK_SIGN_TIMEOUT.getCode();
    }

    @Override
    public FastPathCommand getCommand() {
        return FastPathCommand.BLOCK_SIGN_TIMEOUT;
    }
}
