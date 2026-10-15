package com.example.chat.service;

import com.example.chat.domain.Conversation;
import com.example.chat.domain.User;
import com.example.chat.repository.ConversationRepository;
import com.example.chat.repository.MembershipRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class DirectMessageService {

    public record DirectView(Long id, List<String> members, boolean created) {
    }

    private final ConversationRepository conversations;
    private final MembershipRepository memberships;
    private final UserService users;

    public DirectMessageService(ConversationRepository conversations, MembershipRepository memberships,
                                UserService users) {
        this.conversations = conversations;
        this.memberships = memberships;
        this.users = users;
    }

    /**
     * Returns the direct conversation between two users, creating it (and the users) if needed.
     * Argument order does not matter, and concurrent calls for the same pair all get the same conversation.
     */
    @Transactional
    public DirectView getOrCreate(String usernameA, String usernameB) {
        // Create users in a fixed order: two concurrent requests for the same new pair given in
        // opposite order would otherwise each insert one user and wait on the other (deadlock).
        boolean ordered = usernameA == null || usernameB == null || usernameA.compareTo(usernameB) <= 0;
        User a = users.getOrCreate(ordered ? usernameA : usernameB);
        User b = users.getOrCreate(ordered ? usernameB : usernameA);
        if (a.getId().equals(b.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A DM needs two different users");
        }
        String key = Conversation.directKey(a.getId(), b.getId());
        boolean created = conversations.insertDirectIfAbsent(key) == 1;
        Conversation dm = conversations.findByDirectKey(key).orElseThrow();
        memberships.insertIfAbsent(dm.getId(), a.getId());
        memberships.insertIfAbsent(dm.getId(), b.getId());
        return new DirectView(dm.getId(), memberships.findUsernamesByConversationId(dm.getId()), created);
    }
}
