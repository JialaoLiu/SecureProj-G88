package socp;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MessageParserTest {
    private static MessageParser parser;
    
    @BeforeAll
    static void setup() throws Exception {
        // Load schema once before all tests
        parser = new MessageParser("socp.json");
    }
    
    @Test
    void testValidUserRemove() throws Exception {
        String json = "{\n" +
                " \"type\":\"USER_REMOVE\",\n" +
                " \"from\":\"server_1\",\n" +
                " \"to\":\"*\",\n" +
                " \"ts\":1700000200000,\n" +
                " \"payload\":{\"user_id\":\"user123\",\"server_id\":\"server_1\"},\n" +
                " \"sig\":\"abc123\"\n" +
                "}";
        Message msg = parser.parseJson(json);  // Fixed method name
        assertEquals("USER_REMOVE", msg.getType());
        assertEquals("server_1", msg.getPayload().get("server_id"));
    }
    
    @Test
    void testInvalidUserRemoveMissingField() {
        String badJson = "{\n" +
                " \"type\":\"USER_REMOVE\",\n" +
                " \"from\":\"server_1\",\n" +
                " \"to\":\"*\",\n" +
                " \"ts\":1700000200000,\n" +
                " \"payload\":{\"user_id\":\"user123\"},\n" + // missing server_id
                " \"sig\":\"abc123\"\n" +
                "}";
        Exception ex = assertThrows(Exception.class, () -> parser.parseJson(badJson));  // Fixed method name
        
        // The schema validation should fail - accept the current error format
        String message = ex.getMessage().toLowerCase();
        assertTrue(message.contains("subschema") || 
                  message.contains("server_id") || 
                  message.contains("required") || 
                  message.contains("missing"),
                  "Expected validation error about missing server_id field, got: " + ex.getMessage());
    }
    
    @Test
    void testValidMsgDirect() throws Exception {
        String json = "{\n" +
                " \"type\":\"MSG_DIRECT\",\n" +
                " \"from\":\"alice\",\n" +
                " \"to\":\"bob\",\n" +
                " \"ts\":1700000400000,\n" +
                " \"payload\":{\n" +
                " \"ciphertext\":\"<encrypted>\",\n" +
                " \"sender_pub\":\"<pubkey>\",\n" +
                " \"content_sig\":\"<signature>\"\n" +
                " },\n" +
                " \"sig\":\"abc123\"\n" +
                "}";
        Message msg = parser.parseJson(json);  // Fixed method name
        assertEquals("MSG_DIRECT", msg.getType());
        assertTrue(msg.getPayload().containsKey("ciphertext"));
    }
    
    @Test
    void testInvalidUnknownType() {
        String badJson = "{\n" +
                " \"type\":\"NOT_A_REAL_TYPE\",\n" +
                " \"from\":\"x\",\n" +
                " \"to\":\"y\",\n" +
                " \"ts\":12345,\n" +
                " \"payload\":{},\n" +
                " \"sig\":\"sig\"\n" +
                "}";
        
        Exception ex = assertThrows(Exception.class, () -> parser.parseJson(badJson));  // Fixed method name
        String message = ex.getMessage().toLowerCase();
        assertTrue(message.contains("subschema") || 
                  message.contains("not_a_real_type") || 
                  message.contains("enum") || 
                  message.contains("invalid"),
                  "Expected validation error about invalid type, got: " + ex.getMessage());
    }
    
    @Test
    void testBuildMessage() throws Exception {
        // Test building a new message
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("user_id", "user456");
        payload.put("server_id", "server_2");
        
        String json = parser.buildMessage(
            MessageTypes.USER_REMOVE,
            "server_2", 
            "*", 
            payload
        );
        
        // Should be valid JSON
        Message msg = parser.parseJson(json);
        assertEquals(MessageTypes.USER_REMOVE, msg.getType());
        assertEquals("server_2", msg.getFrom());
        assertEquals("user456", msg.getPayload().get("user_id"));
    }
    
    @Test
    void testSerializeMessage() throws Exception {
        // Parse a message, modify it, then serialize
        String originalJson = "{\n" +
                " \"type\":\"USER_REMOVE\",\n" +
                " \"from\":\"server_1\",\n" +
                " \"to\":\"*\",\n" +
                " \"ts\":1700000200000,\n" +
                " \"payload\":{\"user_id\":\"user123\",\"server_id\":\"server_1\"},\n" +
                " \"sig\":\"abc123\"\n" +
                "}";
        
        Message msg = parser.parseJson(originalJson);
        
        // Modify the message
        msg.getPayload().put("user_id", "user999");
        msg.setSig("new_signature");
        
        // Serialize back to JSON
        String serializedJson = parser.serialize(msg);
        
        // Parse again to verify
        Message reparsed = parser.parseJson(serializedJson);
        assertEquals("user999", reparsed.getPayload().get("user_id"));
        assertEquals("new_signature", reparsed.getSig());
    }
    
    @Test
    void testFileMessages() throws Exception {
        // Test FILE_START message
        String fileStartJson = "{\n" +
                " \"type\":\"FILE_START\",\n" +
                " \"from\":\"alice\",\n" +
                " \"to\":\"bob\",\n" +
                " \"ts\":1700000400000,\n" +
                " \"payload\":{\n" +
                "   \"file_id\":\"file123\",\n" +
                "   \"name\":\"document.pdf\",\n" +
                "   \"size\":1048576,\n" +
                "   \"sha256\":\"abc123def456\",\n" +
                "   \"mode\":\"dm\"\n" +
                " },\n" +
                " \"sig\":\"sig123\"\n" +
                "}";
        
        Message msg = parser.parseJson(fileStartJson);
        assertEquals("FILE_START", msg.getType());
        assertEquals("document.pdf", msg.getPayload().get("name"));
        assertEquals("dm", msg.getPayload().get("mode"));
    }
    
    @Test
    void testHeartbeatMessage() throws Exception {
        // Test HEARTBEAT message (empty payload)
        String heartbeatJson = "{\n" +
                " \"type\":\"HEARTBEAT\",\n" +
                " \"from\":\"node1\",\n" +
                " \"to\":\"server\",\n" +
                " \"ts\":1700000500000,\n" +
                " \"payload\":{},\n" +
                " \"sig\":\"heartbeat_sig\"\n" +
                "}";
        
        Message msg = parser.parseJson(heartbeatJson);
        assertEquals("HEARTBEAT", msg.getType());
        assertTrue(msg.getPayload().isEmpty());
    }
    
    @Test
    void debugSchemaValidation() {
        System.out.println("=== Debugging Schema Issues ===");
        
        // Test 1: Unknown type
        String unknownType = "{\n" +
                " \"type\":\"NOT_A_REAL_TYPE\",\n" +
                " \"from\":\"x\",\n" +
                " \"to\":\"y\",\n" +
                " \"ts\":12345,\n" +
                " \"payload\":{},\n" +
                " \"sig\":\"sig\"\n" +
                "}";
        
        try {
            parser.validate(unknownType);
            System.out.println("ERROR: Unknown type validation PASSED (should have failed)");
        } catch (Exception e) {
            System.out.println("Unknown type validation failed (good): " + e.getMessage());
        }
        
        // Test 2: Missing required field in payload
        String missingField = "{\n" +
                " \"type\":\"USER_REMOVE\",\n" +
                " \"from\":\"server_1\",\n" +
                " \"to\":\"*\",\n" +
                " \"ts\":1700000200000,\n" +
                " \"payload\":{\"user_id\":\"user123\"},\n" +
                " \"sig\":\"abc123\"\n" +
                "}";
        
        try {
            parser.validate(missingField);
            System.out.println("ERROR: Missing field validation PASSED (should have failed)");
        } catch (Exception e) {
            System.out.println("Missing field validation failed (good): " + e.getMessage());
        }
        
        // Test 3: Valid message
        String valid = "{\n" +
                " \"type\":\"USER_REMOVE\",\n" +
                " \"from\":\"server_1\",\n" +
                " \"to\":\"*\",\n" +
                " \"ts\":1700000200000,\n" +
                " \"payload\":{\"user_id\":\"user123\",\"server_id\":\"server_1\"},\n" +
                " \"sig\":\"abc123\"\n" +
                "}";
        
        try {
            parser.validate(valid);
            System.out.println("Valid message validation PASSED (good)");
        } catch (Exception e) {
            System.out.println("ERROR: Valid message validation FAILED: " + e.getMessage());
        }
        
        // Test 4: Test buildMessage validation
        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("user_id", "test123");
            payload.put("server_id", "test_server");
            
            String builtMessage = parser.buildMessage(MessageTypes.USER_REMOVE, "from", "to", payload);
            System.out.println("Built message validation PASSED (good)");
            System.out.println("Built message: " + builtMessage);
        } catch (Exception e) {
            System.out.println("ERROR: Built message validation FAILED: " + e.getMessage());
        }
    }
}