package org.ivy.settlement.core.chain.consensus.sequence;

import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.consensus.sequence.model.ConsensusMsg;
import org.ivy.settlement.core.chain.consensus.sequence.model.Event;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.model.QuorumCert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * description:
 * @author carrot
 */
public abstract class ConsensusProcessor<T> {

    static final Logger logger = LoggerFactory.getLogger("consensus");

    static final boolean IS_DEBUG_ENABLED = logger.isDebugEnabled();

    static final boolean IS_TRACE_ENABLED = logger.isTraceEnabled();

    EpochState epochState;

    public abstract ProcessResult<T> process(ConsensusMsg consensusMsg);

    public EpochState getEpochState() {
        return epochState;
    }

    public abstract void saveTree(List<Event> events, List<QuorumCert> qcs);

    public void releaseResource() {}
}
