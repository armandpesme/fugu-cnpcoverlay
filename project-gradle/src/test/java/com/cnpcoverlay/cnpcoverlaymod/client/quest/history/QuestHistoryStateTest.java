package com.cnpcoverlay.cnpcoverlaymod.client.quest.history;

import com.cnpcoverlay.cnpcoverlaymod.client.integration.QuestProvider.FinishedQuestStamps;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestObjectiveSnapshot;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestSnapshot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuestHistoryStateTest {
    @TempDir
    Path tempDir;

    @Test
    void observesFirstCompletionAndRepeatedQuestWithoutDuplicatingDisappearances() {
        QuestHistoryState state = state();

        state.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of()), true, 1_000L);
        state.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of("7", 10L)), true, 2_000L);

        assertEquals(List.of("7"), state.entries().stream().map(QuestHistoryEntry::questId).toList());
        QuestHistoryEntry first = state.entries().get(0);
        assertEquals("Quest 7", first.title());
        assertEquals("Visible", first.displayLogText());
        assertEquals(List.of("Objective: 1/1"), first.objectives());
        assertEquals(2_000L, first.observedCompletedAtEpochMillis());

        state.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of("7", 11L)), true, 3_000L);
        assertEquals(List.of(2L, 1L), state.entries().stream().map(QuestHistoryEntry::sequence).toList());

        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of("7", 11L)), true, 4_000L);
        assertEquals(2, state.entries().size());
    }

    @Test
    void skipsAnUnchangedIdentityUntilTheForcedFallback() {
        QuestHistoryState state = state();
        Object token = new Object();

        state.observe("ctx", "player", List.of(snapshot("7")), stamps(token, Map.of()), true, 1_000L);
        state.observe("ctx", "player", List.of(snapshot("7")), stamps(token, Map.of("7", 10L)), false, 2_000L);
        assertTrue(state.entries().isEmpty());

        state.observe("ctx", "player", List.of(snapshot("7")), stamps(token, Map.of("7", 10L)), true, 3_000L);
        assertEquals(1, state.entries().size());
    }

    @Test
    void reloadsThePersistedBaselineWithoutDuplicatingACompletion() {
        QuestHistoryState firstSession = state();
        firstSession.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of()), true, 1_000L);
        firstSession.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)),
                true,
                2_000L
        );

        QuestHistoryState secondSession = state();
        secondSession.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)),
                true,
                3_000L
        );

        assertEquals(1, secondSession.entries().size());
        assertEquals(1L, secondSession.entries().get(0).sequence());
    }

    @Test
    void transientEmptyMirrorKeepsTheBaselineAndDoesNotDuplicateAnIdenticalReturn() {
        QuestHistoryState state = state();

        state.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of("7", 10L)), true, 1_000L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 2_000L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 2_500L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of("7", 10L)), true, 3_000L);

        assertTrue(state.entries().isEmpty());
        assertEquals(
                Map.of("7", 10L),
                QuestHistoryStore.load(tempDir.resolve("quest_history.json"), "ctx", "player").lastFinishedStamps()
        );
    }

    @Test
    void threeForcedAbsencesPruneTheBaselineWithoutAnEventAndAnIdenticalReturnCreatesOne() {
        QuestHistoryState state = state();

        state.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of("7", 10L)), true, 1_000L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 2_000L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 3_000L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 4_000L);

        assertTrue(state.entries().isEmpty());
        assertTrue(QuestHistoryStore.load(
                tempDir.resolve("quest_history.json"),
                "ctx",
                "player"
        ).lastFinishedStamps().isEmpty());

        state.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of("7", 10L)), true, 5_000L);

        assertEquals(1, state.entries().size());
        assertEquals(10L, state.entries().get(0).sourceFinishedStamp());
    }

    @Test
    void confirmedRemovalSurvivesReloadAndAllowsTheIdenticalStampOnce() {
        Path historyFile = tempDir.resolve("quest_history.json");
        QuestHistoryState firstSession = new QuestHistoryState(historyFile);

        firstSession.observe(
                "ctx", "player", List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)), true, 1_000L
        );
        firstSession.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 2_000L);
        firstSession.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 3_000L);
        firstSession.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 4_000L);

        QuestHistoryState secondSession = new QuestHistoryState(historyFile);
        secondSession.observe(
                "ctx", "player", List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)), true, 5_000L
        );
        secondSession.observe(
                "ctx", "player", List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)), true, 6_000L
        );

        assertEquals(1, secondSession.entries().size());
        assertEquals(10L, secondSession.entries().get(0).sourceFinishedStamp());
    }

    @Test
    void unavailableAndNonForcedObservationsDoNotConfirmRemoval() {
        QuestHistoryState state = state();

        state.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of("7", 10L)), true, 1_000L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 2_000L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), false, 2_500L);
        state.observe("ctx", "player", List.of(), FinishedQuestStamps.unavailable(), true, 3_000L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 4_000L);
        state.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of("7", 10L)), true, 5_000L);

        assertTrue(state.entries().isEmpty());
        assertEquals(
                Map.of("7", 10L),
                QuestHistoryStore.load(tempDir.resolve("quest_history.json"), "ctx", "player").lastFinishedStamps()
        );
    }

    @Test
    void forcedObservationsCapturedDuringLoadCanConfirmRemoval() {
        Path historyFile = tempDir.resolve("loading-removal.json");
        QuestHistoryWriteQueueTest.ManualExecutor executor =
                new QuestHistoryWriteQueueTest.ManualExecutor();
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(
                executor,
                QuestHistoryStore::saveBatch,
                (path, subject) -> new QuestHistoryStore.PlayerHistory(
                        1L,
                        Map.of("7", 10L),
                        List.of()
                )
        );
        QuestHistoryState state = new QuestHistoryState(historyFile, queue);

        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 1_000L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 2_000L);
        state.observe("ctx", "player", List.of(), stamps(new Object(), Map.of()), true, 3_000L);
        executor.runNext();
        state.observe("ctx", "player", List.of(), FinishedQuestStamps.unavailable(), true, 4_000L);

        state.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of("7", 10L)), true, 5_000L);

        assertEquals(1, state.entries().size());
    }

    @Test
    void unavailableMirrorDoesNotInitializeOrPersistTheBaseline() {
        Path historyFile = tempDir.resolve("quest_history.json");
        QuestHistoryState state = new QuestHistoryState(historyFile);

        state.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                FinishedQuestStamps.unavailable(),
                true,
                1_000L
        );

        assertTrue(Files.notExists(historyFile));

        state.observe("ctx", "player", List.of(snapshot("7")), stamps(new Object(), Map.of("7", 10L)), true, 2_000L);

        assertTrue(state.entries().isEmpty());
        assertEquals(
                Map.of("7", 10L),
                QuestHistoryStore.load(historyFile, "ctx", "player").lastFinishedStamps()
        );
    }

    @Test
    void readableEmptyBaselineSurvivesReloadAndRecordsTheFirstCompletion() {
        Path historyFile = tempDir.resolve("quest_history.json");
        QuestHistoryState firstSession = new QuestHistoryState(historyFile);

        firstSession.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of()),
                true,
                1_000L
        );

        assertEquals(1L, QuestHistoryStore.load(historyFile, "ctx", "player").nextSequence());

        QuestHistoryState secondSession = new QuestHistoryState(historyFile);
        secondSession.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)),
                true,
                2_000L
        );

        assertEquals(1, secondSession.entries().size());
        assertEquals(1L, secondSession.entries().get(0).sequence());
    }

    @Test
    void observeOnlyEnqueuesPersistenceUntilTheInjectedWorkerRuns() {
        Path historyFile = tempDir.resolve("async-history.json");
        QuestHistoryWriteQueueTest.ManualExecutor executor =
                new QuestHistoryWriteQueueTest.ManualExecutor();
        AtomicInteger backendCalls = new AtomicInteger();
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(executor, (path, batch) -> {
            backendCalls.incrementAndGet();
            QuestHistoryStore.saveBatch(path, batch);
        });
        QuestHistoryState state = new QuestHistoryState(historyFile, queue);

        state.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of()),
                true,
                1_000L
        );

        assertEquals(0, backendCalls.get());
        assertTrue(Files.notExists(historyFile));

        executor.runNext();
        state.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of()),
                true,
                2_000L
        );

        assertEquals(0, backendCalls.get());
        assertTrue(Files.notExists(historyFile));

        executor.runNext();

        assertEquals(1, backendCalls.get());
        assertEquals(1L, QuestHistoryStore.load(historyFile, "ctx", "player").nextSequence());
    }

    @Test
    void clearThenReloadKeepsTheDurableBaselineAndEntries() {
        Path historyFile = tempDir.resolve("reload-history.json");
        QuestHistoryWriteQueueTest.ManualExecutor executor =
                new QuestHistoryWriteQueueTest.ManualExecutor();
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(
                executor,
                QuestHistoryStore::saveBatch
        );
        QuestHistoryState firstSession = new QuestHistoryState(historyFile, queue);
        firstSession.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of()),
                true,
                1_000L
        );
        firstSession.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)),
                true,
                2_000L
        );

        executor.runNext();
        firstSession.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)),
                true,
                2_500L
        );
        firstSession.clearForDisconnect();
        executor.runNext();

        QuestHistoryState secondSession = new QuestHistoryState(historyFile, queue);
        secondSession.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)),
                true,
                3_000L
        );
        executor.runNext();
        secondSession.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)),
                true,
                3_500L
        );

        assertEquals(1, secondSession.entries().size());
        assertEquals(1L, secondSession.entries().get(0).sequence());
    }

    @Test
    void asynchronousLoadCapturesACompletionThatArrivesBeforeDiskReadFinishes() {
        Path historyFile = tempDir.resolve("loading-history.json");
        QuestHistoryWriteQueueTest.ManualExecutor executor =
                new QuestHistoryWriteQueueTest.ManualExecutor();
        AtomicInteger loadCalls = new AtomicInteger();
        AtomicInteger saveCalls = new AtomicInteger();
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(
                executor,
                (path, batch) -> saveCalls.incrementAndGet(),
                (path, subject) -> {
                    loadCalls.incrementAndGet();
                    return QuestHistoryStore.PlayerHistory.empty();
                }
        );
        QuestHistoryState state = new QuestHistoryState(historyFile, queue);

        state.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of()),
                true,
                1_000L
        );
        state.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)),
                true,
                2_000L
        );

        assertEquals(0, loadCalls.get());
        assertEquals(0, saveCalls.get());
        assertTrue(state.entries().isEmpty());

        executor.runNext();
        state.observe(
                "ctx",
                "player",
                List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)),
                true,
                3_000L
        );

        assertEquals(1, loadCalls.get());
        assertEquals(0, saveCalls.get());
        assertEquals(1, state.entries().size());
        assertEquals(10L, state.entries().get(0).sourceFinishedStamp());
    }

    @Test
    void failedPendingHistoryWinsOnReconnectAndTheNextCompletionRetriesItCumulatively() {
        Path historyFile = tempDir.resolve("failed-reconnect.json");
        QuestHistoryWriteQueueTest.ManualExecutor executor =
                new QuestHistoryWriteQueueTest.ManualExecutor();
        AtomicReference<QuestHistoryStore.PlayerHistory> disk = new AtomicReference<>(
                new QuestHistoryStore.PlayerHistory(1L, Map.of(), List.of())
        );
        AtomicBoolean failFirstWrite = new AtomicBoolean(true);
        QuestHistoryWriteQueue.Subject subject =
                new QuestHistoryWriteQueue.Subject("ctx", "player");
        QuestHistoryWriteQueue queue = new QuestHistoryWriteQueue(
                executor,
                (path, batch) -> {
                    if (failFirstWrite.getAndSet(false)) {
                        throw new IllegalStateException("simulated write failure");
                    }
                    disk.set(batch.get(subject));
                },
                (path, requestedSubject) -> disk.get()
        );
        QuestHistoryState firstSession = new QuestHistoryState(historyFile, queue);

        firstSession.observe(
                "ctx", "player", List.of(snapshot("7")),
                stamps(new Object(), Map.of()), true, 1_000L
        );
        executor.runNext();
        firstSession.observe(
                "ctx", "player", List.of(snapshot("7")),
                stamps(new Object(), Map.of()), true, 1_500L
        );
        firstSession.observe(
                "ctx", "player", List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)), true, 2_000L
        );
        executor.runNext();
        firstSession.clearForDisconnect();

        QuestHistoryState secondSession = new QuestHistoryState(historyFile, queue);
        secondSession.observe(
                "ctx", "player", List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)), true, 3_000L
        );
        executor.runNext();
        secondSession.observe(
                "ctx", "player", List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 10L)), true, 3_500L
        );

        assertEquals(1, secondSession.entries().size());
        assertEquals(10L, secondSession.entries().get(0).sourceFinishedStamp());

        secondSession.observe(
                "ctx", "player", List.of(snapshot("7")),
                stamps(new Object(), Map.of("7", 11L)), true, 4_000L
        );
        executor.runNext();

        assertEquals(List.of(11L, 10L), disk.get().entries().stream()
                .map(QuestHistoryEntry::sourceFinishedStamp)
                .toList());
    }

    private QuestHistoryState state() {
        return new QuestHistoryState(tempDir.resolve("quest_history.json"));
    }

    private static FinishedQuestStamps stamps(Object token, Map<String, Long> values) {
        return new FinishedQuestStamps(token, values);
    }

    private static QuestSnapshot snapshot(String id) {
        return new QuestSnapshot(
                id,
                "Category",
                "Quest " + id,
                "Visible\n#!1-1,2,3,4",
                List.of(new QuestObjectiveSnapshot(1, "Objective", 1, 1, true)),
                1.0f,
                true,
                "Npc"
        );
    }
}
