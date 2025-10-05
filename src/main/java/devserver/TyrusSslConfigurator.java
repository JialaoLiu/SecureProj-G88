package devserver;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.logging.Logger;
import java.util.logging.Level;

public class TyrusSslConfigurator {
    private static final Logger LOG = Logger.getLogger(TyrusSslConfigurator.class.getName());
    private static SSLContext sslContext;

    static {
        try {
            sslContext = createSecureSSLContext();
            LOG.info("TyrusSslConfigurator: SSL context initialized successfully");
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to init SSLContext: {0}", e.getMessage());
            throw new RuntimeException("SSLContext init failed", e);
        }
    }

    public static SSLContext getSSLContext() {
        return sslContext;
    }

    private static SSLContext createSecureSSLContext() throws Exception {
        // Load keystore
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(Config.KEYSTORE_PATH)) {
            ks.load(fis, Config.KEYSTORE_PASSWORD.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, Config.KEYSTORE_PASSWORD.toCharArray());

        // Load truststore (use same as keystore for self-signed)
        KeyStore ts = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(Config.TRUSTSTORE_PATH)) {
            ts.load(fis, Config.KEYSTORE_PASSWORD.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        // Create SSL context
        SSLContext context = SSLContext.getInstance(Config.TLS_PROTOCOL);
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());

        LOG.info("SSL context created with TLS 1.3 protocol");
        return context;
    }

    public static SSLEngine createSSLEngine(String peerHost, int peerPort) {
        try {
            SSLEngine engine = sslContext.createSSLEngine(peerHost, peerPort);
            SSLParameters params = engine.getSSLParameters();
            params.setProtocols(new String[]{Config.TLS_PROTOCOL});
            params.setCipherSuites(Config.CIPHER_SUITES);
            engine.setSSLParameters(params);
            engine.setUseClientMode(false);
            // Note: Don't require client auth for web browsers
            engine.setWantClientAuth(false);
            engine.setNeedClientAuth(false);
            LOG.info("SSL engine created for " + peerHost + ":" + peerPort);
            return engine;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "SSLEngine creation failed: {0}", e.getMessage());
            throw new RuntimeException("SSLEngine init failed", e);
        }
    }
}