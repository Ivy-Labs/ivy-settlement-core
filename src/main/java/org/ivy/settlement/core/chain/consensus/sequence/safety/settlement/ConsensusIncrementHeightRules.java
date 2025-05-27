package org.ivy.settlement.core.chain.consensus.sequence.safety.settlement;

import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * description:
 * @author carrot
 * @since 2024-03-06
 */
/**
 * ConsensusIncrementHeightRules.java description：
 *
 * @Author laiyiyu create on 2024-08-06 14:40:49
 */
public class ConsensusIncrementHeightRules {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    private final int maxIncrementHeightForAllChain;

    private final int maxIncrementHeightPerChainWithinOneHundred;

    private final int maxIncrementHeightPerChainWithinTwoHundred;

    private final int maxIncrementHeightPerChainWithinFourHundred;

    public ConsensusIncrementHeightRules(int maxIncrementHeightForAllChain, int maxIncrementHeightPerChainWithinOneHundred, int maxIncrementHeightPerChainWithinTwoHundred, int maxIncrementHeightPerChainWithinFourHundred) {
        this.maxIncrementHeightForAllChain = maxIncrementHeightForAllChain;
        this.maxIncrementHeightPerChainWithinOneHundred = maxIncrementHeightPerChainWithinOneHundred;
        this.maxIncrementHeightPerChainWithinTwoHundred = maxIncrementHeightPerChainWithinTwoHundred;
        this.maxIncrementHeightPerChainWithinFourHundred = maxIncrementHeightPerChainWithinFourHundred;
    }

    public ConsensusIncrementHeightRules() {
        this(3500, 32, 16, 8);
    }

    public boolean offsetsCheck(boolean parentHasReconfiguration, boolean emptyPayload, SettlementChainOffsets parent, SettlementChainOffsets current) {
        if (parentHasReconfiguration) {
            if (!parent.equals(current)) {
                logger.error("un except offset,  parent has reconfiguration, current offsets[{}], parent offsets[{}], not equals!", current, parent);
                return false;
            } else {
                return true;
            }
        }

        if (emptyPayload) {
            if (!parent.equals(current)) {
                logger.error("un except offset, current event is empty payload, current offsets[{}], parent offsets[{}], not equals!", current, parent);
                return false;
            } else {
                return true;
            }
        }

        if (parent.getMainChain().getHeight() > current.getMainChain().getHeight()) {
            logger.error("un except main chain height, parent[{}], current[{}]", parent.getMainChain().getHeight(), current.getMainChain().getHeight());
            return false;
        }

        if (current.getMainChain().getHeight() - parent.getMainChain().getHeight() >= maxIncrementHeightPerChainWithinFourHundred) {
            logger.error("un except main chain increment height, parent[{}], current[{}]", parent.getMainChain().getHeight(), current.getMainChain().getHeight());
            return false;
        }

        if (current.getFollowerChains().size() != parent.getFollowerChains().size()) {
            logger.error("un except size, parent[{}], current[{}]", parent.getFollowerChains().size(), current.getFollowerChains().size());
        }

        var maxIncrementHeight = getMaxSizeForPerChain(parent.getFollowerChains().size());
        var incrementSum = 0;
        for (var parentChainOffset : parent.getFollowerChains().values()) {
            var currentChainOffset = current.getChain(parentChainOffset.getChain());
            if (currentChainOffset == null) {
                logger.error("un except chain offset state, parent[{}], current is null", parentChainOffset);
                return false;
            }


            if (parentChainOffset.getHeight() >  currentChainOffset.getHeight()) {
                logger.error("un except chain offset, chain[{}] for parent height [{}], current height[{}]", parentChainOffset.getChain(), parentChainOffset.getHeight(), currentChainOffset.getHeight());
                return false;
            }

            var incrementPerChain = currentChainOffset.getHeight() - parentChainOffset.getHeight();
            if (incrementPerChain > maxIncrementHeight) {
                logger.error("un except chain[{}] height increment, parent[{}] current[{}]", parentChainOffset.getChain(), parentChainOffset.getHeight(), currentChainOffset.getHeight());
                return false;
            }

            incrementSum += incrementPerChain;
        }

        if (incrementSum > this.maxIncrementHeightForAllChain) {
            logger.error("un except all chain height increment sum, expect less than[{}], actual[{}]", this.maxIncrementHeightForAllChain, incrementSum);
            return false;
        }

        return true;
    }

    int getMaxIncrementHeightForAllChain() {
        return maxIncrementHeightForAllChain;
    }

    int getMaxSizeForPerChain(int totalChainsNum) {
        if (totalChainsNum <= 100) {
            return maxIncrementHeightPerChainWithinOneHundred;
        } else if (totalChainsNum <= 200) {
            return maxIncrementHeightPerChainWithinTwoHundred;
        } else {
            return maxIncrementHeightPerChainWithinFourHundred;
        }
    }
}
