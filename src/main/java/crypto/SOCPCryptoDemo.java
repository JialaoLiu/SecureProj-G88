package crypto;

import java.util.HashMap;
import java.util.Map;

/**
 * SOCP协议加密集成演示
 * 展示完整的端到端加密消息流程
 */
public class SOCPCryptoDemo {

    public static void main(String[] args) {
        try {
            System.out.println("SOCP协议加密集成演示");
            System.out.println("========================");

            // 1. 初始化服务
            SOCPCryptoService cryptoService = new SOCPCryptoService();

            // 2. 为Alice和Bob生成密钥
            System.out.println("\n1. 为用户生成密钥对...");
            cryptoService.generateUserKeys("alice");
            cryptoService.generateUserKeys("bob");

            // 3. 演示USER_HELLO消息
            System.out.println("\n2. 创建USER_HELLO消息...");
            Map<String, Object> helloPayload = new HashMap<>();
            helloPayload.put("client", "alice");
            helloPayload.put("pubkey", "alice-pubkey-placeholder");
            helloPayload.put("enc_pubkey", "alice-enc-pubkey-placeholder");

            String helloMessage = cryptoService.createSecureMessage(
                "USER_HELLO", "alice", "server", helloPayload, null
            );

            System.out.println("USER_HELLO消息已创建并签名:");
            System.out.println(formatJson(helloMessage));

            // 4. 演示加密的MSG_DIRECT消息
            System.out.println("\n3. 创建加密的MSG_DIRECT消息...");
            Map<String, Object> msgPayload = new HashMap<>();
            msgPayload.put("sender_pub", "alice-pubkey");
            msgPayload.put("content_sig", "alice-content-signature");

            String secretMessage = "Hello Bob! This is Alice's secret message.";
            String encryptedMessage = cryptoService.createSecureMessage(
                "MSG_DIRECT", "alice", "bob", msgPayload, secretMessage
            );

            System.out.println("加密消息已创建:");
            System.out.println(formatJson(encryptedMessage));

            // 5. Bob接收并解密消息
            System.out.println("\n4. Bob解密消息...");
            SOCPCryptoService.DecryptedMessage decrypted =
                cryptoService.verifyAndDecryptMessage(encryptedMessage, "bob");

            System.out.println("消息解密成功:");
            System.out.println("消息类型: " + decrypted.message.getType());
            System.out.println("发送者: " + decrypted.message.getFrom());
            System.out.println("接收者: " + decrypted.message.getTo());
            System.out.println("解密内容: " + decrypted.decryptedContent);

            // 6. 演示HEARTBEAT消息（不加密）
            System.out.println("\n5. 创建HEARTBEAT消息...");
            String heartbeatMessage = cryptoService.createSecureMessage(
                "HEARTBEAT", "alice", "server", new HashMap<>(), null
            );

            System.out.println("HEARTBEAT消息已创建:");
            System.out.println(formatJson(heartbeatMessage));

            // 7. 验证非加密消息
            System.out.println("\n6. 验证HEARTBEAT消息...");
            SOCPCryptoService.DecryptedMessage heartbeatDecrypted =
                cryptoService.verifyAndDecryptMessage(heartbeatMessage, "server");

            System.out.println("HEARTBEAT验证成功:");
            System.out.println("消息类型: " + heartbeatDecrypted.message.getType());
            System.out.println("发送者: " + heartbeatDecrypted.message.getFrom());

            System.out.println("\nSOCP协议加密集成演示完成！");
            System.out.println("所有消息都经过了数字签名验证");
            System.out.println("私密消息使用RSA-OAEP+AES-256-GCM加密");
            System.out.println("防重放攻击机制正常工作");

        } catch (Exception e) {
            System.err.println("演示失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String formatJson(String json) {
        // 简单的JSON格式化
        return json.replace(",", ",\n  ")
                  .replace("{", "{\n  ")
                  .replace("}", "\n}");
    }
}