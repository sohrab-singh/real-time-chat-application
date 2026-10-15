package com.example.chat.web;

import com.example.chat.service.RoomService;
import com.example.chat.service.RoomService.RoomView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomService rooms;

    public RoomController(RoomService rooms) {
        this.rooms = rooms;
    }

    @GetMapping
    public List<RoomView> list() {
        return rooms.listRooms();
    }

    @PutMapping("/{roomId}/members/{username}")
    public ResponseEntity<Void> join(@PathVariable Long roomId, @PathVariable String username) {
        rooms.join(roomId, username);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{roomId}/members/{username}")
    public ResponseEntity<Void> leave(@PathVariable Long roomId, @PathVariable String username) {
        rooms.leave(roomId, username);
        return ResponseEntity.noContent().build();
    }
}
