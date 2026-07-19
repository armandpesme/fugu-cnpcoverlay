package com.cnpcoverlay.cnpcoverlaymod.client.ui;

import com.cnpcoverlay.cnpcoverlaymod.client.quest.history.QuestHistoryEntry;

import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Prépare une fenêtre immuable de l'historique afin que l'écran n'ait aucun
 * tri, filtrage ou formatage de date à effectuer pendant son rendu.
 */
public final class QuestHistoryViewModel {
    public static final String ALL_CATEGORIES = "Toutes";
    private static final String DEFAULT_CATEGORY = "Général";
    private static final int MAX_ROW_TITLE_LENGTH = 22;
    private static final DateTimeFormatter COMPACT_DATE =
            DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.FRANCE);
    private static final DateTimeFormatter FULL_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRANCE);
    private static final Comparator<String> CATEGORY_ORDER =
            String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder());

    private QuestHistoryViewModel() {
    }

    public static View create(
            List<QuestHistoryEntry> source,
            String filter,
            String requestedCategory,
            String requestedOccurrenceId,
            int requestedCategoryScroll,
            int requestedEntryScroll,
            int visibleCategoryRows,
            int visibleEntryRows,
            ZoneId zoneId
    ) {
        return create(
                source, filter, requestedCategory, requestedOccurrenceId,
                requestedCategoryScroll, requestedEntryScroll,
                visibleCategoryRows, visibleEntryRows, zoneId,
                QuestHistoryViewModel::defaultRow
        );
    }

    static View create(
            List<QuestHistoryEntry> source,
            String filter,
            String requestedCategory,
            String requestedOccurrenceId,
            int requestedCategoryScroll,
            int requestedEntryScroll,
            int visibleCategoryRows,
            int visibleEntryRows,
            ZoneId zoneId,
            RowFactory rowFactory
    ) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(rowFactory, "rowFactory");
        int categoryWindowSize = Math.max(0, visibleCategoryRows);
        int entryWindowSize = Math.max(0, visibleEntryRows);
        String needle = searchable(filter);

        List<QuestHistoryEntry> filtered = source.stream()
                .filter(Objects::nonNull)
                .filter(entry -> needle.isEmpty() || searchableText(entry).contains(needle))
                .sorted(Comparator.comparingLong(QuestHistoryEntry::sequence).reversed())
                .toList();

        List<String> categories = new ArrayList<>();
        categories.add(ALL_CATEGORIES);
        filtered.stream()
                .map(entry -> displayCategory(entry.category()))
                .distinct()
                .sorted(CATEGORY_ORDER)
                .forEach(categories::add);

        String selectedCategory = categories.contains(requestedCategory)
                ? requestedCategory
                : ALL_CATEGORIES;
        List<QuestHistoryEntry> categoryEntries = ALL_CATEGORIES.equals(selectedCategory)
                ? filtered
                : filtered.stream()
                        .filter(entry -> displayCategory(entry.category()).equals(selectedCategory))
                        .toList();

        int categoryScroll = clampScroll(requestedCategoryScroll, categories.size(), categoryWindowSize);
        int entryScroll = clampScroll(requestedEntryScroll, categoryEntries.size(), entryWindowSize);
        List<String> visibleCategories = window(categories, categoryScroll, categoryWindowSize);
        List<Row> rows = window(categoryEntries, entryScroll, entryWindowSize).stream()
                .map(entry -> rowFactory.create(entry, zoneId))
                .toList();

        QuestHistoryEntry selectedEntry = categoryEntries.stream()
                .filter(entry -> entry.occurrenceId().equals(requestedOccurrenceId))
                .findFirst()
                .orElseGet(() -> categoryEntries.stream().findFirst().orElse(null));
        String selectedOccurrenceId = selectedEntry == null ? null : selectedEntry.occurrenceId();

        Detail detail = selectedEntry == null ? null : detail(selectedEntry, zoneId);
        String emptyMessage = filtered.isEmpty()
                ? (source.isEmpty() ? "Aucune quête terminée enregistrée" : "Aucun résultat")
                : "";

        return new View(
                List.copyOf(categories),
                visibleCategories,
                categoryEntries,
                rows,
                detail,
                selectedCategory,
                selectedOccurrenceId,
                categoryScroll,
                entryScroll,
                filtered.size(),
                categoryEntries.size(),
                emptyMessage,
                zoneId
        );
    }

    /**
     * Déplace uniquement les fenêtres visibles d'une vue déjà préparée.
     * Cette opération ne retrie ni ne refiltre et ne formate que les lignes visibles.
     */
    public static View withScroll(
            View prepared,
            int requestedCategoryScroll,
            int requestedEntryScroll,
            int visibleCategoryRows,
            int visibleEntryRows
    ) {
        return withScroll(
                prepared, requestedCategoryScroll, requestedEntryScroll,
                visibleCategoryRows, visibleEntryRows, QuestHistoryViewModel::defaultRow
        );
    }

    static View withScroll(
            View prepared,
            int requestedCategoryScroll,
            int requestedEntryScroll,
            int visibleCategoryRows,
            int visibleEntryRows,
            RowFactory rowFactory
    ) {
        Objects.requireNonNull(prepared, "prepared");
        Objects.requireNonNull(rowFactory, "rowFactory");
        int categoryScroll = clampScroll(
                requestedCategoryScroll, prepared.categories().size(), Math.max(0, visibleCategoryRows));
        int entryScroll = clampScroll(
                requestedEntryScroll, prepared.categoryEntries().size(), Math.max(0, visibleEntryRows));
        List<Row> visibleRows = window(prepared.categoryEntries(), entryScroll, visibleEntryRows).stream()
                .map(entry -> rowFactory.create(entry, prepared.zoneId()))
                .toList();
        return new View(
                prepared.categories(),
                window(prepared.categories(), categoryScroll, visibleCategoryRows),
                prepared.categoryEntries(),
                visibleRows,
                prepared.detail(),
                prepared.selectedCategory(),
                prepared.selectedOccurrenceId(),
                categoryScroll,
                entryScroll,
                prepared.filteredCount(),
                prepared.categoryEntryCount(),
                prepared.emptyMessage(),
                prepared.zoneId()
        );
    }

    /**
     * Change uniquement l'occurrence sélectionnée et son détail préparé.
     */
    public static View withSelection(View prepared, String requestedOccurrenceId) {
        Objects.requireNonNull(prepared, "prepared");
        QuestHistoryEntry selectedEntry = prepared.categoryEntries().stream()
                .filter(entry -> entry.occurrenceId().equals(requestedOccurrenceId))
                .findFirst()
                .orElseGet(() -> prepared.categoryEntries().stream().findFirst().orElse(null));
        String selectedOccurrenceId = selectedEntry == null ? null : selectedEntry.occurrenceId();
        return new View(
                prepared.categories(),
                prepared.visibleCategories(),
                prepared.categoryEntries(),
                prepared.visibleRows(),
                selectedEntry == null ? null : detail(selectedEntry, prepared.zoneId()),
                prepared.selectedCategory(),
                selectedOccurrenceId,
                prepared.categoryScroll(),
                prepared.entryScroll(),
                prepared.filteredCount(),
                prepared.categoryEntryCount(),
                prepared.emptyMessage(),
                prepared.zoneId()
        );
    }

    static int clampScroll(int value, int totalItems, int visibleItems) {
        if (visibleItems <= 0) {
            return 0;
        }
        int maximum = Math.max(0, totalItems - visibleItems);
        return Math.max(0, Math.min(maximum, value));
    }

    static Row defaultRow(QuestHistoryEntry entry, ZoneId zoneId) {
        String compactDate = COMPACT_DATE.withZone(zoneId)
                .format(Instant.ofEpochMilli(entry.observedCompletedAtEpochMillis()));
        String displayTitle = ellipsize(entry.title(), MAX_ROW_TITLE_LENGTH);
        return new Row(
                entry.occurrenceId(),
                entry.questId(),
                entry.title(),
                displayTitle,
                compactDate,
                "✓ " + displayTitle + "  " + compactDate
        );
    }

    private static Detail detail(QuestHistoryEntry entry, ZoneId zoneId) {
        String fullDate = FULL_DATE.withZone(zoneId)
                .format(Instant.ofEpochMilli(entry.observedCompletedAtEpochMillis()));
        return new Detail(
                entry.occurrenceId(),
                entry.title(),
                displayCategory(entry.category()),
                fullDate,
                entry.displayLogText(),
                entry.objectives()
        );
    }

    private static String searchableText(QuestHistoryEntry entry) {
        return searchable(entry.category() + " " + entry.title() + " "
                + entry.displayLogText() + " " + String.join(" ", entry.objectives()));
    }

    private static String searchable(String value) {
        String lower = Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static String displayCategory(String category) {
        String normalized = Objects.requireNonNullElse(category, "").trim();
        return normalized.isEmpty() ? DEFAULT_CATEGORY : normalized;
    }

    private static String ellipsize(String value, int maximumLength) {
        String text = Objects.requireNonNullElse(value, "");
        if (text.length() <= maximumLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maximumLength - 1)).stripTrailing() + "…";
    }

    private static <T> List<T> window(List<T> values, int start, int size) {
        if (size <= 0 || values.isEmpty()) {
            return List.of();
        }
        int from = Math.min(start, values.size());
        int to = Math.min(values.size(), from + size);
        return List.copyOf(values.subList(from, to));
    }

    @FunctionalInterface
    interface RowFactory {
        Row create(QuestHistoryEntry entry, ZoneId zoneId);
    }

    public record Row(
            String occurrenceId,
            String questId,
            String title,
            String displayTitle,
            String compactDate,
            String label
    ) {
    }

    public record Detail(
            String occurrenceId,
            String title,
            String category,
            String fullDate,
            String description,
            List<String> objectives
    ) {
        public Detail {
            objectives = List.copyOf(objectives);
        }
    }

    public record View(
            List<String> categories,
            List<String> visibleCategories,
            List<QuestHistoryEntry> categoryEntries,
            List<Row> visibleRows,
            Detail detail,
            String selectedCategory,
            String selectedOccurrenceId,
            int categoryScroll,
            int entryScroll,
            int filteredCount,
            int categoryEntryCount,
            String emptyMessage,
            ZoneId zoneId
    ) {
        public View {
            categories = List.copyOf(categories);
            visibleCategories = List.copyOf(visibleCategories);
            categoryEntries = List.copyOf(categoryEntries);
            visibleRows = List.copyOf(visibleRows);
            zoneId = Objects.requireNonNull(zoneId);
        }
    }
}
