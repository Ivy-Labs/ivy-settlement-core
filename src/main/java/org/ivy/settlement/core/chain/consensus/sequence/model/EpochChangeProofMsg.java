package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.core.chain.consensus.sequence.safety.Verifier;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.List;

/**
 * description:
 * @author carrot
 */
public class EpochChangeProofMsg extends ConsensusMsg {

    /// The verification of the validator change proof starts with some verifier that is trusted by the
    /// client: could be either a waypoint (upon startup) or a known validator verifier.
    public static enum VerifierType {
        WAY_POINT,
        TRUSTED_VERIFIER;
    }

    private List<LedgerInfoWithSignatures> ledgerInfoWithSigns;

    private boolean more;

    private EpochChangeProofMsg() {super(null);}

    public EpochChangeProofMsg(List<LedgerInfoWithSignatures> ledgerInfoWithSigs, boolean more) {
        super(null);
        this.ledgerInfoWithSigns = ledgerInfoWithSigs;
        this.more = more;
        this.rlpEncoded = rlpEncoded();
    }

    public static EpochChangeProofMsg build(List<LedgerInfoWithSignatures> ledgerInfoWithSignaturesList, boolean more) {
        EpochChangeProofMsg epochChangeProofMsg = new EpochChangeProofMsg();
        epochChangeProofMsg.ledgerInfoWithSigns = ledgerInfoWithSignaturesList;
        return epochChangeProofMsg;
    }

    public ProcessResult<LedgerInfoWithSignatures> verify(Verifier verifier) {
        if (CollectionUtils.isEmpty(ledgerInfoWithSigns)) {
            return ProcessResult.ofError("The EpochChangeProof is empty");
        }
        LedgerInfoWithSignatures lastLedgerInfoSig = ledgerInfoWithSigns.get(ledgerInfoWithSigns.size() - 1);
        if (verifier.isLedgerInfoStale(lastLedgerInfoSig.getLedgerInfo())) {
            return ProcessResult.ofError("The EpochChangeProof is stale as our verifier is already ahead of the entire EpochChangeProof");
        }


        for (LedgerInfoWithSignatures ledgerInfoWithSignatures: ledgerInfoWithSigns) {

            // Skip any stale ledger infos in the proof prefix. Note that with
            // the assertion above, we are guaranteed there is at least one
            // non-stale ledger info in the proof.
            //
            // It's useful to skip these stale ledger infos to better allow for
            // concurrent client requests.
            //
            // For example, suppose the following:
            //
            // 1. My current trusted state is at epoch 5.
            // 2. I make two concurrent requests to two validators A and B, who
            //    live at epochs 9 and 11 respectively.
            //
            // If A's response returns first, I will ratchet my trusted state
            // to epoch 9. When B's response returns, I will still be able to
            // ratchet forward to 11 even though B's EpochChangeProof
            // includes a bunch of stale ledger infos (for epochs 5, 6, 7, 8).
            //
            // Of course, if B's response returns first, we will reject A's
            // response as it's completely stale.
            if (verifier.isLedgerInfoStale(ledgerInfoWithSignatures.getLedgerInfo())) continue;

            // Try to verify each (epoch -> epoch + 1) jump in the EpochChangeProof.
            ProcessResult<Void> verifyRes = verifier.verify(lastLedgerInfoSig);
            if (!verifyRes.isSuccess()) {
                return ProcessResult.ofError(verifyRes.getErrMsg());
            }

            // While the original verification could've been via waypoints,
            // all the next epoch changes are verified using the (already
            // trusted) validator sets.
            if (ledgerInfoWithSignatures.getLedgerInfo().getNewCurrentEpochState().isPresent()) {
                verifier = ledgerInfoWithSignatures.getLedgerInfo().getNewCurrentEpochState().get();
            } else {
                return  ProcessResult.ofError("ledgerInfoWithSignatures has not next epoch state, " + ledgerInfoWithSignatures);
            }

        }

        return ProcessResult.ofSuccess(lastLedgerInfoSig);
    }


    public long getEpoch() {
        if (CollectionUtils.isEmpty(this.ledgerInfoWithSigns)) {
            throw new RuntimeException("ledgerInfoWithSigns must exist!");
        }
        return ledgerInfoWithSigns.get(0).getLedgerInfo().getEpoch();
    }

    @Override
    public byte getCode() {
        return ConsensusCommand.EPOCH_CHANGE.getCode();
    }

    @Override
    public ConsensusCommand getCommand() {
        return ConsensusCommand.EPOCH_CHANGE;
    }
}
