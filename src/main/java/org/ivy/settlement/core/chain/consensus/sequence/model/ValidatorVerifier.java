package org.ivy.settlement.core.chain.consensus.sequence.model;

import org.ivy.settlement.infrastructure.crypto.VerifyingKey;
import org.ivy.settlement.infrastructure.anyhow.ProcessResult;
import org.ivy.settlement.infrastructure.bytes.ByteArrayWrapper;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.ethereum.model.event.VoterUpdateEvent;
import org.ivy.settlement.core.chain.ledger.model.RLPModel;
import org.ivy.settlement.core.chain.ledger.model.ValidatorPublicKeyInfo;
import org.ivy.settlement.ethereum.model.settlement.Signature;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * description:
 * @author carrot
 */
public class ValidatorVerifier extends RLPModel {

    private static final Logger logger = LoggerFactory.getLogger("consensus");

    public static ValidatorVerifier convertFrom(List<ValidatorPublicKeyInfo> validatorKeys) {
        return new ValidatorVerifier(validatorKeys);
    }

    private long ethEpoch;

    TreeMap<ByteArrayWrapper, ValidatorPublicKeyInfo> pk2ValidatorInfo;

    /**
     *  The minimum voting power required to achieve a quorum
     */
    long quorumVotingPower;

    /**
     *  The remaining voting power required
     */
    long remainingVotingPower;

    /**
     * Total voting power of all validators (cached from address_to_validator_info)
     */
    long totalVotingPower;

    List<byte[]> orderPubKeys;


    public ValidatorVerifier(byte[] encode) {
        super(encode);
    }

    public ValidatorVerifier(TreeMap<ByteArrayWrapper, ValidatorPublicKeyInfo> pk2ValidatorInfo) {
        super(null);
        this.pk2ValidatorInfo = pk2ValidatorInfo;
        calculate(pk2ValidatorInfo.values());
        this.rlpEncoded = rlpEncoded();
    }

    public ValidatorVerifier(List<ValidatorPublicKeyInfo> validatorPublicKeyInfos) {
        super(null);
        covertMap(validatorPublicKeyInfos);
        calculate(this.pk2ValidatorInfo.values());
        this.rlpEncoded = rlpEncoded();
    }

    public long getQuorumVotingPower() {
        return quorumVotingPower;
    }

    public long getTotalVotingPower() {
        return totalVotingPower;
    }

    private void covertMap(List<ValidatorPublicKeyInfo> validatorPublicKeyInfos) {
        var pk2ValidatorInfo = new TreeMap<ByteArrayWrapper, ValidatorPublicKeyInfo>();
        if (!CollectionUtils.isEmpty(validatorPublicKeyInfos)) {
            validatorPublicKeyInfos.forEach(validatorPublicKeyInfo -> pk2ValidatorInfo.put(
                    new ByteArrayWrapper(ByteUtil.copyFrom(validatorPublicKeyInfo.getConsensusPublicKey().getKey()))
                    , validatorPublicKeyInfo.clone()));
        }
        this.pk2ValidatorInfo = pk2ValidatorInfo;
    }

    private void calculate(Collection<ValidatorPublicKeyInfo> validatorPublicKeyInfos) {
        long totalVotingPower = 0;
        if (!CollectionUtils.isEmpty(validatorPublicKeyInfos)) {
            for (var validatorInfo: validatorPublicKeyInfos) {
                totalVotingPower += validatorInfo.getConsensusVotingPower();
            }
            this.totalVotingPower = totalVotingPower;
            this.quorumVotingPower = totalVotingPower * 2 / 3 + 1;
            this.remainingVotingPower = this.totalVotingPower - this.quorumVotingPower;
        }
    }


    public ValidatorVerifier addNewValidator(ValidatorPublicKeyInfo newValidatorPKeyInfo) {
        var validatorPublicKeyInfos = exportPKInfos();

        var oldPkInfo = this.pk2ValidatorInfo.get(new ByteArrayWrapper(newValidatorPKeyInfo.getAccountAddress()));
        if (oldPkInfo == null) {
            validatorPublicKeyInfos.add(newValidatorPKeyInfo);
        }

        return new ValidatorVerifier(validatorPublicKeyInfos);
    }

    public ValidatorVerifier removeOldValidator(ValidatorPublicKeyInfo removeValidatorPKInfo) {
        var validatorPublicKeyInfos = new ArrayList<ValidatorPublicKeyInfo>(pk2ValidatorInfo.size());

        for (var validatorPublicKeyInfo: pk2ValidatorInfo.values()) {
            if (removeValidatorPKInfo.equals(validatorPublicKeyInfo)) {
                continue;
            }

            validatorPublicKeyInfos.add(validatorPublicKeyInfo.clone());
        }

        return new ValidatorVerifier(validatorPublicKeyInfos);
    }

    public TreeMap<ByteArrayWrapper, ValidatorPublicKeyInfo> getPk2ValidatorInfo() {
        return pk2ValidatorInfo;
    }

    public List<byte[]> getOrderedPublishKeys() {
        // Since `address_to_validator_info` is a `BTreeMap`, the `.keys()` iterator
        // is guaranteed to be sorted.
        if (orderPubKeys == null) {
            orderPubKeys = pk2ValidatorInfo.keySet().stream().map(ByteArrayWrapper::getData).collect(Collectors.toList());
        }
        return orderPubKeys;
    }

    public List<ValidatorPublicKeyInfo> exportPKInfos() {
        var result = new ArrayList<ValidatorPublicKeyInfo>(pk2ValidatorInfo.values().size());
        pk2ValidatorInfo.values().forEach(validatorInfo -> result.add(validatorInfo.clone()));
        return result;
    }

    public VerifyResult checkNumOfSignatures(Map<ByteArrayWrapper, Signature> aggregatedSignature) {
        var numOfSignatures = aggregatedSignature.size();
        if (numOfSignatures > this.pk2ValidatorInfo.size()) {
            return VerifyResult.ofTooManySignatures(Pair.of(numOfSignatures, pk2ValidatorInfo.size()));
        }
        return VerifyResult.ofSuccess();
    }

    public VerifyResult checkRemainingVotingPower(Set<ByteArrayWrapper> set) {
        long aggregatedVotingPower = 0;
        for (var accountAddr: set) {
            var votingPower = getVotingPower(accountAddr);
            if (votingPower == null) {
                return VerifyResult.ofUnknownAuthor();
            }
            aggregatedVotingPower += votingPower;
        }

        if (aggregatedVotingPower < remainingVotingPower) {
            return VerifyResult.ofTooLittleVotingPower(Pair.of(aggregatedVotingPower, this.remainingVotingPower));
        }

        return VerifyResult.ofSuccess();
    }


    public VerifyResult checkVotingPower(Set<ByteArrayWrapper> set) {
        long aggregatedVotingPower = 0;
        for (var accountAddr: set) {
            var votingPower = getVotingPower(accountAddr);
            if (votingPower == null) {
                return VerifyResult.ofUnknownAuthor();
            }
            aggregatedVotingPower += votingPower;
        }

        if (aggregatedVotingPower < quorumVotingPower) {
            return VerifyResult.ofTooLittleVotingPower(Pair.of(aggregatedVotingPower, this.quorumVotingPower));
        }

        return VerifyResult.ofSuccess();
    }



    // 聚合签名验证
    public ProcessResult<Void> batchVerifyAggregatedSignature(byte[] hash, TreeMap<ByteArrayWrapper, Signature> aggregatedSignature) {
        // todo，聚合签名验证失败的情况下，直接调用verifyAggregatedSignature
//        VerifyResult checkNumRes = checkNumOfSignatures(aggregatedSignature);
//        if (!checkNumRes.isSuccess()) return ProcessResult.ofError("checkNumOfSignatures error, " + checkNumRes);
//
//        VerifyResult checkVotingPowerRes = checkVotingPower(aggregatedSignature.keySet());
//        if (!checkVotingPowerRes.isSuccess()) return ProcessResult.ofError("checkVotingPower error, " + checkVotingPowerRes);
        return verifyAggregatedSignature(hash, aggregatedSignature);
    }

    //逐个签名验证
    public ProcessResult<Void> verifyAggregatedSignature(byte[] hash, Map<ByteArrayWrapper, Signature> aggregatedSignature) {
        var checkNumRes = checkNumOfSignatures(aggregatedSignature);
        if (!checkNumRes.isSuccess()) return ProcessResult.ofError("checkNumOfSignatures error, " + checkNumRes);

        var checkVotingPowerRes = checkVotingPower(aggregatedSignature.keySet());
        if (!checkVotingPowerRes.isSuccess()) return ProcessResult.ofError("checkVotingPower error, " + checkVotingPowerRes);

        for (var entry: aggregatedSignature.entrySet()) {
            var verifyResult = this.verifySignature(entry.getKey(), hash, entry.getValue());
            if (!verifyResult.isSuccess()) {
                return ProcessResult.ofError("verifyAggregatedSignature error, " + verifyResult);
            }
        }

        return ProcessResult.SUCCESSFUL;
    }

    private Long  getVotingPower(ByteArrayWrapper author) {
        var validatorInfo = this.pk2ValidatorInfo.get(author);
        if (validatorInfo == null) {
            return null;
        } else {
            return validatorInfo.getConsensusVotingPower();
        }
    }

    public VerifyResult verifySignature(ByteArrayWrapper authorWrapper, byte[] hash, Signature signature) {
        var publicKey = getPublicKey(authorWrapper);
        if (publicKey == null) {
            return VerifyResult.ofUnknownAuthor();
        }

        return publicKey.verifySignature(hash, signature);
    }

    private ValidatorPublicKeyInfo getPublicKey(ByteArrayWrapper author) {
        return this.pk2ValidatorInfo.get(author);
    }

    public boolean containPublicKey(ByteArrayWrapper pk) {
        return this.pk2ValidatorInfo.containsKey(pk);
    }


    public void updateVerifier(List<VoterUpdateEvent> updateValidatorEvents, long currentEthEpoch) {
        for (var event : updateValidatorEvents) {
            var ethPubKey = VerifyingKey.generateETHKey(event.getPubKey());
            var ethPubKeyBytes = ethPubKey.getSecurePublicKey().getPubKey();
            switch (event.getOpType()) {
                case VoterUpdateEvent.NODE_UPDATE -> {
                    //var pubKey = ByteUtil.copyFrom(event.getPubKey());

                    var pre = this.pk2ValidatorInfo.put(new ByteArrayWrapper(ethPubKeyBytes), new ValidatorPublicKeyInfo(event.getSlotIndex(), event.getConsensusVotingPower(), ethPubKey));
                    var totalPower = this.totalVotingPower + event.getConsensusVotingPower() - (pre == null? 0 : pre.getConsensusVotingPower());
                    if (totalPower != event.getTotalVotingPower()) {
                        logger.error("global state error! for {}, pre total voting power is {}, after update is {}", event, this.totalVotingPower, totalPower);
                        System.exit(0);
                    }
                    this.totalVotingPower = totalPower;
                }
                case VoterUpdateEvent.NODE_DEATH -> {
                    var pre = this.pk2ValidatorInfo.remove(new ByteArrayWrapper(ethPubKeyBytes));
                    if (pre == null) {
                        logger.error("global state error! for un know node death [{}]", event);
                        System.exit(0);
                    }
//                    var totalPower = this.totalVotingPower - pre.getConsensusVotingPower();
//                    if (totalPower != event.getTotalVotingPower()) {
//                        logger.error("global state error! for {}, pre total voting power is {}, after node death is {}", event, this.totalVotingPower, totalPower);
//                        System.exit(0);
//                    }
                    if (currentEthEpoch + 1 == event.getOpEthEpoch()) {
                        this.totalVotingPower = this.totalVotingPower - pre.getConsensusVotingPower();
                    }
                }
                default -> {
                    logger.error("un know {}", event);
                    System.exit(0);
                }
            }
        }

        this.quorumVotingPower = totalVotingPower * 2 / 3 + 1;
        this.remainingVotingPower = this.totalVotingPower - this.quorumVotingPower;
        this.rlpEncoded = rlpEncoded();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidatorVerifier that = (ValidatorVerifier) o;
        return quorumVotingPower == that.quorumVotingPower &&
                totalVotingPower == that.totalVotingPower &&
                Objects.equals(pk2ValidatorInfo, that.pk2ValidatorInfo);
    }

    @Override
    protected byte[] rlpEncoded() {
        byte[][] validatorPublicKeyInfos = new byte[pk2ValidatorInfo.size()][];
        int i = 0;
        for (ValidatorPublicKeyInfo validatorPublicKeyInfo: pk2ValidatorInfo.values()) {
            validatorPublicKeyInfos[i] = validatorPublicKeyInfo.getEncoded();
            i++;
        }

        return RLP.encodeList(validatorPublicKeyInfos);
    }

    @Override
    protected void rlpDecoded() {
        var params = RLP.decode2(rlpEncoded);
        var verifier = (RLPList) params.get(0);
        //RLPList validatorPublicKeyInfos = (RLPList) verifier.get(0);

        var address2ValidatorInfo = new TreeMap<ByteArrayWrapper, ValidatorPublicKeyInfo>();
        for (var rlpElement: verifier) {
            var validatorPublicKeyInfo = new ValidatorPublicKeyInfo(rlpElement.getRLPData());
            address2ValidatorInfo.put(new ByteArrayWrapper(validatorPublicKeyInfo.getAccountAddress()), validatorPublicKeyInfo);
        }

        this.pk2ValidatorInfo = address2ValidatorInfo;
        calculate(this.pk2ValidatorInfo.values());
    }

    @Override
    public String toString() {
        return "vv{" +
                "quorumVotingPower=" + quorumVotingPower +
                ", totalVotingPower=" + totalVotingPower +
                ", p2vi=" + pk2ValidatorInfo +
                '}';
    }
}

