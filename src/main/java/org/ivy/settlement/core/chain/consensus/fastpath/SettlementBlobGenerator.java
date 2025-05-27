package org.ivy.settlement.core.chain.consensus.fastpath;

import org.ivy.settlement.core.chain.ledger.model.Block;
import org.ivy.settlement.core.chain.ledger.model.SettlementBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * description:
 * @author carrot
 */
public interface SettlementBlobGenerator {

    Logger logger = LoggerFactory.getLogger("fast_path");

    void start();

    void stop();

    void accumulate(Block block);

    SettlementBatch generateBlob() throws InterruptedException;
}
