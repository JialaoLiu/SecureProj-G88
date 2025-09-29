package socp.dht;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


public class RoutingTable {
    
    private final String localNodeId;
    private final Map<String, KademliaNode> nodes = new ConcurrentHashMap<>();
    
    private static final int K = 5; 

    public RoutingTable(String localNodeId) {
        this.localNodeId = localNodeId;
    }


    public void insertNode(KademliaNode node) {
        if (node.getNodeId().equals(localNodeId)) {
            return;
        }
        
        nodes.put(node.getNodeId(), node);
    }


    public void removeNode(String nodeId) {
        nodes.remove(nodeId);
        System.out.println("[DHT/RoutingTable] Node removed: " + nodeId);
    }


    public List<KademliaNode> findKNearest(String targetId) {
        if (nodes.isEmpty()) {
            return Collections.emptyList();
        }

        return nodes.values().stream()
                .filter(node -> !node.getNodeId().equals(localNodeId))
                .sorted(Comparator.comparingInt(node -> Math.abs(node.calculateDistance(targetId))))
                .limit(K)
                .collect(Collectors.toList());
    }


    public KademliaNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    public List<KademliaNode> getOnlineMembers() {
        return new ArrayList<>(nodes.values());
    }
    
    public int size() {
        return nodes.size();
    }
}