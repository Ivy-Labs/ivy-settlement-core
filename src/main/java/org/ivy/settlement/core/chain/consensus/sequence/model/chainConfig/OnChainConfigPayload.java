package org.ivy.settlement.core.chain.consensus.sequence.model.chainConfig;


import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;

import java.util.HashMap;

/**
 * description:
 * @author carrot
 */
public class OnChainConfigPayload {

    public static String EPOCH_INFO = "EPOCH_INFO";

    long epoch;

    // configId 2 content
    HashMap<String, Object> configs;

    public OnChainConfigPayload(long epoch, HashMap<String, Object> configs) {
        this.epoch = epoch;
        this.configs = configs;
    }

    public static OnChainConfigPayload build(EpochState epochState) {
        HashMap<String, Object> configs = new HashMap<>();
        EpochState newEpoch = new EpochState(ByteUtil.copyFrom(epochState.getEncoded()));
        configs.put(EPOCH_INFO, newEpoch);
        return new OnChainConfigPayload(epochState.getEpoch(), configs);
    }

    public long getEpoch() {
        return epoch;
    }

    public EpochState getEpochState() {
        return (EpochState) configs.get(EPOCH_INFO);
    }


}
