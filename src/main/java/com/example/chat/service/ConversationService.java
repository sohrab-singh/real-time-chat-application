package com.example.chat.service;

import com.example.chat.domain.Conversation;
import com.example.chat.domain.ConversationType;
import com.example.chat.domain.Membership;
import com.example.chat.repository.MembershipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    /**
     * {@code members} lists everyone in the conversation, including the requesting user. Because seqs
     * have no gaps, {@code unreadCount} is exactly {@code lastSeq - lastReadSeq}.
     */
    public record ConversationView(Long id, ConversationType type, String name, List<String> members,
                                   long lastSeq, long lastReadSeq, long unreadCount) {
    }

    private final MembershipRepository memberships;
    private final UserService users;

    public ConversationService(MembershipRepository memberships, UserService users) {
        this.memberships = memberships;
        this.users = users;
    }

    /** Group rooms and DMs the user belongs to. Unknown users simply have none. */
    @Transactional(readOnly = true)
    public List<ConversationView> forUser(String username) {
        if (users.find(username).isEmpty()) {
            return List.of();
        }
        List<Membership> myMemberships = memberships.findByUsernameWithConversation(username);
        if (myMemberships.isEmpty()) {
            return List.of();
        }
        List<Conversation> mine = myMemberships.stream().map(Membership::getConversation).toList();
        Map<Long, List<String>> membersById = memberships
                .findByConversationIdsWithUser(mine.stream().map(Conversation::getId).toList()).stream()
                .collect(Collectors.groupingBy(m -> m.getConversation().getId(),
                        Collectors.mapping(m -> m.getUser().getUsername(), Collectors.toList())));
        return myMemberships.stream()
                .map(m -> {
                    Conversation c = m.getConversation();
                    List<String> members = membersById.getOrDefault(c.getId(), List.of()).stream().sorted().toList();
                    return new ConversationView(c.getId(), c.getType(), c.getName(), members,
                            c.getLastSeq(), m.getLastReadSeq(), c.getLastSeq() - m.getLastReadSeq());
                })
                .toList();
    }
}
