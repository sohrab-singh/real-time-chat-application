package com.example.chat.ws;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.regex.Pattern;

/**
 * Receive-only socket at {@code /ws?user={username}}. Clients send messages over REST; the server
 * pushes {@code {"type":"message","message":{...}}} frames for conversations the user belongs to.
 */
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final String USER_ATTR = "user";
    private static final String OUTBOUND_ATTR = "outbound";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.-]{1,50}");
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_SIZE_LIMIT = 512 * 1024;

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
        // Raw sessions are not safe for concurrent sends; the decorator serializes and buffers them
        WebSocketSession outbound = new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_SIZE_LIMIT);
        session.getAttributes().put(USER_ATTR, user);
        session.getAttributes().put(OUTBOUND_ATTR, outbound);
        registry.add(user, outbound);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String user = (String) session.getAttributes().get(USER_ATTR);
        if (user != null) {
            registry.remove(user, (WebSocketSession) session.getAttributes().get(OUTBOUND_ATTR));
        }
    }
}
