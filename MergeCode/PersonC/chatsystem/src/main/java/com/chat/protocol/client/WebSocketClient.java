package com.chat.protocol.client;

import com.chat.protocol.model.Msg;
import com.chat.protocol.ssl.TyrusSslConfigurator;
import com.chat.protocol.stub.JSONStub;
import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import javax.websocket.*;
import java.net.URI;
import java.util.logging.Logger;
import java.util.logging.Level;

@ClientEndpoint
public class WebSocketClient {
    private static final Logger LOG = Logger.getLogger(WebSocketClient.class.getName());
    private Session session;
    private String peerId;

    public void connect(String peerIp, int port, String peerId) throws Exception {
        this.peerId = peerId;
        ClientManager client = ClientManager.createClient();
        client.getProperties().put(ClientProperties.SSL_ENGINE_CONFIGURATOR, 
            new org.glassfish.tyrus.client.SslEngineConfigurator(TyrusSslConfigurator.getSSLContext()));
        URI uri = new URI("wss://" + peerIp + ":" + port + "/chat?peerId=" + peerId);
        session = client.connectToServer(this, uri);
        LOG.log(Level.INFO, "Connected to {0}:{1}", new Object[]{peerIp, port});
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        this.session = session;
        LOG.log(Level.INFO, "Client session opened: {0}", session.getId());
    }

    @OnMessage
    public void onMessage(String json, Session session) {
        try {
            Msg msg = JSONStub.deserialize(json);
            if (msg == null || msg.getHeader() == null) {
                LOG.warning("Invalid JSON received");
                return;
            }
            LOG.log(Level.INFO, "Client received: {0}", msg.getBody().getContent());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Client message error: {0}", e.getMessage());
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason reason) {
        LOG.log(Level.INFO, "Client disconnected: {0}", reason);
        this.session = null;
    }

    @OnError
    public void onError(Session session, Throwable error) {
        LOG.log(Level.SEVERE, "Client error: {0}", error.getMessage());
    }

    public void send(Msg msg) throws Exception {
        if (session != null && session.isOpen()) {
            session.getBasicRemote().sendText(JSONStub.serialize(msg));
            LOG.log(Level.INFO, "Sent message to peer: {0}", peerId);
        }
    }

    public void close() throws Exception {
        if (session != null && session.isOpen()) {
            session.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Client shutdown"));
        }
    }
}