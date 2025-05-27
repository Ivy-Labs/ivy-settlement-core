package org.ivy.settlement.core.chain.consensus.slowpath.model;


import org.apache.commons.lang3.tuple.Pair;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.core.chain.consensus.sequence.model.RetrievalStatus;
import org.ivy.settlement.infrastructure.rlp.RLP;
import org.ivy.settlement.infrastructure.rlp.RLPList;
import org.ivy.settlement.core.chain.consensus.fastpath.model.FastPathCommand;
import org.ivy.settlement.core.chain.ledger.model.Block;
import org.ivy.settlement.core.chain.ledger.model.BlockSign;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * description:
 * @author carrot
 */
public class CommitBatchResponseMsg extends SlowPathMsg {

    static Comparator<Pair<Block, BlockSign>> COMMIT_BLOCKS_SORTER = (o1, o2) -> (int) (o1.getKey().getNumber() - o2.getKey().getNumber());

    RetrievalStatus status;

    List<Pair<Block, BlockSign>> commitBlocks;


    public CommitBatchResponseMsg(byte[] encode) {
        super(encode);
    }

    public CommitBatchResponseMsg(RetrievalStatus status, List<Pair<Block, BlockSign>> commitBlocks) {
        super(null);
        this.status = status;
        this.commitBlocks = commitBlocks;
        this.rlpEncoded = rlpEncoded();
    }

    public long getLastNumber() {
        if (this.commitBlocks.isEmpty()) {
            throw new RuntimeException("commitBlocks is empty!");
        }
        return this.commitBlocks.getLast().getLeft().getNumber();
    }

    @Override
    protected byte[] rlpEncoded() {
        var encode = new byte[this.commitBlocks.size() * 2 + 1][];
        encode[0] = RLP.encodeInt(status.ordinal());
        var i = 1;
        for (var info : this.commitBlocks) {
            encode[i] = info.getLeft().getEncoded();
            i++;
            encode[i] = info.getRight().getEncoded();
            i++;
        }
        return RLP.encodeList(encode);
    }

    @Override
    protected void rlpDecoded() {
        var params = RLP.decode2(rlpEncoded);
        var retrievalRes = (RLPList) params.get(0);
        this.status = RetrievalStatus.convertFromOrdinal(ByteUtil.byteArrayToInt(retrievalRes.get(0).getRLPData()));
        this.commitBlocks = new ArrayList<>((retrievalRes.size() - 1) / 2);
        for (int i = 1; i < retrievalRes.size(); i = i + 2) {
            this.commitBlocks.add(Pair.of(new Block(retrievalRes.get(i).getRLPData()), new BlockSign(retrievalRes.get(i + 1).getRLPData())));
        }

        commitBlocks.sort(COMMIT_BLOCKS_SORTER);
    }

    public boolean checkContent() {
        if (this.commitBlocks.isEmpty()) return true;
        if (this.commitBlocks.size() == 1) {
            var pair = this.commitBlocks.get(0);
            var block = pair.getLeft();
            var blockSign = pair.getRight();
            if (block.getNumber() != blockSign.getNumber()) return false;
            if (!Arrays.equals(block.getHash(), blockSign.getHash())) return false;
        }

        for (var i = 0; i < this.commitBlocks.size() - 1; i++) {
            var pair = this.commitBlocks.get(i);
            var block = pair.getLeft();
            var blockSign = pair.getRight();
            if (block.getNumber() != blockSign.getNumber()) return false;
            if (!Arrays.equals(block.getHash(), blockSign.getHash())) return false;
            var nextBlock = this.commitBlocks.get(i + 1);
            if (block.getNumber() + 1 != nextBlock.getKey().getNumber()) return false;
        }

        return true;
    }

    public RetrievalStatus getStatus() {
        return status;
    }

    public List<Pair<Block, BlockSign>> getCommitBlocks() {
        return commitBlocks;
    }

    @Override
    public byte getCode() {
        return FastPathCommand.COMMIT_BLOCK_RESP.getCode();
    }

    @Override
    public SlowPathCommand getCommand() {
        return SlowPathCommand.COMMIT_BLOB_RESP;
    }

    @Override
    public String toString() {
        return "CommitBlobResponseMsg{" +
                "status=" + status +
                ", commitBlocks=" + commitBlocks +
                '}';
    }
}
