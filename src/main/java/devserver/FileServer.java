package devserver;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileServer {
    private static final int PORT = 8081;
    private static final String UPLOADS_DIR = "./uploads/files";

    public static void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

            // CORS headers for all responses
            server.createContext("/uploads/files/", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    // Add CORS headers
                    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
                    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");

                    if ("OPTIONS".equals(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(204, -1);
                        return;
                    }

                    String requestPath = exchange.getRequestURI().getPath();
                    String fileName = requestPath.substring("/uploads/files/".length());

                    Path filePath = Paths.get(UPLOADS_DIR, fileName);

                    if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                        byte[] fileContent = Files.readAllBytes(filePath);

                        // Set content type based on file extension
                        String contentType = getContentType(fileName);
                        exchange.getResponseHeaders().set("Content-Type", contentType);
                        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + getOriginalFileName(fileName) + "\"");

                        exchange.sendResponseHeaders(200, fileContent.length);
                        OutputStream os = exchange.getResponseBody();
                        os.write(fileContent);
                        os.close();
                    } else {
                        String response = "File not found";
                        exchange.sendResponseHeaders(404, response.length());
                        OutputStream os = exchange.getResponseBody();
                        os.write(response.getBytes());
                        os.close();
                    }
                }
            });

            server.setExecutor(null);
            server.start();
            System.out.println("[HTTP] File server started on port " + PORT);
        } catch (IOException e) {
            System.err.println("[HTTP] Failed to start file server: " + e.getMessage());
        }
    }

    private static String getContentType(String fileName) {
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".pdf")) return "application/pdf";
        if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
        if (lowerName.endsWith(".png")) return "image/png";
        if (lowerName.endsWith(".gif")) return "image/gif";
        if (lowerName.endsWith(".txt")) return "text/plain";
        if (lowerName.endsWith(".doc") || lowerName.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lowerName.endsWith(".xls") || lowerName.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        return "application/octet-stream";
    }

    private static String getOriginalFileName(String fileName) {
        // 文件名格式: {fileId}-{originalFileName}
        int dashIndex = fileName.indexOf('-');
        if (dashIndex > 0 && dashIndex < fileName.length() - 1) {
            return fileName.substring(dashIndex + 1);
        }
        return fileName;
    }
}
