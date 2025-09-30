package com.chat.protocol.server;

import com.chat.protocol.config.Config;
import com.chat.protocol.handler.ConnectionHandler;
import com.chat.protocol.ssl.TyrusSslConfigurator;
import org.glassfish.tyrus.server.Server;
import javax.websocket.server.ServerEndpointConfig;
import java.util.logging.Logger;
import java.util.logging.Level;

public class WebSocketServer {
    private static final Logger LOG = Logger.getLogger(WebSocketServer.class.getName());
    private Server server;

    public void start(int port) {
        try {
            ServerEndpointConfig config = ServerEndpointConfig.Builder
                .create(ConnectionHandler.class, "/chat")
                .configurator(new TyrusSslConfigurator())
                .build();
            server = new Server("localhost", port, "/chat", null, ConnectionHandler.class);
            server.start();
            LOG.log(Level.INFO, "WSS Server started on wss://localhost:{0}/chat", port);
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Server start failed: {0}", e.getMessage());
            throw new RuntimeException("Failed to start server", e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
            LOG.info("WSS Server stopped");
        }
    }

    public static void main(String[] args) {
        WebSocketServer wsServer = new WebSocketServer();
        wsServer.start(Config.DEFAULT_PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(wsServer::stop));
    }
}