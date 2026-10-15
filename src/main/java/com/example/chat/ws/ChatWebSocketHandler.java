package com.example.chat.ws;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.regex.Pattern;

/**
 * Receive-only socket at {@code /ws?user={username}}. Clients send messages over REST; the server
 * pushes {@code {"type":"message","message":{...}}} frames for conversations the user belongs to.
 */
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String USER_ATTR = "user";
    private static final String OUTBOX_ATTR = "outbox";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{1,50}");

    private final SessionRegistry registry;

    public ChatWebSocketHandler(SessionRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String user = session.getUri() == null ? null
                : UriComponentsBuilder.fromUri(session.getUri()).build().getQueryParams().getFirst("user");
        if (user == null || !USERNAME.matcher(user).matches()) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Missing or invalid ?user="));
            return;
        }
        Outbox outbox = new Outbox(session);
        session.getAttributes().put(USER_ATTR, user);
        session.getAttributes().put(OUTBOX_ATTR, outbox);
        registry.add(user, outbox);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String user = (String) session.getAttributes().get(USER_ATTR);
        Outbox outbox = (Outbox) session.getAttributes().get(OUTBOX_ATTR);
        if (user != null && outbox != null) {
            registry.remove(user, outbox);
            outbox.shutdown();
        }
    }
}
