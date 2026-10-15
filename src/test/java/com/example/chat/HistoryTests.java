package com.example.chat;

import com.example.chat.service.DirectMessageService;
import com.example.chat.service.MessageService;
import com.example.chat.service.MessageService.HistoryPage;
import com.example.chat.service.MessageService.MessageView;
import com.example.chat.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HistoryTests {

    @Autowired
    DirectMessageService dms;

    @Autowired
    MessageService messages;

    @Autowired
    UserService users;

    @Test
    void pagesForwardFromAfterSeq() {
        String alice = "alice-" + System.nanoTime();
        String bob = "bob-" + System.nanoTime();
        Long dmId = dms.getOrCreate(alice, bob).id();
        for (int i = 1; i <= 5; i++) {
            messages.send(dmId, i % 2 == 0 ? bob : alice, "m" + i);
        }

        HistoryPage first = messages.history(dmId, bob, 0, 2);
        assertThat(first.messages()).extracting(MessageView::seq).containsExactly(1L, 2L);
        assertThat(first.hasMore()).isTrue();

        HistoryPage second = messages.history(dmId, bob, 2, 2);
        assertThat(second.messages()).extracting(MessageView::content).containsExactly("m3", "m4");
        assertThat(second.hasMore()).isTrue();

        HistoryPage last = messages.history(dmId, bob, 4, 2);
        assertThat(last.messages()).extracting(MessageView::seq).containsExactly(5L);
        assertThat(last.hasMore()).isFalse();

        assertThat(messages.history(dmId, bob, 5, 2).messages()).isEmpty();
    }

    @Test
    void nonMembersCanNeitherReadNorSend() {
        Long dmId = dms.getOrCreate("alice-" + System.nanoTime(), "bob-" + System.nanoTime()).id();
        String carol = users.getOrCreate("carol-" + System.nanoTime()).getUsername();

        assertThatThrownBy(() -> messages.history(dmId, carol, 0, 10))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> messages.send(dmId, carol, "hi"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }
}
