package com.example.chat.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Outgoing frames for one WebSocket session, written by a single virtual thread so they arrive in order.
 * <p>
 * Callers only enqueue, so a slow or stalled client never blocks the request that sent the message
 * (which would otherwise also hold its DB connection, since after-commit listeners run before the
 * connection is released). A client that falls more than {@link #MAX_QUEUED} frames behind is
 * disconnected; it catches up from history when it reconnects.
 */
public class Outbox {

    static final int MAX_QUEUED = 1_000;
    private static final Logger log = LoggerFactory.getLogger(Outbox.class);

    private final WebSocketSession session;
    private final ThreadPoolExecutor sender;

    public Outbox(WebSocketSession session) {
        this.session = session;
        this.sender = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_QUEUED), Thread.ofVirtual().name("ws-push-" + session.getId()).factory());
    }

    public void send(TextMessage frame) {
        try {
            sender.execute(() -> write(frame));
        } catch (RejectedExecutionException e) {
            if (!sender.isShutdown()) {
                log.warn("Session {} is {} frames behind; disconnecting it", session.getId(), MAX_QUEUED);
                closeSession(CloseStatus.SESSION_NOT_RELIABLE);
            }
        }
    }

    /** Stops sending; frames still queued are dropped (the client catches up from history). */
    public void shutdown() {
        sender.shutdownNow();
    }

    private void write(TextMessage frame) {
        try {
            if (session.isOpen()) {
                session.sendMessage(frame);
            }
        } catch (Exception e) {
            log.warn("Push to session {} failed: {}", session.getId(), e.getMessage());
            closeSession(CloseStatus.SESSION_NOT_RELIABLE);
        }
    }

    private void closeSession(CloseStatus status) {
        shutdown();
        try {
            session.close(status);
        } catch (Exception ignored) {
            // Already closing or gone
        }
    }
}
