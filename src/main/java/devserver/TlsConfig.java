package devserver;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * TLS Configuration for WebSocket Server
 * Based on PersonC's TyrusSslConfigurator implementation
 */
public class TlsConfig {
    private static final Logger LOG = Logger.getLogger(TlsConfig.class.getName());

    // TLS 1.3 Configuration (from PersonC's Config.java)
    public static final String TLS_PROTOCOL = "TLSv1.3";
    public static final String KEYSTORE_PATH = "src/main/resources/keystore.jks";
    public static final String TRUSTSTORE_PATH = "src/main/resources/truststore.jks";
    public static final String KEYSTORE_PASSWORD = "changeit";

    private static SSLContext sslContext;

    static {
        try {
            sslContext = createSecureSSLContext();
            LOG.info("TLS 1.3 context initialized successfully");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to init TLS context: {0}", e.getMessage());
            throw new RuntimeException("TLS context init failed", e);
        }
    }

    /**
     * Create secure SSL Context with TLS 1.3
     * Adapted from PersonC's TyrusSslConfigurator
     */
    private static SSLContext createSecureSSLContext() throws Exception {
        // Load keystore
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream fis = TlsConfig.class.getClassLoader().getResourceAsStream("keystore.jks")) {
            if (fis == null) {
                // Fallback to file system
                try (FileInputStream fileFis = new FileInputStream(KEYSTORE_PATH)) {
                    ks.load(fileFis, KEYSTORE_PASSWORD.toCharArray());
                }
            } else {
                ks.load(fis, KEYSTORE_PASSWORD.toCharArray());
            }
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, KEYSTORE_PASSWORD.toCharArray());

        // Load truststore
        KeyStore ts = KeyStore.getInstance("JKS");
        try (InputStream fis = TlsConfig.class.getClassLoader().getResourceAsStream("truststore.jks")) {
            if (fis == null) {
                // Fallback to file system
                try (FileInputStream fileFis = new FileInputStream(TRUSTSTORE_PATH)) {
                    ts.load(fileFis, KEYSTORE_PASSWORD.toCharArray());
                }
            } else {
                ts.load(fis, KEYSTORE_PASSWORD.toCharArray());
            }
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        // Create TLS 1.3 context
        SSLContext context = SSLContext.getInstance(TLS_PROTOCOL);
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        return context;
    }

    /**
     * Get configured SSL Context
     */
    public static SSLContext getSSLContext() {
        return sslContext;
    }

    /**
     * Create SSL Socket Factory for WebSocket server
     */
    public static SSLSocketFactory getSSLSocketFactory() {
        return sslContext.getSocketFactory();
    }

    /**
     * Create SSL Server Socket Factory for WebSocket server
     */
    public static SSLServerSocketFactory getSSLServerSocketFactory() {
        return sslContext.getServerSocketFactory();
    }

    /**
     * Create configured SSL Engine (from PersonC's implementation)
     */
    public static SSLEngine createSSLEngine(String peerHost, int peerPort) {
        try {
            SSLEngine engine = sslContext.createSSLEngine(peerHost, peerPort);
            SSLParameters params = engine.getSSLParameters();

            // Force TLS 1.3 only
            params.setProtocols(new String[]{TLS_PROTOCOL});

            // Use TLS 1.3 cipher suites
            params.setCipherSuites(new String[]{
                "TLS_AES_256_GCM_SHA384",
                "TLS_CHACHA20_POLY1305_SHA256",
                "TLS_AES_128_GCM_SHA256"
            });

            engine.setSSLParameters(params);
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(false); // For demo purposes

            return engine;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "SSLEngine creation failed: {0}", e.getMessage());
            throw new RuntimeException("SSLEngine init failed", e);
        }
    }
}