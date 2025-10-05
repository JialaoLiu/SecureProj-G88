package devserver;

/**
 * Simple test to diagnose TLS configuration issues
 */
public class TlsTest {
    public static void main(String[] args) {
        System.out.println("[TLS-TEST] Starting TLS configuration test...");

        try {
            System.out.println("[TLS-TEST] Attempting to load TlsConfig...");

            // This will trigger the static initialization
            System.out.println("[TLS-TEST] Getting SSL context...");
            javax.net.ssl.SSLContext context = TlsConfig.getSSLContext();

            if (context != null) {
                System.out.println("[TLS-TEST] SUCCESS: SSL context loaded successfully!");
                System.out.println("[TLS-TEST] Context protocol: " + context.getProtocol());
                System.out.println("[TLS-TEST] Context provider: " + context.getProvider().getName());
            } else {
                System.out.println("[TLS-TEST] FAILED: SSL context is null");
            }

        } catch (Exception e) {
            System.out.println("[TLS-TEST] EXCEPTION during TLS test:");
            e.printStackTrace();
        }

        System.out.println("[TLS-TEST] Test completed.");
    }
}