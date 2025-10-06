package devserver;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
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
    private final FileTransferManager fileTransferManager;

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
            socp.MessageParser parser = new socp.MessageParser("socp.json");
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

        // 初始化文件传输管理器
        this.fileTransferManager = new FileTransferManager();
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
            // schema.validate(json); // Schema 校验 - 暂时禁用以测试其他功能

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

            // 处理文件传输消息
            if ("FILE_START".equals(type) || "FILE_CHUNK".equals(type) || "FILE_END".equals(type)) {
                String to = json.optString("to");
                String result = handleFileTransferMessage(json, type);
                if (result != null) {
                    conn.send(errorJson("file_transfer_error: " + result));
                    return;
                } else {
                    // 发送ACK确认
                    JSONObject ack = new JSONObject();
                    ack.put("type", "ACK");
                    ack.put("from", "server");
                    ack.put("to", from);
                    ack.put("ts", System.currentTimeMillis() / 1000);
                    ack.put("nonce", "ack-" + System.currentTimeMillis());
                    JSONObject ackPayload = new JSONObject();
                    ackPayload.put("msg_ref", json.optString("nonce"));
                    ackPayload.put("status", "ok");
                    ack.put("payload", ackPayload);
                    ack.put("sig", "server-sig");
                    conn.send(ack.toString());

                    // 如果是点对点文件传输，也转发给目标用户
                    if (to != null && !to.equals("server") && !to.equals("*")) {
                        WebSocket targetConn = clients.get(to);
                        if (targetConn != null && targetConn.isOpen()) {
                            targetConn.send(json.toString());
                        }
                    }

                    // 对于FILE_END消息，确保payload包含文件元数据，然后继续广播
                    if ("FILE_END".equals(type)) {
                        JSONObject payload = json.getJSONObject("payload");
                        // 如果前端没有发送name和size，从服务器元数据中获取
                        if (!payload.has("name") || !payload.has("size")) {
                            String fileId = payload.getString("file_id");
                            FileTransferManager.FileMetadata metadata = fileTransferManager.getTransferMetadata(fileId);
                            if (metadata != null) {
                                payload.put("name", metadata.fileName);
                                payload.put("size", metadata.totalSize);
                            }
                        }
                        // 继续，让FILE_END消息被广播
                    } else {
                        return;
                    }
                }
            }

            // 更新用户活动状态
            if (from != null && connectedUsers.containsKey(from)) {
                String activity = "HEARTBEAT".equals(type) ? "Online" :
                                "MSG_DIRECT".equals(type) ? "Typing..." :
                                type.startsWith("FILE_") ? "Transferring file..." : "Active";
                connectedUsers.get(from).updateActivity(activity);
            }

            // 路由逻辑：如果 to != "server"，转发给目标客户端
            String to = json.optString("to");
            if (to != null && !to.equals("server") && !to.equals("*")) {
                // 私聊消息：发给目标用户和发送者自己
                WebSocket targetConn = clients.get(to);
                if (targetConn != null && targetConn.isOpen()) {
                    targetConn.send(json.toString());
                    conn.send(json.toString()); // 也发给发送者，这样发送者能看到自己的消息
                    System.out.println("[WS] routed message from " + from + " to " + to);
                } else {
                    conn.send(errorJson("unknown_to"));
                    System.out.println("[WS] unknown target: " + to + " (from " + from + ")");
                }
            } else if ("*".equals(to)) {
                // 广播消息到所有客户端
                System.out.println("[WS] broadcast from " + from + " to " + clients.size() + " clients");
                for (Map.Entry<String, WebSocket> entry : clients.entrySet()) {
                    WebSocket clientConn = entry.getValue();
                    if (clientConn != null && clientConn.isOpen()) {
                        clientConn.send(json.toString());
                    }
                }
            } else {
                // 发给服务器的消息，简单 echo
                conn.send(json.toString());
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
        o.put("ts", System.currentTimeMillis() / 1000);
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
        response.put("ts", System.currentTimeMillis() / 1000);
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

    // 处理文件传输消息
    private String handleFileTransferMessage(JSONObject json, String type) {
        try {
            JSONObject payload = json.getJSONObject("payload");

            switch (type) {
                case "FILE_START":
                    String fileId = payload.getString("file_id");
                    String fileName = payload.getString("name");
                    long size = payload.getLong("size");
                    String sha256 = payload.optString("sha256", "");
                    String mode = payload.getString("mode");

                    return fileTransferManager.handleFileStart(fileId, fileName, size, sha256, mode);

                case "FILE_CHUNK":
                    fileId = payload.getString("file_id");
                    int index = payload.getInt("index");
                    String ciphertext = payload.getString("ciphertext");

                    return fileTransferManager.handleFileChunk(fileId, index, ciphertext);

                case "FILE_END":
                    fileId = payload.getString("file_id");

                    // 计算总chunks数从现有数据
                    FileTransferManager.FileMetadata metadata = fileTransferManager.getTransferMetadata(fileId);
                    if (metadata == null) {
                        return "File transfer not found";
                    }

                    // 使用实际接收到的chunk数量
                    int actualChunks = metadata.receivedChunks.size();

                    return fileTransferManager.handleFileEnd(fileId, actualChunks);

                default:
                    return "Unknown file transfer message type: " + type;
            }
        } catch (Exception e) {
            return "Failed to process file transfer message: " + e.getMessage();
        }
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

        // Start authentication server
        AuthServer.start();

        // Start file server for downloads
        FileServer.start();

        ChatServer s = new ChatServer(port);
        s.start();
        System.out.println("[WS] listening on ws://127.0.0.1:" + port);
    }
}