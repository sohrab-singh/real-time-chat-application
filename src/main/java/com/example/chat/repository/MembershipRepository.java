package com.example.chat.repository;

import com.example.chat.domain.ConversationType;
import com.example.chat.domain.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MembershipRepository extends JpaRepository<Membership, Long> {

    @Query("""
            SELECT m FROM Membership m JOIN FETCH m.user
            WHERE m.conversation.type = :type
            ORDER BY m.user.username""")
    List<Membership> findAllByConversationTypeWithUser(ConversationType type);

    /** Adds the membership if missing; safe under concurrent calls for the same pair. */
    @Modifying
    @Query(value = """
            INSERT INTO memberships (conversation_id, user_id, joined_at) VALUES (:conversationId, :userId, now())
            ON CONFLICT (conversation_id, user_id) DO NOTHING""", nativeQuery = true)
    void insertIfAbsent(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("SELECT m.user.username FROM Membership m WHERE m.conversation.id = :conversationId ORDER BY m.user.username")
    List<String> findUsernamesByConversationId(Long conversationId);

    @Modifying
    @Query("DELETE FROM Membership m WHERE m.conversation.id = :conversationId AND m.user.username = :username")
    void deleteByConversationIdAndUsername(Long conversationId, String username);
}
