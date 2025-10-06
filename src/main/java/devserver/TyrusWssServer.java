package devserver;

import org.glassfish.tyrus.server.Server;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.*;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

@ServerEndpoint(value = "/chat", configurator = TyrusWssConfigurator.class)
public class TyrusWssServer {
    private static final Logger LOG = Logger.getLogger(TyrusWssServer.class.getName());
    private Server server;
    private ChatWebSocketHandler handler = new ChatWebSocketHandler();

    @OnOpen
    public void onOpen(Session session) {
        try {
            // 使用ChatWebSocketHandler处理连接
            handler.onOpen(null); // Tyrus使用不同的连接对象
            LOG.info("Tyrus WebSocket connection opened: " + session.getId());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error in onOpen: " + e.getMessage(), e);
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        try {
            // 使用ChatWebSocketHandler处理消息
            handler.onMessage(null, message); // 需要适配
            LOG.fine("Tyrus message received: " + message);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error in onMessage: " + e.getMessage(), e);
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        try {
            handler.onClose(null, 0, "", false);
            LOG.info("Tyrus WebSocket connection closed: " + session.getId());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Error in onClose: " + e.getMessage(), e);
        }
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        handler.onError(null, new Exception(throwable));
        LOG.log(Level.SEVERE, "Tyrus WebSocket error: " + throwable.getMessage(), throwable);
    }

    public void start(int port, boolean useSSL) {
        try {
            String protocol = useSSL ? "wss" : "ws";

            // 创建Tyrus服务器
            server = new Server("localhost", port, "/", null, TyrusWssServer.class);

            if (useSSL) {
                // 配置SSL上下文
                server.getServerContainer().getWebSocketContainer().setDefaultMaxSessionIdleTimeout(0);
            }

            server.start();
            LOG.log(Level.INFO, "{0} Server started on {1}://localhost:{2}/chat",
                new Object[]{protocol.toUpperCase(), protocol, port});

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Server start failed: {0}", e.getMessage());
            throw new RuntimeException("Failed to start server", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
            LOG.info("Server stopped");
        }
    }

    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        boolean useSSL = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;

        TyrusWssServer server = new TyrusWssServer();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));

        server.start(port, useSSL);

        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            LOG.info("Server interrupted");
        }
    }
}