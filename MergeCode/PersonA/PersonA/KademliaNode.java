package socp.dht;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;


public class KademliaNode {

    private final String nodeId; 
    private final InetSocketAddress address; 
    private final String publicKey; 
    private long lastSeenTimestamp; 
    

    public KademliaNode(String nodeId, InetSocketAddress address, String publicKey) {
        this.nodeId = nodeId;
        this.address = address;
        this.publicKey = publicKey;
        this.lastSeenTimestamp = System.currentTimeMillis();
    }


    public String getNodeId() { return nodeId; }
    public InetSocketAddress getAddress() { return address; }
    public String getPublicKey() { return publicKey; }
    public long getLastSeenTimestamp() { return lastSeenTimestamp; }


    public void markAsAlive() {
        this.lastSeenTimestamp = System.currentTimeMillis();
    }


    public int calculateDistance(String targetId) {
        return nodeId.compareTo(targetId); 
    }
    

    public Map<String, Object> toPayloadMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("user_id", this.nodeId);
        map.put("host", this.address.getHostString());
        map.put("port", this.address.getPort());
        map.put("pubkey", this.publicKey);
        return map;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KademliaNode that = (KademliaNode) o;
        return nodeId.equals(that.nodeId);
    }

    @Override
    public int hashCode() {
        return nodeId.hashCode();
    }
}