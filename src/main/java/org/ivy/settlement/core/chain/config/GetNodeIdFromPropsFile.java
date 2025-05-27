package org.ivy.settlement.core.chain.config;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Strategy to generate the getCaHash and the nodePrivateKey from a getRemoteNodeId.properties file.
 * <p>
 *
 * @author Lucas Saldanha
 * @since 14.12.2017
 */
public class GetNodeIdFromPropsFile implements GenerateNodeIdStrategy {

    private String databaseDir;

    GetNodeIdFromPropsFile(String databaseDir) {
        this.databaseDir = databaseDir;
    }

    @Override
    public Map<String, String> getCaNodeInfoMap() {
        Properties props = new Properties();
        File file = new File(databaseDir, "nodeInfo.properties");
        if (file.canRead()) {
            try (Reader r = new FileReader(file)) {
              props.load(r);
              Map<String, String> result = new HashMap(props);
              return  result;
            } catch (IOException e) {
              throw new RuntimeException("Error reading 'nodeInfo.properties' file", e);
            }
        } else {
            throw new RuntimeException("Can't read 'nodeInfo.properties'" + " and current path:" + databaseDir);
        }
    }

//    public GenerateNodeIdStrategy withFallback(GenerateNodeIdStrategy generateNodeIdStrategy) {
//        this.fallbackGenerateNodeIdStrategy = generateNodeIdStrategy;
//        return this;
//    }
}
