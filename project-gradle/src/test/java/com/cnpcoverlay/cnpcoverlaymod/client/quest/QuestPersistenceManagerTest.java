package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuestPersistenceManagerTest {

    @TempDir
    Path tempDir;

    private final QuestPersistenceManager manager = QuestPersistenceManager.get();

    @Test
    void saveAndLoadRoundTrip() throws Exception {
        Path file = tempDir.resolve("tracked_quests.json");
        String serverKey = "myserver.example.com";
        String playerKey = "player-uuid-123";

        manager.saveToPath(file, serverKey, playerKey, Set.of("1", "2", "3"), "1");

        Set<String> loaded = manager.loadFollowedQuestIds(file, serverKey, playerKey);
        assertEquals(Set.of("1", "2", "3"), loaded);
        assertEquals("1", manager.loadActiveQuestId(file, serverKey, playerKey));
    }

    @Test
    void differentServersAreIsolated() throws Exception {
        Path file = tempDir.resolve("tracked_quests.json");
        String playerKey = "player-uuid-123";

        manager.saveToPath(file, "server-a", playerKey, Set.of("1"), "1");
        manager.saveToPath(file, "server-b", playerKey, Set.of("2"), "2");

        assertEquals(Set.of("1"), manager.loadFollowedQuestIds(file, "server-a", playerKey));
        assertEquals("1", manager.loadActiveQuestId(file, "server-a", playerKey));
        assertEquals(Set.of("2"), manager.loadFollowedQuestIds(file, "server-b", playerKey));
        assertEquals("2", manager.loadActiveQuestId(file, "server-b", playerKey));
    }

    @Test
    void differentPlayersAreIsolated() throws Exception {
        Path file = tempDir.resolve("tracked_quests.json");
        String serverKey = "myserver";

        manager.saveToPath(file, serverKey, "player-a", Set.of("1"), "1");
        manager.saveToPath(file, serverKey, "player-b", Set.of("2"), null);

        assertEquals(Set.of("1"), manager.loadFollowedQuestIds(file, serverKey, "player-a"));
        assertEquals("1", manager.loadActiveQuestId(file, serverKey, "player-a"));
        assertEquals(Set.of("2"), manager.loadFollowedQuestIds(file, serverKey, "player-b"));
        assertNull(manager.loadActiveQuestId(file, serverKey, "player-b"));
    }

    @Test
    void missingFileReturnsEmpty() {
        Path file = tempDir.resolve("nonexistent.json");
        Set<String> loaded = manager.loadFollowedQuestIds(file, "srv", "p");
        assertTrue(loaded.isEmpty());
        assertNull(manager.loadActiveQuestId(file, "srv", "p"));
    }

    @Test
    void corruptJsonReturnsEmpty() throws Exception {
        Path file = tempDir.resolve("corrupt.json");
        Files.writeString(file, "ceci n'est pas du JSON {{");

        Set<String> loaded = manager.loadFollowedQuestIds(file, "srv", "p");
        assertTrue(loaded.isEmpty());
        assertNull(manager.loadActiveQuestId(file, "srv", "p"));
    }

    @Test
    void overwriteExistingEntry() throws Exception {
        Path file = tempDir.resolve("tracked_quests.json");
        String serverKey = "srv";
        String playerKey = "p";

        manager.saveToPath(file, serverKey, playerKey, Set.of("1", "2"), "1");
        manager.saveToPath(file, serverKey, playerKey, Set.of("3"), "3");

        assertEquals(Set.of("3"), manager.loadFollowedQuestIds(file, serverKey, playerKey));
        assertEquals("3", manager.loadActiveQuestId(file, serverKey, playerKey));
    }

    @Test
    void emptyFollowedSet() throws Exception {
        Path file = tempDir.resolve("tracked_quests.json");
        String serverKey = "srv";
        String playerKey = "p";

        manager.saveToPath(file, serverKey, playerKey, Set.of(), null);

        Set<String> loaded = manager.loadFollowedQuestIds(file, serverKey, playerKey);
        assertTrue(loaded.isEmpty());
        assertNull(manager.loadActiveQuestId(file, serverKey, playerKey));
    }

    @Test
    void distinctSingleplayerContextKeysAreIsolated() throws Exception {
        Path file = tempDir.resolve("tracked_quests.json");
        String playerKey = "uuid-solo";

        String firstWorld = QuestPersistenceManager.singleplayerContextKey("monde-a");
        String secondWorld = QuestPersistenceManager.singleplayerContextKey("monde-b");
        manager.saveToPath(file, firstWorld, playerKey, Set.of("42"), "42");
        manager.saveToPath(file, secondWorld, playerKey, Set.of("99"), "99");

        assertEquals(Set.of("42"), manager.loadFollowedQuestIds(file, firstWorld, playerKey));
        assertEquals("42", manager.loadActiveQuestId(file, firstWorld, playerKey));
        assertEquals(Set.of("99"), manager.loadFollowedQuestIds(file, secondWorld, playerKey));
    }

    @Test
    void multiplayerContextIncludesTheServerAddress() {
        assertEquals("multiplayer:example.org:25565", QuestPersistenceManager.multiplayerContextKey("example.org:25565"));
    }

    @Test
    void fileIsCreatedOnSave() throws Exception {
        Path file = tempDir.resolve("newdir").resolve("tracked_quests.json");
        manager.saveToPath(file, "srv", "p", Set.of("1"), null);
        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);
    }

    @Test
    void activeQuestIdCanBeNull() throws Exception {
        Path file = tempDir.resolve("tracked_quests.json");

        manager.saveToPath(file, "srv", "p", Set.of("1", "2"), null);
        assertNull(manager.loadActiveQuestId(file, "srv", "p"));
    }

    @Test
    void seenQuestIdsPreserveAQuestThePlayerUnfollowed() throws Exception {
        Path file = tempDir.resolve("tracked_quests.json");

        manager.saveToPath(file, "srv", "p", Set.of(), Set.of("new-quest"), null);

        assertTrue(manager.hasSeenQuestIds(file, "srv", "p"));
        assertEquals(Set.of("new-quest"), manager.loadSeenQuestIds(file, "srv", "p"));
    }

    @Test
    void legacyPersistenceHasNoSeenQuestBaseline() throws Exception {
        Path file = tempDir.resolve("tracked_quests.json");
        Files.writeString(file, "{\"srv\":{\"p\":{\"followedQuestIds\":[\"legacy\"],\"activeQuestId\":\"legacy\"}}}");

        assertTrue(manager.loadSeenQuestIds(file, "srv", "p").isEmpty());
        assertTrue(!manager.hasSeenQuestIds(file, "srv", "p"));
    }

    @Test
    void loadReturnsImmutableSet() throws Exception {
        Path file = tempDir.resolve("tracked_quests.json");
        manager.saveToPath(file, "srv", "p", Set.of("1"), null);

        Set<String> loaded = manager.loadFollowedQuestIds(file, "srv", "p");
        assertNotNull(loaded);
        try {
            loaded.add("should-fail");
            // Si on arrive ici, le set n'est pas immutable — on le signale.
            assertTrue(false, "Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Attendu
        }
    }
}
