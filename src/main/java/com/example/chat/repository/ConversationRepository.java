package com.example.chat.repository;

import com.example.chat.domain.Conversation;
import com.example.chat.domain.ConversationType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByTypeOrderByName(ConversationType type);

    Optional<Conversation> findByIdAndType(Long id, ConversationType type);

    Optional<Conversation> findByDirectKey(String directKey);

    /** SELECT ... FOR UPDATE: serializes concurrent senders on the same conversation until commit. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Conversation c WHERE c.id = :id")
    Optional<Conversation> findByIdForUpdate(Long id);

    /**
     * Creates the direct conversation if missing. Under concurrent calls for the same key, Postgres
     * blocks the loser until the winner commits, then skips the insert. Returns rows inserted (0 or 1).
     */
    @Modifying
    @Query(value = """
            INSERT INTO conversations (type, direct_key, last_seq, created_at) VALUES ('DIRECT', :directKey, 0, now())
            ON CONFLICT (direct_key) DO NOTHING""", nativeQuery = true)
    int insertDirectIfAbsent(String directKey);
}
