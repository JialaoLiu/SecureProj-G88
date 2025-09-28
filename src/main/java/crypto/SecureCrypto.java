package crypto;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

public final class SecureCrypto implements CryptoPorts {
    @Override
    public KeyPair generateRSAKeys() {
        try {
            return RSAkeypair.generate_keys();
        } catch (Exception e) {
            throw new RuntimeException("generateRSAKeys failed", e);
        }
    }

    @Override
    public byte[] encryptAES(byte[] data, byte[] key) {
        try {
            return AesGcm.encrypt(data, key);
        } catch (Exception e) {
            throw new RuntimeException("encryptAES failed", e);
        }
    }

    @Override
    public byte[] decryptAES(byte[] ciphertext, byte[] key) {
        try {
            return AesGcm.decrypt(ciphertext, key);
        } catch (Exception e) {
            throw new RuntimeException("decryptAES failed", e);
        }
    }

    @Override
    public byte[] sign(byte[] data, PrivateKey priv) {
        try {
            return RsaSign.sign(data, priv);
        } catch (Exception e) {
            throw new RuntimeException("sign failed", e);
        }
    }
    @Override
    public boolean verify(byte[] data, byte[] sig, PublicKey pub) {
        try {
            return RsaSign.verify(data, sig, pub);
        } catch (Exception e) {
            return  false;
        }
    }
    public Header sign_header(String senderID, PrivateKey priv) {
        try {
            long ts = System.currentTimeMillis(); //time
            String nonce = NonceCreator.creat();  //nonce

            Header tmp = new Header(senderID, ts, nonce, null);

            byte[] headerBytes = HeaderUtil.format(tmp);
            String sigB64 = RsaSign.signToBase64(headerBytes, priv);

            return new Header(senderID, ts, nonce, sigB64);
        } catch (Exception e) {
            throw new RuntimeException("sign_header failed", e);
        }
    }
    public EncryptedPacket encrypyAndwrap(byte[] content,  PublicKey receiverPubk) {
        try {
            byte[] aesKey32 = new byte[32];
            new java.security.SecureRandom().nextBytes(aesKey32);
            byte[] encryptedText = AesGcm.encrypt(content, aesKey32);
            byte[] ekey = RsaOaep.encryptKey(aesKey32, receiverPubk);
            String encryptedTextB64 = java.util.Base64.getEncoder().encodeToString(encryptedText);
            String ekeyB64 = java.util.Base64.getEncoder().encodeToString(ekey);
            return EncryptedPacket.createStandardPacket(ekeyB64, encryptedTextB64);
        }catch (Exception e) {
            throw new RuntimeException("encrypyAndwrap failed", e);
        }
    }
    private final nonceWh noncewh = new nonceWh();
    /**
     * @param header           消息头（包含 senderID, time, nonce, signb64）
     * @param senderPubk       发送者的 RSA 公钥（用于验签）
     * @param receiverPrivk    接收者的 RSA 私钥（用于解密 AESkey）
     * @param packet           加密过后的数据包
     * @return                 解密后的原始明文字节数组
     */
    public byte[] verityAnddecrypt(Header header, PublicKey senderPubk, PrivateKey receiverPrivk,EncryptedPacket packet) throws Exception {
        if(header==null||header.signb64==null){
            throw new SecurityException("Header or signature missing");
        }
        if(packet==null||packet.ekB64 == null||packet.encryptedText == null){
            throw new SecurityException("packet missing");
        }
        if (senderPubk == null) {
            throw new SecurityException("Sender public key missing");
        }
        if (receiverPrivk == null) {
            throw new SecurityException("receiver Private key missing");
        }
        byte[] hdrBytes = HeaderUtil.format(header);
        if (!RsaSign.verifyBase64(hdrBytes, header.signb64, senderPubk)) {
            throw new SecurityException("Header signature invalid");
        }

        if (!noncewh.checkAndstore(header.senderID, header.nonce, header.time)) {
            throw new SecurityException(" Expired timestamp");
        }


        if (!"AES-256-GCM".equals(packet.algo) || !"RSA-OAEP".equals(packet.method)) {
            throw new SecurityException("Unsupported algorithms: " + packet.algo + " / " + packet.method);
        } //检查方法


        byte[] ekey = java.util.Base64.getDecoder().decode(packet.ekB64);
        byte[] okey = RsaOaep.dencryptKey(ekey, receiverPrivk);
        if (okey.length != 32) throw new SecurityException("Bad AES key length"); // 用接收者 RSA 私钥解封 AES 会话密钥得到临时key


        byte[] encryptedText = java.util.Base64.getDecoder().decode(packet.encryptedText);
        return AesGcm.decrypt(encryptedText, okey);   //  用 AES-GCM 解正文
    }



    }

