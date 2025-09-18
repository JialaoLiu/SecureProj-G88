EchoChat - Distributed Secure Chat System
=========================================

Authors: Group G88
University of Adelaide - Secure Programming Assignment
WARNING: This version contains intentional vulnerabilities for educational purposes.

DESCRIPTION
-----------
EchoChat is a peer-to-peer distributed chat system implementing the SOCP v1.3 protocol.
Each node can connect to other nodes forming an overlay network with no central server.
All messages are encrypted using RSA encryption.

FEATURES
--------
- Distributed P2P architecture (no central server)
- RSA encryption for secure messaging
- WebSocket-based communication
- Private and broadcast messaging
- Node discovery and routing
- JWT-based authentication tokens

REQUIREMENTS
------------
- Java 11 or higher
- Maven 3.6+
- Network connectivity between nodes

COMPILATION
-----------
Navigate to the project directory and run:

    mvn clean compile

Or to create a runnable JAR:

    mvn clean package

RUNNING THE APPLICATION
-----------------------

Method 1: Using Maven
    mvn exec:java -Dexec.args="<nodeId> <port>"

Method 2: Using JAR file (after mvn package)
    java -jar target/echo-chat-1.0-SNAPSHOT.jar <nodeId> <port>

Method 3: Direct class execution
    java -cp target/classes echochat.EchoChatNode <nodeId> <port>

EXAMPLES
--------

Start first node:
    mvn exec:java -Dexec.args="alice 8080"

Start second node:
    mvn exec:java -Dexec.args="bob 8081"

Start third node:
    mvn exec:java -Dexec.args="charlie 8082"

USAGE COMMANDS
--------------
Once a node is running, use these commands:

    help                        - Show available commands
    connect <host:port>         - Connect to another node
    list                        - List all known nodes
    msg <nodeId> <message>     - Send private message to specific node
    broadcast <message>         - Send message to all connected nodes
    status                      - Show current node status
    quit/exit                   - Shutdown the node

EXAMPLE SESSION
---------------

Terminal 1 (Alice):
    alice> connect localhost:8081
    alice> msg bob Hello Bob!
    alice> broadcast Hello everyone!

Terminal 2 (Bob):
    bob> connect localhost:8080
    bob> msg alice Hi Alice!
    bob> list

NETWORK TOPOLOGY
----------------
The system creates an overlay network where each node maintains connections
to other nodes. Messages are routed through the network using a simple
flooding algorithm.

SECURITY FEATURES
-----------------
- RSA 2048-bit encryption for message confidentiality
- Digital signatures for message authenticity
- Public key exchange during node handshake
- Encrypted private messaging
- Input validation and sanitization

TESTING WITH OTHER GROUPS
-------------------------
To test interoperability:
1. Ensure your node is running on an accessible port
2. Share your IP address and port with other groups
3. Use the 'connect' command to join their network
4. Test messaging and broadcast functionality

PROTOCOL COMPLIANCE
-------------------
This implementation follows SOCP v1.3 specification:
- WebSocket transport layer
- RSA-only encryption
- Public channel broadcasting
- Distributed routing without central authority

TROUBLESHOOTING
---------------
- Port already in use: Try a different port number
- Connection refused: Ensure target node is running and accessible
- Decryption failed: Check if nodes have exchanged public keys properly
- Maven build fails: Ensure Java 11+ is installed and JAVA_HOME is set

CONTACT
-------
For questions about this implementation, contact Group G88.

IMPORTANT NOTES
---------------
- This code contains intentional vulnerabilities for peer review
- Do not use in production environments
- Run only in isolated/sandboxed environments
- Code is for educational purposes only

VERSION HISTORY
---------------
v1.0 - Initial implementation with basic P2P functionality
     - RSA encryption
     - WebSocket communication
     - Command-line interface