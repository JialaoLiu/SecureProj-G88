package edu.adelaide.socp.crypto;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

public class RsaSign {
    private RsaSign() {}

    public static byte[] sign(byte[] data, PrivateKey privateKey) throws Exception { //生成数字签名，使用RSA私钥
        Signature sig = Signature.getInstance("SHA256withRSA"); //创建签名对象，并指定算法
        sig.initSign(privateKey); //初始化并提供私钥
        sig.update(data); //传入原始数据
        return sig.sign();
    }

    public static boolean verify(byte[] data, byte[] signature, PublicKey publicKey) throws Exception {
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(publicKey);
        sig.update(data);
        return sig.verify(signature);
    }
    public static String signToBase64(byte[] data, PrivateKey privateKey) throws Exception {
        return Base64.getEncoder().encodeToString(sign(data, privateKey)); //把字节数组转化成base64字符串方便打印传递
    }
    public static boolean verifyBase64(byte[] data, String sigB64, PublicKey publicKey) throws Exception {
        byte[] sigBytes = Base64.getDecoder().decode(sigB64); //解码回原始二进制
        return verify(data, sigBytes, publicKey); //验证是否被篡改
    }
}



