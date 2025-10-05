package devserver;

public class Config {
    // TLS Configuration
    public static final String TLS_PROTOCOL = "TLSv1.3";
    public static final String KEYSTORE_PATH = "localhost.jks";
    public static final String TRUSTSTORE_PATH = "localhost.jks";
    public static final String KEYSTORE_PASSWORD = "changeit";

    // Server Configuration
    public static final int DEFAULT_PORT = 9443;
    public static final int MAX_CONNECTIONS = 100;
    public static final int HEARTBEAT_INTERVAL_MS = 30000;
    public static final int MAX_MSGS_PER_SEC = 10;
    public static final int BUFFER_SIZE = 4096;

    // TLS Cipher Suites
    public static final String[] CIPHER_SUITES = {
        "TLS_AES_256_GCM_SHA384",
        "TLS_CHACHA20_POLY1305_SHA256",
        "TLS_AES_128_GCM_SHA256"
    };
}