package com.cnpcoverlay.cnpcoverlaymod.client.quest.history;

import com.mojang.logging.LogUtils;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;

/**
 * File client mono-thread pour coalescer les instantanés complets avant leur
 * persistance. Le worker ne reçoit que des valeurs pures et n'accède jamais à
 * Minecraft.
 */
public final class QuestHistoryWriteQueue {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Duration PRODUCTION_SHUTDOWN_TIMEOUT = Duration.ofSeconds(2);

    private final Object monitor = new Object();
    private final Executor executor;
    private final ExecutorService managedExecutor;
    private final Backend backend;
    private final Loader loader;
    private final Runnable firstShutdownFlushRequested;
    private final Map<Path, Map<Subject, PendingWrite>> pendingByPath = new LinkedHashMap<>();
    private final Map<Path, Map<Subject, QuestHistoryStore.PlayerHistory>> latestAccepted =
            new LinkedHashMap<>();
    private final List<Waiter> waiters = new ArrayList<>();
    private boolean drainScheduled;
    private long generation;
    private Throwable idleFailure;
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final CompletableFuture<Boolean> shutdownCompletion = new CompletableFuture<>();

    QuestHistoryWriteQueue(Executor executor, Backend backend) {
        this(
                executor,
                backend,
                (path, subject) -> QuestHistoryStore.load(
                        path,
                        subject.contextKey(),
                        subject.playerKey()
                ),
                null,
                () -> {
                }
        );
    }

    QuestHistoryWriteQueue(Executor executor, Backend backend, Loader loader) {
        this(executor, backend, loader, null, () -> {
        });
    }

    private QuestHistoryWriteQueue(
            Executor executor,
            Backend backend,
            Loader loader,
            ExecutorService managedExecutor,
            Runnable firstShutdownFlushRequested
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.backend = Objects.requireNonNull(backend, "backend");
        this.loader = Objects.requireNonNull(loader, "loader");
        this.managedExecutor = managedExecutor;
        this.firstShutdownFlushRequested =
                Objects.requireNonNull(firstShutdownFlushRequested, "firstShutdownFlushRequested");
    }

    static QuestHistoryWriteQueue direct() {
        return new QuestHistoryWriteQueue(Runnable::run, QuestHistoryStore::saveBatch);
    }

    static QuestHistoryWriteQueue managed(
            ExecutorService executor,
            Backend backend,
            Loader loader
    ) {
        return managed(executor, backend, loader, () -> {
        });
    }

    static QuestHistoryWriteQueue managed(
            ExecutorService executor,
            Backend backend,
            Loader loader,
            Runnable firstShutdownFlushRequested
    ) {
        return new QuestHistoryWriteQueue(
                executor,
                backend,
                loader,
                executor,
                firstShutdownFlushRequested
        );
    }

    public static QuestHistoryWriteQueue get() {
        return Holder.INSTANCE;
    }

    public void enqueue(
            Path filePath,
            String contextKey,
            String playerKey,
            QuestHistoryStore.PlayerHistory history
    ) {
        Objects.requireNonNull(filePath, "filePath");
        Subject subject = new Subject(contextKey, playerKey);
        Objects.requireNonNull(history, "history");

        synchronized (monitor) {
            long writeGeneration = ++generation;
            Map<Subject, PendingWrite> pendingSubjects =
                    pendingByPath.computeIfAbsent(filePath, ignored -> new LinkedHashMap<>());
            PendingWrite previous = pendingSubjects.get(subject);
            long earliestGeneration = previous == null
                    ? writeGeneration
                    : previous.earliestGeneration();
            pendingSubjects.put(
                    subject,
                    new PendingWrite(earliestGeneration, writeGeneration, history)
            );
            latestAccepted.computeIfAbsent(filePath, ignored -> new LinkedHashMap<>())
                    .put(subject, history);
            idleFailure = null;
            if (!drainScheduled) {
                drainScheduled = true;
                scheduleDrain();
            }
        }
    }

    /**
     * Charge sur le même executor sérialisé que les écritures. Le snapshot le
     * plus récent accepté en mémoire gagne toujours sur le disque.
     */
    public CompletableFuture<QuestHistoryStore.PlayerHistory> loadAsync(
            Path filePath,
            Subject subject
    ) {
        Objects.requireNonNull(filePath, "filePath");
        Objects.requireNonNull(subject, "subject");
        CompletableFuture<QuestHistoryStore.PlayerHistory> future = new CompletableFuture<>();
        try {
            executor.execute(() -> {
                try {
                    QuestHistoryStore.PlayerHistory accepted = latest(filePath, subject);
                    if (accepted != null) {
                        future.complete(accepted);
                        return;
                    }
                    QuestHistoryStore.PlayerHistory loaded =
                            Objects.requireNonNullElse(loader.load(filePath, subject),
                                    QuestHistoryStore.PlayerHistory.empty());
                    QuestHistoryStore.PlayerHistory acceptedAfterLoad = latest(filePath, subject);
                    future.complete(acceptedAfterLoad == null ? loaded : acceptedAfterLoad);
                } catch (Exception exception) {
                    future.completeExceptionally(exception);
                }
            });
        } catch (RuntimeException exception) {
            future.completeExceptionally(exception);
        }
        return future;
    }

    /**
     * Barrière non bloquante couvrant tous les instantanés acceptés avant cet
     * appel. Elle échoue si leur backend échoue, sans relance automatique.
     */
    public CompletableFuture<Void> flush() {
        synchronized (monitor) {
            if (!drainScheduled) {
                if (idleFailure != null) {
                    return CompletableFuture.failedFuture(idleFailure);
                }
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> future = new CompletableFuture<>();
            waiters.add(new Waiter(generation, future));
            return future;
        }
    }

    /** Attend une barrière uniquement aux transitions de contexte/rechargements. */
    void awaitPendingWrites() {
        flush().join();
    }

    /**
     * Draine les écritures acceptées puis arrête le worker géré, avec un budget
     * total borné. Les appels suivants observent le même résultat.
     */
    boolean shutdown(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must not be negative");
        }
        if (!shutdownStarted.compareAndSet(false, true)) {
            return await(shutdownCompletion, timeout.toNanos());
        }

        boolean result = false;
        long timeoutNanos = timeout.toNanos();
        long startedAt = System.nanoTime();
        try {
            CompletableFuture<Void> flushFuture = requestFlushForShutdown();
            firstShutdownFlushRequested.run();
            boolean flushed = await(flushFuture, timeoutNanos);
            if (!flushed) {
                long retryBudgetNanos = remainingNanos(startedAt, timeoutNanos);
                if (retryBudgetNanos > 0L) {
                    flushed = await(requestFlushForShutdown(), retryBudgetNanos);
                }
            }
            if (managedExecutor == null) {
                result = flushed;
                return result;
            }

            managedExecutor.shutdown();
            long remainingNanos = remainingNanos(startedAt, timeoutNanos);
            boolean terminated = awaitTermination(managedExecutor, remainingNanos);
            if (!terminated) {
                managedExecutor.shutdownNow();
                terminated = managedExecutor.isTerminated()
                        || awaitTermination(
                                managedExecutor,
                                remainingNanos(startedAt, timeoutNanos)
                        );
            }
            result = flushed && terminated;
            return result;
        } finally {
            shutdownCompletion.complete(result);
        }
    }

    private CompletableFuture<Void> requestFlushForShutdown() {
        synchronized (monitor) {
            if (!pendingByPath.isEmpty() && !drainScheduled) {
                idleFailure = null;
                drainScheduled = true;
                scheduleDrain();
            }
        }
        return flush();
    }

    private static boolean await(CompletableFuture<?> future, long timeoutNanos) {
        if (future.isDone()) {
            try {
                future.join();
                return true;
            } catch (RuntimeException exception) {
                return false;
            }
        }
        if (timeoutNanos <= 0L) {
            return false;
        }
        try {
            future.get(timeoutNanos, TimeUnit.NANOSECONDS);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException exception) {
            return false;
        }
    }

    private static boolean awaitTermination(ExecutorService executor, long timeoutNanos) {
        if (executor.isTerminated()) {
            return true;
        }
        if (timeoutNanos <= 0L) {
            return false;
        }
        try {
            return executor.awaitTermination(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static long remainingNanos(long startedAt, long timeoutNanos) {
        return Math.max(0L, timeoutNanos - (System.nanoTime() - startedAt));
    }

    private void scheduleDrain() {
        try {
            executor.execute(this::drain);
        } catch (RuntimeException exception) {
            drainScheduled = false;
            idleFailure = exception;
            completeWaiters(generation, 0L, exception);
            LOGGER.warn(
                    "Impossible de planifier la sauvegarde de l'historique des quêtes : {}",
                    exception.getMessage()
            );
        }
    }

    private void drain() {
        while (true) {
            DrainBatch batch;
            synchronized (monitor) {
                if (pendingByPath.isEmpty()) {
                    drainScheduled = false;
                    return;
                }
                batch = takePending();
            }

            Map<Path, Map<Subject, PendingWrite>> failed = new LinkedHashMap<>();
            Throwable firstFailure = null;
            long earliestFailedGeneration = Long.MAX_VALUE;
            for (Map.Entry<Path, Map<Subject, PendingWrite>> pathBatch : batch.byPath().entrySet()) {
                try {
                    backend.saveBatch(pathBatch.getKey(), histories(pathBatch.getValue()));
                } catch (Exception failure) {
                    if (firstFailure == null) {
                        firstFailure = failure;
                    }
                    failed.put(pathBatch.getKey(), pathBatch.getValue());
                    for (PendingWrite failedWrite : pathBatch.getValue().values()) {
                        earliestFailedGeneration = Math.min(
                                earliestFailedGeneration,
                                failedWrite.earliestGeneration()
                        );
                    }
                    LOGGER.warn(
                            "Impossible d'écrire l'historique des quêtes : {}",
                            failure.getMessage()
                    );
                }
            }

            synchronized (monitor) {
                if (firstFailure == null) {
                    idleFailure = null;
                    completeWaiters(batch.maximumGeneration(), Long.MAX_VALUE, null);
                    continue;
                }

                restoreFailed(failed);
                completeWaiters(
                        batch.maximumGeneration(),
                        earliestFailedGeneration,
                        firstFailure
                );
                if (hasGenerationAfter(batch.maximumGeneration())) {
                    continue;
                }
                drainScheduled = false;
                idleFailure = firstFailure;
                return;
            }
        }
    }

    private DrainBatch takePending() {
        Map<Path, Map<Subject, PendingWrite>> batch = new LinkedHashMap<>();
        long maximumGeneration = 0L;
        for (Map.Entry<Path, Map<Subject, PendingWrite>> pathEntry : pendingByPath.entrySet()) {
            Map<Subject, PendingWrite> subjectBatch = new LinkedHashMap<>(pathEntry.getValue());
            batch.put(pathEntry.getKey(), subjectBatch);
            for (PendingWrite pending : subjectBatch.values()) {
                maximumGeneration = Math.max(maximumGeneration, pending.latestGeneration());
            }
        }
        pendingByPath.clear();
        return new DrainBatch(batch, maximumGeneration);
    }

    private void restoreFailed(Map<Path, Map<Subject, PendingWrite>> failed) {
        for (Map.Entry<Path, Map<Subject, PendingWrite>> pathEntry : failed.entrySet()) {
            Map<Subject, PendingWrite> pendingSubjects =
                    pendingByPath.computeIfAbsent(pathEntry.getKey(), ignored -> new LinkedHashMap<>());
            for (Map.Entry<Subject, PendingWrite> subjectEntry : pathEntry.getValue().entrySet()) {
                pendingSubjects.putIfAbsent(subjectEntry.getKey(), subjectEntry.getValue());
            }
        }
    }

    private QuestHistoryStore.PlayerHistory latest(Path filePath, Subject subject) {
        synchronized (monitor) {
            Map<Subject, QuestHistoryStore.PlayerHistory> subjects = latestAccepted.get(filePath);
            return subjects == null ? null : subjects.get(subject);
        }
    }

    private boolean hasGenerationAfter(long completedGeneration) {
        return pendingByPath.values().stream()
                .flatMap(subjects -> subjects.values().stream())
                .anyMatch(pending -> pending.latestGeneration() > completedGeneration);
    }

    private void completeWaiters(
            long maximumGeneration,
            long earliestFailedGeneration,
            Throwable failure
    ) {
        var iterator = waiters.iterator();
        while (iterator.hasNext()) {
            Waiter waiter = iterator.next();
            if (waiter.generation() > maximumGeneration) {
                continue;
            }
            if (failure == null || waiter.generation() < earliestFailedGeneration) {
                waiter.future().complete(null);
            } else {
                waiter.future().completeExceptionally(failure);
            }
            iterator.remove();
        }
    }

    private static Map<Subject, QuestHistoryStore.PlayerHistory> histories(
            Map<Subject, PendingWrite> pending
    ) {
        Map<Subject, QuestHistoryStore.PlayerHistory> histories = new LinkedHashMap<>();
        pending.forEach((subject, write) -> histories.put(subject, write.history()));
        return Map.copyOf(histories);
    }

    public record Subject(String contextKey, String playerKey) {
        public Subject {
            Objects.requireNonNull(contextKey, "contextKey");
            Objects.requireNonNull(playerKey, "playerKey");
        }
    }

    @FunctionalInterface
    interface Backend {
        void saveBatch(
                Path filePath,
                Map<Subject, QuestHistoryStore.PlayerHistory> histories
        ) throws Exception;
    }

    @FunctionalInterface
    interface Loader {
        QuestHistoryStore.PlayerHistory load(Path filePath, Subject subject) throws Exception;
    }

    private record PendingWrite(
            long earliestGeneration,
            long latestGeneration,
            QuestHistoryStore.PlayerHistory history
    ) {
    }

    private record DrainBatch(
            Map<Path, Map<Subject, PendingWrite>> byPath,
            long maximumGeneration
    ) {
    }

    private record Waiter(long generation, CompletableFuture<Void> future) {
    }

    private static final class Holder {
        private static final ExecutorService EXECUTOR =
                Executors.newSingleThreadExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "cnpcoverlay-quest-history-writer");
                    thread.setDaemon(true);
                    return thread;
                });
        private static final QuestHistoryWriteQueue INSTANCE = managed(
                EXECUTOR,
                QuestHistoryStore::saveBatch,
                (path, subject) -> QuestHistoryStore.load(
                        path,
                        subject.contextKey(),
                        subject.playerKey()
                )
        );

        static {
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> INSTANCE.shutdown(PRODUCTION_SHUTDOWN_TIMEOUT),
                    "cnpcoverlay-quest-history-shutdown"
            ));
        }
    }
}
