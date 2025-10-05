#!/usr/bin/env python3
import http.server
import ssl
import socketserver

class MyHTTPRequestHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()
        html_content = '''
        <html>
        <head><title>Certificate Test - Success!</title></head>
        <body>
        <h1>Certificate Trust Successful!</h1>
        <p>Great! Your browser now trusts the localhost certificate.</p>
        <p><strong>You can now close this tab and return to the chat application at:</strong></p>
        <p><a href="http://localhost:5173/">http://localhost:5173/</a></p>
        <p>The WSS connection should work now with the trusted certificate.</p>
        <hr>
        <p><small>This temporary server can be closed once you have tested the chat application.</small></p>
        </body>
        </html>
        '''
        self.wfile.write(html_content.encode('utf-8'))

PORT = 8444
Handler = MyHTTPRequestHandler

print(f"🚀 Starting temporary HTTPS server on port {PORT}")
print(f"📋 Please open this URL in your browser: https://localhost:{PORT}")
print(f"💡 This will force the certificate trust dialog to appear.")

with socketserver.TCPServer(("", PORT), Handler) as httpd:
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain('temp-cert.pem', 'temp-key.pem')
    httpd.socket = context.wrap_socket(httpd.socket, server_side=True)

    print(f"✅ HTTPS server is ready! Visit: https://localhost:{PORT}")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n🛑 Server stopped")