package crypto;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;

/**
 * 演示Person B的完整加密功能
 * 包括RSA密钥生成、混合加密、数字签名等
 */
public class CryptoDemo {

    public static void main(String[] args) {
        try {
            System.out.println("Person B 加密模块集成演示");
            System.out.println("================================");

            // 初始化加密实例
            SecureCrypto crypto = new SecureCrypto();

            // 1. 生成RSA密钥对
            System.out.println("\n1. 生成RSA密钥对...");
            KeyPair aliceKeys = crypto.generateRSAKeys();
            KeyPair bobKeys = crypto.generateRSAKeys();

            PublicKey alicePub = aliceKeys.getPublic();
            PrivateKey alicePriv = aliceKeys.getPrivate();
            PublicKey bobPub = bobKeys.getPublic();
            PrivateKey bobPriv = bobKeys.getPrivate();

            System.out.println("Alice和Bob的RSA密钥对已生成");

            // 2. 测试基础AES加密
            System.out.println("\n2. 测试AES-256-GCM加密...");
            byte[] aesKey = new byte[32]; // 256-bit key
            new java.security.SecureRandom().nextBytes(aesKey);

            String plaintext = "Hello World! This is a test message.";
            byte[] encrypted = crypto.encryptAES(plaintext.getBytes(), aesKey);
            byte[] decrypted = crypto.decryptAES(encrypted, aesKey);

            System.out.println("原文: " + plaintext);
            System.out.println("解密: " + new String(decrypted));
            System.out.println("AES加密测试成功");

            // 3. 测试数字签名
            System.out.println("\n3. 测试RSA数字签名...");
            byte[] message = "Important message".getBytes();
            byte[] signature = crypto.sign(message, alicePriv);
            boolean isValid = crypto.verify(message, signature, alicePub);

            System.out.println("签名验证结果: " + (isValid ? "成功" : "失败"));

            // 4. 测试Header签名功能
            System.out.println("\n4. 测试Header签名功能...");
            Header signedHeader = crypto.sign_header("alice", alicePriv);
            System.out.println("生成的Header:");
            System.out.println("  senderID: " + signedHeader.senderID);
            System.out.println("  time: " + signedHeader.time);
            System.out.println("  nonce: " + signedHeader.nonce);
            System.out.println("  signature: " + signedHeader.signb64.substring(0, 20) + "...");

            // 5. 测试混合加密功能
            System.out.println("\n5. 测试混合加密 (RSA-OAEP + AES-GCM)...");
            String secretMessage = "This is Alice's secret message to Bob!";

            // Alice加密给Bob
            EncryptedPacket packet = crypto.encrypyAndwrap(secretMessage.getBytes(), bobPub);
            System.out.println("消息已用Bob的公钥加密");
            System.out.println("  算法: " + packet.algo);
            System.out.println("  密钥封装: " + packet.method);
            System.out.println("  加密密钥: " + packet.ekB64.substring(0, 20) + "...");
            System.out.println("  加密内容: " + packet.encryptedText.substring(0, 20) + "...");

            // Bob解密
            byte[] decryptedMessage = crypto.verityAnddecrypt(signedHeader, alicePub, bobPriv, packet);
            System.out.println("Bob解密结果: " + new String(decryptedMessage));
            System.out.println("混合加密测试成功");

            // 6. 测试防重放攻击
            System.out.println("\n6. 测试防重放攻击...");
            try {
                // 尝试重复使用同一个header
                crypto.verityAnddecrypt(signedHeader, alicePub, bobPriv, packet);
                System.out.println("重放攻击检测失败");
            } catch (SecurityException e) {
                System.out.println("重放攻击被正确检测: " + e.getMessage());
            }

            System.out.println("\n所有加密功能测试完成！");
            System.out.println("Person B的加密模块已成功集成到项目中");

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}