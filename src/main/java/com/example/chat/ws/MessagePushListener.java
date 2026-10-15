package com.example.chat.ws;

import com.example.chat.service.MessageService.MessageSentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/** Pushes each message to its conversation's members, only once the send transaction has committed. */
@Component
public class MessagePushListener {

    private static final Logger log = LoggerFactory.getLogger(MessagePushListener.class);

    private final SessionRegistry registry;
    private final JsonMapper json;

    public MessagePushListener(SessionRegistry registry, JsonMapper json) {
        this.registry = registry;
        this.json = json;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMessageSent(MessageSentEvent event) {
        TextMessage frame = new TextMessage(json.writeValueAsString(Map.of("type", "message", "message", event.message())));
        for (String recipient : event.recipients()) {
            for (WebSocketSession session : registry.sessionsOf(recipient)) {
                try {
                    session.sendMessage(frame);
                } catch (Exception e) {
                    // The message is committed; a client that misses it catches up via history
                    log.warn("Push to {} (session {}) failed: {}", recipient, session.getId(), e.getMessage());
                }
            }
        }
    }
}
