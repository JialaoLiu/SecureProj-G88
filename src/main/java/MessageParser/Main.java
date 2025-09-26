package socp;

import java.util.HashMap;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        try {
            // 1. Load schema
            MessageParser parser = new MessageParser("socp.json");
            
            // 2. Example valid USER_REMOVE message
            String json = "{\n" +
                " \"type\":\"USER_REMOVE\",\n" +
                " \"from\":\"server_1\",\n" +
                " \"to\":\"*\",\n" +
                " \"ts\":1700000200000,\n" +
                " \"payload\":{\"user_id\":\"user123\",\"server_id\":\"server_1\"},\n" +
                " \"sig\":\"abc123\"\n" +
                "}";
            
            // 3. Parse & validate (correct method name)
            Message msg = parser.parseJson(json);
            System.out.println("Parsed type: " + msg.getType());
            System.out.println("Original user_id: " + msg.getPayload().get("user_id"));
            
            // 4. Modify & rebuild
            msg.getPayload().put("user_id", "user999");
            String rebuilt = parser.serialize(msg);  // Use serialize for existing Message objects
            System.out.println("Rebuilt JSON: " + rebuilt);
            
            // 5. Example of building a new message from scratch
            Map<String, Object> newPayload = new HashMap<>();
            newPayload.put("user_id", "user456");
            newPayload.put("server_id", "server_2");
            
            String newMessage = parser.buildMessage(
                MessageTypes.USER_REMOVE, 
                "server_2", 
                "*", 
                newPayload
            );
            System.out.println("New message: " + newMessage);
            
            // 6. Validate the new message
            parser.validate(newMessage);
            System.out.println("New message is valid!");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}