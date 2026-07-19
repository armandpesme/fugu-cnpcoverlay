package com.cnpcoverlay.cnpcoverlaymod.client.ui;

import com.cnpcoverlay.cnpcoverlaymod.client.quest.history.QuestHistoryEntry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuestHistoryViewModelTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    @Test
    void sortsOccurrencesByDescendingSequenceAndKeepsRepeatablesSeparate() {
        var older = entry("occ-1", "7", "Combat", "Dragon", 1L, 1_700_000_000_000L);
        var newer = entry("occ-2", "7", "Combat", "Dragon", 2L, 1_700_000_060_000L);

        var view = QuestHistoryViewModel.create(
                List.of(older, newer), "", "Toutes", null, 0, 0, 8, 4, UTC
        );

        assertEquals(List.of("occ-2", "occ-1"),
                view.visibleRows().stream().map(QuestHistoryViewModel.Row::occurrenceId).toList());
        assertNotEquals(view.visibleRows().get(0).occurrenceId(), view.visibleRows().get(1).occurrenceId());
        assertEquals("occ-2", view.selectedOccurrenceId());
    }

    @Test
    void filtersAllFieldsWithLocaleRoot() {
        var entry = new QuestHistoryEntry(
                "occ-1", "7", "ÎLES", "Le Trésor", "Trouver la GROTTE",
                List.of("Parler à İBRAHIM"), 1_700_000_000_000L, 10L, 1L
        );

        assertEquals(1, view(List.of(entry), "îles").filteredCount());
        assertEquals(1, view(List.of(entry), "trésor").filteredCount());
        assertEquals(1, view(List.of(entry), "grotte").filteredCount());
        assertEquals(1, view(List.of(entry), "ibrahim").filteredCount());
        assertEquals(0, view(List.of(entry), "introuvable").filteredCount());
    }

    @Test
    void exposesToutesThenSortedCategoriesFromFilteredEntries() {
        var entries = List.of(
                entry("1", "1", "Zoologie", "Z", 1L, 1L),
                entry("2", "2", "alchimie", "A", 2L, 2L),
                entry("3", "3", "", "Sans catégorie", 3L, 3L)
        );

        var all = view(entries, "");
        assertEquals(List.of("Toutes", "alchimie", "Général", "Zoologie"), all.categories());

        var filtered = view(entries, "zoologie");
        assertEquals(List.of("Toutes", "Zoologie"), filtered.categories());
    }

    @Test
    void clampsScrollsAndOnlyMaterializesVisibleWindowForThousandsOfEntries() {
        List<QuestHistoryEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 5_000; i++) {
            entries.add(entry("occ-" + i, Integer.toString(i), "Catégorie " + (i % 20),
                    "Quête " + i, i, i));
        }

        var view = QuestHistoryViewModel.create(
                entries, "", "Toutes", null, 999, 99_999, 8, 4, UTC
        );

        assertEquals(8, view.visibleCategories().size());
        assertEquals(4, view.visibleRows().size());
        assertEquals(13, view.categoryScroll());
        assertEquals(4_996, view.entryScroll());
        assertEquals("occ-4", view.visibleRows().get(0).occurrenceId());
        assertEquals(5_000, view.filteredCount());
    }

    @Test
    void movesOnlyThePreparedWindowsWhenScrolling() {
        List<QuestHistoryEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 1_000; i++) {
            entries.add(entry("occ-" + i, Integer.toString(i), "Combat",
                    "Quête " + i, i, i));
        }
        var prepared = view(entries, "");

        var scrolled = QuestHistoryViewModel.withScroll(prepared, 0, 996, 8, 4);

        assertEquals(List.of("occ-4", "occ-3", "occ-2", "occ-1"),
                scrolled.visibleRows().stream().map(QuestHistoryViewModel.Row::occurrenceId).toList());
        assertEquals(prepared.categoryEntries(), scrolled.categoryEntries());
        assertEquals(prepared.detail(), scrolled.detail());
    }

    @Test
    void formatsAtMostTheFourVisibleRowsAndSelectionDoesNotRebuildRows() {
        List<QuestHistoryEntry> entries = new ArrayList<>();
        for (int i = 1; i <= 5_000; i++) {
            entries.add(entry("occ-" + i, Integer.toString(i), "Combat",
                    "Quête " + i, i, i));
        }
        AtomicInteger formattedRows = new AtomicInteger();
        QuestHistoryViewModel.RowFactory countingFactory = (entry, zoneId) -> {
            formattedRows.incrementAndGet();
            return QuestHistoryViewModel.defaultRow(entry, zoneId);
        };

        var prepared = QuestHistoryViewModel.create(
                entries, "", "Toutes", null, 0, 0, 8, 4, UTC, countingFactory);
        assertEquals(4, formattedRows.get());

        var scrolled = QuestHistoryViewModel.withScroll(prepared, 0, 4_996, 8, 4, countingFactory);
        assertEquals(8, formattedRows.get());
        assertEquals(List.of("occ-4", "occ-3", "occ-2", "occ-1"),
                scrolled.visibleRows().stream().map(QuestHistoryViewModel.Row::occurrenceId).toList());

        var selected = QuestHistoryViewModel.withSelection(scrolled, "occ-2");
        assertEquals(8, formattedRows.get());
        assertEquals("occ-2", selected.selectedOccurrenceId());
        assertEquals("occ-2", selected.detail().occurrenceId());
        assertEquals(scrolled.visibleRows(), selected.visibleRows());
    }

    @Test
    void preparesStableLabelsAndDatesOutsideRendering() {
        long timestamp = Instant.parse("2023-11-14T22:13:20Z").toEpochMilli();
        var entry = entry("occ-1", "7", "Combat", "Un titre de quête vraiment beaucoup trop long", 1L, timestamp);

        var view = view(List.of(entry), "");

        assertEquals("14/11 22:13", view.visibleRows().get(0).compactDate());
        assertTrue(view.visibleRows().get(0).label().startsWith("✓ "));
        assertTrue(view.visibleRows().get(0).label().contains("…"));
        assertEquals("14/11/2023 à 22:13", view.detail().fullDate());
        assertEquals("Combat", view.detail().category());
    }

    @Test
    void distinguishesEmptyHistoryFromSearchWithoutResults() {
        assertEquals("Aucune quête terminée enregistrée", view(List.of(), "").emptyMessage());
        assertEquals("Aucun résultat",
                view(List.of(entry("1", "1", "Combat", "Dragon", 1L, 1L)), "absent").emptyMessage());
    }

    private static QuestHistoryViewModel.View view(List<QuestHistoryEntry> entries, String filter) {
        return QuestHistoryViewModel.create(entries, filter, "Toutes", null, 0, 0, 8, 4, UTC);
    }

    private static QuestHistoryEntry entry(
            String occurrenceId,
            String questId,
            String category,
            String title,
            long sequence,
            long observedAt
    ) {
        return new QuestHistoryEntry(
                occurrenceId, questId, category, title, "Description " + title,
                List.of("Objectif terminé"), observedAt, sequence * 10L, sequence
        );
    }
}
