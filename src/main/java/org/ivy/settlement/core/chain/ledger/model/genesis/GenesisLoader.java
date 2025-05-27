package org.ivy.settlement.core.chain.ledger.model.genesis;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ivy.settlement.core.chain.config.IrisCoreSystemConfig;
import org.ivy.settlement.infrastructure.bytes.ByteUtil;
import org.ivy.settlement.core.chain.ledger.model.Genesis;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;

import static org.ivy.settlement.infrastructure.bytes.ByteUtil.bytesToBigInteger;
import static org.ivy.settlement.infrastructure.bytes.ByteUtil.hexStringToBytes;

public class GenesisLoader {

    public static final int NONCE_LENGTH = 8;

    /**
     * Load genesis from passed location or from classpath `genesis` directory
     */
    public static GenesisJson loadGenesisJson(IrisCoreSystemConfig config, ClassLoader classLoader) throws RuntimeException {
        final String genesisFile = config.dbPath() + File.separator + "genesis.json";

        // #1 try to find genesis at passed location
        if (genesisFile != null) {
            try (InputStream is = new FileInputStream(new File(genesisFile))) {
                return loadGenesisJson(is);
            } catch (Exception e) {
                new RuntimeException("Problem loading genesis file from " + genesisFile);
            }
        }

//        // #2 fall back to old genesis location at `src/main/resources/genesis` directory
//        InputStream is = classLoader.getResourceAsStream("genesis/" + genesisResource);
//        if (is != null) {
//            try {
//                return loadGenesisJson(is);
//            } catch (Exception e) {
//                showLoadError("Problem loading genesis file from resource directory", genesisFile, genesisResource);
//            }
//        } else {
//            showLoadError("Genesis file was not found in resource directory", genesisFile, genesisResource);
//        }

        return null;
    }


    public static Genesis parseGenesis(GenesisJson genesisJson) throws RuntimeException {
        try {
            Genesis genesis = createBlockForJson(genesisJson);

            //genesis.setPremine(generatePreMine(blockchainNetConfig, genesisJson.getAlloc()));

//            byte[] rootHash = generateRootHash(genesis.getPremine());
//            genesis.setStateRoot(rootHash);

            return genesis;
        } catch (Exception e) {
            new RuntimeException("Problem parsing genesis error:" + e.getMessage());
        }
        return null;
    }



    /**
     * Method used much in tests.
     */
    public static Genesis loadGenesis(InputStream resourceAsStream) {
        GenesisJson genesisJson = loadGenesisJson(resourceAsStream);
        return parseGenesis(genesisJson);
    }

    public static GenesisJson loadGenesisJson(InputStream genesisJsonIS) throws RuntimeException {
        String json = null;
        try {
            json = new String(genesisJsonIS.readAllBytes());

            ObjectMapper mapper = new ObjectMapper()
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);

            GenesisJson genesisJson  = mapper.readValue(json, GenesisJson.class);
            return genesisJson;
        } catch (Exception e) {

            throw new RuntimeException(e.getMessage(), e);
        }
    }


    private static Genesis createBlockForJson(GenesisJson genesisJson) {

        var nonce       = prepareNonce(hexStringToBytes(genesisJson.nonce));
        var mixHash     = hexStringToBytesValidate(genesisJson.mixhash, 32, false);
        var coinbase    = hexStringToBytesValidate(genesisJson.coinbase, 20, false);

        var timestampBytes = hexStringToBytesValidate(genesisJson.timestamp, 8, true);
        var timestamp         = ByteUtil.byteArrayToLong(timestampBytes);

        var parentHash  = hexStringToBytesValidate(genesisJson.parentHash, 32, false);

        var gasLimitBytes    = hexStringToBytesValidate(genesisJson.gasLimit, 8, true);
        var gasLimit         = ByteUtil.byteArrayToLong(gasLimitBytes);

        return new Genesis(0, 0, genesisJson.maxShardingNum, genesisJson.shardingNum, genesisJson.genesisSettlementConfig.mainChainStartNumber, genesisJson.genesisSettlementConfig.followerChainOffsets);
    }



    private static byte[] hexStringToBytesValidate(String hex, int bytes, boolean notGreater) {
        byte[] ret = hexStringToBytes(hex);
        if (notGreater) {
            if (ret.length > bytes) {
                throw new RuntimeException("Wrong value length: " + hex + ", expected length < " + bytes + " bytes");
            }
        } else {
            if (ret.length != bytes) {
                throw new RuntimeException("Wrong value length: " + hex + ", expected length " + bytes + " bytes");
            }
        }
        return ret;
    }

    /**
     * Prepares nonce to be correct length
     * @param nonceUnchecked    unchecked, user-provided nonce
     * @return  correct nonce
     * @throws RuntimeException when nonce is too long
     */
    private static byte[] prepareNonce(byte[] nonceUnchecked) {
        if (nonceUnchecked.length > 8) {
            throw new RuntimeException(String.format("Invalid nonce, should be %s length", NONCE_LENGTH));
        } else if (nonceUnchecked.length == 8) {
            return nonceUnchecked;
        }
        byte[] nonce = new byte[NONCE_LENGTH];
        int diff = NONCE_LENGTH - nonceUnchecked.length;
        for (int i = diff; i < NONCE_LENGTH; ++i) {
            nonce[i] = nonceUnchecked[i - diff];
        }
        return nonce;
    }



    /**
     * @param rawValue either hex started with 0x or dec
     * return BigInteger
     */
    private static BigInteger parseHexOrDec(String rawValue) {
        if (rawValue != null) {
            return rawValue.startsWith("0x") ? bytesToBigInteger(hexStringToBytes(rawValue)) : new BigInteger(rawValue);
        } else {
            return BigInteger.ZERO;
        }
    }

//    public static byte[] generateRootHash(Map<ByteArrayWrapper, PremineAccount> premine){
//
//        Trie<byte[]> state = new SecureTrie((byte[]) null);
//
//        for (ByteArrayWrapper key : premine.keySet()) {
//            state.put(key.getData(), premine.get(key).accountState.rlpEncoded());
//        }
//
//        return state.getRootHash();
//    }
}
