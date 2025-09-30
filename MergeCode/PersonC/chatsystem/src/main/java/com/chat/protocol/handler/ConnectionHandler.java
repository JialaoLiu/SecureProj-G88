package com.chat.protocol.handler;

import com.chat.protocol.auth.AuthManager;
import com.chat.protocol.config.Config;
import com.chat.protocol.model.Msg;
import com.chat.protocol.stub.DHTStub;
import com.chat.protocol.stub.JSONStub;
import com.chat.protocol.util.ChatRateLimiter;
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

@ServerEndpoint(value = "/chat", configurator = com.chat.protocol.ssl.TyrusSslConfigurator.class)
public class ConnectionHandler {
    private static final Logger LOG = Logger.getLogger(ConnectionHandler.class.getName());
    private static final Set<Session> sessions = new CopyOnWriteArraySet<>();
    private String peerId;
    private Session session;

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;

        // Connection limit check
        synchronized (sessions) {
            if (sessions.size() >= Config.MAX_CONNECTIONS) {
                closeWithReason("Max connections exceeded");
                return;
            }
            sessions.add(session);
        }

        // Extract peerId and authenticate
        String queryPeerId = extractPeerIdFromQuery(session.getRequestURI());
        if (queryPeerId == null || queryPeerId.trim().isEmpty()) {
            closeWithReason("Invalid peerId");
            return;
        }
        SSLSession sslSession = (SSLSession) config.getUserProperties().get("org.glassfish.tyrus.client.sslSession");
        if (sslSession == null || !AuthManager.authenticate(sslSession, queryPeerId)) {
            closeWithReason("Authentication failed");
            return;
        }
        this.peerId = queryPeerId;

        // Send handshake message
        Msg handshake = new Msg();
        Msg.Header header = new Msg.Header();
        header.setMsgType("handshake");
        header.setNonce(AuthManager.generateNonce());
        header.setSenderId(peerId);
        header.setTimestamp(System.currentTimeMillis());
        handshake.setHeader(header);
        sendText(JSONStub.serialize(handshake));

        LOG.log(Level.INFO, "Connected: {0}", peerId);
    }

    @OnMessage
    public void onMessage(String json, Session session) {
        if (!ChatRateLimiter.acquire(peerId)) {
            closeWithReason("Rate limit exceeded");
            return;
        }

        try {
            Msg msg = JSONStub.deserialize(json);
            if (msg == null || msg.getHeader() == null) {
                LOG.warning("Invalid JSON from peer: " + peerId);
                return;
            }
            String recipientId = msg.getHeader().getRecipientId();
            if (recipientId == null || recipientId.trim().isEmpty()) {
                LOG.warning("Invalid recipientId from peer: " + peerId);
                return;
            }
            String nextHop = DHTStub.lookupPeerIP(recipientId);
            if (nextHop != null && !nextHop.equals(session.getRequestURI().getHost() + ":" + session.getRequestURI().getPort())) {
                LOG.log(Level.INFO, "Routing to: {0}", nextHop);
                // Forward via client (for Person A)
            } else {
                LOG.log(Level.INFO, "Received: {0}", msg.getBody().getContent());
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Invalid message from {0}: {1}", new Object[]{peerId, e.getMessage()});
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        sessions.remove(session);
        ChatRateLimiter.remove(peerId);
        LOG.log(Level.INFO, "Disconnected: {0} ({1})", new Object[]{peerId, reason});
    }

    @OnError
    public void onError(Session session, Throwable error) {
        LOG.log(Level.SEVERE, "Error for {0}: {1}", new Object[]{peerId, error.getMessage()});
        closeWithReason("Error: " + error.getMessage());
    }

    private void sendText(String text) {
        try {
            if (session != null && session.isOpen()) {
                session.getBasicRemote().sendText(text);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Send failed to {0}: {1}", new Object[]{peerId, e.getMessage()});
        }
    }

    private void closeWithReason(String reason) {
        try {
            if (session != null && session.isOpen()) {
                session.close(new CloseReason(CloseReason.CloseCodes.PROTOCOL_ERROR, reason));
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Close failed for {0}: {1}", new Object[]{peerId, e.getMessage()});
        }
    }

    private String extractPeerIdFromQuery(URI uri) {
        if (uri == null) {
            LOG.warning("Null URI in peerId extraction");
            return null;
        }
        String query = uri.getQuery();
        if (query != null && query.startsWith("peerId=")) {
            return query.substring(7);
        }
        LOG.warning("Invalid or missing peerId in query: " + query);
        return null;
    }
}