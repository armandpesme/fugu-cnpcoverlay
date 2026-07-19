package com.cnpcoverlay.cnpcoverlaymod.client.quest.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuestHistoryWriteQueueTest {
    @TempDir
    Path tempDir;

    @Test
    void enqueueReturnsBeforeBackendAndCoalescesTheLatestSubjectSnapshot() {
        ManualExecutor executor = new ManualExecutor();
        List<Map<QuestHistoryWriteQueue.Subject, QuestHistoryStore.PlayerHistory>> batches =
                new ArrayList<>();
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(
                executor,
                (path, batch) -> batches.add(batch)
        );
        Path file = tempDir.resolve("history.json");

        queue.enqueue(file, "ctx", "player", history(1L));
        queue.enqueue(file, "ctx", "player", history(2L));
        queue.enqueue(file, "ctx", "player", history(3L));

        assertTrue(batches.isEmpty());
        assertEquals(1, executor.size());

        executor.runNext();

        assertEquals(1, batches.size());
        assertEquals(
                history(3L),
                batches.get(0).get(new QuestHistoryWriteQueue.Subject("ctx", "player"))
        );
    }

    @Test
    void enqueueDuringDrainIsWrittenByTheFollowingSerializedBatch() {
        ManualExecutor executor = new ManualExecutor();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        List<Long> writtenSequences = new ArrayList<>();
        QuestHistoryWriteQueue[] queueReference = new QuestHistoryWriteQueue[1];
        Path file = tempDir.resolve("history.json");
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(executor, (path, batch) -> {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            long sequence = batch.values().iterator().next().nextSequence();
            writtenSequences.add(sequence);
            if (sequence == 1L) {
                queueReference[0].enqueue(file, "ctx", "player", history(2L));
            }
            active.decrementAndGet();
        });
        queueReference[0] = queue;

        queue.enqueue(file, "ctx", "player", history(1L));
        var barrier = queue.flush();
        executor.runNext();

        assertEquals(List.of(1L, 2L), writtenSequences);
        assertEquals(1, maximumActive.get());
        assertTrue(barrier.isDone());
        assertFalse(barrier.isCompletedExceptionally());
        assertEquals(0, executor.size());
    }

    @Test
    void barrierWaitsForTheWorkerAndReloadSeesTheLatestSnapshot() {
        ManualExecutor executor = new ManualExecutor();
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(
                executor,
                QuestHistoryStore::saveBatch
        );
        Path file = tempDir.resolve("history.json");

        queue.enqueue(file, "ctx", "player", history(4L));
        var barrier = queue.flush();

        assertFalse(barrier.isDone());
        executor.runNext();
        barrier.join();

        assertEquals(history(4L), QuestHistoryStore.load(file, "ctx", "player"));
    }

    @Test
    void failedDrainCompletesBarrierExceptionallyWithoutSpinningAndNextEnqueueRetries() {
        ManualExecutor executor = new ManualExecutor();
        AtomicBoolean fail = new AtomicBoolean(true);
        List<Long> writes = new ArrayList<>();
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(executor, (path, batch) -> {
            long sequence = batch.values().iterator().next().nextSequence();
            if (fail.getAndSet(false)) {
                throw new IllegalStateException("simulated backend failure");
            }
            writes.add(sequence);
        });
        Path file = tempDir.resolve("history.json");

        queue.enqueue(file, "ctx", "player", history(1L));
        var failedBarrier = queue.flush();
        executor.runNext();

        assertTrue(failedBarrier.isCompletedExceptionally());
        assertThrows(CompletionException.class, failedBarrier::join);
        assertEquals(0, executor.size());

        queue.enqueue(file, "ctx", "player", history(2L));
        var retryBarrier = queue.flush();
        executor.runNext();

        retryBarrier.join();
        assertEquals(List.of(2L), writes);
        assertEquals(0, executor.size());
    }

    @Test
    void laterUnrelatedFailureDoesNotPoisonAnEarlierBarrier() {
        ManualExecutor executor = new ManualExecutor();
        Path successfulFile = tempDir.resolve("successful.json");
        Path failingFile = tempDir.resolve("failing.json");
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(executor, (path, batch) -> {
            if (path.equals(failingFile)) {
                throw new IllegalStateException("later failure");
            }
        });

        queue.enqueue(successfulFile, "ctx", "player", history(1L));
        var earlierBarrier = queue.flush();
        queue.enqueue(failingFile, "ctx", "player", history(2L));
        executor.runNext();

        assertTrue(earlierBarrier.isDone());
        assertFalse(earlierBarrier.isCompletedExceptionally());
    }

    @Test
    void failedLatestSnapshotWinsOverStaleDiskOnReloadAndIsKeptByTheRetry() {
        ManualExecutor executor = new ManualExecutor();
        Path file = tempDir.resolve("stale-disk.json");
        QuestHistoryWriteQueue.Subject subject =
                new QuestHistoryWriteQueue.Subject("ctx", "player");
        AtomicReference<QuestHistoryStore.PlayerHistory> disk =
                new AtomicReference<>(historyWithEntries(1L, "h0"));
        AtomicBoolean failFirstWrite = new AtomicBoolean(true);
        AtomicInteger diskLoads = new AtomicInteger();
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(
                executor,
                (path, batch) -> {
                    if (failFirstWrite.getAndSet(false)) {
                        throw new IllegalStateException("simulated write failure");
                    }
                    disk.set(batch.get(subject));
                },
                (path, requestedSubject) -> {
                    diskLoads.incrementAndGet();
                    return disk.get();
                }
        );
        var h1 = historyWithEntries(2L, "h0", "h1");

        queue.enqueue(file, "ctx", "player", h1);
        executor.runNext();

        var reload = queue.loadAsync(file, subject);
        assertFalse(reload.isDone());
        executor.runNext();

        assertEquals(h1, reload.join());
        assertEquals(0, diskLoads.get());

        var h2 = historyWithEntries(3L, "h0", "h1", "h2");
        queue.enqueue(file, "ctx", "player", h2);
        executor.runNext();

        assertEquals(h2, disk.get());
        assertEquals(List.of("h0", "h1", "h2"), disk.get().entries().stream()
                .map(QuestHistoryEntry::questId)
                .toList());
    }

    @Test
    void loadRechecksAnEnqueueAcceptedWhileTheDiskBackendWasReading() {
        ManualExecutor executor = new ManualExecutor();
        Path file = tempDir.resolve("load-race.json");
        QuestHistoryWriteQueue.Subject subject =
                new QuestHistoryWriteQueue.Subject("ctx", "player");
        var h0 = historyWithEntries(1L, "h0");
        var h1 = historyWithEntries(2L, "h0", "h1");
        QuestHistoryWriteQueue[] queueReference = new QuestHistoryWriteQueue[1];
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(
                executor,
                (path, batch) -> {
                },
                (path, requestedSubject) -> {
                    queueReference[0].enqueue(file, "ctx", "player", h1);
                    return h0;
                }
        );
        queueReference[0] = queue;

        var reload = queue.loadAsync(file, subject);
        executor.runNext();

        assertEquals(h1, reload.join());
        assertEquals(1, executor.size());
        executor.runNext();
    }

    @Test
    void shutdownDrainsTheLastEnqueueAndIsIdempotent() {
        Path file = tempDir.resolve("shutdown-history.json");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        QuestHistoryWriteQueue queue = QuestHistoryWriteQueue.managed(
                executor,
                QuestHistoryStore::saveBatch,
                (path, subject) -> QuestHistoryStore.load(
                        path,
                        subject.contextKey(),
                        subject.playerKey()
                )
        );
        var latest = historyWithEntries(2L, "h0", "h1");

        queue.enqueue(file, "ctx", "player", latest);

        assertTrue(queue.shutdown(Duration.ofSeconds(2)));
        assertTrue(queue.shutdown(Duration.ofSeconds(2)));
        assertEquals(latest, QuestHistoryStore.load(file, "ctx", "player"));
        assertTrue(executor.isShutdown());
    }

    @Test
    void shutdownTimeoutIsBoundedWhenTheWorkerIsBlocked() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch backendStarted = new CountDownLatch(1);
        CountDownLatch releaseBackend = new CountDownLatch(1);
        QuestHistoryWriteQueue queue = QuestHistoryWriteQueue.managed(
                executor,
                (path, batch) -> {
                    backendStarted.countDown();
                    if (!releaseBackend.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test backend was not released");
                    }
                },
                (path, subject) -> QuestHistoryStore.PlayerHistory.empty()
        );
        queue.enqueue(tempDir.resolve("blocked.json"), "ctx", "player", history(1L));
        assertTrue(backendStarted.await(2, TimeUnit.SECONDS));

        long startedAt = System.nanoTime();
        boolean drained = queue.shutdown(Duration.ZERO);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        releaseBackend.countDown();

        assertFalse(drained);
        assertTrue(elapsedMillis < 500L, "shutdown took " + elapsedMillis + " ms");
        assertTrue(executor.isShutdown());
    }

    @Test
    void shutdownRetriesOnceWhenTheBackendFailsDuringItsFirstFlush()
            throws InterruptedException {
        Path file = tempDir.resolve("shutdown-retry.json");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch firstAttemptStarted = new CountDownLatch(1);
        CountDownLatch shutdownFlushRequested = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        var latest = historyWithEntries(2L, "h0", "h1");
        QuestHistoryWriteQueue queue = QuestHistoryWriteQueue.managed(
                executor,
                (path, batch) -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt == 1) {
                        firstAttemptStarted.countDown();
                        if (!shutdownFlushRequested.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("shutdown did not request its flush");
                        }
                        throw new IllegalStateException("first shutdown flush failure");
                    }
                    QuestHistoryStore.saveBatch(path, batch);
                },
                (path, subject) -> QuestHistoryStore.PlayerHistory.empty(),
                shutdownFlushRequested::countDown
        );
        queue.enqueue(file, "ctx", "player", latest);
        assertTrue(firstAttemptStarted.await(2, TimeUnit.SECONDS));

        assertTrue(queue.shutdown(Duration.ofSeconds(2)));

        assertEquals(2, attempts.get());
        assertEquals(latest, QuestHistoryStore.load(file, "ctx", "player"));
    }

    @Test
    void persistentShutdownFailureGetsOnlyOneRetryAndReturnsFalseWithinBudget()
            throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch firstAttemptStarted = new CountDownLatch(1);
        CountDownLatch shutdownFlushRequested = new CountDownLatch(1);
        AtomicInteger attempts = new AtomicInteger();
        QuestHistoryWriteQueue queue = QuestHistoryWriteQueue.managed(
                executor,
                (path, batch) -> {
                    int attempt = attempts.incrementAndGet();
                    if (attempt == 1) {
                        firstAttemptStarted.countDown();
                        if (!shutdownFlushRequested.await(2, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("shutdown did not request its flush");
                        }
                    }
                    throw new IllegalStateException("persistent shutdown failure");
                },
                (path, subject) -> QuestHistoryStore.PlayerHistory.empty(),
                shutdownFlushRequested::countDown
        );
        queue.enqueue(tempDir.resolve("persistent-failure.json"), "ctx", "player", history(1L));
        assertTrue(firstAttemptStarted.await(2, TimeUnit.SECONDS));

        long startedAt = System.nanoTime();
        boolean drained = queue.shutdown(Duration.ofSeconds(2));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertFalse(drained);
        assertEquals(2, attempts.get());
        assertTrue(elapsedMillis < 2_500L, "shutdown took " + elapsedMillis + " ms");
    }

    private static QuestHistoryStore.PlayerHistory history(long nextSequence) {
        return new QuestHistoryStore.PlayerHistory(nextSequence, Map.of(), List.of());
    }

    private static QuestHistoryStore.PlayerHistory historyWithEntries(
            long nextSequence,
            String... questIds
    ) {
        List<QuestHistoryEntry> entries = new ArrayList<>();
        for (int index = 0; index < questIds.length; index++) {
            String questId = questIds[index];
            long sequence = index + 1L;
            entries.add(new QuestHistoryEntry(
                    "ctx:player:" + sequence,
                    questId,
                    "",
                    questId,
                    "",
                    List.of(),
                    sequence,
                    sequence,
                    sequence
            ));
        }
        return new QuestHistoryStore.PlayerHistory(nextSequence, Map.of(), entries);
    }

    static final class ManualExecutor implements Executor {
        private final Queue<Runnable> tasks = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            tasks.add(command);
        }

        int size() {
            return tasks.size();
        }

        void runNext() {
            tasks.remove().run();
        }
    }
}
