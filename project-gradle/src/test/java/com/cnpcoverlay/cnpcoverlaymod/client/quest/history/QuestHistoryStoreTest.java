package com.cnpcoverlay.cnpcoverlaymod.client.quest.history;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuestHistoryStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripPreservesSequenceStampsAndRepeatedEntries() {
        Path file = tempDir.resolve("quest_history.json");
        var first = entry("ctx:p:1", "7", 1L, 10L);
        var second = entry("ctx:p:2", "7", 2L, 11L);
        var history = new QuestHistoryStore.PlayerHistory(
                3L,
                Map.of("7", 11L),
                List.of(first, second)
        );

        QuestHistoryStore.save(file, "ctx", "p", history);

        assertEquals(history, QuestHistoryStore.load(file, "ctx", "p"));
    }

    @Test
    void differentContextsAreIsolated() {
        Path file = tempDir.resolve("quest_history.json");
        var first = new QuestHistoryStore.PlayerHistory(
                2L,
                Map.of("7", 10L),
                List.of(entry("ctx-a:p:1", "7", 1L, 10L))
        );
        var second = new QuestHistoryStore.PlayerHistory(
                4L,
                Map.of("8", 20L),
                List.of(entry("ctx-b:p:3", "8", 3L, 20L))
        );

        QuestHistoryStore.save(file, "ctx-a", "p", first);
        QuestHistoryStore.save(file, "ctx-b", "p", second);

        assertEquals(first, QuestHistoryStore.load(file, "ctx-a", "p"));
        assertEquals(second, QuestHistoryStore.load(file, "ctx-b", "p"));
        assertEquals(QuestHistoryStore.PlayerHistory.empty(), QuestHistoryStore.load(file, "ctx-c", "p"));
    }

    @Test
    void differentPlayersAreIsolated() {
        Path file = tempDir.resolve("quest_history.json");
        var first = new QuestHistoryStore.PlayerHistory(2L, Map.of("7", 10L), List.of());
        var second = new QuestHistoryStore.PlayerHistory(4L, Map.of("8", 20L), List.of());

        QuestHistoryStore.save(file, "ctx", "p-a", first);
        QuestHistoryStore.save(file, "ctx", "p-b", second);

        assertEquals(first, QuestHistoryStore.load(file, "ctx", "p-a"));
        assertEquals(second, QuestHistoryStore.load(file, "ctx", "p-b"));
        assertEquals(QuestHistoryStore.PlayerHistory.empty(), QuestHistoryStore.load(file, "ctx", "p-c"));
    }

    @Test
    void batchPreservesExistingRootAndWritesSeveralSubjectsAtOnce() throws IOException {
        Path file = tempDir.resolve("quest_history.json");
        var existing = new QuestHistoryStore.PlayerHistory(1L, Map.of("old", 1L), List.of());
        var first = new QuestHistoryStore.PlayerHistory(2L, Map.of("7", 10L), List.of());
        var second = new QuestHistoryStore.PlayerHistory(3L, Map.of("8", 20L), List.of());
        QuestHistoryStore.save(file, "existing", "player", existing);

        QuestHistoryStore.saveBatch(file, Map.of(
                new QuestHistoryWriteQueue.Subject("ctx-a", "player-a"), first,
                new QuestHistoryWriteQueue.Subject("ctx-b", "player-b"), second
        ));

        assertEquals(existing, QuestHistoryStore.load(file, "existing", "player"));
        assertEquals(first, QuestHistoryStore.load(file, "ctx-a", "player-a"));
        assertEquals(second, QuestHistoryStore.load(file, "ctx-b", "player-b"));
    }

    @Test
    void missingContextReturnsEmptyHistory() {
        assertEquals(
                QuestHistoryStore.PlayerHistory.empty(),
                QuestHistoryStore.load(tempDir.resolve("missing.json"), "ctx", "p")
        );
    }

    @Test
    void corruptFileIsBackedUpAndReturnsEmptyHistory() throws IOException {
        Path file = tempDir.resolve("quest_history.json");
        Files.writeString(file, "{broken");

        assertEquals(QuestHistoryStore.PlayerHistory.empty(), QuestHistoryStore.load(file, "ctx", "p"));

        try (var files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(path ->
                    path.getFileName().toString().startsWith("quest_history.json.broken-")));
        }
    }

    @Test
    void readIOExceptionDoesNotBackupTheUnreadablePath() throws IOException {
        Path file = tempDir.resolve("quest_history.json");
        Files.createDirectory(file);

        assertEquals(QuestHistoryStore.PlayerHistory.empty(), QuestHistoryStore.load(file, "ctx", "p"));

        assertTrue(Files.isDirectory(file));
        assertFalse(hasBrokenBackup());
    }

    @Test
    void saveAbortsWhenExistingDataCannotBeRead() throws IOException {
        Path file = tempDir.resolve("quest_history.json");
        Files.createDirectory(file);

        QuestHistoryStore.save(
                file,
                "ctx",
                "p",
                new QuestHistoryStore.PlayerHistory(2L, Map.of("7", 10L), List.of())
        );

        assertTrue(Files.isDirectory(file));
        assertFalse(hasBrokenBackup());
    }

    @Test
    void writeIOExceptionCleansTemporaryPathAndPreservesOriginalData() throws IOException {
        Path file = tempDir.resolve("quest_history.json");
        var original = new QuestHistoryStore.PlayerHistory(2L, Map.of("7", 10L), List.of());
        var replacement = new QuestHistoryStore.PlayerHistory(3L, Map.of("8", 20L), List.of());
        QuestHistoryStore.save(file, "ctx", "p", original);
        Path temporaryPath = file.resolveSibling(file.getFileName() + ".tmp");
        Files.createDirectory(temporaryPath);

        QuestHistoryStore.save(file, "ctx", "p", replacement);

        assertFalse(Files.exists(temporaryPath));
        assertEquals(original, QuestHistoryStore.load(file, "ctx", "p"));
    }

    @Test
    void runtimeFailureDuringJsonWriteIsContainedAndCleansTemporaryPath() throws IOException {
        Path file = tempDir.resolve("quest_history.json");
        var original = new QuestHistoryStore.PlayerHistory(2L, Map.of("7", 10L), List.of());
        var replacement = new QuestHistoryStore.PlayerHistory(3L, Map.of("8", 20L), List.of());
        QuestHistoryStore.save(file, "ctx", "p", original);
        Path temporaryPath = file.resolveSibling(file.getFileName() + ".tmp");
        QuestHistoryStore.WriterFactory failingWriterFactory = path -> {
            Writer delegate = Files.newBufferedWriter(path);
            return new Writer() {
                @Override
                public void write(char[] buffer, int offset, int length) {
                    throw new IllegalStateException("simulated Gson write failure");
                }

                @Override
                public void flush() throws IOException {
                    delegate.flush();
                }

                @Override
                public void close() throws IOException {
                    delegate.close();
                }
            };
        };

        assertDoesNotThrow(() ->
                QuestHistoryStore.save(file, "ctx", "p", replacement, failingWriterFactory));

        assertFalse(Files.exists(temporaryPath));
        assertEquals(original, QuestHistoryStore.load(file, "ctx", "p"));
    }

    @Test
    void failedCorruptFileBackupAbortsSaveAndPreservesOriginal() throws IOException {
        Path file = tempDir.resolve("quest_history.json");
        String corruptJson = "{broken";
        Files.writeString(file, corruptJson);
        Path temporaryPath = file.resolveSibling(file.getFileName() + ".tmp");
        QuestHistoryStore.BackupStrategy failingBackup = (source, target) -> {
            throw new IOException("simulated backup failure");
        };
        var replacement = new QuestHistoryStore.PlayerHistory(2L, Map.of("7", 10L), List.of());

        assertDoesNotThrow(() -> QuestHistoryStore.save(
                file,
                "ctx",
                "p",
                replacement,
                Files::newBufferedWriter,
                failingBackup
        ));

        assertEquals(corruptJson, Files.readString(file));
        assertFalse(Files.exists(temporaryPath));
        assertFalse(hasBrokenBackup());
    }

    @Test
    void playerHistoryDefensivelyCopiesCollections() {
        Map<String, Long> stamps = new HashMap<>(Map.of("7", 10L));
        List<QuestHistoryEntry> entries = new ArrayList<>(List.of(entry("ctx:p:1", "7", 1L, 10L)));

        var history = new QuestHistoryStore.PlayerHistory(2L, stamps, entries);
        stamps.put("8", 20L);
        entries.clear();

        assertEquals(Map.of("7", 10L), history.lastFinishedStamps());
        assertEquals(1, history.entries().size());
        assertThrows(UnsupportedOperationException.class, () -> history.lastFinishedStamps().put("8", 20L));
        assertThrows(UnsupportedOperationException.class, () -> history.entries().clear());
    }

    private boolean hasBrokenBackup() throws IOException {
        try (var files = Files.list(tempDir)) {
            return files.anyMatch(path ->
                    path.getFileName().toString().startsWith("quest_history.json.broken-"));
        }
    }

    private static QuestHistoryEntry entry(
            String occurrenceId,
            String questId,
            long sequence,
            long sourceFinishedStamp
    ) {
        return new QuestHistoryEntry(
                occurrenceId,
                questId,
                "category",
                "Quest " + questId,
                "Log",
                List.of("Objective"),
                1_000L + sequence,
                sourceFinishedStamp,
                sequence
        );
    }
}
