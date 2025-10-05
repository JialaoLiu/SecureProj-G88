package devserver;

import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;

import socp.dht.DhtService;
import socp.dht.KademliaNode;
import socp.MessageParser;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Secure WebSocket Chat Server with TLS 1.3 support
 * Integrates PersonC's TLS configuration with main ChatServer functionality
 */
public class SecureChatServer extends ChatServer {
    private static final System.Logger TLS_LOG = System.getLogger(SecureChatServer.class.getName());

    public SecureChatServer(int port, boolean enableTLS) {
        super(port);

        if (enableTLS) {
            try {
                System.out.println("[TLS] Starting TLS initialization with TyrusSslConfigurator...");
                // Use TyrusSslConfigurator based on PersonC's design
                SSLContext sslContext = TyrusSslConfigurator.getSSLContext();
                System.out.println("[TLS] SSLContext retrieved: " + (sslContext != null ? "SUCCESS" : "FAILED"));
                DefaultSSLWebSocketServerFactory sslFactory = new DefaultSSLWebSocketServerFactory(sslContext);
                System.out.println("[TLS] SSL WebSocket factory created");
                setWebSocketFactory(sslFactory);
                System.out.println("[TLS] TLS 1.3 context loaded successfully with JKS keystore");
            } catch (Exception e) {
                System.err.println("[TLS] Failed to enable TLS: " + e.getMessage());
                e.printStackTrace();
                throw new RuntimeException("TLS initialization failed", e);
            }
        } else {
            System.out.println("[TLS] TLS disabled by configuration");
        }
    }

    @Override
    public void onStart() {
        String protocol = getWebSocketFactory() instanceof DefaultSSLWebSocketServerFactory ? "wss" : "ws";
        System.out.println("[WSS] Secure server started on " + protocol + "://" + getAddress());

        if (protocol.equals("wss")) {
            System.out.println("[WSS] TLS 1.3 encryption active");
        } else {
            System.out.println("[WSS] Warning: Running without TLS encryption");
        }
    }

    /**
     * Main method with TLS support
     */
    public static void main(String[] args) {
        int port = 8443; // Standard HTTPS/WSS port
        boolean enableTLS = true;

        // Parse command line arguments
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number: " + args[0]);
                System.exit(1);
            }
        }

        if (args != null && args.length > 1) {
            enableTLS = Boolean.parseBoolean(args[1]);
        }

        try {
            SecureChatServer server = new SecureChatServer(port, enableTLS);
            server.start();

            String protocol = enableTLS ? "wss" : "ws";
            System.out.println("[WSS] Listening on " + protocol + "://127.0.0.1:" + port);

            if (enableTLS) {
                System.out.println("[WSS] TLS 1.3 enabled with certificate CN=localhost");
                System.out.println("[WSS] Frontend should connect to wss://localhost:" + port);
            }

            // Graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("[WSS] Shutting down secure server...");
                try {
                    server.stop();
                } catch (Exception e) {
                    System.err.println("Error during shutdown: " + e.getMessage());
                }
            }));

        } catch (Exception e) {
            System.err.println("Failed to start secure server: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}