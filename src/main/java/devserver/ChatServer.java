package devserver;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;

public class ChatServer extends WebSocketServer {
    private final Schema schema;
    private final Map<WebSocket, Rate> rates = new ConcurrentHashMap<>();
    private final Set<String> usedNonces = ConcurrentHashMap.newKeySet();
    private final Map<String, WebSocket> clients = new ConcurrentHashMap<>();

    static class Rate { long windowStartMs = System.currentTimeMillis(); int count = 0; }

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
    }

    @Override public void onStart() { System.out.println("[WS] started on " + getAddress()); }

    @Override public void onOpen(WebSocket conn, ClientHandshake handshake) {
        System.out.println("[WS] open " + conn.getRemoteSocketAddress());
    }

    @Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        System.out.println("[WS] close " + conn.getRemoteSocketAddress() + " reason=" + reason);
        rates.remove(conn);
        // 移除客户端注册
        clients.entrySet().removeIf(entry -> entry.getValue() == conn);
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
                    System.out.println("[WS] registered client: " + clientId);
                }
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

    public static void main(String[] args) {
        int port = 8080;
        if (args != null && args.length > 0) port = Integer.parseInt(args[0]);
        ChatServer s = new ChatServer(port);
        s.start();
        System.out.println("[WS] listening on ws://127.0.0.1:" + port);
    }
}