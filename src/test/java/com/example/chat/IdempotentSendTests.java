package com.example.chat;

import com.example.chat.service.DirectMessageService;
import com.example.chat.service.MessageService;
import com.example.chat.service.MessageService.MessageView;
import com.example.chat.service.MessageService.SendResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotentSendTests {

    @Autowired
    DirectMessageService dms;

    @Autowired
    MessageService messages;

    private Long newDm(String a, String b) {
        return dms.getOrCreate(a, b).id();
    }

    @Test
    void retryWithSameClientMessageIdReturnsTheOriginal() {
        String alice = "alice-" + System.nanoTime();
        Long dmId = newDm(alice, "bob-" + System.nanoTime());

        SendResult first = messages.send(dmId, alice, "hello", "c-1");
        SendResult retry = messages.send(dmId, alice, "hello", "c-1");

        assertThat(first.created()).isTrue();
        assertThat(retry.created()).isFalse();
        assertThat(retry.message().id()).isEqualTo(first.message().id());
        assertThat(retry.message().seq()).isEqualTo(1);
        // The retry didn't consume a sequence number
        assertThat(messages.send(dmId, alice, "next", "c-2").message().seq()).isEqualTo(2);
        assertThat(messages.history(dmId, alice, 0, 10).messages()).hasSize(2);
    }

    @Test
    void reusingClientMessageIdForDifferentContentIsAConflict() {
        String alice = "alice-" + System.nanoTime();
        Long dmId = newDm(alice, "bob-" + System.nanoTime());
        messages.send(dmId, alice, "hello", "c-1");

        assertThatThrownBy(() -> messages.send(dmId, alice, "something else", "c-1"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void sameClientMessageIdFromDifferentSendersAreDifferentMessages() {
        String alice = "alice-" + System.nanoTime();
        String bob = "bob-" + System.nanoTime();
        Long dmId = newDm(alice, bob);

        assertThat(messages.send(dmId, alice, "hi", "c-1").created()).isTrue();
        assertThat(messages.send(dmId, bob, "hi", "c-1").created()).isTrue();
    }

    @Test
    void concurrentRetriesStoreTheMessageOnce() throws Exception {
        String alice = "alice-" + System.nanoTime();
        Long dmId = newDm(alice, "bob-" + System.nanoTime());

        CountDownLatch start = new CountDownLatch(1);
        List<Future<SendResult>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(16)) {
            for (int i = 0; i < 32; i++) {
                Callable<SendResult> retry = () -> {
                    start.await();
                    return messages.send(dmId, alice, "only once", "same-id");
                };
                futures.add(pool.submit(retry));
            }
            start.countDown();
            List<SendResult> results = new ArrayList<>();
            for (Future<SendResult> f : futures) {
                results.add(f.get());
            }
            assertThat(results).filteredOn(SendResult::created).hasSize(1);
            assertThat(results).extracting(r -> r.message().id()).containsOnly(results.getFirst().message().id());
        }
        assertThat(messages.history(dmId, alice, 0, 100).messages()).extracting(MessageView::seq).containsExactly(1L);
    }
}
