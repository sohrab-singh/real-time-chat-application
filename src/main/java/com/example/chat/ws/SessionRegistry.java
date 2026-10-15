package com.example.chat.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Open WebSocket sessions by username. A user may have several (e.g. multiple tabs). */
@Component
public class SessionRegistry {

    private final Map<String, Set<WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();

    public void add(String username, WebSocketSession session) {
        sessionsByUser.computeIfAbsent(username, u -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(String username, WebSocketSession session) {
        sessionsByUser.computeIfPresent(username, (u, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
    }

    public Set<WebSocketSession> sessionsOf(String username) {
        return sessionsByUser.getOrDefault(username, Set.of());
    }
}
