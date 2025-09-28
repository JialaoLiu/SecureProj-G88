package edu.adelaide.socp.crypto;

public final class Header {
    public final String senderID;
    public final long time;
    public final String nonce;
    public final String signb64;
    public Header(String sendID, long time, String nonce, String signb64) {
        this.senderID = sendID;
        this.time = time;
        this.nonce = nonce;
        this.signb64 = signb64;
    }

}
