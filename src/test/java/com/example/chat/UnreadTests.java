package com.example.chat;

import com.example.chat.service.ConversationService;
import com.example.chat.service.ConversationService.ConversationView;
import com.example.chat.service.DirectMessageService;
import com.example.chat.service.MessageService;
import com.example.chat.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnreadTests {

    @Autowired
    DirectMessageService dms;

    @Autowired
    MessageService messages;

    @Autowired
    ConversationService conversations;

    @Autowired
    RoomService rooms;

    private long unread(String user, Long conversationId) {
        return conversations.forUser(user).stream()
                .filter(c -> c.id().equals(conversationId))
                .mapToLong(ConversationView::unreadCount)
                .findFirst().orElseThrow();
    }

    @Test
    void unreadCountsOthersMessagesAndReadPointerOnlyMovesForward() {
        String alice = "alice-" + System.nanoTime();
        String bob = "bob-" + System.nanoTime();
        Long dmId = dms.getOrCreate(alice, bob).id();
        for (int i = 0; i < 3; i++) {
            messages.send(dmId, alice, "m" + i, null);
        }

        assertThat(unread(alice, dmId)).as("own messages are read").isZero();
        assertThat(unread(bob, dmId)).isEqualTo(3);

        messages.markRead(dmId, bob, 2);
        assertThat(unread(bob, dmId)).isEqualTo(1);

        messages.markRead(dmId, bob, 1);
        assertThat(unread(bob, dmId)).as("read pointer never moves back").isEqualTo(1);

        messages.markRead(dmId, bob, 99);
        assertThat(unread(bob, dmId)).as("capped at last seq").isZero();

        messages.send(dmId, alice, "one more", null);
        assertThat(unread(bob, dmId)).isEqualTo(1);
    }

    @Test
    void joiningARoomStartsWithNothingUnread() {
        Long roomId = rooms.listRooms().getFirst().id();
        String early = "early-" + System.nanoTime();
        rooms.join(roomId, early);
        messages.send(roomId, early, "before you joined", null);

        String late = "late-" + System.nanoTime();
        rooms.join(roomId, late);
        assertThat(unread(late, roomId)).isZero();

        messages.send(roomId, early, "after you joined", null);
        assertThat(unread(late, roomId)).isEqualTo(1);
    }

    @Test
    void nonMemberCannotMarkRead() {
        Long dmId = dms.getOrCreate("alice-" + System.nanoTime(), "bob-" + System.nanoTime()).id();
        String carol = "carol-" + System.nanoTime();
        rooms.join(rooms.listRooms().getFirst().id(), carol);

        assertThatThrownBy(() -> messages.markRead(dmId, carol, 1))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
