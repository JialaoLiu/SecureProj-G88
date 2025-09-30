package com.chat.protocol.config;

public class Config {
    public static final int DEFAULT_PORT = 8080;
    public static final int MAX_CONNECTIONS = 100;
    public static final int HEARTBEAT_INTERVAL_MS = 30000;
    public static final int MAX_MSGS_PER_SEC = 10;
    public static final String TLS_PROTOCOL = "TLSv1.3";
    public static final String KEYSTORE_PATH = "keystore.jks";
    public static final String TRUSTSTORE_PATH = "truststore.jks";
    public static final String KEYSTORE_PASSWORD = "changeit";
    public static final int BUFFER_SIZE = 4096;
}