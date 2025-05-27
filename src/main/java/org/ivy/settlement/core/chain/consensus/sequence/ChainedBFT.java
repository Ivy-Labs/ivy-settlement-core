package org.ivy.settlement.core.chain.consensus.sequence;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.ivy.settlement.infrastructure.async.ThanosThreadFactory;
import org.ivy.settlement.infrastructure.async.IrisSettlementWorker;
import org.ivy.settlement.core.chain.consensus.sequence.model.*;
import org.ivy.settlement.core.chain.consensus.sequence.model.chainConfig.OnChainConfigPayload;
import org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainRetrievalRequestMsg;
import org.ivy.settlement.core.chain.network.protocols.MessageDuplexDispatcher;
import org.ivy.settlement.core.chain.network.protocols.base.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * description:
 * @author carrot
 */
public class ChainedBFT {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    private static final BlockingQueue<ConsensusMsg> consensusMsgQueue = new ArrayBlockingQueue<>(10000);

    private static final BlockingQueue<OnChainConfigPayload> configPayloadQueue = new ArrayBlockingQueue<>(100);

    EpochManager epochManager;

    ThreadPoolExecutor decodeExecutor = new ThreadPoolExecutor(2, Runtime.getRuntime().availableProcessors(), 3, TimeUnit.SECONDS, new ArrayBlockingQueue<>(10000), new ThanosThreadFactory("chained_bft_msg_decode"));

    public ChainedBFT(EpochManager epochManager) {
        this.epochManager = epochManager;
    }

    public void start() {
        epochManager.startProcess(OnChainConfigPayload.build(this.epochManager.consensusEventExecutor.getLatestLedgerInfo().getCurrentEpochState()));

        new IrisSettlementWorker("decode_consensus_start_thread") {
            @Override
            protected void doWork() {
                Message msg = MessageDuplexDispatcher.getConsensusMsg();
                if (epochManager.isSyncing()) return;
                decodeExecutor.execute(() -> {
                    try {
                        ConsensusMsg consensusMsg = switch (ConsensusCommand.fromByte(msg.getCode())) {
                            case PROPOSAL -> new ProposalMsg(msg.getEncoded());
                            case VOTE -> new VoteMsg(msg.getEncoded());
                            case HOTSTUFF_CHAIN_SYNC -> new HotstuffChainSyncInfo(msg.getEncoded());
                            case EVENT_RETRIEVAL_REQ -> new EventRetrievalRequestMsg(msg.getEncoded());
                            case CROSS_CHAIN_RETRIEVAL_REQ -> new SettlementChainRetrievalRequestMsg(msg.getEncoded());
                            case LATEST_LEDGER_REQ -> new LatestLedgerInfoRequestMsg();
                            case LATEST_LEDGER_RESP -> new LatestLedgerInfoResponseMsg(msg.getEncoded());
                            default -> throw new RuntimeException("un except msg type!");
                        };

                        consensusMsg.setRemoteType(msg.getRemoteType());
                        consensusMsg.setRpcId(msg.getRpcId());
                        consensusMsg.setNodeId(msg.getNodeId());
                        //logger.debug("consensus msg:{}", consensusMsg);
                        consensusMsgQueue.put(consensusMsg);
                    } catch (Throwable e) {
                        logger.error("decode error, {}", ExceptionUtils.getStackTrace(e));
                    }
                });
            }
        }.start();

        new IrisSettlementWorker("process_consensus_msg_thread") {
            @Override
            protected void doWork() throws Exception {
                {
                    // don't wait
                    var onChainConfigPayload = configPayloadQueue.poll();
                    if (onChainConfigPayload != null) {
                        epochManager.startProcess(onChainConfigPayload);
                    }
                    onChainConfigPayload = null;

                }
                //logger.debug("poll msg hehe!");
                {
                    // avoid cpu 100%
                    var consensusMsg = consensusMsgQueue.poll(20, TimeUnit.MILLISECONDS);
                    if (consensusMsg != null) {
                        epochManager.processMessage(consensusMsg);
                    }
                    consensusMsg = null;
                }
            }
        }.start();
    }

    public static void putConsensusMsg(ConsensusMsg msg) {
        try {
            consensusMsgQueue.put(msg);
        } catch (Exception e) {
        }
    }

    public static void publishConfigPayload(OnChainConfigPayload onChainConfigPayload) {
        try {
            configPayloadQueue.put(onChainConfigPayload);
        } catch (Exception e) {
        }
    }
}
