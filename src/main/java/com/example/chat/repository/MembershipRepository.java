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

    /**
     * Adds the membership if missing; safe under concurrent calls for the same pair. New members start
     * with everything already sent marked as read, so joining a busy room doesn't show a huge unread count.
     */
    @Modifying
    @Query(value = """
            INSERT INTO memberships (conversation_id, user_id, joined_at, last_read_seq)
            SELECT :conversationId, :userId, now(), c.last_seq FROM conversations c WHERE c.id = :conversationId
            ON CONFLICT (conversation_id, user_id) DO NOTHING""", nativeQuery = true)
    void insertIfAbsent(Long conversationId, Long userId);

    /**
     * Moves the read pointer forward to {@code seq}, capped at the conversation's last seq. Never moves it
     * back, so repeated or out-of-order calls are harmless. Returns rows updated (0 if not a member).
     */
    @Modifying
    @Query(value = """
            UPDATE memberships m SET last_read_seq = GREATEST(m.last_read_seq, LEAST(:seq, c.last_seq))
            FROM conversations c
            WHERE c.id = m.conversation_id AND m.conversation_id = :conversationId AND m.user_id = :userId""",
            nativeQuery = true)
    int advanceLastRead(Long conversationId, Long userId, long seq);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("SELECT m.user.username FROM Membership m WHERE m.conversation.id = :conversationId ORDER BY m.user.username")
    List<String> findUsernamesByConversationId(Long conversationId);

    @Query("""
            SELECT m FROM Membership m JOIN FETCH m.conversation
            WHERE m.user.username = :username
            ORDER BY m.conversation.type DESC, m.conversation.name, m.conversation.id""")
    List<Membership> findByUsernameWithConversation(String username);

    @Query("""
            SELECT m FROM Membership m JOIN FETCH m.user
            WHERE m.conversation.id IN :conversationIds""")
    List<Membership> findByConversationIdsWithUser(List<Long> conversationIds);

    @Modifying
    @Query("DELETE FROM Membership m WHERE m.conversation.id = :conversationId AND m.user.username = :username")
    void deleteByConversationIdAndUsername(Long conversationId, String username);
}
