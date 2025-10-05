package socp.dht;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;

/**
 * SHA-256 Hash Utility for DHT Node IDs and Key Hashing
 * Implements PersonA's requirement for proper cryptographic hashing
 */
public class HashUtil {

    /**
     * Generate SHA-256 hash for DHT node ID from username
     * As specified in PersonA's requirements: "Node ID generation (SHA-256 hash of username)"
     */
    public static String generateNodeId(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        return sha256Hash(username.trim());
    }

    /**
     * Generate SHA-256 hash for DHT keys
     * Replaces PersonA's simple hashCode() implementation
     */
    public static String hashKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Key cannot be null or empty");
        }
        return sha256Hash(key);
    }

    /**
     * Core SHA-256 implementation
     */
    private static String sha256Hash(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert to hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            // This should never happen as SHA-256 is always available
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Calculate XOR distance between two SHA-256 hashes
     * Used for Kademlia routing table bucket calculation
     */
    public static String xorDistance(String hash1, String hash2) {
        if (hash1 == null || hash2 == null || hash1.length() != hash2.length()) {
            throw new IllegalArgumentException("Hashes must be non-null and same length");
        }

        StringBuilder distance = new StringBuilder();
        for (int i = 0; i < hash1.length(); i++) {
            int val1 = Character.digit(hash1.charAt(i), 16);
            int val2 = Character.digit(hash2.charAt(i), 16);
            distance.append(Integer.toHexString(val1 ^ val2));
        }
        return distance.toString();
    }

    /**
     * Get leading zero count for bucket calculation
     * Used to determine which bucket a node belongs to in routing table
     */
    public static int getLeadingZeroBits(String hexHash) {
        if (hexHash == null || hexHash.isEmpty()) {
            return 0;
        }

        int leadingZeros = 0;
        for (char c : hexHash.toCharArray()) {
            int val = Character.digit(c, 16);
            if (val == 0) {
                leadingZeros += 4; // 4 bits per hex digit
            } else {
                // Count leading zeros in this hex digit
                leadingZeros += Integer.numberOfLeadingZeros(val) - 28; // Java int is 32 bits, hex is 4
                break;
            }
        }
        return leadingZeros;
    }

    /**
     * Check if hash1 is closer to target than hash2
     * Used for DHT lookup operations
     */
    public static boolean isCloser(String hash1, String hash2, String target) {
        String dist1 = xorDistance(hash1, target);
        String dist2 = xorDistance(hash2, target);
        return dist1.compareTo(dist2) < 0;
    }
}