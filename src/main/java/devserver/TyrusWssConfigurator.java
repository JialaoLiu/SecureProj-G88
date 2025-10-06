package devserver;

import javax.net.ssl.*;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.HandshakeRequest;
import javax.websocket.HandshakeResponse;
import org.glassfish.tyrus.server.TyrusServerContainer;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.logging.Logger;
import java.util.logging.Level;

public class TyrusWssConfigurator extends ServerEndpointConfig.Configurator {
    private static final Logger LOG = Logger.getLogger(TyrusWssConfigurator.class.getName());
    private static SSLContext sslContext;

    static {
        try {
            sslContext = createSecureSSLContext();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to init SSLContext: {0}", e.getMessage());
            throw new RuntimeException("SSLContext init failed", e);
        }
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        super.modifyHandshake(sec, request, response);
        try {
            // Enhanced SSL session handling for Tyrus
            sec.getUserProperties().put("org.glassfish.tyrus.ssl.context", sslContext);
            LOG.log(Level.FINE, "SSL context injected for WebSocket handshake");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to modify handshake: {0}", e.getMessage());
        }
    }

    public static SSLContext getSSLContext() {
        return sslContext;
    }

    private static SSLContext createSecureSSLContext() throws Exception {
        // Load keystore
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = TyrusWssConfigurator.class.getClassLoader()
            .getResourceAsStream("localhost.jks")) {
            if (is == null) {
                throw new FileNotFoundException("Keystore not found: localhost.jks");
            }
            ks.load(is, "changeit".toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, "changeit".toCharArray());

        // Load truststore (use same as keystore for self-signed)
        KeyStore ts = KeyStore.getInstance("JKS");
        try (InputStream is = TyrusWssConfigurator.class.getClassLoader()
            .getResourceAsStream("localhost.jks")) {
            if (is == null) {
                throw new FileNotFoundException("Truststore not found: localhost.jks");
            }
            ts.load(is, "changeit".toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    public static SSLEngine createSSLEngine(String peerHost, int peerPort) {
        try {
            SSLEngine engine = sslContext.createSSLEngine(peerHost, peerPort);
            SSLParameters params = engine.getSSLParameters();
            params.setProtocols(new String[]{"TLSv1.3"});
            params.setCipherSuites(new String[]{
                "TLS_AES_256_GCM_SHA384",
                "TLS_AES_128_GCM_SHA256",
                "TLS_CHACHA20_POLY1305_SHA256"
            });
            engine.setSSLParameters(params);
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(false); // Allow browser connections
            return engine;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "SSLEngine creation failed: {0}", e.getMessage());
            throw new RuntimeException("SSLEngine init failed", e);
        }
    }
}