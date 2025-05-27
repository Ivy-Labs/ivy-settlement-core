package org.ivy.settlement.core.chain.network.peer;

/**
 * description:
 * @author carrot
 */
public class BootNode {

    String peerId;

    String host;

    int port;

    public BootNode(String peerId, String host, int port) {
        this.peerId = peerId;
        this.host = host;
        this.port = port;
    }

    public String getPeerId() {
        return peerId;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public String toString() {
        return "BootNode{" +
                "peerId='" + peerId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                '}';
    }
}
