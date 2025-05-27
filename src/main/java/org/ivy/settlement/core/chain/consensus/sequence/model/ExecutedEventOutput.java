package org.ivy.settlement.core.chain.consensus.sequence.model;

import io.libp2p.core.utils.Triple;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.crypto.HashUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochStateHolder;
import org.ivy.settlement.infrastructure.datasource.model.Keyable;
import org.ivy.settlement.infrastructure.datasource.model.Persistable;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.util.*;

/**
 * description:
 * @author carrot
 */
public class ExecutedEventOutput extends Persistable {

    long eventNumber;

    byte[] stateHash;

    byte[] stateRoot;

    //变化增量
    Map<Keyable.DefaultKeyable, byte[]> output;

    //Transient
    Triple<EpochStateHolder, EpochStateHolder, EpochStateHolder> epochStateHolders;

    EpochStateHolder newCurrentEpoch;

    EpochStateHolder newNextEpoch;



    public ExecutedEventOutput(byte[] encode, EpochStateHolder newCurrentEpoch, EpochStateHolder newNextEpoch) {
        super(encode);
        this.newCurrentEpoch = newCurrentEpoch;
        this.newNextEpoch = newNextEpoch;
    }



    public ExecutedEventOutput(Map<Keyable.DefaultKeyable, byte[]> output, long eventNumber, byte[] parentStateRoot, EpochStateHolder newCurrentEpoch, EpochStateHolder newNextEpoch) {
        super(null);
        this.output = output;
        this.eventNumber = eventNumber;
        this.newCurrentEpoch = newCurrentEpoch;
        this.newNextEpoch = newNextEpoch;
        byte[] mergedArray = merge(output);
        if (output.size() == 0) {
            this.stateHash = HashUtil.EMPTY_DATA_HASH;
            this.stateRoot = parentStateRoot;
        } else {
            this.stateHash = HashUtil.sha3(mergedArray);
            this.stateRoot = HashUtil.sha3(parentStateRoot, stateHash);
        }


        if (newCurrentEpoch.isPresent()) {
            this.stateHash = HashUtil.sha3(stateHash, newCurrentEpoch.get().getEncoded());
            this.stateRoot = HashUtil.sha3(stateRoot, stateHash);
        }

        if (newNextEpoch.isPresent()) {
            this.stateHash = HashUtil.sha3(stateHash, newNextEpoch.get().getEncoded());
            this.stateRoot = HashUtil.sha3(stateRoot, stateHash);
        }

        // lazy
        this.rlpEncoded = rlpEncoded();
    }


    public ExecutedEventOutput(long eventNumber, byte[] stateHash, byte[] stateRoot, Map<Keyable.DefaultKeyable, byte[]> output, EpochStateHolder newCurrentEpoch, EpochStateHolder newNextEpoch) {
        super(null);
        this.eventNumber = eventNumber;
        this.stateHash = stateHash;
        this.stateRoot = stateRoot;
        this.output = output;
        this.newCurrentEpoch = newCurrentEpoch;
        this.newNextEpoch = newNextEpoch;
        this.rlpEncoded = rlpEncoded();
    }

    public Map<Keyable.DefaultKeyable, byte[]> getOutput() {
        return output;
    }

    public Triple<EpochStateHolder, EpochStateHolder, EpochStateHolder> getEpochStateHolders() {
        return epochStateHolders;
    }

    public EpochStateHolder getNewCurrentEpochState() {
        return this.newCurrentEpoch;
    }

    public EpochStateHolder getNewNextEpochState() {
        return this.newNextEpoch;
    }

//    public void resetEpcohState() {
//        this.epochState = Optional.empty();
//    }


    public void setEpochState(EpochStateHolder newCurrentEpoch, EpochStateHolder newNextEpoch) {

    }

    public boolean hasReconfiguration() { return newCurrentEpoch.isPresent(); }

    public long getEventNumber() {
        return eventNumber;
    }

    public byte[] getStateRoot() {
        return stateRoot;
    }

    public byte[] getStateHash() {
        return stateHash;
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[][] encode = new byte[3 + output.size()][];

        encode[0] = RLP.encodeBigInteger(BigInteger.valueOf(this.eventNumber));
        encode[1] = RLP.encodeElement(this.stateHash);
        encode[2] = RLP.encodeElement(this.stateRoot);
        int i = 3;
        for (Map.Entry<Keyable.DefaultKeyable, byte[]> entry: output.entrySet()) {
            encode[i] =
                    RLP.encodeList(
                            RLP.encodeElement(entry.getKey().keyBytes()),
                            RLP.encodeElement(entry.getValue())
                    );
            i++;
        }
        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        RLPList params = RLP.decode2(rlpEncoded);
        RLPList exeOutput = (RLPList) params.get(0);
        this.eventNumber = ByteUtil.byteArrayToLong(exeOutput.get(0).getRLPData());
        this.stateHash = exeOutput.get(1).getRLPData();
        this.stateRoot = exeOutput.get(2).getRLPData();

        Map<Keyable.DefaultKeyable, byte[]> output = new HashMap<>();
        for (int i = 3; i < exeOutput.size(); i++) {
            RLPList kvBytes = (RLPList) RLP.decode2(exeOutput.get(i).getRLPData()).get(0);
            output.put(Keyable.ofDefault(kvBytes.get(0).getRLPData()), kvBytes.get(1).getRLPData());
        }
        this.output = output;
    }

    @Override
    public String toString() {
        return "ExecutedEventOutput{" +
                "eventNumber=" + eventNumber +
                ", stateHash=" + Hex.toHexString(stateHash) +
                ", stateRoot=" + Hex.toHexString(stateRoot) +
                ", output=" + output +
                ", newCurrentEpoch=" + this.newCurrentEpoch +
                ", newNextEpoch=" + this.newNextEpoch +
                '}';
    }

    public static byte[] merge(Map<Keyable.DefaultKeyable, byte[]> input) {
        int count = 0;
        for (Map.Entry<Keyable.DefaultKeyable, byte[]> entry: input.entrySet())
        {
            count += (entry.getKey().keyBytes().length + entry.getValue().length);
        }
        byte[] mergedArray = new byte[count];
        int start = 0;
        for (Map.Entry<Keyable.DefaultKeyable, byte[]> entry: input.entrySet())
        {
            System.arraycopy(entry.getKey().keyBytes(), 0, mergedArray, start, entry.getKey().keyBytes().length);
            start += entry.getKey().keyBytes().length;
            System.arraycopy(entry.getValue(), 0, mergedArray, start, entry.getValue().length);
            start += entry.getValue().length;
        }
        return mergedArray;
    }

//    public void clear() {
//        stateHash = null;
//        stateRoot = null;
//        output.clear();
//        epochState = null;
//    }
}
