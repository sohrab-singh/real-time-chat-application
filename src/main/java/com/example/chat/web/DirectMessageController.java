package com.example.chat.web;

import com.example.chat.service.DirectMessageService;
import com.example.chat.service.DirectMessageService.DirectView;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dms")
public class DirectMessageController {

    public record CreateDirectRequest(String userA, String userB) {
    }

    private final DirectMessageService dms;

    public DirectMessageController(DirectMessageService dms) {
        this.dms = dms;
    }

    /** 201 if the DM was created by this call, 200 if it already existed. */
    @PostMapping
    public ResponseEntity<DirectView> getOrCreate(@RequestBody CreateDirectRequest request) {
        DirectView dm = dms.getOrCreate(request.userA(), request.userB());
        return ResponseEntity.status(dm.created() ? HttpStatus.CREATED : HttpStatus.OK).body(dm);
    }
}
