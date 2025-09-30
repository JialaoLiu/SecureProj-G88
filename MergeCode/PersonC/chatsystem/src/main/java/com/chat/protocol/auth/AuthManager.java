package com.chat.protocol.auth;

import com.chat.protocol.stub.DHTStub;
import javax.net.ssl.SSLSession;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.logging.Logger;
import java.util.logging.Level;

public class AuthManager {
    private static final Logger LOG = Logger.getLogger(AuthManager.class.getName());

    public static boolean authenticate(SSLSession session, String peerId) {
        if (session == null || peerId == null || peerId.trim().isEmpty()) {
            LOG.log(Level.WARNING, "Invalid input: session={0}, peerId={1}", 
                    new Object[]{session == null ? "null" : "non-null", peerId});
            return false;
        }

        try {
            Certificate[] certs = session.getPeerCertificates();
            if (certs == null || certs.length == 0 || !(certs[0] instanceof X509Certificate)) {
                LOG.warning("No valid peer certificate found");
                return false;
            }

            X509Certificate peerCert = (X509Certificate) certs[0];
            String certPubKey = Base64.getEncoder().encodeToString(peerCert.getPublicKey().getEncoded());

            String expectedPubKey = DHTStub.lookupPubKey(peerId);
            if (expectedPubKey == null) {
                LOG.warning("Peer ID not found in DHT: " + peerId);
                return false;
            }
            boolean isValid = expectedPubKey.equals(certPubKey);
            if (!isValid) {
                LOG.warning("Certificate public key mismatch for peer: " + peerId);
            }
            return isValid;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Authentication failed for peer {0}: {1}", 
                    new Object[]{peerId, e.getMessage()});
            return false;
        }
    }

    public static String generateNonce() {
        try {
            byte[] nonceBytes = new byte[16];
            new java.security.SecureRandom().nextBytes(nonceBytes);
            String nonce = Base64.getEncoder().encodeToString(nonceBytes);
            LOG.log(Level.FINE, "Generated nonce: {0}", nonce);
            return nonce;
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Nonce generation failed: {0}", e.getMessage());
            return null;
        }
    }
}