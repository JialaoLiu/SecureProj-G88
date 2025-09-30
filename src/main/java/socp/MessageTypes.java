package socp;

/**
 * SOCP Message Types - matches schema enum
 * All valid message types defined in the SOCP protocol
 */
public class MessageTypes {
    public static final String SERVER_HELLO_JOIN = "SERVER_HELLO_JOIN";
    public static final String SERVER_WELCOME = "SERVER_WELCOME";
    public static final String SERVER_ANNOUNCE = "SERVER_ANNOUNCE";
    public static final String USER_ADVERTISE = "USER_ADVERTISE";
    public static final String USER_REMOVE = "USER_REMOVE";
    public static final String SERVER_DELIVER = "SERVER_DELIVER";
    public static final String HEARTBEAT = "HEARTBEAT";
    public static final String USER_HELLO = "USER_HELLO";
    public static final String MSG_DIRECT = "MSG_DIRECT";
    public static final String USER_DELIVER = "USER_DELIVER";
    public static final String PUBLIC_CHANNEL_ADD = "PUBLIC_CHANNEL_ADD";
    public static final String PUBLIC_CHANNEL_UPDATED = "PUBLIC_CHANNEL_UPDATED";
    public static final String PUBLIC_CHANNEL_KEY_SHARE = "PUBLIC_CHANNEL_KEY_SHARE";
    public static final String MSG_PUBLIC_CHANNEL = "MSG_PUBLIC_CHANNEL";
    public static final String FILE_START = "FILE_START";
    public static final String FILE_CHUNK = "FILE_CHUNK";
    public static final String FILE_END = "FILE_END";
    public static final String ACK = "ACK";
    public static final String ERROR = "ERROR";

    // Kademlia Discovery/Lookup Messages
    public static final String FIND_NODE = "FIND_NODE";
    public static final String FIND_NODE_RESP = "FIND_NODE_RESP";

    // K-V Storage Messages
    public static final String STORE_VALUE = "STORE_VALUE";
    public static final String FIND_VALUE = "FIND_VALUE";
    public static final String VALUE_RESPONSE = "VALUE_RESPONSE";
    
    /**
     * Validate if a message type is supported
     */
    public static boolean isValid(String type) {
        return SERVER_HELLO_JOIN.equals(type) ||
               SERVER_WELCOME.equals(type) ||
               SERVER_ANNOUNCE.equals(type) ||
               USER_ADVERTISE.equals(type) ||
               USER_REMOVE.equals(type) ||
               SERVER_DELIVER.equals(type) ||
               HEARTBEAT.equals(type) ||
               USER_HELLO.equals(type) ||
               MSG_DIRECT.equals(type) ||
               USER_DELIVER.equals(type) ||
               PUBLIC_CHANNEL_ADD.equals(type) ||
               PUBLIC_CHANNEL_UPDATED.equals(type) ||
               PUBLIC_CHANNEL_KEY_SHARE.equals(type) ||
               MSG_PUBLIC_CHANNEL.equals(type) ||
               FILE_START.equals(type) ||
               FILE_CHUNK.equals(type) ||
               FILE_END.equals(type) ||
               ACK.equals(type) ||
               ERROR.equals(type) ||
               FIND_NODE.equals(type) ||
               FIND_NODE_RESP.equals(type) ||
               STORE_VALUE.equals(type) ||
               FIND_VALUE.equals(type) ||
               VALUE_RESPONSE.equals(type);
    }
    
    /**
     * Get all valid message types
     */
    public static String[] getAllTypes() {
        return new String[] {
            SERVER_HELLO_JOIN, SERVER_WELCOME, SERVER_ANNOUNCE,
            USER_ADVERTISE, USER_REMOVE, SERVER_DELIVER,
            HEARTBEAT, USER_HELLO, MSG_DIRECT, USER_DELIVER,
            PUBLIC_CHANNEL_ADD, PUBLIC_CHANNEL_UPDATED, PUBLIC_CHANNEL_KEY_SHARE,
            MSG_PUBLIC_CHANNEL, FILE_START, FILE_CHUNK, FILE_END,
            ACK, ERROR, FIND_NODE, FIND_NODE_RESP,
            STORE_VALUE, FIND_VALUE, VALUE_RESPONSE
        };
    }
}