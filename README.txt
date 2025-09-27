EchoChat - Distributed Secure Chat System
Group G88

COMPILATION
-----------
mvn clean compile

RUNNING (Original)
------------------
mvn exec:java -Dexec.args="<nodeId> <port>"

EXAMPLES
--------
mvn exec:java -Dexec.args="alice 8080"
mvn exec:java -Dexec.args="bob 8081"
mvn exec:java -Dexec.args="charlie 8082"

COMMANDS
--------
connect <host:port>
list
msg <nodeId> <message>
broadcast <message>
auth <nodeId>
sessions
revoke <nodeId>
status
quit

FRONTEND DEVELOPMENT SETUP
---------------------------
1. Start backend WebSocket server:
   mvn -q exec:java -Dexec.mainClass=devserver.ChatServer

2. Start frontend development server:
   cd frontend && npm run dev

3. Open browser: http://localhost:5173

TESTING
-------
- Page auto-connects and sends USER_HELLO
- Input box: type message and press Enter or click "Send MSG_DIRECT"
- "Send HEARTBEAT" button for manual heartbeat test
- Rate limiting: send >10 messages/second to trigger ERROR response

IMPORTANT NOTES
---------------
- devserver.ChatServer is DEVELOPMENT STUB only (not Person C's implementation)
- crypto.CryptoPorts are interface placeholders (for Person B's implementation)
- All signatures use "dev-mock" placeholder (not real crypto)
- Frontend strictly follows SOCP schema from src/main/resources/socp.json