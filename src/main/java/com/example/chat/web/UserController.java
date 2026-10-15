package com.example.chat.web;

import com.example.chat.service.ConversationService;
import com.example.chat.service.ConversationService.ConversationView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/{username}")
public class UserController {

    private final ConversationService conversations;

    public UserController(ConversationService conversations) {
        this.conversations = conversations;
    }

    @GetMapping("/conversations")
    public List<ConversationView> conversations(@PathVariable String username) {
        return conversations.forUser(username);
    }
}
