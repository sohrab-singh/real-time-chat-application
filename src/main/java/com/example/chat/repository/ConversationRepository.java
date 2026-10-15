package com.example.chat.repository;

import com.example.chat.domain.Conversation;
import com.example.chat.domain.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    List<Conversation> findByTypeOrderByName(ConversationType type);

    Optional<Conversation> findByIdAndType(Long id, ConversationType type);
}
