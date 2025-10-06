EchoChat - Distributed Secure Chat System
Group G88

QUICK START
-----------
1. Start backend servers:
   mvn exec:java

   This starts three servers:
   - WebSocket server: ws://localhost:8080
   - File server: http://localhost:8081
   - Authentication server: http://localhost:8082

2. Start frontend (in a new terminal):
   cd frontend
   npm install
   npm run dev

3. Open browser: http://localhost:5173

4. Login with demo account:
   Username: alice
   Password: demo123

COMPILATION
-----------
mvn clean compile

AUTHENTICATION
--------------
JWT-based authentication system.

Demo Accounts:
- alice / demo123
- bob / demo123
- charlie / demo123

You can also register new accounts.

API Endpoints:
- POST http://localhost:8082/api/auth/login      - Login with username/password
- POST http://localhost:8082/api/auth/register   - Register new account
- POST http://localhost:8082/api/auth/verify     - Verify JWT token
- GET  http://localhost:8082/api/debug/users     - List all users (debug endpoint)

FEATURES
--------
- JWT authentication (login/register)
- Real-time messaging (direct & group chat)
- User presence (online/offline)
- File upload and download
- Rate limiting
- SOCP protocol with encryption support

FILE UPLOAD
-----------
Files stored in: ./uploads/files/
Maximum size: 256MB
Chunk size: 256KB

TESTING
-------
- Login with demo accounts
- Open multiple browser windows for multi-user testing
- Upload files via attachment icon
- Test debug endpoint: http://localhost:8082/api/debug/users

COMMAND LINE MODE (Original)
-----------------------------
mvn exec:java -Dexec.args="<nodeId> <port>"

Examples:
mvn exec:java -Dexec.args="alice 8080"
mvn exec:java -Dexec.args="bob 8081"

Commands:
connect <host:port>
msg <nodeId> <message>
broadcast <message>
list
quit
