package devserver;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.security.MessageDigest;
import java.util.Base64;

public class AuthServer {
    private static final int PORT = 8082;
    // In-memory user storage (username -> hashed password)
    private static final Map<String, String> users = new ConcurrentHashMap<>();

    static {
        // Pre-populate with demo users
        try {
            users.put("alice", hashPassword("demo123"));
            users.put("bob", hashPassword("demo123"));
            users.put("charlie", hashPassword("demo123"));
            users.put("sarah.johnson@example.com", hashPassword("demo123"));
            users.put("admin' OR '1'='1'--", hashPassword("admin123"));
            users.put("root", hashPassword("toor"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // Register endpoint
            server.createContext("/api/auth/register", exchange -> {
                addCorsHeaders(exchange);
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                    return;
                }

                try {
                    String body = readRequestBody(exchange);
                    JSONObject json = new JSONObject(body);
                    String username = json.getString("username");
                    String password = json.getString("password");

                    if (username == null || username.trim().isEmpty()) {
                        sendResponse(exchange, 400, "{\"error\": \"Username is required\"}");
                        return;
                    }

                    if (password == null || password.length() < 6) {
                        sendResponse(exchange, 400, "{\"error\": \"Password must be at least 6 characters\"}");
                        return;
                    }

                    if (users.containsKey(username)) {
                        sendResponse(exchange, 409, "{\"error\": \"Username already exists\"}");
                        return;
                    }

                    // Store user with hashed password
                    users.put(username, hashPassword(password));

                    // Generate JWT token
                    String token = JwtService.generateToken(username);

                    JSONObject response = new JSONObject();
                    response.put("token", token);
                    response.put("username", username);

                    sendResponse(exchange, 201, response.toString());
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
                }
            });

            // Login endpoint
            server.createContext("/api/auth/login", exchange -> {
                addCorsHeaders(exchange);
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                    return;
                }

                try {
                    String body = readRequestBody(exchange);
                    JSONObject json = new JSONObject(body);
                    String username = json.getString("username");
                    String password = json.getString("password");

                    if (!users.containsKey(username)) {
                        sendResponse(exchange, 401, "{\"error\": \"Invalid credentials\"}");
                        return;
                    }

                    String hashedPassword = hashPassword(password);
                    if (!users.get(username).equals(hashedPassword)) {
                        sendResponse(exchange, 401, "{\"error\": \"Invalid credentials\"}");
                        return;
                    }

                    // Generate JWT token
                    String token = JwtService.generateToken(username);

                    JSONObject response = new JSONObject();
                    response.put("token", token);
                    response.put("username", username);

                    sendResponse(exchange, 200, response.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
                }
            });

            // Verify token endpoint
            server.createContext("/api/auth/verify", exchange -> {
                addCorsHeaders(exchange);
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                if (!"POST".equals(exchange.getRequestMethod())) {
                    sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                    return;
                }

                try {
                    String body = readRequestBody(exchange);
                    JSONObject json = new JSONObject(body);
                    String token = json.getString("token");

                    String username = JwtService.validateToken(token);
                    if (username == null) {
                        sendResponse(exchange, 401, "{\"error\": \"Invalid token\"}");
                        return;
                    }

                    JSONObject response = new JSONObject();
                    response.put("valid", true);
                    response.put("username", username);

                    sendResponse(exchange, 200, response.toString());
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
                }
            });

            // Debug endpoint
            server.createContext("/api/debug/users", exchange -> {
                addCorsHeaders(exchange);
                if ("OPTIONS".equals(exchange.getRequestMethod())) {
                    exchange.sendResponseHeaders(204, -1);
                    return;
                }

                try {
                    JSONObject response = new JSONObject();
                    response.put("users", new org.json.JSONArray(users.keySet()));
                    response.put("count", users.size());
                    sendResponse(exchange, 200, response.toString());
                } catch (Exception e) {
                    sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
                }
            });

            server.setExecutor(null);
            server.start();
            System.out.println("[AUTH] Authentication server started on port " + PORT);
        } catch (IOException e) {
            System.err.println("[AUTH] Failed to start auth server: " + e.getMessage());
        }
    }

    private static void addCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
    }

    private static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = is.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        return result.toString(StandardCharsets.UTF_8);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static String hashPassword(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
}
