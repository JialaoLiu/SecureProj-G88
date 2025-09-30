package com.chat.protocol;

import com.chat.protocol.auth.AuthManager;
import com.chat.protocol.util.ChatRateLimiter;
import com.chat.protocol.ssl.TyrusSslConfigurator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import javax.net.ssl.SSLSession;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class WebSocketHandlerTest {
    private SSLSession mockSslSession;

    @BeforeEach
    void setUp() {
        mockSslSession = Mockito.mock(SSLSession.class);
    }

    @AfterEach
    void tearDown() {
        ChatRateLimiter.remove("testPeer");
    }

    @Test
    void testRateLimiter() {
        assertTrue(ChatRateLimiter.acquire("testPeer"), "Rate limiter should allow first message");
    }

    @Test
    void testAuthManager() {
        when(mockSslSession.getPeerCertificates()).thenReturn(new java.security.cert.Certificate[]{});
        assertDoesNotThrow(() -> AuthManager.generateNonce(), "Nonce generation should not throw");
    }

    @Test
    void testSslConfiguration() {
        assertDoesNotThrow(() -> TyrusSslConfigurator.getSSLContext(), "SSLContext should initialize without errors");
    }
}