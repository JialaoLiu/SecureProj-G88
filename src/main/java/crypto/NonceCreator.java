package crypto;

import java.security.SecureRandom;
import java.util.Base64;

public final class NonceCreator {
    private static final SecureRandom random = new SecureRandom();

    public static String creat() {
        byte[] buf = new byte[16]; // 128-bit 随机数
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
