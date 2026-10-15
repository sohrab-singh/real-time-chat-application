package com.example.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "messages",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"conversation_id", "seq"}),
                // Backstop for idempotent sends; nulls are distinct, so messages without an id never collide
                @UniqueConstraint(columnNames = {"conversation_id", "sender_id", "client_message_id"})
        })
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    /** Position within the conversation, starting at 1. Messages are ordered by this. */
    @Column(nullable = false)
    private long seq;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    /** Optional id chosen by the client so a retried send returns the original message instead of a duplicate. */
    @Column(length = 64)
    private String clientMessageId;

    @Column(nullable = false)
    private Instant sentAt;

    protected Message() {
    }

    public Message(Conversation conversation, User sender, long seq, String content, String clientMessageId) {
        this.conversation = conversation;
        this.sender = sender;
        this.seq = seq;
        this.content = content;
        this.clientMessageId = clientMessageId;
        // Postgres stores microseconds; truncate so the live push and history report the same value
        this.sentAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public User getSender() {
        return sender;
    }

    public long getSeq() {
        return seq;
    }

    public String getContent() {
        return content;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public Instant getSentAt() {
        return sentAt;
    }
}
