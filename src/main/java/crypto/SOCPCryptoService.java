package crypto;

import socp.Message;
import socp.MessageParser;
import org.json.JSONObject;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * SOCP消息加密服务
 * 整合Person B的加密功能和SOCP协议
 */
public class SOCPCryptoService {

    private final SecureCrypto crypto;
    private final MessageParser parser;

    // 密钥存储 (实际项目中应该使用安全的密钥管理系统)
    private final Map<String, KeyPair> keyStore = new HashMap<>();
    private final Map<String, PublicKey> publicKeys = new HashMap<>();

    public SOCPCryptoService() throws Exception {
        this.crypto = new SecureCrypto();
        this.parser = new MessageParser("socp.json");
    }

    /**
     * 为用户生成并存储密钥对
     */
    public void generateUserKeys(String userId) {
        KeyPair keyPair = crypto.generateRSAKeys();
        keyStore.put(userId, keyPair);
        publicKeys.put(userId, keyPair.getPublic());
        System.out.println("✅ 为用户 " + userId + " 生成密钥对");
    }

    /**
     * 获取用户公钥
     */
    public PublicKey getUserPublicKey(String userId) {
        return publicKeys.get(userId);
    }

    /**
     * 获取用户私钥
     */
    public PrivateKey getUserPrivateKey(String userId) {
        KeyPair keyPair = keyStore.get(userId);
        return keyPair != null ? keyPair.getPrivate() : null;
    }

    /**
     * 创建并签名SOCP消息
     */
    public String createSecureMessage(String type, String from, String to,
                                    Map<String, Object> payload, String content) throws Exception {

        // 1. 生成签名的Header
        PrivateKey senderPriv = getUserPrivateKey(from);
        if (senderPriv == null) {
            throw new SecurityException("发送者 " + from + " 的私钥不存在");
        }

        Header header = crypto.sign_header(from, senderPriv);

        // 2. 准备消息payload
        Map<String, Object> messagePayload = new HashMap<>(payload);

        // 3. 如果有内容需要加密，进行加密并设置到ciphertext字段
        if (content != null && to != null && !to.equals("server") && !to.equals("*")) {
            PublicKey receiverPub = getUserPublicKey(to);
            if (receiverPub != null) {
                EncryptedPacket encryptedContent = crypto.encrypyAndwrap(content.getBytes(), receiverPub);

                // 将完整的EncryptedPacket序列化到ciphertext字段
                JSONObject encryptedJson = new JSONObject();
                encryptedJson.put("algo", encryptedContent.algo);
                encryptedJson.put("method", encryptedContent.method);
                encryptedJson.put("encrypted_key", encryptedContent.ekB64);
                encryptedJson.put("encrypted_content", encryptedContent.encryptedText);

                messagePayload.put("ciphertext", encryptedJson.toString());
            }
        }

        // 4. 创建完整的SOCP消息JSON
        JSONObject msgJson = new JSONObject();
        msgJson.put("type", type);
        msgJson.put("from", from);
        msgJson.put("to", to);
        msgJson.put("ts", header.time);
        msgJson.put("nonce", header.nonce);
        msgJson.put("payload", new JSONObject(messagePayload));
        msgJson.put("sig", header.signb64);

        // 5. 验证生成的消息
        String finalMessage = msgJson.toString();
        parser.validate(finalMessage);

        return finalMessage;
    }

    /**
     * 验证并解密SOCP消息
     */
    public DecryptedMessage verifyAndDecryptMessage(String messageJson, String receiverId) throws Exception {

        // 1. 解析消息
        JSONObject msgJson = new JSONObject(messageJson);
        Message message = parser.parseJson(messageJson);

        // 2. 提取header信息
        String from = msgJson.getString("from");
        long ts = msgJson.getLong("ts");
        String nonce = msgJson.getString("nonce");
        String signature = msgJson.getString("sig");

        Header header = new Header(from, ts, nonce, signature);

        // 3. 获取发送者公钥
        PublicKey senderPub = getUserPublicKey(from);
        if (senderPub == null) {
            throw new SecurityException("发送者 " + from + " 的公钥不存在");
        }

        // 4. 检查是否有加密内容
        Map<String, Object> payload = message.getPayload();
        String decryptedContent = null;

        if (payload.containsKey("ciphertext")) {
            String ciphertextStr = (String) payload.get("ciphertext");

            // 尝试解析为JSON（如果是加密的EncryptedPacket）
            try {
                JSONObject encryptedJson = new JSONObject(ciphertextStr);
                if (encryptedJson.has("encrypted_content") && encryptedJson.has("encrypted_key")) {
                    // 获取接收者私钥
                    PrivateKey receiverPriv = getUserPrivateKey(receiverId);
                    if (receiverPriv == null) {
                        throw new SecurityException("接收者 " + receiverId + " 的私钥不存在");
                    }

                    // 重建加密包
                    EncryptedPacket packet = new EncryptedPacket(
                        encryptedJson.getString("algo"),
                        encryptedJson.getString("method"),
                        encryptedJson.getString("encrypted_key"),
                        encryptedJson.getString("encrypted_content")
                    );

                    // 验证并解密
                    byte[] decryptedBytes = crypto.verityAnddecrypt(header, senderPub, receiverPriv, packet);
                    decryptedContent = new String(decryptedBytes);
                }
            } catch (Exception e) {
                // 如果解析失败，可能是明文消息
                decryptedContent = ciphertextStr;
            }
        }

        return new DecryptedMessage(message, decryptedContent);
    }

    /**
     * 解密消息结果
     */
    public static class DecryptedMessage {
        public final Message message;
        public final String decryptedContent;

        public DecryptedMessage(Message message, String decryptedContent) {
            this.message = message;
            this.decryptedContent = decryptedContent;
        }
    }
}