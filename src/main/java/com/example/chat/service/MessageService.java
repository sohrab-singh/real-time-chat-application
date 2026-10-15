package com.example.chat.service;

import com.example.chat.domain.Conversation;
import com.example.chat.domain.Message;
import com.example.chat.domain.User;
import com.example.chat.repository.ConversationRepository;
import com.example.chat.repository.MembershipRepository;
import com.example.chat.repository.MessageRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class MessageService {

    static final int MAX_CONTENT_LENGTH = 4000;
    static final int MAX_PAGE_SIZE = 200;

    public record MessageView(Long id, Long conversationId, long seq, String sender, String content, Instant sentAt) {

        static MessageView of(Message m) {
            return new MessageView(m.getId(), m.getConversation().getId(), m.getSeq(),
                    m.getSender().getUsername(), m.getContent(), m.getSentAt());
        }
    }

    /** {@code hasMore} means another page exists after the last message returned. */
    public record HistoryPage(List<MessageView> messages, boolean hasMore) {
    }

    /** Published inside the send transaction; delivered to listeners only once it commits. */
    public record MessageSentEvent(MessageView message, List<String> recipients) {
    }

    private final ConversationRepository conversations;
    private final MembershipRepository memberships;
    private final MessageRepository messages;
    private final UserService users;
    private final ApplicationEventPublisher events;

    public MessageService(ConversationRepository conversations, MembershipRepository memberships,
                          MessageRepository messages, UserService users, ApplicationEventPublisher events) {
        this.conversations = conversations;
        this.memberships = memberships;
        this.messages = messages;
        this.users = users;
        this.events = events;
    }

    /**
     * Appends a message to the conversation. The conversation row is locked for the rest of the
     * transaction, so concurrent sends get consecutive sequence numbers; a failed send rolls back
     * its increment, so there are no gaps.
     */
    @Transactional
    public MessageView send(Long conversationId, String senderName, String content) {
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Content must be non-blank and at most " + MAX_CONTENT_LENGTH + " characters");
        }
        User sender = requireUser(senderName);
        Conversation conversation = conversations.findByIdForUpdate(conversationId)
                .orElseThrow(() -> notFound(conversationId));
        // Checked after taking the lock, so a concurrent leave can't slip in between check and insert
        requireMember(conversationId, sender);
        Message message = messages.save(new Message(conversation, sender, conversation.nextSeq(), content));
        MessageView view = MessageView.of(message);
        events.publishEvent(new MessageSentEvent(view, memberships.findUsernamesByConversationId(conversationId)));
        return view;
    }

    /** Messages with {@code seq > afterSeq}, oldest first. Members only. */
    @Transactional(readOnly = true)
    public HistoryPage history(Long conversationId, String username, long afterSeq, int limit) {
        if (afterSeq < 0 || limit < 1 || limit > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "afterSeq must be >= 0 and limit between 1 and " + MAX_PAGE_SIZE);
        }
        User user = requireUser(username);
        if (!conversations.existsById(conversationId)) {
            throw notFound(conversationId);
        }
        requireMember(conversationId, user);
        List<MessageView> page = messages
                .findByConversationIdAndSeqGreaterThanOrderBySeq(conversationId, afterSeq, Limit.of(limit + 1))
                .stream().map(MessageView::of).toList();
        boolean hasMore = page.size() > limit;
        return new HistoryPage(hasMore ? page.subList(0, limit) : page, hasMore);
    }

    private User requireUser(String username) {
        return users.find(username).orElseThrow(MessageService::notMember);
    }

    private void requireMember(Long conversationId, User user) {
        if (!memberships.existsByConversationIdAndUserId(conversationId, user.getId())) {
            throw notMember();
        }
    }

    private static ResponseStatusException notMember() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a member of this conversation");
    }

    private static ResponseStatusException notFound(Long conversationId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation " + conversationId + " not found");
    }
}
