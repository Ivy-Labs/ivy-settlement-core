package org.ivy.settlement.core.chain.consensus.fastpath;

import io.libp2p.core.peer.PeerId;
import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.core.chain.consensus.sequence.model.RetrievalStatus;
import org.ivy.settlement.infrastructure.string.StringUtils;
import org.ivy.settlement.core.chain.consensus.fastpath.model.CommitBlockResponseMsg;
import org.ivy.settlement.core.chain.consensus.fastpath.model.GetCommitBlockRequestMsg;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.model.ValidatorVerifier;
import org.ivy.settlement.core.chain.ledger.StateLedger;
import org.ivy.settlement.core.chain.ledger.model.Block;
import org.ivy.settlement.core.chain.ledger.model.BlockSign;
import org.ivy.settlement.core.chain.network.NetInvoker;
import org.ivy.settlement.core.chain.sync.SettlementChainsSyncer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * description:
 * @author carrot
 */
public class BlockSyncCoordinator {

    static final Logger logger = LoggerFactory.getLogger("fast_path");

    static final int MAX_RETRY_TIME = 3;

    static final int MAX_BLOCK_RETRIEVAL_NUM = 10;

    StateLedger stateLedger;

    SettlementChainsSyncer settlementChainsSyncer;

    NetInvoker netInvoker;


    public BlockSyncCoordinator(StateLedger stateLedger, SettlementChainsSyncer settlementChainsSyncer, NetInvoker netInvoker) {
        this.stateLedger = stateLedger;
        this.settlementChainsSyncer = settlementChainsSyncer;
        this.netInvoker = netInvoker;
    }

    public ProcessResult<Void> fastForward(long startNumber, long endNumber, PeerId preferredPeer) {
        logger.info("will sync from[{}], fast forward from [{}] to [{}]", preferredPeer, startNumber, endNumber);
        if (endNumber < startNumber) return ProcessResult.SUCCESSFUL;
        var requestMsg = new GetCommitBlockRequestMsg(startNumber, endNumber);
        for (var i = 0; i < MAX_RETRY_TIME; i++) {
            try {
                var response = netInvoker.rpcSend(preferredPeer, requestMsg);
                if (response == null) {
                    return ProcessResult.ofError(StringUtils.format("fastForward(startNumber:{}, endNumber:{}, preferredPeer:{}) fail, case response is null!", startNumber, endNumber, preferredPeer));
                }
                var responseMsg = new CommitBlockResponseMsg(response.getEncoded());
                if (!responseMsg.checkContent()) {
                    return ProcessResult.ofError(StringUtils.format("fastForward(startNumber:{}, endNumber:{}, preferredPeer:{}) fail, case response[{}] check is fail!", startNumber, endNumber, preferredPeer, responseMsg));
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("doSync retrieval resp:{}", responseMsg);
                }

                logger.info("block sync resp:[{}]-[{}]-[{}]", responseMsg.getStatus(), responseMsg.getCommitBlocks().isEmpty() ? "0" : responseMsg.getCommitBlocks().getFirst().getLeft().getNumber(), responseMsg.getCommitBlocks().isEmpty() ? "0" : responseMsg.getCommitBlocks().getLast().getLeft().getNumber());
                return switch (responseMsg.getStatus()) {
                    case SUCCESS -> doSuccess(responseMsg.getCommitBlocks());
                    case CONTINUE ->
                            continueSync(responseMsg.getCommitBlocks(), responseMsg.getLastNumber() + 1, endNumber, preferredPeer);
                    default -> ProcessResult.ofError("un except status!");
                };
            } catch (Exception e) {
                logger.warn("block fast forward sync  error!", e);
            }
        }
        return ProcessResult.ofError(StringUtils.format("fastForward(startNumber:{}, endNumber:{}, preferredPeer:{}) has try max time,but fail!", startNumber, endNumber, preferredPeer));
    }

    private ProcessResult<Void> doSuccess(List<Pair<Block, BlockSign>> commitBlocks) {
        return doSync(commitBlocks);
    }

    private ProcessResult<Void> continueSync(List<Pair<Block, BlockSign>> commitBlocks, long startNumber, long endNumber, PeerId preferredPeer) {
        var syncRes = doSync(commitBlocks);
        if (!syncRes.isSuccess()) return syncRes;
        return fastForward(startNumber, endNumber, preferredPeer);
    }

    private ProcessResult<Void> doSync(List<Pair<Block, BlockSign>> commitBlocks) {
        var fistBlock = commitBlocks.getFirst().getLeft();
        var parentChainOffsets = (fistBlock.getNumber() == 1)? fistBlock.getCrossChainOffsets() : this.stateLedger.getEventData(fistBlock.getNumber() - 1).getPayload().getCrossChainOffsets();
        var currentEpoch = this.stateLedger.getEpochState(fistBlock.getEpoch());

        for (var pair : commitBlocks) {
            var block = pair.getLeft();
            var blockSign = pair.getRight();
            if (!checkEventConsist(block)) {
                return ProcessResult.ofError(StringUtils.format("do sync error, cause block[{}] fail to event consist check!", block));
            }

            if (!this.settlementChainsSyncer.checkSettlementInfos(parentChainOffsets, block.getCrossChainOffsets(), block.getSettlementBlockInfos())) {
                return ProcessResult.ofError(StringUtils.format("do sync error, cause  settlement info[{}] check error!", block.getSettlementBlockInfos()));
            }

            if (currentEpoch.getEpoch() != block.getEpoch()) {
                currentEpoch = this.stateLedger.getEpochState(block.getEpoch());
            }

            if (currentEpoch == null) {
                logger.error("block[{}] query epoch fail!", block);
            }

            if (!checkSign(Objects.requireNonNull(currentEpoch).getValidatorVerifier(), blockSign)) {
                return ProcessResult.ofError(StringUtils.format("do sync error, cause  block[{}] check sign error!", block));

            }
            logger.info("will do sync persist block[{}]!", block.getNumber());
            this.stateLedger.persistBlock(block, blockSign);
            parentChainOffsets = block.getCrossChainOffsets();
        }
        return ProcessResult.SUCCESSFUL;
    }

    private boolean checkEventConsist(Block block) {
        var event = this.stateLedger.getEventData(block.getNumber());
        if (event == null) {
            logger.error("un except system status, event for number[{}] is not exist！", block.getNumber());
            System.exit(0);
        }

        if (!event.getPayload().getCrossChainOffsets().equals(block.getCrossChainOffsets())) {
            logger.error("check event consist error, cause event[number[{}]] chain offsets[{}] not equals block chain offsets[{}]", event.getNumber(), event.getPayload().getCrossChainOffsets(), block.getCrossChainOffsets());
            return false;
        }

        if (event.getEpoch() != block.getEpoch()) {
            logger.error("check event consist error, cause event epoch[{}] not equals block epoch[{}]", event.getEpoch(), block.getEpoch());
            return false;
        }

        if (Arrays.equals(event.getHash(), block.getEventId())) {
            logger.error("check event consist error, cause event id[{}] not equals block event id[{}]", Hex.toHexString(event.getHash()), Hex.toHexString(block.getEventId()));
            return false;
        }
        return true;
    }

    private boolean checkSign(ValidatorVerifier validatorVerifier, BlockSign blockSign) {
        var checkSignRes = validatorVerifier.verifyAggregatedSignature(blockSign.getHash(), blockSign.getSignatures());
        if (!checkSignRes.isSuccess()) {
            logger.error("BlockSign[{}] check sign error, cause:{}", blockSign, checkSignRes.getErrMsg());
            return false;
        } else {
            return true;
        }
    }

    public Pair<RetrievalStatus, List<Pair<Block, BlockSign>>> retrievalBlocks(long startNumber, long endNumber) {
        var latestExecutedBlock = this.stateLedger.getLatestBeExecutedNum();
        logger.info("receive retrieval block from {} to {}, self be executed is {}!", startNumber, endNumber, latestExecutedBlock);
        if (latestExecutedBlock < endNumber) {
            return Pair.of(RetrievalStatus.NOT_FOUND, Collections.emptyList());
        } else {
            var retrievalEndHeight = Math.min(startNumber + MAX_BLOCK_RETRIEVAL_NUM, endNumber);
            var status = retrievalEndHeight < endNumber ? RetrievalStatus.CONTINUE : RetrievalStatus.SUCCESS;
            var commitBlocks = this.stateLedger.getCommitBlocksByNumber(startNumber, endNumber);
            return Pair.of(status, commitBlocks);
        }
    }
}
