package com.cnpcoverlay.cnpcoverlaymod.client.quest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuestEntryTest {
    @Test
    void displayLogTextRemovesOnlyLinesWhoseFirstNonWhitespaceCharacterIsHash() {
        QuestEntry entry = entryWithLogText("Texte visible\n#\n#meta=1\n  # masqué\nAller voir #3\nTexte final");

        assertEquals("Texte visible\nAller voir #3\nTexte final", entry.displayLogText());
    }

    @Test
    void displayLogTextPreservesDescriptionsWithoutMetadataLines() {
        QuestEntry entry = entryWithLogText("Première ligne\nDeuxième ligne");

        assertEquals(entry.logText(), entry.displayLogText());
    }

    @Test
    void displayLogTextReturnsEmptyForEmptyOrMetadataOnlyDescriptions() {
        assertEquals("", entryWithLogText("").displayLogText());
        assertEquals("", entryWithLogText("#un\n  #deux").displayLogText());
    }

    @Test
    void exposesTurnInInstructionWhenObjectivesAreCompleteAndCompleterIsConfigured() {
        QuestEntry entry = new QuestEntry("id", "category", "title", "", List.of(
                new QuestObjectiveSnapshot(1, "Trouver l'artefact", 1, 1, true)),
                1.0f, false, "Capitaine Éloïse");

        assertTrue(entry.turnInInstruction().isPresent());
        assertEquals("Aller voir Capitaine Éloïse", entry.turnInInstruction().get());
    }

    @Test
    void hidesTurnInInstructionUntilQuestProgressReachesOneHundredPercent() {
        QuestEntry entry = new QuestEntry("id", "category", "title", "", List.of(
                new QuestObjectiveSnapshot(1, "Trouver l'artefact", 1, 1, true)),
                0.99f, false, "Capitaine Éloïse");

        assertTrue(entry.turnInInstruction().isEmpty());
    }

    private static QuestEntry entryWithLogText(String logText) {
        return new QuestEntry("id", "category", "title", logText, List.of(), 0.0f);
    }
}
