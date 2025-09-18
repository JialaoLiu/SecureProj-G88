package echochat;

public class Message {
    public String type;
    public String senderId;
    public String targetId;
    public String data;
    public long timestamp;
    public String signature;

    public Message() {
        this.timestamp = System.currentTimeMillis();
    }

    public Message(String type, String senderId, String data) {
        this();
        this.type = type;
        this.senderId = senderId;
        this.data = data;
    }

    @Override
    public String toString() {
        return "Message{" +
                "type='" + type + '\'' +
                ", senderId='" + senderId + '\'' +
                ", targetId='" + targetId + '\'' +
                ", data='" + data + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}