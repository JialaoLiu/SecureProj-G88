package crypto;

import java.nio.charset.StandardCharsets;

public class HeaderUtil {
    static final long Maxtime = 180_000L;
    static byte[] format (Header h){
        String s = h.senderID + "|" + h.time + "|" + h.nonce;
        return s.getBytes(StandardCharsets.UTF_8);

    }
}
