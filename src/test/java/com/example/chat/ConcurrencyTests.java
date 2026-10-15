package com.example.chat;

import com.example.chat.service.DirectMessageService;
import com.example.chat.service.DirectMessageService.DirectView;
import com.example.chat.service.MessageService;
import com.example.chat.service.MessageService.HistoryPage;
import com.example.chat.service.MessageService.MessageView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConcurrencyTests {

    private static final int THREADS = 32;

    @Autowired
    DirectMessageService dms;

    @Autowired
    MessageService messages;

    /** Runs all tasks at once (released together by a latch) and returns their results. */
    private static <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> f : futures) {
                results.add(f.get());
            }
            return results;
        }
    }

    private static String unique(String name) {
        return name + "-" + System.nanoTime();
    }

    @Test
    void concurrentSendsGetSequenceNumbersOneToNWithNoGaps() throws Exception {
        int n = 200;
        String alice = unique("alice");
        String bob = unique("bob");
        Long dmId = dms.getOrCreate(alice, bob).id();

        List<Callable<Long>> sends = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String sender = i % 2 == 0 ? alice : bob;
            String content = "msg " + i;
            sends.add(() -> messages.send(dmId, sender, content, null).message().seq());
        }
        List<Long> returnedSeqs = runConcurrently(sends);

        List<Long> expected = LongStream.rangeClosed(1, n).boxed().toList();
        assertThat(returnedSeqs).containsExactlyInAnyOrderElementsOf(expected);

        HistoryPage stored = messages.history(dmId, alice, 0, n);
        assertThat(stored.messages()).extracting(MessageView::seq).containsExactlyElementsOf(expected);
        assertThat(stored.hasMore()).isFalse();
    }

    @Test
    void concurrentDmCreationForTheSamePairYieldsOneConversation() throws Exception {
        String alice = unique("alice");
        String bob = unique("bob");

        List<Callable<DirectView>> creates = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            // Alternate argument order: both must map to the same conversation without deadlocking
            boolean flip = i % 2 == 0;
            creates.add(() -> dms.getOrCreate(flip ? bob : alice, flip ? alice : bob));
        }
        List<DirectView> results = runConcurrently(creates);

        assertThat(results).extracting(DirectView::id).containsOnly(results.getFirst().id());
        assertThat(results).filteredOn(DirectView::created).hasSize(1);
        assertThat(results).allSatisfy(dm -> assertThat(dm.members()).containsExactlyInAnyOrder(alice, bob));
    }
}
