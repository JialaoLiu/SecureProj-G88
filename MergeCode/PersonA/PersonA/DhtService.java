package socp.dht;

import socp.Message;
import socp.MessageTypes;
import socp.MessageParser;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.stream.Collectors;


public class DhtService {
    
    private final KademliaNode localNode;
    private final RoutingTable routingTable;
    private final MessageParser parser;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    
    @FunctionalInterface
    public interface WebSocketSender {
        void sendToPeer(String peerId, String jsonMessage);
    }
    private final WebSocketSender wsSender;


    private static final int TTL_MAX = 10;
    private static final int HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int NODE_TIMEOUT_SECONDS = 60; 
    private static final int MULTIPATH_FORWARD_COUNT = 3; 


    private final AtomicLong requestIdCounter = new AtomicLong();
    private final Map<Long, NodeLookupCallback> pendingNodeLookups = new ConcurrentHashMap<>();
    private final Map<Long, ValueLookupCallback> pendingValueLookups = new ConcurrentHashMap<>();
    

    private final Map<String, Object> localStorage = new ConcurrentHashMap<>();
    private final Map<String, Long> valueExpiry = new ConcurrentHashMap<>();
    private static final long DEFAULT_TTL = 24 * 60 * 60 * 1000;

    public DhtService(
        String localNodeId, 
        InetSocketAddress localAddress, 
        String localPublicKey, 
        MessageParser parser,
        WebSocketSender wsSender
    ) {
        this.localNode = new KademliaNode(localNodeId, localAddress, localPublicKey);
        this.routingTable = new RoutingTable(localNodeId);
        this.parser = parser;
        this.wsSender = wsSender;
        startBackgroundTasks();
    }
    
    private void startBackgroundTasks() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 0, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::performRoutingMaintenance, 
                                      HEARTBEAT_INTERVAL_SECONDS + 5, HEARTBEAT_INTERVAL_SECONDS + 5, TimeUnit.SECONDS);
        scheduler.scheduleAtFixedRate(this::cleanupExpiredValues, 1, 60, TimeUnit.MINUTES);
    }
    
    
    private void sendHeartbeat() {
        try {
            Map<String, Object> payload = new HashMap<>(); 
            String heartbeatJson = parser.buildMessage(MessageTypes.HEARTBEAT, localNode.getNodeId(), "*", payload);
            List<KademliaNode> neighbors = routingTable.findKNearest(localNode.getNodeId());
            for (KademliaNode neighbor : neighbors) {
                wsSender.sendToPeer(neighbor.getNodeId(), heartbeatJson);
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to send HEARTBEAT: " + e.getMessage());
        }
    }
    
    private void performRoutingMaintenance() {
        long timeoutCutoff = System.currentTimeMillis() - (NODE_TIMEOUT_SECONDS * 1000L);
        int removedCount = 0;
        
        for (KademliaNode node : routingTable.getOnlineMembers()) {
            if (node.getLastSeenTimestamp() < timeoutCutoff) {
                routingTable.removeNode(node.getNodeId());
                removedCount++;
            }
        }
        
        if (routingTable.size() < 5 || removedCount > 0) {
            sendFindNode(localNode.getNodeId());
        }
    }
    
    private void sendFindNode(String targetId) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("target_id", targetId); 
            String findNodeJson = parser.buildMessage(MessageTypes.FIND_NODE, localNode.getNodeId(), targetId, payload);
            List<KademliaNode> neighbors = routingTable.findKNearest(targetId);
            for (KademliaNode neighbor : neighbors) {
                wsSender.sendToPeer(neighbor.getNodeId(), findNodeJson);
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to send FIND_NODE: " + e.getMessage());
        }
    }

    public void processIncomingMessage(String rawJson) {
        try {
            Message msg = parser.parseJson(rawJson);
            
            // 处理消息类型
            if (msg.getType().equals(MessageTypes.HEARTBEAT)) {
                handleHeartbeat(msg);
                return; 
            } else if (msg.getType().equals(MessageTypes.FIND_NODE)) {
                handleFindNode(msg);
                return; 
            } else if (msg.getType().equals(MessageTypes.FIND_NODE_RESP)) {
                handleFindNodeResponse(msg);
            } else if (msg.getType().equals(MessageTypes.SERVER_WELCOME)) {
                handleServerWelcome(msg);
            } else if (msg.getType().equals(MessageTypes.USER_REMOVE)) {
                handleUserRemove(msg);
            } else if (msg.getType().equals(MessageTypes.USER_ADVERTISE) || msg.getType().equals(MessageTypes.SERVER_ANNOUNCE)) {
                 handleNodeDiscovery(msg);
            }
            // --- 新增消息类型 ---
            else if (msg.getType().equals(MessageTypes.STORE_VALUE)) {
                handleStoreValue(msg);
                return;
            } else if (msg.getType().equals(MessageTypes.FIND_VALUE)) {
                handleFindValue(msg);
                return;
            } else if (msg.getType().equals(MessageTypes.VALUE_RESPONSE)) {
                handleValueResponse(msg);
                return;
            }
            
            // 路由转发
            if (!msg.getTo().equals(localNode.getNodeId()) && !msg.getTo().equals("*")) {
                forwardMessage(msg);
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to process incoming message: " + e.getMessage());
        }
    }
    
    private void handleFindNode(Message msg) {
        try {
            String targetId = (String) msg.getPayload().get("target_id");
            String requesterId = msg.getFrom();
            if (targetId == null) return;

            List<KademliaNode> nearestNodes = routingTable.findKNearest(targetId);
            List<Map<String, Object>> nodesPayload = nearestNodes.stream()
                .map(KademliaNode::toPayloadMap)
                .collect(Collectors.toList());
            
            Map<String, Object> responsePayload = new HashMap<>();
            responsePayload.put("nodes", nodesPayload);
            responsePayload.put("target_id", targetId);
            
            String responseJson = parser.buildMessage(MessageTypes.FIND_NODE_RESP, localNode.getNodeId(), requesterId, responsePayload);
            wsSender.sendToPeer(requesterId, responseJson);
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to process FIND_NODE: " + e.getMessage());
        }
    }
    
    private void handleFindNodeResponse(Message msg) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) msg.getPayload().get("nodes");
            Long requestId = (Long) msg.getPayload().get("request_id");
            
            if (nodes != null) {
                for (Map<String, Object> nodePayload : nodes) {
                    processNodeDiscoveryPayload(nodePayload, "user_id");
                }
            }
            
            if (requestId != null) {
                NodeLookupCallback callback = pendingNodeLookups.remove(requestId);
                if (callback != null && nodes != null) {
                    List<KademliaNode> resultNodes = nodes.stream()
                        .map(payload -> createNodeFromPayload(payload, "user_id"))
                        .filter(node -> node != null)
                        .collect(Collectors.toList());
                    callback.onSuccess(resultNodes);
                }
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to process FIND_NODE_RESP: " + e.getMessage());
        }
    }

    private void forwardMessage(Message msg) {
        int currentTtl = (int) msg.getPayload().getOrDefault("ttl", TTL_MAX);
        if (currentTtl <= 0) return;

        msg.getPayload().put("ttl", currentTtl - 1);
        String targetId = msg.getTo();
        
        List<KademliaNode> nextHops = routingTable.findKNearest(targetId).stream()
            .filter(node -> !node.getNodeId().equals(msg.getFrom()))
            .limit(MULTIPATH_FORWARD_COUNT)
            .collect(Collectors.toList());

        if (nextHops.isEmpty()) return;

        try {
            String forwardedJson = parser.serialize(msg);
            for (KademliaNode nextHop : nextHops) {
                wsSender.sendToPeer(nextHop.getNodeId(), forwardedJson);
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to serialize message for forwarding: " + e.getMessage());
        }
    }
    
    private void handleHeartbeat(Message msg) {
        String nodeId = msg.getFrom();
        KademliaNode existingNode = routingTable.getNode(nodeId);
        if (existingNode != null) {
            existingNode.markAsAlive();
        } 
    }
    
    private void handleServerWelcome(Message msg) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> clients = (List<Map<String, Object>>) msg.getPayload().get("clients");
            if (clients != null) {
                for (Map<String, Object> clientPayload : clients) {
                    processNodeDiscoveryPayload(clientPayload, "user_id");
                }
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to process SERVER_WELCOME: " + e.getMessage());
        }
    }

    private void handleUserRemove(Message msg) {
        try {
            String userId = (String) msg.getPayload().get("user_id");
            if (userId != null) {
                routingTable.removeNode(userId);
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to process USER_REMOVE: " + e.getMessage());
        }
    }
    
    private void handleNodeDiscovery(Message msg) {
        String idKey = msg.getType().equals(MessageTypes.USER_ADVERTISE) ? "user_id" : "server_id";
        processNodeDiscoveryPayload(msg.getPayload(), idKey);
    }
    
    private void processNodeDiscoveryPayload(Map<String, Object> payload, String idKey) {
        try {
            String nodeId = (String) payload.get(idKey); 
            String ip = (String) payload.get("host");
            Object portObj = payload.get("port"); 
            int port = portObj instanceof Integer ? (Integer) portObj : ((Number) portObj).intValue();
            String pubKey = (String) payload.get("pubkey");

            if (nodeId != null && ip != null && port > 0 && pubKey != null) {
                KademliaNode newNode = new KademliaNode(nodeId, new InetSocketAddress(ip, port), pubKey);
                routingTable.insertNode(newNode);
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to process node discovery payload: " + e.getMessage());
        }
    }


    public interface DhtCallback<T> {
        void onSuccess(T result);
        void onFailure(String error);
    }

    public interface NodeLookupCallback extends DhtCallback<List<KademliaNode>> {
    }

    public interface ValueLookupCallback extends DhtCallback<Object> {
    }


    public void dhtLookup(String targetId, NodeLookupCallback callback) {
        long requestId = requestIdCounter.incrementAndGet();
        pendingNodeLookups.put(requestId, callback);
        
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("target_id", targetId);
            payload.put("request_id", requestId);
            payload.put("lookup_type", "node");
            
            String findNodeJson = parser.buildMessage(MessageTypes.FIND_NODE, localNode.getNodeId(), targetId, payload);
            List<KademliaNode> neighbors = routingTable.findKNearest(targetId);
            for (KademliaNode neighbor : neighbors) {
                wsSender.sendToPeer(neighbor.getNodeId(), findNodeJson);
            }
        } catch (Exception e) {
            pendingNodeLookups.remove(requestId);
            callback.onFailure("Failed to initiate lookup: " + e.getMessage());
        }
    }


    public void dhtInsert(String key, Object value) {
        dhtInsert(key, value, DEFAULT_TTL);
    }
    
    public void dhtInsert(String key, Object value, long ttl) {
        try {
            localStorage.put(key, value);
            valueExpiry.put(key, System.currentTimeMillis() + ttl);
            
            String hashedKey = hashKey(key);
            Map<String, Object> storePayload = new HashMap<>();
            storePayload.put("key", key);
            storePayload.put("value", value);
            storePayload.put("ttl", ttl);
            storePayload.put("timestamp", System.currentTimeMillis());
            
            String storeJson = parser.buildMessage(MessageTypes.STORE_VALUE, localNode.getNodeId(), hashedKey, storePayload);
            
            routingTable.findKNearest(hashedKey).stream()
                .filter(node -> !node.getNodeId().equals(localNode.getNodeId()))
                .limit(3)
                .forEach(node -> wsSender.sendToPeer(node.getNodeId(), storeJson));
                
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to store value: " + e.getMessage());
        }
    }

    public void dhtLookup(String key, ValueLookupCallback callback) {
        long requestId = requestIdCounter.incrementAndGet();
        pendingValueLookups.put(requestId, callback);
        
        try {
            Long expiry = valueExpiry.get(key);
            if (localStorage.containsKey(key) && expiry != null && expiry > System.currentTimeMillis()) {
                pendingValueLookups.remove(requestId);
                callback.onSuccess(localStorage.get(key));
                return;
            }
            
            String hashedKey = hashKey(key);
            Map<String, Object> findPayload = new HashMap<>();
            findPayload.put("key", key);
            findPayload.put("hashed_key", hashedKey);
            findPayload.put("request_id", requestId);
            findPayload.put("lookup_type", "value");
            
            String findValueJson = parser.buildMessage(MessageTypes.FIND_VALUE, localNode.getNodeId(), hashedKey, findPayload);
            routingTable.findKNearest(hashedKey).forEach(neighbor -> 
                wsSender.sendToPeer(neighbor.getNodeId(), findValueJson));
                
        } catch (Exception e) {
            pendingValueLookups.remove(requestId);
            callback.onFailure("Failed to initiate value lookup: " + e.getMessage());
        }
    }

    private void handleStoreValue(Message msg) {
        try {
            String key = (String) msg.getPayload().get("key");
            Object value = msg.getPayload().get("value");
            Long ttl = (Long) msg.getPayload().get("ttl");
            Long timestamp = (Long) msg.getPayload().get("timestamp");
            
            if (key != null && value != null && ttl != null && timestamp != null) {
                if (System.currentTimeMillis() > timestamp + ttl) return;
                
                localStorage.put(key, value);
                valueExpiry.put(key, timestamp + ttl);
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to process STORE_VALUE: " + e.getMessage());
        }
    }

    private void handleFindValue(Message msg) {
        try {
            String key = (String) msg.getPayload().get("key");
            String hashedKey = (String) msg.getPayload().get("hashed_key");
            Long requestId = (Long) msg.getPayload().get("request_id");
            String requesterId = msg.getFrom();
            
            if (key == null) return;
            
            Map<String, Object> responsePayload = new HashMap<>();
            Long expiry = valueExpiry.get(key);
            
            if (localStorage.containsKey(key) && expiry != null && expiry > System.currentTimeMillis()) {
                responsePayload.put("found", true);
                responsePayload.put("key", key);
                responsePayload.put("value", localStorage.get(key));
            } else {
                responsePayload.put("found", false);
                List<Map<String, Object>> nodesPayload = routingTable.findKNearest(hashedKey).stream()
                    .map(KademliaNode::toPayloadMap)
                    .collect(Collectors.toList());
                responsePayload.put("nodes", nodesPayload);
            }
            
            if (requestId != null) responsePayload.put("request_id", requestId);
            
            String responseJson = parser.buildMessage(MessageTypes.VALUE_RESPONSE, localNode.getNodeId(), requesterId, responsePayload);
            wsSender.sendToPeer(requesterId, responseJson);
            
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to process FIND_VALUE: " + e.getMessage());
        }
    }

    private void handleValueResponse(Message msg) {
        try {
            Boolean found = (Boolean) msg.getPayload().get("found");
            Long requestId = (Long) msg.getPayload().get("request_id");
            
            if (requestId != null) {
                ValueLookupCallback callback = pendingValueLookups.remove(requestId);
                if (callback != null) {
                    if (found != null && found) {
                        callback.onSuccess(msg.getPayload().get("value"));
                    } else {
                        callback.onFailure("Value not found in DHT");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to process VALUE_RESPONSE: " + e.getMessage());
        }
    }

    private void cleanupExpiredValues() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> iterator = valueExpiry.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Long> entry = iterator.next();
            if (entry.getValue() < now) {
                localStorage.remove(entry.getKey());
                iterator.remove();
            }
        }
    }

    private String hashKey(String key) {
        return Integer.toHexString(key.hashCode());
    }

    private KademliaNode createNodeFromPayload(Map<String, Object> payload, String idKey) {
        try {
            String nodeId = (String) payload.get(idKey);
            String ip = (String) payload.get("host");
            Object portObj = payload.get("port");
            int port = portObj instanceof Integer ? (Integer) portObj : ((Number) portObj).intValue();
            String pubKey = (String) payload.get("pubkey");

            if (nodeId != null && ip != null && port > 0 && pubKey != null) {
                return new KademliaNode(nodeId, new InetSocketAddress(ip, port), pubKey);
            }
        } catch (Exception e) {
            System.err.println("[DHT Error] Failed to create node from payload: " + e.getMessage());
        }
        return null;
    }


    public List<KademliaNode> getOnlineMembers() {
        return routingTable.getOnlineMembers();
    }
}