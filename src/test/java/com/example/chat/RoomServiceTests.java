package com.example.chat;

import com.example.chat.service.RoomService;
import com.example.chat.service.RoomService.RoomView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomServiceTests {

    @Autowired
    RoomService rooms;

    private RoomView room(Long id) {
        return rooms.listRooms().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void joinAndLeaveAreIdempotent() {
        Long roomId = rooms.listRooms().getFirst().id();

        rooms.join(roomId, "alice");
        rooms.join(roomId, "alice");
        assertThat(room(roomId).members()).containsOnlyOnce("alice");

        rooms.leave(roomId, "alice");
        rooms.leave(roomId, "alice");
        assertThat(room(roomId).members()).doesNotContain("alice");
    }

    @Test
    void unknownRoomIsRejected() {
        assertThatThrownBy(() -> rooms.join(999_999L, "alice")).isInstanceOf(ResponseStatusException.class);
    }
}
