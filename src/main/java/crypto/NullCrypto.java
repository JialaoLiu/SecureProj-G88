package crypto;

public class NullCrypto implements CryptoPorts {
    private RuntimeException todo(){ return new UnsupportedOperationException("Handled by Person B"); }
    public java.security.KeyPair generateRSAKeys(){ throw todo(); }
    public byte[] encryptAES(byte[] d, byte[] k){ throw todo(); }
    public byte[] decryptAES(byte[] c, byte[] k){ throw todo(); }
    public byte[] sign(byte[] d, java.security.PrivateKey p){ throw todo(); }
    public boolean verify(byte[] d, byte[] s, java.security.PublicKey k){ throw todo(); }
}