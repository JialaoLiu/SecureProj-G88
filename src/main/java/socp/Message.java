package socp;

import java.util.Map;

public class Message {
    private String type;
    private String from;
    private String to;
    private long ts;
    private String nonce;
    private Map<String, Object> payload;
    private String sig;

    // Getters & Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }

    public String getTo() { return to; }
    public void setTo(String to) { this.to = to; }

    public long getTs() { return ts; }
    public void setTs(long ts) { this.ts = ts; }

    public String getNonce() { return nonce; }
    public void setNonce(String nonce) { this.nonce = nonce; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public String getSig() { return sig; }
    public void setSig(String sig) { this.sig = sig; }
}
