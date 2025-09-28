package crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

//储存和生成密钥对 公钥 私钥 公钥是锁头 私钥是钥匙

public final class RSAkeypair {
    public static KeyPair generate_keys() throws Exception {
        KeyPairGenerator kp= KeyPairGenerator.getInstance("RSA");
        kp.initialize(2048);
        return kp.generateKeyPair();
    }
}
