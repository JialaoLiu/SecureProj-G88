package crypto;



import java.security.KeyPair;

public interface CryptoPorts {
    KeyPair generateRSAKeys();
    byte[] encryptAES(byte[] data, byte[] key);
    byte[] decryptAES(byte[] ciphertext, byte[] key);
    byte[] sign(byte[] data, java.security.PrivateKey priv);
    boolean verify(byte[] data, byte[] sig, java.security.PublicKey pub);
}