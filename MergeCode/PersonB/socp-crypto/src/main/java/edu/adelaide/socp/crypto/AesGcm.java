package edu.adelaide.socp.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

public final class AesGcm {
    public static byte[] encrypt(byte[] content, byte[] key32) throws Exception { //加密模式
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[12];
        random.nextBytes(iv); //随机生成
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec sks = new SecretKeySpec(key32, "AES");
        GCMParameterSpec gcmP = new GCMParameterSpec(128, iv);
        c.init(Cipher.ENCRYPT_MODE, sks, gcmP);
        byte[] Ct = c.doFinal(content);
        byte[] out = new byte[12 + Ct.length];
        System.arraycopy(iv, 0, out, 0, 12);
        System.arraycopy(Ct, 0, out, 12, Ct.length);
        return out;
    }
    public static byte[] decrypt(byte[] ivandCt,byte[] key32) throws Exception { //解密模式
        byte[] iv = new byte[12];
        System.arraycopy(ivandCt, 0, iv, 0, 12);
        int ctLength = ivandCt.length - 12;
        byte[] Ct = new byte[ctLength];
        System.arraycopy(ivandCt, 12, Ct, 0, ctLength);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec sks = new SecretKeySpec(key32, "AES");
        GCMParameterSpec gcmP = new GCMParameterSpec(128, iv);
        c.init(Cipher.DECRYPT_MODE, sks, gcmP);
        return c.doFinal(Ct);

    }
}
