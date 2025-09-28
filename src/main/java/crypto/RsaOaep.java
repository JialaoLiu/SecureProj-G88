package crypto;

import javax.crypto.Cipher;
import java.security.PublicKey;
import java.security.PrivateKey;

public class RsaOaep {
    private RsaOaep() {
    }

    public static byte[] encryptKey(byte[] okey, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256AndMGF1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return cipher.doFinal(okey);
    }

    public static byte[] dencryptKey(byte[] ekey, PrivateKey priv) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWITHSHA-256AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE, priv);
        return cipher.doFinal(ekey);
    }
}

    /*AESkey 不能用明文传输 也需要加密，这里采取RSA公钥加密
    AESkey cannot be transmitted in plaintext and also
    needs to be encrypted. Here, RSA public key encryption is adopted
*/