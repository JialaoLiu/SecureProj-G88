package edu.adelaide.socp.crypto;

public final class EncryptedPacket {
    public final String algo;          // 算法名
    public final String method;        // 密钥封装方式
    public final String ekB64;         // RSA-OAEP 加密后的 AES key（Base64）
    public final String encryptedText; // AES-GCM 加密后的正文（Base64）

    public EncryptedPacket(String algo, String method, String ekB64, String encryptedText) {
        this.algo = algo;
        this.method = method;
        this.ekB64 = ekB64;
        this.encryptedText = encryptedText;
    }

    public static EncryptedPacket createStandardPacket(String ekB64, String encryptedText) {
        return new EncryptedPacket("AES-256-GCM", "RSA-OAEP", ekB64, encryptedText);
    }
}
