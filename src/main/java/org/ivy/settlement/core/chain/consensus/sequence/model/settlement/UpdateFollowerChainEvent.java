package org.ivy.settlement.core.chain.consensus.sequence.model.settlement;

/**
 * description:
 * @author carrot
 * @since 2024-02-29
 */
public class UpdateFollowerChainEvent {

    public static final int CHAIN_JOIN = 1;
    public static final int CHAIN_QUIT = 2;

    //1:join
    //2:quit
    int status;

    long comeIntoEffectHeight;

    int chain;

    int chainType;

    public UpdateFollowerChainEvent(int status, long comeIntoEffectHeight, int chain) {
        this.status = status;
        this.comeIntoEffectHeight = comeIntoEffectHeight;
        this.chain = chain;
        this.chainType = chainType;
    }

    public int getStatus() {
        return status;
    }

    public long getComeIntoEffectHeight() {
        return comeIntoEffectHeight;
    }

    public int getChain() {
        return chain;
    }

    public int getChainType() {
        return chainType;
    }

    @Override
    public String toString() {
        return "UpdateFollowerChainEvent{" +
                "status=" + status +
                ", comeIntoEffectHeight=" + comeIntoEffectHeight +
                ", chain=" + chain +
                '}';
    }
}
