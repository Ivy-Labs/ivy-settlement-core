package org.ivy.settlement.core.chain.consensus.slowpath.impl;

import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecureKey;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.liveness.EpochState;
import org.ivy.settlement.core.chain.consensus.slowpath.SettlementBatchFinality;
import org.ivy.settlement.core.chain.ledger.model.SettlementBatch;
import org.ivy.settlement.core.chain.sync.SettlementChainNetSender;
import org.ivy.settlement.core.chain.sync.SettlementChainsSyncer;
import org.ivy.settlement.core.chain.sync.OnChainStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

import static org.ivy.settlement.core.chain.consensus.sequence.model.settlement.SettlementChainOffsets.MAIN_CHAIN_CODE;
import static org.ivy.settlement.core.chain.sync.OnChainStatus.RESIGN;
import static org.ivy.settlement.core.chain.sync.OnChainStatus.SUCCESS;

/**
 * description:
 * @author carrot
 */
public class SettlementBatchFinalityImpl implements SettlementBatchFinality {

    static final Logger logger = LoggerFactory.getLogger("slow_path");

    public static final int BEACON_CONFIRM_TIME = 12 * 1000;

    SecureKey secureKey;

    SettlementChainsSyncer settlementChainsSyncer;

    SettlementChainNetSender settlementChainNetSender;

    public SettlementBatchFinalityImpl(SecureKey secureKey, SettlementChainsSyncer settlementChainsSyncer) {
        this.secureKey = secureKey;
        this.settlementChainsSyncer = settlementChainsSyncer;
    }

    @Override
    public boolean onChain(EpochState signEpoch, SettlementBatch settlementBatch) {
        var forwardRes = forward(MAIN_CHAIN_CODE, settlementBatch, signEpoch, true);
        if (!forwardRes.isSuccess()) {
            logger.error("on chain error:{}", forwardRes.getErrMsg());
            System.exit(0);
        }
        return forwardRes.getResult() == SUCCESS;
    }


    private ProcessResult<OnChainStatus> forward(int chain, SettlementBatch settlementBatch, EpochState epochState, boolean waitSuccess) {
        while (true) {
            try {
                var onChainRes = settlementChainsSyncer.getSettlementBlobOnChainResult(settlementBatch.getNumber());
                if (onChainRes == SUCCESS) ProcessResult.ofSuccess(SUCCESS);
                if (onChainRes == RESIGN) ProcessResult.ofSuccess(RESIGN);
                var sendIndex = getSendIndex(epochState, settlementBatch.getNumber());
                var orderKeys = epochState.getOrderedPublishKeys();

                if (!Arrays.equals(this.secureKey.getPubKey(), orderKeys.get((int) (sendIndex % orderKeys.size())))) {
                    this.wait(500);
                    continue;
                }

                settlementChainNetSender.send(chain, settlementBatch);
                this.wait(BEACON_CONFIRM_TIME);
            } catch (Throwable t) {
                logger.warn("forward error!", t);
                if (waitSuccess) {
                    try {
                        this.wait(1000);
                    } catch (InterruptedException ignored) {}
                } else {
                    return ProcessResult.ofError();
                }
            }
        }
    }

    private long getSendIndex(EpochState epochState, long blobNumber) {
        long startBlobOffset = blobNumber;
        long endBlobOffset = this.settlementChainsSyncer.getLatestMainChainFinalityNumber();
//                var latestBlobOffset = this.settlementChainsSyncer.getLatestConfirmHeight(chain);
//                if (latestBlobOffset == null) {
//                    endBlobOffset = this.settlementChainsSyncer.getLatestOnChainCrossChainOffsets().getChain(SettlementChainOffsets.MAIN_CHAIN_CODE).getHeight();
//                } else {
//                    endBlobOffset = latestBlobOffset.getHeight();
//                }
        return Math.abs(endBlobOffset - startBlobOffset) / epochState.getOnChainTimeoutInterval();
    }
}
