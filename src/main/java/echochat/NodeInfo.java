package echochat;

public class NodeInfo {
    public String nodeId;
    public String host;
    public int port;
    public String publicKey;
    public long lastSeen;

    public NodeInfo() {
        this.lastSeen = System.currentTimeMillis();
    }

    public NodeInfo(String nodeId, String host, int port, String publicKey) {
        this();
        this.nodeId = nodeId;
        this.host = host;
        this.port = port;
        this.publicKey = publicKey;
    }

    @Override
    public String toString() {
        return "NodeInfo{" +
                "nodeId='" + nodeId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", lastSeen=" + lastSeen +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeInfo nodeInfo = (NodeInfo) o;
        return nodeId.equals(nodeInfo.nodeId);
    }

    @Override
    public int hashCode() {
        return nodeId.hashCode();
    }
}