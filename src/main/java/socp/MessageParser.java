package socp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SchemaLoader;

import java.io.*;
import java.util.*;

/**
 * SOCP Message Parser - Protocol Layer
 * Handles SOCP JSON validation, parsing, and serialization
 */
public class MessageParser {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Schema schema;
    
    public MessageParser(String schemaPath) throws IOException {
        // Load JSON schema from resources
        InputStream is = getClass().getClassLoader().getResourceAsStream(schemaPath);
        if (is == null) {
            throw new IOException(schemaPath + " not found in resources");
        }
        
        JSONObject rawSchema = new JSONObject(new JSONTokener(is));
        SchemaLoader loader = SchemaLoader.builder()
            .schemaJson(rawSchema)
            .draftV7Support()
            .build();
        this.schema = loader.load().build();
    }
    
    /**
     * Parse and validate JSON string to SOCP message
     * API: parse_json(char* raw, struct msg** out) equivalent
     */
    public Message parseJson(String rawJson) throws Exception {
        validate(rawJson);
        return mapper.readValue(rawJson, Message.class);
    }
    
    /**
     * Build SOCP message from components
     * API: build_msg(enum type, char* content) equivalent
     */
    public String buildMessage(String type, String from, String to, Map<String, Object> payload) throws Exception {
        Message message = new Message();
        message.setType(type);
        message.setFrom(from);
        message.setTo(to);
        message.setTs(System.currentTimeMillis());
        message.setPayload(payload);
        message.setSig(""); // Will be populated by crypto layer
        
        String json = mapper.writeValueAsString(message);
        validate(json); // Ensure output matches schema
        return json;
    }
    
    /**
     * Validate JSON against SOCP schema
     */
    public void validate(String json) throws Exception {
        JSONObject obj = new JSONObject(json);
        schema.validate(obj); // throws ValidationException if invalid
    }
    
    /**
     * Re-serialize message for forwarding
     */
    public String serialize(Message message) throws Exception {
        String json = mapper.writeValueAsString(message);
        validate(json);
        return json;
    }
}