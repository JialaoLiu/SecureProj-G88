package devserver;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthServer {
    private static final int PORT = 8082;
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";
    private static final Map<String, UserCredential> users = new ConcurrentHashMap<>();
    private static final Map<String, LoginAttempts> loginAttempts = new ConcurrentHashMap<>();
    private static final int MAX_LOGIN_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000;

    static class UserCredential {
        String hashedPassword;
        byte[] salt;
        UserCredential(String hashedPassword, byte[] salt) {
            this.hashedPassword = hashedPassword;
            this.salt = salt;
        }
    }

    static class LoginAttempts {
        int count = 0;
        long lockoutUntil = 0;
        boolean isLockedOut() {
            if (lockoutUntil > System.currentTimeMillis()) {
                return true;
            } else if (lockoutUntil > 0) {
                count = 0;
                lockoutUntil = 0;
            }
            return false;
        }
        void recordFailure() {
            count++;
            if (count >= MAX_LOGIN_ATTEMPTS) {
                lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
            }
        }
        void recordSuccess() {
            count = 0;
            lockoutUntil = 0;
        }
    }

    static {
        try {
            users.put("alice", createUserCredential("demo123"));
            users.put("bob", createUserCredential("demo123"));
            users.put("charlie", createUserCredential("demo123"));
            users.put("sarah.johnson@example.com", createUserCredential("demo123"));
        } catch (Exception e) {
            System.err.println("[AUTH] Failed to initialize demo users: " + e.getMessage());
        }
    }

    private static UserCredential createUserCredential(String password) throws Exception {
        byte[] salt = generateSalt();
        String hashed = hashPasswordWithSalt(password, salt);
        return new UserCredential(hashed, salt);
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

                    if (username.length() > 50) {
                        sendResponse(exchange, 400, "{\"error\": \"Username too long\"}");
                        return;
                    }

                    if (password == null || password.length() < 8) {
                        sendResponse(exchange, 400, "{\"error\": \"Password must be at least 8 characters\"}");
                        return;
                    }

                    if (users.containsKey(username)) {
                        sendResponse(exchange, 400, "{\"error\": \"Registration failed\"}");
                        return;
                    }

                    byte[] salt = generateSalt();
                    String hashedPassword = hashPasswordWithSalt(password, salt);
                    users.put(username, new UserCredential(hashedPassword, salt));

                    // Generate JWT token
                    String token = JwtService.generateToken(username);

                    JSONObject response = new JSONObject();
                    response.put("token", token);
                    response.put("username", username);

                    sendResponse(exchange, 201, response.toString());
                } catch (Exception e) {
                    System.err.println("[AUTH] Registration error: " + e.getMessage());
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

                    LoginAttempts attempts = loginAttempts.computeIfAbsent(username, k -> new LoginAttempts());
                    if (attempts.isLockedOut()) {
                        long remainingMs = attempts.lockoutUntil - System.currentTimeMillis();
                        long remainingMinutes = remainingMs / 60000;
                        sendResponse(exchange, 429, "{\"error\": \"Too many failed attempts. Try again in " +
                                    remainingMinutes + " minutes\"}");
                        return;
                    }

                    if (!users.containsKey(username)) {
                        attempts.recordFailure();
                        sendResponse(exchange, 401, "{\"error\": \"Invalid credentials\"}");
                        return;
                    }

                    UserCredential credential = users.get(username);
                    String hashedPassword = hashPasswordWithSalt(password, credential.salt);

                    if (!MessageDigest.isEqual(hashedPassword.getBytes(), credential.hashedPassword.getBytes())) {
                        attempts.recordFailure();
                        sendResponse(exchange, 401, "{\"error\": \"Invalid credentials\"}");
                        return;
                    }

                    attempts.recordSuccess();

                    // Generate JWT token
                    String token = JwtService.generateToken(username);

                    JSONObject response = new JSONObject();
                    response.put("token", token);
                    response.put("username", username);

                    sendResponse(exchange, 200, response.toString());
                } catch (Exception e) {
                    System.err.println("[AUTH] Login error: " + e.getMessage());
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
                    System.err.println("[AUTH] Token verification error: " + e.getMessage());
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
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", ALLOWED_ORIGIN);
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
        exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");
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

    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }

    private static String hashPasswordWithSalt(String password, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] hash = factory.generateSecret(spec).getEncoded();
        return Base64.getEncoder().encodeToString(hash);
    }
}
