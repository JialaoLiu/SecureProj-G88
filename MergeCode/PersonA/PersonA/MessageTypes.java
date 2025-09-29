package socp;

public class MessageTypes {
    
    // Server-Initiated Messages
    public static final String SERVER_HELLO_JOIN = "SERVER_HELLO_JOIN";
    public static final String SERVER_WELCOME = "SERVER_WELCOME";
    public static final String SERVER_ANNOUNCE = "SERVER_ANNOUNCE";
    public static final String SERVER_DELIVER = "SERVER_DELIVER";

    // User/Client-Initiated Messages
    public static final String USER_ADVERTISE = "USER_ADVERTISE";
    public static final String USER_REMOVE = "USER_REMOVE";
    public static final String USER_HELLO = "USER_HELLO";
    public static final String MSG_DIRECT = "MSG_DIRECT";
    public static final String USER_DELIVER = "USER_DELIVER";

    // Channel Messages
    public static final String PUBLIC_CHANNEL_ADD = "PUBLIC_CHANNEL_ADD";
    public static final String PUBLIC_CHANNEL_UPDATED = "PUBLIC_CHANNEL_UPDATED";
    public static final String PUBLIC_CHANNEL_KEY_SHARE = "PUBLIC_CHANNEL_KEY_SHARE";
    public static final String MSG_PUBLIC_CHANNEL = "MSG_PUBLIC_CHANNEL";

    // File Transfer Messages
    public static final String FILE_START = "FILE_START";
    public static final String FILE_CHUNK = "FILE_CHUNK";
    public static final String FILE_END = "FILE_END";

    // Control/Utility Messages
    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String ACK = "ACK";
    public static final String ERROR = "ERROR";

    // Kademlia Discovery/Lookup Messages
    public static final String FIND_NODE = "FIND_NODE";
    public static final String FIND_NODE_RESP = "FIND_NODE_RESP";

    // K-V Storage Messages
    public static final String STORE_VALUE = "STORE_VALUE";
    public static final String FIND_VALUE = "FIND_VALUE";
    public static final String VALUE_RESPONSE = "VALUE_RESPONSE";

    private MessageTypes() {
    }
}