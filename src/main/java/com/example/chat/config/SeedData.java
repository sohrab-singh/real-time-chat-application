package com.example.chat.config;

import com.example.chat.domain.Conversation;
import com.example.chat.repository.ConversationRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SeedData {

    private static final List<String> GROUP_ROOMS = List.of("general", "random", "help");

    @Bean
    ApplicationRunner seedGroupRooms(ConversationRepository conversations) {
        return args -> conversations.saveAll(GROUP_ROOMS.stream().map(Conversation::group).toList());
    }
}
