package org.ivy.settlement.core.chain.config;

import java.util.Map;

/**
 * Strategy interface to generate the getCaHash and the nodePrivateKey.
 * <p>
 * Two strategies are available:
 * <ul>
 * <li>{@link GetNodeIdFromPropsFile}: searches for a nodeInfo.properties
 * and uses the values in the file to set the getCaHash and the nodePrivateKey.</li>
 * </ul>
 *
 * @author Lucas Saldanha
 * @since 14.12.2017
 */
public interface GenerateNodeIdStrategy {

    Map<String, String> getCaNodeInfoMap();

}
