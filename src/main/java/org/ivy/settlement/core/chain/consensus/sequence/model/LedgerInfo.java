package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.crypto.CryptoHash;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.config.Constants;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochStateHolder;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.spongycastle.util.encoders.Hex;

/**
 * description:
 * @author carrot
 */
public class LedgerInfo extends RLPModel implements CryptoHash {

    private EventInfo commitEvent;

    // equals EventInfo.getHash()
    private byte[] consensusDataHash;


    private LedgerInfo(){
        super(null);
    }

    public LedgerInfo(byte[] encode){
        super(encode);
    }

    public static LedgerInfo build(EventInfo commitEvent, byte[] consensusDataHash) {
        LedgerInfo info = new LedgerInfo();
        info.commitEvent = commitEvent;
        info.consensusDataHash = consensusDataHash;
        info.rlpEncoded = info.rlpEncoded();
        return info;
    }

    public EventInfo getCommitEvent() {
        return commitEvent;
    }

    public byte[] getConsensusDataHash() {
        return consensusDataHash;
    }

    public long getEpoch() {
        return this.commitEvent.getEpoch();
    }

    public long getRound() {
        return this.commitEvent.getRound();
    }

    public byte[] getConsensusEventId() {
        return this.commitEvent.getId();
    }

    public byte[] getExecutedStateId() {
        return this.commitEvent.getExecutedStateId();
    }

    public long getNumber() {
        return this.commitEvent.getNumber();
    }

    public long getTimestamp() {
        return this.commitEvent.getTimestamp();
    }

    public EpochStateHolder getNewCurrentEpochState() {
        return this.commitEvent.getNewCurrentEpochState();
    }

    public EpochStateHolder getNewNextEpochState() {
        return this.commitEvent.getNewNextEpochState();
    }

    @Override
    public byte[] getHash() {
        return this.consensusDataHash;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[] commitEvent = this.commitEvent.getEncoded();
        byte[] consensusDataHash = RLP.encodeElement(this.consensusDataHash);
        return RLP.encodeList(commitEvent, consensusDataHash);
    }

    @Override
    protected void rlpDecoded() {
        RLPList rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.commitEvent = new EventInfo(rlpDecode.get(0).getRLPData());

        if (rlpDecode.size() > 1) {
            this.consensusDataHash = rlpDecode.get(1).getRLPData();
        } else {
            this.consensusDataHash = Constants.EMPTY_HASH_BYTES;
        }
    }

    @Override
    public String toString() {
        return "LI{" +
                "commitEvent=" + commitEvent +
                ", consensusDataHash=" + Hex.toHexString(this.consensusDataHash) +
                '}';
    }
}
