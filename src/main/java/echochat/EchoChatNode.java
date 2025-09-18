package echochat;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.server.WebSocketServer;

import com.fasterxml.jackson.databind.ObjectMapper;

public class EchoChatNode {
    private final String nodeId;
    private final int port;
    private final WebSocketServer server;
    private final Map<String, WebSocketClient> connections = new ConcurrentHashMap<>();
    private final Map<String, NodeInfo> knownNodes = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final CryptoManager cryptoManager;
    private final Scanner scanner = new Scanner(System.in);

    public EchoChatNode(String nodeId, int port) {
        this.nodeId = nodeId;
        this.port = port;
        this.cryptoManager = new CryptoManager();
        this.server = new ChatWebSocketServer(new InetSocketAddress(port));
    }

    public void start() {
        System.out.println("Starting EchoChat Node: " + nodeId + " on port " + port);

        server.start();

        // Add self to known nodes
        knownNodes.put(nodeId, new NodeInfo(nodeId, "localhost", port, cryptoManager.getPublicKeyString()));

        System.out.println("Node started! Type 'help' for commands.");

        // Command loop
        while (true) {
            System.out.print(nodeId + "> ");
            String input = scanner.nextLine().trim();

            if (input.equals("quit") || input.equals("exit")) {
                shutdown();
                break;
            }

            handleCommand(input);
        }
    }

    private void handleCommand(String input) {
        String[] parts = input.split(" ", 2);
        String command = parts[0].toLowerCase();

        switch (command) {
            case "help":
                showHelp();
                break;
            case "connect":
                if (parts.length > 1) {
                    connectToNode(parts[1]);
                } else {
                    System.out.println("Usage: connect <host:port>");
                }
                break;
            case "list":
                listNodes();
                break;
            case "msg":
                if (parts.length > 1) {
                    String[] msgParts = parts[1].split(" ", 2);
                    if (msgParts.length == 2) {
                        sendPrivateMessage(msgParts[0], msgParts[1]);
                    } else {
                        System.out.println("Usage: msg <nodeId> <message>");
                    }
                } else {
                    System.out.println("Usage: msg <nodeId> <message>");
                }
                break;
            case "broadcast":
                if (parts.length > 1) {
                    broadcastMessage(parts[1]);
                } else {
                    System.out.println("Usage: broadcast <message>");
                }
                break;
            case "status":
                showStatus();
                break;
            default:
                System.out.println("Unknown command. Type 'help' for available commands.");
        }
    }

    private void showHelp() {
        System.out.println("Available commands:");
        System.out.println("  connect <host:port>     - Connect to another node");
        System.out.println("  list                    - List all known nodes");
        System.out.println("  msg <nodeId> <message> - Send private message");
        System.out.println("  broadcast <message>     - Send message to all nodes");
        System.out.println("  status                  - Show node status");
        System.out.println("  quit/exit               - Shutdown node");
    }

    private void connectToNode(String hostPort) {
        try {
            String[] parts = hostPort.split(":");
            if (parts.length != 2) {
                System.out.println("Invalid format. Use host:port");
                return;
            }

            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            URI serverUri = URI.create("ws://" + host + ":" + port);
            WebSocketClient client = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("Connected to " + serverUri);
                    // Send node info exchange
                    sendNodeInfo(this);
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessage(message, this);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("Disconnected from " + serverUri + ": " + reason);
                }

                @Override
                public void onError(Exception ex) {
                    System.out.println("Connection error: " + ex.getMessage());
                }
            };

            client.connect();

        } catch (Exception e) {
            System.out.println("Failed to connect: " + e.getMessage());
        }
    }

    private void sendNodeInfo(WebSocket conn) {
        try {
            Message nodeInfoMsg = new Message();
            nodeInfoMsg.type = "NODE_INFO";
            nodeInfoMsg.senderId = nodeId;
            nodeInfoMsg.data = mapper.writeValueAsString(knownNodes.get(nodeId));

            String json = mapper.writeValueAsString(nodeInfoMsg);
            conn.send(json);
        } catch (Exception e) {
            System.out.println("Failed to send node info: " + e.getMessage());
        }
    }

    private void handleIncomingMessage(String message, WebSocket conn) {
        try {
            Message msg = mapper.readValue(message, Message.class);

            switch (msg.type) {
                case "NODE_INFO":
                    handleNodeInfo(msg, conn);
                    break;
                case "PRIVATE_MESSAGE":
                    handlePrivateMessage(msg);
                    break;
                case "BROADCAST":
                    handleBroadcast(msg);
                    break;
                case "ROUTING":
                    handleRouting(msg);
                    break;
            }
        } catch (Exception e) {
            System.out.println("Failed to process message: " + e.getMessage());
        }
    }

    private void handleNodeInfo(Message msg, WebSocket conn) {
        try {
            NodeInfo nodeInfo = mapper.readValue(msg.data, NodeInfo.class);
            knownNodes.put(nodeInfo.nodeId, nodeInfo);
            connections.put(nodeInfo.nodeId, (WebSocketClient) conn);

            System.out.println("Node joined: " + nodeInfo.nodeId);

            // Send back our known nodes
            Message response = new Message();
            response.type = "NODE_LIST";
            response.senderId = nodeId;
            response.data = mapper.writeValueAsString(knownNodes.values());

            conn.send(mapper.writeValueAsString(response));

        } catch (Exception e) {
            System.out.println("Failed to handle node info: " + e.getMessage());
        }
    }

    private void handlePrivateMessage(Message msg) {
        if (msg.targetId.equals(nodeId)) {
            // Message for us
            String decrypted = cryptoManager.decrypt(msg.data);
            System.out.println("\n[Private] " + msg.senderId + ": " + decrypted);
            System.out.print(nodeId + "> ");
        } else {
            // Route the message
            routeMessage(msg);
        }
    }

    private void handleBroadcast(Message msg) {
        String decrypted = cryptoManager.decrypt(msg.data);
        System.out.println("\n[Broadcast] " + msg.senderId + ": " + decrypted);
        System.out.print(nodeId + "> ");

        // Forward to other nodes
        forwardBroadcast(msg);
    }

    private void handleRouting(Message msg) {
        routeMessage(msg);
    }

    private void sendPrivateMessage(String targetId, String message) {
        if (!knownNodes.containsKey(targetId)) {
            System.out.println("Unknown node: " + targetId);
            return;
        }

        try {
            NodeInfo target = knownNodes.get(targetId);
            String encrypted = cryptoManager.encrypt(message, target.publicKey);

            Message msg = new Message();
            msg.type = "PRIVATE_MESSAGE";
            msg.senderId = nodeId;
            msg.targetId = targetId;
            msg.data = encrypted;

            routeMessage(msg);
            System.out.println("Message sent to " + targetId);

        } catch (Exception e) {
            System.out.println("Failed to send message: " + e.getMessage());
        }
    }

    private void broadcastMessage(String message) {
        try {
            String encrypted = cryptoManager.encrypt(message, cryptoManager.getPublicKeyString());

            Message msg = new Message();
            msg.type = "BROADCAST";
            msg.senderId = nodeId;
            msg.data = encrypted;
            msg.timestamp = System.currentTimeMillis();

            String json = mapper.writeValueAsString(msg);

            for (WebSocketClient client : connections.values()) {
                if (client.isOpen()) {
                    client.send(json);
                }
            }

            System.out.println("Broadcast sent to " + connections.size() + " nodes");

        } catch (Exception e) {
            System.out.println("Failed to broadcast: " + e.getMessage());
        }
    }

    private void routeMessage(Message msg) {
        // Simple routing: send to all connected nodes
        try {
            String json = mapper.writeValueAsString(msg);

            for (WebSocketClient client : connections.values()) {
                if (client.isOpen()) {
                    client.send(json);
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to route message: " + e.getMessage());
        }
    }

    private void forwardBroadcast(Message msg) {
        // Prevent loops by checking if we've seen this message
        try {
            String json = mapper.writeValueAsString(msg);

            for (WebSocketClient client : connections.values()) {
                if (client.isOpen()) {
                    client.send(json);
                }
            }
        } catch (Exception e) {
            System.out.println("Failed to forward broadcast: " + e.getMessage());
        }
    }

    private void listNodes() {
        System.out.println("Known nodes:");
        for (NodeInfo node : knownNodes.values()) {
            String status = connections.containsKey(node.nodeId) ? "Connected" : "Known";
            System.out.println("  " + node.nodeId + " (" + node.host + ":" + node.port + ") - " + status);
        }
    }

    private void showStatus() {
        System.out.println("Node Status:");
        System.out.println("  ID: " + nodeId);
        System.out.println("  Port: " + port);
        System.out.println("  Known nodes: " + knownNodes.size());
        System.out.println("  Active connections: " + connections.size());
        System.out.println("  Public key: " + cryptoManager.getPublicKeyString().substring(0, 50) + "...");
    }

    private void shutdown() {
        System.out.println("Shutting down...");

        for (WebSocketClient client : connections.values()) {
            client.close();
        }

        try {
            server.stop();
        } catch (Exception e) {
            System.out.println("Error stopping server: " + e.getMessage());
        }

        scanner.close();
        System.out.println("Goodbye!");
    }

    // Inner WebSocket Server class
    private class ChatWebSocketServer extends WebSocketServer {
        public ChatWebSocketServer(InetSocketAddress address) {
            super(address);
        }

        @Override
        public void onOpen(WebSocket conn, ClientHandshake handshake) {
            System.out.println("New connection from: " + conn.getRemoteSocketAddress());
        }

        @Override
        public void onClose(WebSocket conn, int code, String reason, boolean remote) {
            System.out.println("Connection closed: " + conn.getRemoteSocketAddress());
            // Remove from connections
            connections.entrySet().removeIf(entry -> entry.getValue().equals(conn));
        }

        @Override
        public void onMessage(WebSocket conn, String message) {
            handleIncomingMessage(message, conn);
        }

        @Override
        public void onError(WebSocket conn, Exception ex) {
            System.out.println("WebSocket error: " + ex.getMessage());
        }

        @Override
        public void onStart() {
            System.out.println("WebSocket server started on port " + port);
        }
    }

    public static void main(String[] args) {
        String nodeId = "node" + System.currentTimeMillis();
        int port = 8080;

        if (args.length >= 1) {
            nodeId = args[0];
        }
        if (args.length >= 2) {
            port = Integer.parseInt(args[1]);
        }

        EchoChatNode node = new EchoChatNode(nodeId, port);
        node.start();
    }
}