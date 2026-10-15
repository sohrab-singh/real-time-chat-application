package com.example.chat.web;

import com.example.chat.service.MessageService;
import com.example.chat.service.MessageService.HistoryPage;
import com.example.chat.service.MessageService.MessageView;
import com.example.chat.service.MessageService.SendResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}")
public class ConversationController {

    /** {@code clientMessageId} is optional; reuse it when retrying so the message isn't stored twice. */
    public record SendMessageRequest(String sender, String content, String clientMessageId) {
    }

    public record MarkReadRequest(String user, long seq) {
    }

    private final MessageService messages;

    public ConversationController(MessageService messages) {
        this.messages = messages;
    }

    /** 201 for a new message, 200 when a retry matched an earlier send with the same clientMessageId. */
    @PostMapping("/messages")
    public ResponseEntity<MessageView> send(@PathVariable Long conversationId, @RequestBody SendMessageRequest request) {
        SendResult result = messages.send(conversationId, request.sender(), request.content(), request.clientMessageId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.message());
    }

    /** Catch-up paging: pass the highest seq you have; repeat with the last returned seq while hasMore. */
    @GetMapping("/messages")
    public HistoryPage history(@PathVariable Long conversationId,
                               @RequestParam String user,
                               @RequestParam(defaultValue = "0") long afterSeq,
                               @RequestParam(defaultValue = "50") int limit) {
        return messages.history(conversationId, user, afterSeq, limit);
    }

    /** Moves the user's read pointer forward to {@code seq}. Idempotent. */
    @PutMapping("/read")
    public ResponseEntity<Void> markRead(@PathVariable Long conversationId, @RequestBody MarkReadRequest request) {
        messages.markRead(conversationId, request.user(), request.seq());
        return ResponseEntity.noContent().build();
    }
}
