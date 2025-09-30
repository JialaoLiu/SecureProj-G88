package com.chat.protocol.model;

import com.google.gson.annotations.SerializedName;

public class Msg {
    @SerializedName("header")
    private Header header;

    @SerializedName("body")
    private Body body;

    public Msg() {}
    public Msg(Header header, Body body) {
        this.header = header;
        this.body = body;
    }

    public Header getHeader() { return header; }
    public void setHeader(Header header) { this.header = header; }
    public Body getBody() { return body; }
    public void setBody(Body body) { this.body = body; }

    public static class Header {
        @SerializedName("version")
        private String version = "1.0";

        @SerializedName("msg_type")
        private String msgType;

        @SerializedName("sender_id")
        private String senderId;

        @SerializedName("recipient_id")
        private String recipientId;

        @SerializedName("timestamp")
        private long timestamp;

        @SerializedName("nonce")
        private String nonce;

        @SerializedName("signature")
        private String signature;

        @SerializedName("ttl")
        private int ttl = 10;

        public Header() {}
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getMsgType() { return msgType; }
        public void setMsgType(String msgType) { this.msgType = msgType; }
        public String getSenderId() { return senderId; }
        public void setSenderId(String senderId) { this.senderId = senderId; }
        public String getRecipientId() { return recipientId; }
        public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
        public String getNonce() { return nonce; }
        public void setNonce(String nonce) { this.nonce = nonce; }
        public String getSignature() { return signature; }
        public void setSignature(String signature) { this.signature = signature; }
        public int getTtl() { return ttl; }
        public void setTtl(int ttl) { this.ttl = ttl; }
    }

    public static class Body {
        @SerializedName("content")
        private String content;

        @SerializedName("aes_key")
        private String aesKey;

        @SerializedName("file")
        private File file;

        public Body() {}
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getAesKey() { return aesKey; }
        public void setAesKey(String aesKey) { this.aesKey = aesKey; }
        public File getFile() { return file; }
        public void setFile(File file) { this.file = file; }
    }

    public static class File {
        @SerializedName("name")
        private String name;

        @SerializedName("chunks")
        private String[] chunks;

        @SerializedName("hash")
        private String hash;

        public File() {}
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String[] getChunks() { return chunks; }
        public void setChunks(String[] chunks) { this.chunks = chunks; }
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
    }
}