package com.example.chat.web;

import com.example.chat.service.MessageService;
import com.example.chat.service.MessageService.HistoryPage;
import com.example.chat.service.MessageService.MessageView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    public record SendMessageRequest(String sender, String content) {
    }

    private final MessageService messages;

    public MessageController(MessageService messages) {
        this.messages = messages;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MessageView send(@PathVariable Long conversationId, @RequestBody SendMessageRequest request) {
        return messages.send(conversationId, request.sender(), request.content());
    }

    /** Catch-up paging: pass the highest seq you have; repeat with the last returned seq while hasMore. */
    @GetMapping
    public HistoryPage history(@PathVariable Long conversationId,
                               @RequestParam String user,
                               @RequestParam(defaultValue = "0") long afterSeq,
                               @RequestParam(defaultValue = "50") int limit) {
        return messages.history(conversationId, user, afterSeq, limit);
    }
}
