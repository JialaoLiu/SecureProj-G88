package devserver;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;

public class ChatServer extends WebSocketServer {
    private final Schema schema;
    private final Map<WebSocket, Rate> rates = new ConcurrentHashMap<>();

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
            // 简单 echo（或广播）
            conn.send(json.toString());
        } catch (Exception e) {
            conn.send(errorJson("invalid_message: "+ e.getMessage()));
        }
    }

    private String errorJson(String detail){
        // 构造最小合法协议（按你们模型：type/from/ts/payload/sig）
        JSONObject o = new JSONObject();
        o.put("type", "ERROR");
        o.put("from", "server");
        o.put("ts", Instant.now().toEpochMilli());
        o.put("payload", new JSONObject().put("detail", detail));
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