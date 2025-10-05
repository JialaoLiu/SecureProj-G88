#!/usr/bin/env python3
import http.server
import ssl
import socketserver

class MyHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()
        html_content = '''<!DOCTYPE html>
<html>
<head>
    <title>Certificate Trust Test</title>
</head>
<body>
    <h1>Certificate Trust Established!</h1>
    <p>You have successfully accessed this HTTPS page, which means your browser now trusts the certificate.</p>
    <p>Now you can test WSS connection:</p>
    <ul>
        <li><a href="http://localhost:5173/">Go to Chat Application</a></li>
        <li>The chat will now use WSS (encrypted WebSocket)</li>
    </ul>
    <script>
        // Test WSS connection
        console.log('Testing WSS connection...');
        const ws = new WebSocket('wss://localhost:9443');
        ws.onopen = function() {
            document.body.innerHTML += '<p style="color: green;">WSS Connection Success!</p>';
            console.log('WSS connected successfully');
            ws.close();
        };
        ws.onerror = function(error) {
            document.body.innerHTML += '<p style="color: red;">WSS Connection Failed</p>';
            console.error('WSS connection failed:', error);
        };
    </script>
</body>
</html>'''
        self.wfile.write(html_content.encode('utf-8'))

PORT = 8444
httpd = socketserver.TCPServer(("", PORT), MyHandler)

# Load the PEM certificate and key
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain('localhost.pem')

httpd.socket = context.wrap_socket(httpd.socket, server_side=True)

print(f"🔒 HTTPS Certificate Trust Server running on https://localhost:{PORT}")
print("Visit https://localhost:8444 to trust the certificate first")
print("Then go to http://localhost:5173 to test WSS")
httpd.serve_forever()