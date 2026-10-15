package com.example.chat.repository;

import com.example.chat.domain.Message;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @EntityGraph(attributePaths = "sender")
    List<Message> findByConversationIdAndSeqGreaterThanOrderBySeq(Long conversationId, long afterSeq, Limit limit);
}
