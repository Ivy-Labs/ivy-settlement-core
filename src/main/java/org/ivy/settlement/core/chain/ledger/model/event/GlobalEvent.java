package org.ivy.settlement.core.chain.ledger.model.event;

import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;

import java.util.Arrays;

/**
 * description:
 * @author carrot
 */
public class GlobalEvent extends RLPModel {

    //Optional<EpochChangeEvent> epochChangeEvent;

    GlobalNodeEvent[] globalNodeEvents;

    public GlobalEvent() {
        this(new GlobalNodeEvent[0]);
    }

    public GlobalEvent(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    public GlobalEvent(GlobalNodeEvent[] globalNodeEvents) {
        super(null);
        this.globalNodeEvents = globalNodeEvents;
        //this.epochChangeEvent = Optional.empty();
        this.rlpEncoded = rlpEncoded();
    }

    private boolean isGlobalNodeEventsEmpty() {
        return this.globalNodeEvents.length == 0;
    }


    public boolean isEmpty() {
        return isGlobalNodeEventsEmpty();
    }

    public GlobalNodeEvent[] getGlobalNodeEvents() {
        return globalNodeEvents;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[][] encode = new byte[1 + globalNodeEvents.length][];
        encode[0] = RLP.encodeInt(globalNodeEvents.length);
        int i = 1;
        for (GlobalNodeEvent globalNodeEvent : globalNodeEvents) {
            encode[i] = globalNodeEvent.getEncoded();
            i++;
        }

        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        RLPList params = RLP.decode2(rlpEncoded);
        RLPList payload = (RLPList) params.get(0);
        int globalNodeEventsSize = ByteUtil.byteArrayToInt(payload.get(0).getRLPData());
        GlobalNodeEvent[] globalNodeEvents = new GlobalNodeEvent[globalNodeEventsSize];
        int offset = 1;
        for (var i = offset; i < globalNodeEventsSize + offset; i++) {
            globalNodeEvents[i - offset] = new GlobalNodeEvent(payload.get(i).getRLPData());
        }
        this.globalNodeEvents = globalNodeEvents;
    }

    @Override
    public String toString() {
        return "GlobalEvent{globalNodeEvents=" + Arrays.toString(globalNodeEvents) +
                '}';
    }

    public void clear() {
        this.globalNodeEvents = null;
    }
}
