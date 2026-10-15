package com.example.chat.ws;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Outboxes of open WebSocket sessions by username. A user may have several (e.g. multiple tabs). */
@Component
public class SessionRegistry {

    private final Map<String, Set<Outbox>> outboxesByUser = new ConcurrentHashMap<>();

    public void add(String username, Outbox outbox) {
        outboxesByUser.computeIfAbsent(username, u -> ConcurrentHashMap.newKeySet()).add(outbox);
    }

    public void remove(String username, Outbox outbox) {
        outboxesByUser.computeIfPresent(username, (u, outboxes) -> {
            outboxes.remove(outbox);
            return outboxes.isEmpty() ? null : outboxes;
        });
    }

    public Set<Outbox> outboxesOf(String username) {
        return outboxesByUser.getOrDefault(username, Set.of());
    }
}
