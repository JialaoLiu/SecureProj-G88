package com.chat.protocol.stub;

import com.chat.protocol.model.Msg;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.util.logging.Logger;
import java.util.logging.Level;

public class JSONStub {
    private static final Logger LOG = Logger.getLogger(JSONStub.class.getName());
    private static final Gson gson = new Gson();

    public static String serialize(Msg msg) {
        if (msg == null) {
            LOG.warning("Attempted to serialize null message");
            throw new IllegalArgumentException("Message cannot be null");
        }
        return gson.toJson(msg);
    }

    public static Msg deserialize(String json) throws JsonSyntaxException {
        if (json == null || json.trim().isEmpty()) {
            LOG.warning("Invalid JSON string: null or empty");
            throw new IllegalArgumentException("JSON string cannot be null or empty");
        }
        try {
            return gson.fromJson(json, Msg.class);
        } catch (JsonSyntaxException e) {
            LOG.log(Level.WARNING, "JSON parsing failed: {0}", e.getMessage());
            throw e;
        }
    }
}