package com.example.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A group room or a 1:1 direct conversation.
 * <p>
 * Direct conversations carry a {@code directKey} built from the two user ids (lower id first),
 * so the unique constraint guarantees at most one direct conversation per pair of users.
 * <p>
 * {@code lastSeq} is the sequence number of the most recent message; the next message gets
 * {@code lastSeq + 1}.
 */
@Entity
@Table(name = "conversations")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ConversationType type;

    /** Display name for group rooms; null for direct conversations. */
    @Column(unique = true, length = 100)
    private String name;

    /** "{lowerUserId}:{higherUserId}" for direct conversations; null for groups. */
    @Column(unique = true, length = 50)
    private String directKey;

    @Column(nullable = false)
    private long lastSeq;

    @Column(nullable = false)
    private Instant createdAt;

    protected Conversation() {
    }

    private Conversation(ConversationType type, String name, String directKey) {
        this.type = type;
        this.name = name;
        this.directKey = directKey;
        this.lastSeq = 0;
        this.createdAt = Instant.now();
    }

    public static Conversation group(String name) {
        return new Conversation(ConversationType.GROUP, name, null);
    }

    public static Conversation direct(User a, User b) {
        return new Conversation(ConversationType.DIRECT, null, directKey(a.getId(), b.getId()));
    }

    public static String directKey(long userA, long userB) {
        if (userA == userB) {
            throw new IllegalArgumentException("A direct conversation needs two different users");
        }
        return Math.min(userA, userB) + ":" + Math.max(userA, userB);
    }

    /** Reserves and returns the next sequence number. Call while holding a lock on this row. */
    public long nextSeq() {
        return ++lastSeq;
    }

    public Long getId() {
        return id;
    }

    public ConversationType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDirectKey() {
        return directKey;
    }

    public long getLastSeq() {
        return lastSeq;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
