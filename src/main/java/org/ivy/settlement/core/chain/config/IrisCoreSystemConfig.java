package org.ivy.settlement.core.chain.config;

import com.typesafe.config.Config;
import io.libp2p.core.peer.PeerId;
import org.bouncycastle.util.encoders.Hex;
import org.ivy.settlement.infrastructure.config.SystemConfig;
import org.ivy.settlement.infrastructure.config.ValidateMe;
import org.ivy.settlement.infrastructure.crypto.key.asymmetric.SecureKey;
import org.ivy.settlement.infrastructure.string.StringUtils;
import org.ivy.settlement.core.chain.ledger.model.Genesis;
import org.ivy.settlement.core.chain.ledger.model.genesis.GenesisJson;
import org.ivy.settlement.core.chain.ledger.model.genesis.GenesisLoader;
import org.ivy.settlement.core.chain.network.peer.BootNode;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.URL;
import java.util.*;

import static io.libp2p.core.crypto.keys.Secp256k1.unmarshalSecp256k1PrivateKey;


/**
 * description:
 * @author carrot
 */
public class IrisCoreSystemConfig extends SystemConfig {

    //private static Logger logger = LoggerFactory.getLogger("general");

    private GenesisJson genesisJson;

    private Genesis genesis;

    // mutable options for tests
    private String databaseDir = null;

    private final ClassLoader classLoader;

    private GenerateNodeIdStrategy generateNodeIdStrategy = null;

    private Map<String, String> generatedNodePrivateKey;

    private SecureKey myKey;

    private PeerId myPeerId;

    private String projectVersion = null;

    private String projectVersionModifier = null;

    private String bindIp = null;

    public IrisCoreSystemConfig() {
        this("thanos-chain.conf");
    }

    public IrisCoreSystemConfig(String configName) {
        super(configName);
        try {
            this.classLoader = IrisCoreSystemConfig.class.getClassLoader();
            List<InputStream> iStreams = loadResources("version.properties", this.classLoader);
            for (InputStream is : iStreams) {
                Properties props = new Properties();
                props.load(is);
                if (props.getProperty("versionNumber") == null || props.getProperty("databaseVersion") == null) {
                    continue;
                }
                this.projectVersion = props.getProperty("versionNumber");
                this.projectVersion = this.projectVersion.replaceAll("'", "");

                if (this.projectVersion == null) this.projectVersion = "-.-.-";

                this.projectVersionModifier = "master".equals(BuildInfo.buildBranch) ? "RELEASE" : "SNAPSHOT";
                this.generateNodeIdStrategy = new GetNodeIdFromPropsFile(dbPath());
                break;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * Loads resources using given ClassLoader assuming, there could be several resources
     * with the same name
     */
    private List<InputStream> loadResources(
            final String name, final ClassLoader classLoader) throws IOException {
        final var list = new ArrayList<InputStream>();
        final var systemResources =
                (classLoader == null ? ClassLoader.getSystemClassLoader() : classLoader)
                        .getResources(name);
        while (systemResources.hasMoreElements()) {
            list.add(systemResources.nextElement().openStream());
        }
        return list;
    }

    public Config getConfig() {
        return config;
    }



    public Genesis getGenesis() {
        if (genesis == null) {
            genesis = GenesisLoader.parseGenesis(getGenesisJson());
        }
        return genesis;
    }

    public GenesisJson getGenesisJson() {
        if (genesisJson == null) {
            genesisJson = GenesisLoader.loadGenesisJson(this, classLoader);
        }
        return genesisJson;
    }

    public SecureKey getMyKey() {
        if (myKey != null) {
            return myKey;
        }


        Map<String, String> caNodeInfoMap = generatedCaNodeInfoMap();
        String privateKey = caNodeInfoMap.get("nodeIdPrivateKey");
        String name = caNodeInfoMap.get("name");
        String agency = caNodeInfoMap.get("agency");
        String caHash = caNodeInfoMap.get("caHash");

        if (StringUtils.isEmpty(name) || StringUtils.isEmpty(agency) || StringUtils.isEmpty(caHash)) {
            throw new RuntimeException("be short of ca node info!");
        }

        this.myKey = SecureKey.fromPrivate(Hex.decode(privateKey));
        return myKey;
    }

    private Map<String, String> generatedCaNodeInfoMap() {
        if (generatedNodePrivateKey == null) {
            generatedNodePrivateKey = generateNodeIdStrategy.getCaNodeInfoMap();
        }
        return generatedNodePrivateKey;
    }

    public PeerId getPeerId() {
        if (this.myPeerId == null) {
            this.myPeerId = PeerId.fromPubKey(unmarshalSecp256k1PrivateKey(getMyKey().getRawPrivKeyBytes()).publicKey());
            //this.nodeId = getMyKey().doGetPubKey();
        }
        return this.myPeerId;
    }

    //start resource
    @ValidateMe
    public String logConfigPath() {
        return config.getString("resource.logConfigPath");
    }

    @ValidateMe
    public String beaconFinalityStatePath() {
        return databaseDir == null ? config.getString("resource.beaconFinalityStatePath") : databaseDir;
    }

    @ValidateMe
    public String dbPath() {
        return databaseDir == null ? config.getString("resource.dbPath") : databaseDir;
    }
    //end resource


    //start network


    /**
     * This can be a blocking call with long timeout (thus no ValidateMe)
     */
    public String bindIp() {
        if (!config.hasPath("network.peer.bind.ip") || config.getString("network.peer.bind.ip").trim().isEmpty()) {
            if (bindIp == null) {
                //logger.info("External IP wasn't set, using checkip.amazonaws.com to identify it...");
                try {
                    var in = new BufferedReader(new InputStreamReader(
                            new URL("http://checkip.amazonaws.com").openStream()));
                    bindIp = in.readLine();
                    if (bindIp == null || bindIp.trim().isEmpty()) {
                        throw new IOException("Invalid address: '" + bindIp + "'");
                    }
                    try {
                        InetAddress.getByName(bindIp);
                    } catch (Exception e) {
                        throw new IOException("Invalid address: '" + bindIp + "'");
                    }
                    //logger.info("External address identified: {}", bindIp);
                } catch (IOException e) {
                    bindIp = "0.0.0.0";
                    //logger.warn("Can't get bindIp IP. Fall back to peer.bind.ip: " + bindIp + " :" + e);
                }
            }
            return bindIp;

        } else {
            return config.getString("network.peer.bind.ip").trim();
        }
    }

    @ValidateMe
    public int listenRpcPort() {
        return config.getInt("network.peer.listen.rpcPort");
    }

    @ValidateMe
    public Integer peerChannelReadTimeout() {
        return config.getInt("network.peer.channel.read.timeout");
    }

    @ValidateMe
    public List<BootNode> getBootNodes() {
        if (!config.hasPath("network.peer.discovery.boot.list")) {
            return Collections.EMPTY_LIST;
        }

        var boots = config.getStringList("network.peer.discovery.boot.list");
        var ret = new ArrayList<BootNode>();
        for (var bootStr : boots) {
            String[] splits = bootStr.split(":");
            var peerIdStr = splits[0];
            var ip = splits[1];
            var port = Integer.parseInt(splits[2]);
            ret.add(new BootNode(peerIdStr, ip, port));
        }

        return ret;
    }

    @ValidateMe
    public String gatewayRpcIpPort() {
        return config.getString("network.gateway.address");
    }

    public int gatewayRpcAcceptCount() {
        return config.hasPath("gateway.rpc.acceptCount") ? config.getInt("network.gateway.acceptCount") : 100;
    }
    //end network


    //start consensus
    @ValidateMe
    public int getProposerType() {
        return config.getInt("consensus.sequence.proposerType");
    }

    @ValidateMe
    public int getContiguousRounds() {
        return config.getInt("consensus.sequence.contiguousRounds");
    }

    @ValidateMe
    public int getMaxCommitEventNumInMemory() {
        return config.getInt("consensus.sequence.maxCommitEventNumInMemory");
    }

    @ValidateMe
    public int getMaxPrunedEventsInMemory() {
        return config.getInt("consensus.sequence.maxPrunedEventsInMemory");
    }

    @ValidateMe
    public int getRoundTimeoutBaseMS() {
        return config.getInt("consensus.sequence.roundTimeoutBaseMS");
    }

    @ValidateMe
    public int getMaxPackSize() {
        return config.getInt("consensus.sequence.maxPackSize");
    }

    @ValidateMe
    public boolean reimportUnCommitEvent() {
        return config.hasPath("consensus.sequence.reimportUnCommitEvent")?
                config.getBoolean("consensus.sequence.reimportUnCommitEvent"): true;
    }

    @ValidateMe
    public boolean dsCheck() {
        return config.hasPath("consensus.sequence.dsCheck")?
                config.getBoolean("consensus.sequence.dsCheck"): true;
    }

    @ValidateMe
    public int getPoolLimit() {
        return config.getInt("consensus.sequence.poolLimit");
    }

    @ValidateMe
    public int comingQueueSize() {
        return config.hasPath("consensus.sequence.comingQueueSize")? config.getInt("consensus.sequence.comingQueueSize"): 64;
    }

    public int getConsensusChainMaxOpenFiles() {
        return config.hasPath("consensusChain.sequence.storeConfig.maxOpenFiles") ? config.getInt("consensusChain.sequence.storeConfig.maxOpenFiles") : 1000;
    }

    public int getConsensusChainMaxThreads() {
        return config.hasPath("consensusChain.sequence.storeConfig.maxThreads") ? config.getInt("consensusChain.sequence.storeConfig.maxThreads") : 4;
    }

    public int getConsensusChainWriteBufferSize() {
        return config.hasPath("consensusChain.sequence.storeConfig.writeBufferSize") ? config.getInt("consensusChain.sequence.storeConfig.writeBufferSize") : 64;
    }

    public boolean getConsensusChainBloomFilterFlag() {
        return config.hasPath("consensusChain.sequence.storeConfig.bloomFilterFlag") ? config.getBoolean("consensusChain.sequence.storeConfig.bloomFilterFlag") : true;
    }

    @ValidateMe
    public int getFastPathCheckTimeoutMS() {
        return config.hasPath("consensus.fastPath.checkTimeoutMS") ? config.getInt("consensus.fastPath.checkTimeoutMS") : 5000;
    }

    @ValidateMe
    public int getFastPathMaxCommitBlockInMemory() {
        return config.hasPath("consensus.fastPath.maxCommitBlockInMemory") ? config.getInt("consensus.fastPath.maxCommitBlockInMemory") : 16;
    }

    @ValidateMe
    public int getSlowPathCheckTimeoutMS() {
        return config.hasPath("consensus.slowPath.checkTimeoutMS") ? config.getInt("consensus.slowPath.checkTimeoutMS") : 5000;
    }

    @ValidateMe
    public int getSlowPathMaxCommitBlobInMemory() {
        return config.hasPath("consensus.slowPath.maxCommitBlobInMemory") ? config.getInt("consensus.slowPath.maxCommitBlobInMemory") : 16;
    }
    //end consensus


    //--------------start ledger--------------
    public int getLedgerMaxOpenFiles() {
        return config.hasPath("ledger.storeConfig.maxOpenFiles") ? config.getInt("ledger.storeConfig.maxOpenFiles") : 1000;
    }

    public int getLedgerMaxThreads() {
        return config.hasPath("ledger.storeConfig.maxThreads") ? config.getInt("ledger.storeConfig.maxThreads") : 4;
    }

    public int getLedgerWriteBufferSize() {
        return config.hasPath("ledger.storeConfig.writeBufferSize") ? config.getInt("ledger.storeConfig.writeBufferSize") : 64;
    }

    //--------------end ledger--------------


    //start eth
    @ValidateMe
    public long getEthFinalityBlockHeight() {
        return config.getLong("eth.finality.ethBlock.height");
    }

    public List<String> getEthExecuteNodeUrls() {
        return config.getStringList("eth.execute.node.urls");
    }

    public List<String> getEthBeaconNodeUrls() {
        return config.getStringList("eth.beacon.node.urls");
    }

    public List<String> getMonitorAddresses() {
        return config.getStringList("eth.monitor.addresses");
    }

    public int getEthLogCacheSize() {
        return config.getInt("eth.logCacheSize");
    }


    public int getEthStoreMaxOpenFiles() {
        return config.hasPath("eth.storeConfig.maxOpenFiles") ? config.getInt("eth.storeConfig.maxOpenFiles") : 1000;
    }

    public int getEthStoreMaxThreads() {
        return config.hasPath("eth.storeConfig.maxThreads") ? config.getInt("eth.storeConfig.maxThreads") : 4;
    }

    public int getEthStoreWriteBufferSize() {
        return config.hasPath("eth.storeConfig.writeBufferSize") ? config.getInt("eth.storeConfig.writeBufferSize") : 64;
    }
    //end eth
}

