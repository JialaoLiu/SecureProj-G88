package com.chat.protocol.ssl;

import com.chat.protocol.config.Config;
import javax.net.ssl.*;
import javax.websocket.server.ServerEndpointConfig;
import javax.websocket.HandshakeRequest;
import javax.websocket.HandshakeResponse;
import org.glassfish.tyrus.server.TyrusHandshakeRequest;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileNotFoundException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.logging.Logger;
import java.util.logging.Level;

public class TyrusSslConfigurator extends ServerEndpointConfig.Configurator {
    private static final Logger LOG = Logger.getLogger(TyrusSslConfigurator.class.getName());
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
            if (request instanceof TyrusHandshakeRequest) {
                SSLSession sslSession = ((TyrusHandshakeRequest) request).getSslSession();
                if (sslSession != null) {
                    sec.getUserProperties().put("org.glassfish.tyrus.client.sslSession", sslSession);
                    LOG.log(Level.FINE, "SSL session injected for handshake");
                } else {
                    LOG.warning("No SSL session available in handshake");
                }
            } else {
                LOG.warning("HandshakeRequest is not TyrusHandshakeRequest");
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Failed to modify handshake: {0}", e.getMessage());
        }
    }

    public static SSLContext getSSLContext() {
        return sslContext;
    }

    private static SSLContext createSecureSSLContext() throws Exception {
        KeyStore ks = KeyStore.getInstance("JKS");
        try (InputStream is = TyrusSslConfigurator.class.getClassLoader()
            .getResourceAsStream(Config.KEYSTORE_PATH)) {
            if (is == null) {
                throw new FileNotFoundException("Keystore not found: " + Config.KEYSTORE_PATH);
            }
            ks.load(is, Config.KEYSTORE_PASSWORD.toCharArray());
        }

        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, Config.KEYSTORE_PASSWORD.toCharArray());

        KeyStore ts = KeyStore.getInstance("JKS");
        try (InputStream is = TyrusSslConfigurator.class.getClassLoader()
            .getResourceAsStream(Config.TRUSTSTORE_PATH)) {
            if (is == null) {
                throw new FileNotFoundException("Truststore not found: " + Config.TRUSTSTORE_PATH);
            }
            ts.load(is, Config.KEYSTORE_PASSWORD.toCharArray());
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);

        SSLContext context = SSLContext.getInstance(Config.TLS_PROTOCOL);
        context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    public static SSLEngine createSSLEngine(String peerHost, int peerPort) {
        try {
            SSLEngine engine = sslContext.createSSLEngine(peerHost, peerPort);
            SSLParameters params = engine.getSSLParameters();
            params.setProtocols(new String[]{Config.TLS_PROTOCOL});
            params.setCipherSuites(new String[]{
                "TLS_AES_256_GCM_SHA384",
                "TLS_AES_128_GCM_SHA256",
                "TLS_CHACHA20_POLY1305_SHA256"
            });
            engine.setSSLParameters(params);
            engine.setUseClientMode(false);
            engine.setNeedClientAuth(false);
            return engine;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "SSLEngine creation failed: {0}", e.getMessage());
            throw new RuntimeException("SSLEngine init failed", e);
        }
    }
}