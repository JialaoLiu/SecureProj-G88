package devserver;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;

import socp.dht.DhtService;
import socp.dht.KademliaNode;
import socp.MessageParser;

public class ChatServer extends WebSocketServer {
    private final Schema schema;
    private final Map<WebSocket, Rate> rates = new ConcurrentHashMap<>();
    private final Set<String> usedNonces = ConcurrentHashMap.newKeySet();
    private final Map<String, WebSocket> clients = new ConcurrentHashMap<>();
    private final Map<String, UserInfo> connectedUsers = new ConcurrentHashMap<>();
    private final DhtService dhtService;

    static class Rate { long windowStartMs = System.currentTimeMillis(); int count = 0; }
    static class UserInfo {
        String userId;
        long lastSeen;
        String status;
        String activity;

        UserInfo(String userId, String status, String activity) {
            this.userId = userId;
            this.lastSeen = System.currentTimeMillis();
            this.status = status;
            this.activity = activity;
        }

        void updateActivity(String activity) {
            this.activity = activity;
            this.lastSeen = System.currentTimeMillis();
        }
    }

    public ChatServer(int port) {
        super(new InetSocketAddress(port));
        // 读取 resources/socp.json
        JSONObject raw = new JSONObject(new JSONTokener(
            ChatServer.class.getResourceAsStream("/socp.json")));
        SchemaLoader loader = SchemaLoader.builder()
            .schemaJson(raw)
            .draftV7Support()
            .build();
        this.schema = loader.load().build();

        // 初始化DHT服务
        try {
            MessageParser parser = new MessageParser("socp.json");
            this.dhtService = new DhtService(
                "server",
                new InetSocketAddress("127.0.0.1", port),
                "server-pubkey",
                parser,
                this::sendToPeer
            );
            System.out.println("[DHT] DHT service initialized");
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize DHT service", e);
        }
    }

    @Override public void onStart() { System.out.println("[WS] started on " + getAddress()); }

    @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("[WS] open " + conn.getRemoteSocketAddress());
    }

    @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("[WS] close " + conn.getRemoteSocketAddress() + " reason=" + reason);
        rates.remove(conn);

        // 移除客户端注册并从DHT中移除
        String disconnectedUser = null;
        for (Map.Entry<String, WebSocket> entry : clients.entrySet()) {
            if (entry.getValue() == conn) {
                disconnectedUser = entry.getKey();
                break;
            }
        }

        clients.entrySet().removeIf(entry -> entry.getValue() == conn);
        if (disconnectedUser != null) {
            connectedUsers.remove(disconnectedUser);
            System.out.println("[WS] user disconnected: " + disconnectedUser);
        }

        for (Map.Entry<String, WebSocket> entry : clients.entrySet()) {
            System.out.println("[WS] remaining client: " + entry.getKey());
        }
    }

    @Override public void onError(WebSocket conn, Exception ex) {
        System.err.println("[WS] error " + ex.getMessage());
    }

    private boolean allow(WebSocket c){
        Rate r = rates.computeIfAbsent(c, k -> new Rate());
        long now = System.currentTimeMillis();
        if (now - r.windowStartMs >= 1000) { r.windowStartMs = now; r.count = 0; }
        if (r.count >= 10) return false;
        r.count++; return true;
    }

    @Override public void onMessage(WebSocket conn, String msg) {
        try {
            if (!allow(conn)) {
                conn.send(errorJson("rate_limited"));
                return;
            }
            JSONObject json = new JSONObject(msg);
            schema.validate(json); // Schema 校验

            // 防重放攻击：检查nonce是否已使用
            String nonce = json.optString("nonce");
            if (nonce != null && !nonce.isEmpty()) {
                if (usedNonces.contains(nonce)) {
                    conn.send(errorJson("replay_attack_detected"));
                    return;
                }
                usedNonces.add(nonce);
                // 限制内存使用：当nonce集合过大时清理旧的（简单实现）
                if (usedNonces.size() > 10000) {
                    usedNonces.clear();
                }
            }

            // 处理客户端注册（从 USER_HELLO 获取 client ID）
            String type = json.getString("type");
            String from = json.optString("from");
            if ("USER_HELLO".equals(type) && json.has("payload")) {
                JSONObject payload = json.getJSONObject("payload");
                String clientId = payload.optString("client", from);
                if (clientId != null && !clientId.isEmpty()) {
                    clients.put(clientId, conn);
                    // 注册到DHT和在线用户列表
                    connectedUsers.put(clientId, new UserInfo(clientId, "online", "Connected"));
                    System.out.println("[WS] registered client: " + clientId);
                }
            }

            // 处理在线用户查询
            if ("USER_LIST_REQUEST".equals(type)) {
                JSONObject response = createOnlineUsersResponse();
                conn.send(response.toString());
                return;
            }

            // 更新用户活动状态
            if (from != null && connectedUsers.containsKey(from)) {
                String activity = "HEARTBEAT".equals(type) ? "Online" :
                                "MSG_DIRECT".equals(type) ? "Typing..." : "Active";
                connectedUsers.get(from).updateActivity(activity);
            }

            // 路由逻辑：如果 to != "server"，转发给目标客户端
            String to = json.optString("to");
            if (to != null && !to.equals("server") && !to.equals("*")) {
                WebSocket targetConn = clients.get(to);
                if (targetConn != null && targetConn.isOpen()) {
                    targetConn.send(json.toString());
                    System.out.println("[WS] routed message from " + from + " to " + to);
                } else {
                    conn.send(errorJson("unknown_to"));
                    System.out.println("[WS] unknown target: " + to + " (from " + from + ")");
                }
            } else {
                // 发给服务器的消息或广播，简单 echo
                conn.send(json.toString());
                if ("*".equals(to)) {
                    System.out.println("[WS] broadcast from " + from);
                }
            }
        } catch (Exception e) {
            conn.send(errorJson("invalid_message: "+ e.getMessage()));
        }
    }

    private String errorJson(String detail){
        // 构造最小合法协议（按你们模型：type/from/to/ts/nonce/payload/sig）
        JSONObject o = new JSONObject();
        o.put("type", "ERROR");
        o.put("from", "server");
        o.put("to", "client");
        o.put("ts", Instant.now().toEpochMilli());
        o.put("nonce", java.util.UUID.randomUUID().toString().replace("-", ""));
        JSONObject payload = new JSONObject();
        payload.put("code", "error");
        payload.put("detail", detail);
        o.put("payload", payload);
        o.put("sig", "dev-mock");
        return o.toString();
    }

    private JSONObject createOnlineUsersResponse() {
        JSONObject response = new JSONObject();
        response.put("type", "USER_LIST_RESPONSE");
        response.put("from", "server");
        response.put("to", "client");
        response.put("ts", Instant.now().toEpochMilli());
        response.put("nonce", java.util.UUID.randomUUID().toString().replace("-", ""));

        JSONArray users = new JSONArray();
        for (UserInfo user : connectedUsers.values()) {
            JSONObject userObj = new JSONObject();
            userObj.put("id", user.userId);
            userObj.put("name", user.userId);
            userObj.put("status", user.status);
            userObj.put("activity", user.activity);
            userObj.put("lastSeen", user.lastSeen);
            users.put(userObj);
        }

        JSONObject payload = new JSONObject();
        payload.put("online_users", users);
        payload.put("total_count", connectedUsers.size());

        response.put("payload", payload);
        response.put("sig", "server-sig");
        return response;
    }

    // DHT通信方法
    private void sendToPeer(String peerId, String jsonMessage) {
        WebSocket peerConn = clients.get(peerId);
        if (peerConn != null && peerConn.isOpen()) {
            peerConn.send(jsonMessage);
        }
    }

    public static void main(String[] args) {
        int port = 8080;
        if (args != null && args.length > 0) port = Integer.parseInt(args[0]);
        ChatServer s = new ChatServer(port);
        s.start();
        System.out.println("[WS] listening on ws://127.0.0.1:" + port);
    }
}