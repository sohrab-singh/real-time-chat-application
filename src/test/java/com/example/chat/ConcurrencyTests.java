package com.example.chat;

import com.example.chat.service.DirectMessageService;
import com.example.chat.service.DirectMessageService.DirectView;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ConcurrencyTests {

    private static final int THREADS = 32;

    @Autowired
    DirectMessageService dms;

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
