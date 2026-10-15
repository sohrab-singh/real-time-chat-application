package com.example.chat.ws;

import com.example.chat.service.MessageService.MessageSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

/**
 * Pushes each message to its conversation's members, only once the send transaction has committed.
 * Runs on the sending request's thread, so it only enqueues; each session's {@link Outbox} does the I/O.
 */
@Component
public class MessagePushListener {

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
            for (Outbox outbox : registry.outboxesOf(recipient)) {
                outbox.send(frame);
            }
        }
    }
}
