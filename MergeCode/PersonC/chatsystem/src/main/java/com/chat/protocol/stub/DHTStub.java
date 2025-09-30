package com.chat.protocol.stub;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;

public class DHTStub {
    private static final Logger LOG = Logger.getLogger(DHTStub.class.getName());
    private static final Map<String, String> peerPubKeys = new HashMap<>();

    static {
        peerPubKeys.put("localhost:8081", "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...");
    }

    public static String lookupPeerIP(String targetId) {
        if (targetId == null) {
            LOG.warning("Null targetId in lookupPeerIP");
            return null;
        }
        LOG.log(Level.INFO, "DHT lookup for peer IP: {0}", targetId);
        return "localhost:8081";
    }

    public static String lookupPubKey(String peerId) {
        if (peerId == null) {
            LOG.warning("Null peerId in lookupPubKey");
            return null;
        }
        String pubKey = peerPubKeys.getOrDefault(peerId, null);
        LOG.log(Level.INFO, "DHT lookup for pubkey of {0}: {1}", new Object[]{peerId, pubKey != null ? "found" : "not found"});
        return pubKey;
    }

    public static void insertPubKey(String peerId, String pubKey) {
        if (peerId == null || pubKey == null) {
            LOG.warning("Invalid input for insertPubKey");
            return;
        }
        peerPubKeys.put(peerId, pubKey);
        LOG.log(Level.INFO, "Inserted pubkey for peer: {0}", peerId);
    }
}