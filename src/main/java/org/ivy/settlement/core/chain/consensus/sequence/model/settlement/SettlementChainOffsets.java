package org.ivy.settlement.core.chain.consensus.sequence.model.settlement;

import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.infrastructure.datasource.model.Persistable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import static org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffset.EMPTY_OFFSET;

/**
 * description:
 * @author carrot
 * @since 2024-03-26
 */
public class SettlementChainOffsets extends Persistable {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    public static SettlementChainOffsets EMPTY_OFFSETS = new SettlementChainOffsets(EMPTY_OFFSET, new TreeMap<>());

    public static final int MAIN_CHAIN_CODE = 1;

    SettlementChainOffset mainChain;

    TreeMap<Integer, SettlementChainOffset> followerChains;


    public SettlementChainOffsets(byte[] rlpEncoded) {
        super(rlpEncoded);
    }

    public SettlementChainOffsets(SettlementChainOffset mainChain, TreeMap<Integer, SettlementChainOffset> followerChains) {
        super(null);
        this.mainChain = mainChain;
        this.followerChains = followerChains;
        this.rlpEncoded = rlpEncoded();
    }

    public static SettlementChainOffsets genesisBuild(long mainChainStartNumber, Map<String, Long> followers) {
        return new SettlementChainOffsets(mainChainStartNumber, followers);
    }

    private SettlementChainOffsets(long mainChainStartNumber, Map<String, Long> followers) {
        super(null);
        this.mainChain = new SettlementChainOffset(UpdateFollowerChainEvent.CHAIN_JOIN, mainChainStartNumber, 1, mainChainStartNumber);
        this.followerChains = new TreeMap<>();
        for (var e : followers.entrySet()) {
            var chain = Integer.parseInt(e.getKey());
            var startHeight = e.getValue();
            this.followerChains.put(chain, new SettlementChainOffset(UpdateFollowerChainEvent.CHAIN_JOIN, startHeight, chain, startHeight));
        }
        this.rlpEncoded = rlpEncoded();
    }

    public SettlementChainOffset getMainChain() {
        return mainChain;
    }

    public TreeMap<Integer, SettlementChainOffset> getFollowerChains() {
        return followerChains;
    }


    public TreeMap<Integer, SettlementChainOffset> getValidFollowerChains() {
        var res = new TreeMap<Integer, SettlementChainOffset>();
        for (var o : followerChains.values()) {
            if (o.height > o.comeIntoEffectHeight && o.status == UpdateFollowerChainEvent.CHAIN_QUIT) {
                continue;
            }
            res.put(o.chain, o.copy());
        }

        return res;
    }

    public SettlementChainOffset getChain(int chain) {
        if (chain == MAIN_CHAIN_CODE) {
            return mainChain;
        } else {
            return this.followerChains.get(chain);
        }
    }

    public SettlementChainOffset getFollowerChain(Integer chain) {
        return this.followerChains.get(chain);
    }

    public SettlementChainOffsets copy() {
        var followerChains = new TreeMap<Integer, SettlementChainOffset>();
        for (var o : this.followerChains.values()) {
            followerChains.put(o.chain, o.copy());
        }

        return new SettlementChainOffsets(this.mainChain.copy(), followerChains);
    }

    public void reEncoded() {
        this.rlpEncoded = rlpEncoded();
    }

    public void resetMainChainHeight(long height) {
        this.mainChain.height = height;
    }

    public void resetFollowerChainHeight(int chain, long height) {
        var offset = this.followerChains.get(chain);
        if (offset == null) {
            logger.warn("chain[{}] was not exist!", chain);
            return;
        }
        offset.height = height;
    }

    @Override
    protected byte[] rlpEncoded() {
        var content = new byte[1 + this.followerChains.size()][];
        content[0] = this.mainChain.getEncoded();
        var i = 0;
        for (var o : this.followerChains.values()) {
            content[i + 1] = o.getEncoded();
            i++;
        }
        return RLP.encodeList(content);
    }

    @Override
    protected void rlpDecoded() {
        var rlpDecode = (RLPList) RLP.decode2(rlpEncoded).get(0);
        this.mainChain = new SettlementChainOffset(rlpDecode.get(0).getRLPData());
        this.followerChains = new TreeMap<>();
        for (var i = 1; i < rlpDecode.size(); i++) {
            var sourceChainOffset = new SettlementChainOffset(rlpDecode.get(i).getRLPData());
            this.followerChains.put(sourceChainOffset.chain, sourceChainOffset);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        var that = (SettlementChainOffsets) o;
        return Objects.equals(mainChain, that.mainChain) && Objects.equals(followerChains, that.followerChains);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mainChain, followerChains);
    }

    @Override
    public String toString() {
        return "ccos{" +
                "mc=" + mainChain +
                ", scs=" + followerChains +
                '}';
    }
}
