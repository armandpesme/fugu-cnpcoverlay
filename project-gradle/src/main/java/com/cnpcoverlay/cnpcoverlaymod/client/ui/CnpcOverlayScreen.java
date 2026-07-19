package com.cnpcoverlay.cnpcoverlaymod.client.ui;

import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestEntry;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestTrackerState;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.history.QuestHistoryEntry;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.history.QuestHistoryState;
import com.cnpcoverlay.cnpcoverlaymod.client.ui.widgets.FlatButton;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;

import java.text.Normalizer;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class CnpcOverlayScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 260;
    private static final int ROW_H = 14;
    private static final int ROW_STEP = 16;
    private static final int MAX_QUEST_ROWS = 4;
    private static final int MAX_CAT_ROWS = 4;
    private static final int FOLLOW_WIDTH = 18;
    private static final int DETAILS_TOP_OFFSET = 142;
    private static final int DETAILS_BOTTOM_OFFSET = 222;
    private static final int CLOSE_TOP_OFFSET = 236;
    private static final int COLOR_TEXT = 0xFFEFFFFF;
    private static final int COLOR_MUTED = 0xFFBFEFF2;
    private static final int COLOR_GREEN = 0xFF55FF55;
    private static final String ALL_CATEGORIES = QuestHistoryViewModel.ALL_CATEGORIES;

    private static Tab lastTab = Tab.ACTIVE;
    private static String lastActiveFilter = "";
    private static String lastHistoryFilter = "";
    private static String lastActiveCategory = ALL_CATEGORIES;
    private static String lastHistoryCategory = ALL_CATEGORIES;
    private static String lastActiveQuestId;
    private static String lastHistoryOccurrenceId;
    private static int lastActiveCategoryScroll;
    private static int lastHistoryCategoryScroll;
    private static int lastActiveQuestScroll;
    private static int lastHistoryQuestScroll;
    private static int lastActiveDetailScroll;
    private static int lastHistoryDetailScroll;
    private static boolean lastActiveFilterFocused;
    private static boolean lastHistoryFilterFocused;
    private static int lastActiveFilterCursor;
    private static int lastHistoryFilterCursor;

    private final QuestTrackerState questState = QuestTrackerState.get();
    private final QuestHistoryState historyState = QuestHistoryState.get();
    private Tab activeTab = lastTab;
    private Tab searchBoxTab;
    private EditBox searchBox;
    private boolean filterDirty;
    private long filterDirtyAtMs;
    private boolean rebuilding;
    private final QuestOverlayRebuildQueue rebuildQueue = new QuestOverlayRebuildQueue();
    private QuestOverlayLayout layout = QuestOverlayLayout.forScreen(PANEL_WIDTH, PANEL_HEIGHT);

    private List<String> preparedCategories = List.of();
    private List<QuestEntry> preparedActiveRows = List.of();
    private QuestHistoryViewModel.View preparedHistory = emptyHistoryView();
    private List<DetailLine> preparedDetailLines = List.of();
    private String preparedDetailTitle = "";
    private String preparedEmptyMessage = "";
    private QuestEntry preparedActiveSelection;
    private int preparedCategoryTotal;
    private int preparedEntryTotal;
    private int preparedDetailTotal;
    private int preparedDetailVisible;
    private List<QuestHistoryEntry> historySourceIdentity = List.of();

    private enum Tab {
        ACTIVE,
        HISTORY
    }

    private record DetailLine(FormattedCharSequence text, int color) {
    }

    public CnpcOverlayScreen() {
        super(Component.literal("CNPC Overlay"));
    }

    @Override
    protected void init() {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            questState.refresh(player);
        }
        rebuildPreparedWidgets(true);
    }

    private void rebuildPreparedWidgets(boolean prepare) {
        if (rebuilding) {
            return;
        }
        rebuilding = true;
        persistUiState();
        this.setFocused(null);
        this.clearWidgets();
        this.searchBox = null;
        this.searchBoxTab = null;
        this.layout = QuestOverlayLayout.forScreen(this.width, this.height);
        if (prepare) {
            prepareView();
        }
        buildFixedWidgets();
        persistUiState();
        rebuilding = false;
    }

    private void prepareView() {
        if (activeTab == Tab.HISTORY) {
            prepareHistoryView();
        } else {
            prepareActiveView();
        }
    }

    private void prepareActiveView() {
        String needle = searchable(lastActiveFilter);
        List<QuestEntry> filtered = questState.getQuests().stream()
                .filter(quest -> needle.isEmpty() || searchableText(quest).contains(needle))
                .sorted(Comparator.comparing((QuestEntry quest) -> displayCategory(quest.category()),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(QuestEntry::title, String.CASE_INSENSITIVE_ORDER))
                .toList();

        List<String> allCategories = new ArrayList<>();
        allCategories.add(ALL_CATEGORIES);
        filtered.stream()
                .map(quest -> displayCategory(quest.category()))
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER.thenComparing(Comparator.naturalOrder()))
                .forEach(allCategories::add);
        if (!allCategories.contains(lastActiveCategory)) {
            lastActiveCategory = ALL_CATEGORIES;
        }

        List<QuestEntry> entries = ALL_CATEGORIES.equals(lastActiveCategory)
                ? filtered
                : filtered.stream()
                        .filter(quest -> displayCategory(quest.category()).equals(lastActiveCategory))
                        .toList();
        lastActiveCategoryScroll = QuestHistoryViewModel.clampScroll(
                lastActiveCategoryScroll, allCategories.size(), MAX_CAT_ROWS);
        lastActiveQuestScroll = QuestHistoryViewModel.clampScroll(
                lastActiveQuestScroll, entries.size(), MAX_QUEST_ROWS);
        preparedCategories = window(allCategories, lastActiveCategoryScroll, MAX_CAT_ROWS);
        preparedActiveRows = window(entries, lastActiveQuestScroll, MAX_QUEST_ROWS);
        preparedCategoryTotal = allCategories.size();
        preparedEntryTotal = entries.size();
        preparedEmptyMessage = filtered.isEmpty()
                ? (questState.getQuests().isEmpty() ? "Aucune quête" : "Aucun résultat")
                : "";

        preparedActiveSelection = entries.stream()
                .filter(quest -> quest.id().equals(lastActiveQuestId))
                .findFirst()
                .orElseGet(() -> questState.getActiveQuest()
                        .filter(entries::contains)
                        .orElseGet(() -> entries.stream().findFirst().orElse(null)));
        lastActiveQuestId = preparedActiveSelection == null ? null : preparedActiveSelection.id();
        prepareActiveDetails(preparedActiveSelection);
    }

    private void prepareHistoryView() {
        historySourceIdentity = historyState.entries();
        preparedHistory = QuestHistoryViewModel.create(
                historySourceIdentity,
                lastHistoryFilter,
                lastHistoryCategory,
                lastHistoryOccurrenceId,
                lastHistoryCategoryScroll,
                lastHistoryQuestScroll,
                MAX_CAT_ROWS,
                MAX_QUEST_ROWS,
                ZoneId.systemDefault()
        );
        lastHistoryCategory = preparedHistory.selectedCategory();
        lastHistoryOccurrenceId = preparedHistory.selectedOccurrenceId();
        lastHistoryCategoryScroll = preparedHistory.categoryScroll();
        lastHistoryQuestScroll = preparedHistory.entryScroll();
        preparedCategories = preparedHistory.visibleCategories();
        preparedCategoryTotal = preparedHistory.categories().size();
        preparedEntryTotal = preparedHistory.categoryEntryCount();
        preparedEmptyMessage = preparedHistory.emptyMessage();
        prepareHistoryDetails(preparedHistory.detail());
    }

    private void prepareActiveDetails(QuestEntry quest) {
        List<DetailLine> lines = new ArrayList<>();
        preparedDetailTitle = quest == null ? "" : fitTitle(quest.title(), layout.width(170));
        if (quest != null) {
            if (!quest.displayLogText().isBlank()) {
                addSplitLines(lines, quest.displayLogText(), 410, COLOR_MUTED);
                lines.add(new DetailLine(FormattedCharSequence.EMPTY, COLOR_MUTED));
            }
            addSplitLines(lines, "Objectifs :", 410, COLOR_MUTED);
            for (String objective : quest.objectives()) {
                if (objective == null || objective.isBlank()) {
                    continue;
                }
                boolean completed = isCompletedObjective(objective);
                addSplitLines(lines, (completed ? "✓ " : "• ") + objective,
                        410, completed ? COLOR_GREEN : COLOR_MUTED);
            }
            quest.turnInInstruction().ifPresent(instruction ->
                    addSplitLines(lines, instruction, 410, COLOR_GREEN));
        }
        preparedDetailLines = List.copyOf(lines);
        prepareDetailWindow(Tab.ACTIVE);
    }

    private void prepareHistoryDetails(QuestHistoryViewModel.Detail detail) {
        List<DetailLine> lines = new ArrayList<>();
        preparedDetailTitle = detail == null ? "" : fitTitle(detail.title(), layout.width(170));
        if (detail != null) {
            addSplitLines(lines, "Catégorie : " + detail.category(), 410, COLOR_MUTED);
            addSplitLines(lines, "Validée le " + detail.fullDate(), 410, COLOR_MUTED);
            if (!detail.description().isBlank()) {
                lines.add(new DetailLine(FormattedCharSequence.EMPTY, COLOR_MUTED));
                addSplitLines(lines, detail.description(), 410, COLOR_MUTED);
            }
            if (!detail.objectives().isEmpty()) {
                lines.add(new DetailLine(FormattedCharSequence.EMPTY, COLOR_MUTED));
                addSplitLines(lines, "Objectifs :", 410, COLOR_MUTED);
                for (String objective : detail.objectives()) {
                    if (objective != null && !objective.isBlank()) {
                        addSplitLines(lines, "✓ " + objective, 410, COLOR_GREEN);
                    }
                }
            }
        }
        preparedDetailLines = List.copyOf(lines);
        prepareDetailWindow(Tab.HISTORY);
    }

    private void prepareDetailWindow(Tab tab) {
        preparedDetailTotal = preparedDetailLines.size();
        preparedDetailVisible = 7;
        if (tab == Tab.HISTORY) {
            lastHistoryDetailScroll = QuestHistoryViewModel.clampScroll(
                    lastHistoryDetailScroll, preparedDetailTotal, preparedDetailVisible);
        } else {
            lastActiveDetailScroll = QuestHistoryViewModel.clampScroll(
                    lastActiveDetailScroll, preparedDetailTotal, preparedDetailVisible);
        }
    }

    private void buildFixedWidgets() {
        addTabButtons();
        addSearchBox();
        addCategoryButtons();
        if (activeTab == Tab.HISTORY) {
            addHistoryButtons();
        } else {
            addActiveButtons();
        }
        QuestOverlayLayout.Rect close = layout.rect(310, CLOSE_TOP_OFFSET, 60, 14);
        this.addRenderableWidget(new FlatButton(
                close.x(), close.y(), close.width(), close.height(),
                Component.literal("Fermer"),
                button -> onClose(),
                scaledText(0.70f)
        ));
    }

    private void addTabButtons() {
        QuestOverlayLayout.Rect active = layout.rect(204, 10, 76, 14);
        this.addRenderableWidget(new FlatButton(
                active.x(), active.y(), active.width(), active.height(), Component.literal("En cours"),
                button -> switchTab(Tab.ACTIVE), () -> activeTab == Tab.ACTIVE, scaledText(0.78f)
        ));
        QuestOverlayLayout.Rect history = layout.rect(286, 10, 84, 14);
        this.addRenderableWidget(new FlatButton(
                history.x(), history.y(), history.width(), history.height(), Component.literal("Historique"),
                button -> switchTab(Tab.HISTORY), () -> activeTab == Tab.HISTORY, scaledText(0.78f)
        ));
    }

    private void addSearchBox() {
        this.searchBoxTab = activeTab;
        String filter = activeTab == Tab.HISTORY ? lastHistoryFilter : lastActiveFilter;
        QuestOverlayLayout.Rect search = layout.rect(10, 36, PANEL_WIDTH - 20, 14);
        this.searchBox = this.addRenderableWidget(new EditBox(
                this.font, search.x(), search.y(), search.width(), search.height(), Component.literal("Recherche")
        ));
        this.searchBox.setValue(filter);
        this.searchBox.setResponder(value -> {
            if (searchBoxTab == Tab.HISTORY) {
                lastHistoryFilter = Objects.requireNonNullElse(value, "");
                lastHistoryFilterFocused = true;
                lastHistoryFilterCursor = this.searchBox.getCursorPosition();
            } else {
                lastActiveFilter = Objects.requireNonNullElse(value, "");
                lastActiveFilterFocused = true;
                lastActiveFilterCursor = this.searchBox.getCursorPosition();
            }
            filterDirty = true;
            filterDirtyAtMs = System.currentTimeMillis();
        });
        boolean restoreFocus = activeTab == Tab.HISTORY
                ? lastHistoryFilterFocused : lastActiveFilterFocused;
        int restoreCursor = activeTab == Tab.HISTORY
                ? lastHistoryFilterCursor : lastActiveFilterCursor;
        if (restoreFocus) {
            this.setFocused(this.searchBox);
            this.searchBox.setFocused(true);
            this.searchBox.setCursorPosition(Math.min(restoreCursor, this.searchBox.getValue().length()));
        }
    }

    private void addCategoryButtons() {
        for (int row = 0; row < preparedCategories.size(); row++) {
            String category = preparedCategories.get(row);
            QuestOverlayLayout.Rect bounds = layout.rect(10, 68 + row * ROW_STEP, 110, ROW_H);
            this.addRenderableWidget(new FlatButton(
                    bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    Component.literal(category),
                    button -> selectCategory(category),
                    () -> category.equals(selectedCategory()),
                    scaledText(0.78f)
            ));
        }
    }

    private void addActiveButtons() {
        int virtualTitleWidth = PANEL_WIDTH - 20 - 128 - FOLLOW_WIDTH - 4;
        for (int row = 0; row < preparedActiveRows.size(); row++) {
            QuestEntry quest = preparedActiveRows.get(row);
            QuestOverlayLayout.Rect follow = layout.rect(128, 68 + row * ROW_STEP, FOLLOW_WIDTH, ROW_H);
            this.addRenderableWidget(new FlatButton(
                    follow.x(), follow.y(), follow.width(), follow.height(),
                    Component.literal(quest.isFollowed() ? "✓" : ""),
                    button -> toggleFollow(quest),
                    quest::isFollowed,
                    scaledText(0.78f)
            ));
            String marker = questState.getActiveQuest()
                    .filter(active -> active.id().equals(quest.id()))
                    .map(active -> "> ")
                    .orElse("");
            QuestOverlayLayout.Rect titleBounds =
                    layout.rect(128 + FOLLOW_WIDTH + 4, 68 + row * ROW_STEP, virtualTitleWidth, ROW_H);
            String title = fitTitle(marker + quest.title(), titleBounds.width() - layout.width(8));
            this.addRenderableWidget(new FlatButton(
                    titleBounds.x(), titleBounds.y(), titleBounds.width(), titleBounds.height(),
                    Component.literal(title),
                    button -> selectActiveQuest(quest),
                    () -> quest.id().equals(lastActiveQuestId),
                    scaledText(0.78f)
            ));
        }
    }

    private void addHistoryButtons() {
        for (int row = 0; row < preparedHistory.visibleRows().size(); row++) {
            QuestHistoryViewModel.Row historyRow = preparedHistory.visibleRows().get(row);
            QuestOverlayLayout.Rect bounds =
                    layout.rect(128, 68 + row * ROW_STEP, PANEL_WIDTH - 20 - 128, ROW_H);
            MutableComponent label = Component.empty()
                    .append(Component.literal("✓ ").withStyle(ChatFormatting.GREEN))
                    .append(Component.literal(historyRow.displayTitle() + "  " + historyRow.compactDate())
                            .withStyle(ChatFormatting.WHITE));
            this.addRenderableWidget(new FlatButton(
                    bounds.x(), bounds.y(), bounds.width(), bounds.height(), label,
                    button -> selectHistoryOccurrence(historyRow.occurrenceId()),
                    () -> historyRow.occurrenceId().equals(lastHistoryOccurrenceId),
                    scaledText(0.68f)
            ));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        QuestOverlayLayout.Rect panel = layout.panel();
        graphics.fill(panel.x(), panel.y(), panel.right(), panel.bottom(), 0xCC0B0B0B);
        graphics.fill(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.bottom() - 1, 0xCC1A1A1A);
        drawPanelBorder(graphics, panel);
        QuestOverlayLayout.Rect header = layout.rect(6, 6, PANEL_WIDTH - 12, 22);
        graphics.fill(header.x(), header.y(), header.right(), header.bottom(), 0xFF111111);
        graphics.drawString(this.font, this.title, layout.x(12), layout.y(13), COLOR_TEXT, false);
        graphics.hLine(layout.x(6), layout.x(PANEL_WIDTH - 7), layout.y(30), 0xAA00E5FF);

        drawLabels(graphics);
        drawDetails(graphics);
        drawPreparedScrollbars(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawPanelBorder(GuiGraphics graphics, QuestOverlayLayout.Rect panel) {
        int border = 0xFFB8B8B8;
        graphics.hLine(panel.x(), panel.right() - 1, panel.y(), border);
        graphics.hLine(panel.x(), panel.right() - 1, panel.bottom() - 1, border);
        graphics.vLine(panel.x(), panel.y(), panel.bottom() - 1, border);
        graphics.vLine(panel.right() - 1, panel.y(), panel.bottom() - 1, border);
    }

    private void drawLabels(GuiGraphics graphics) {
        graphics.drawString(this.font, "Catégories", layout.x(12), layout.y(58), COLOR_MUTED, false);
        graphics.drawString(this.font,
                activeTab == Tab.HISTORY ? "Quêtes terminées" : "Quêtes",
                layout.x(128), layout.y(58), COLOR_MUTED, false);
        graphics.drawString(this.font, "Recherche", layout.x(12), layout.y(27), COLOR_MUTED, false);
        if (!preparedEmptyMessage.isBlank()) {
            graphics.drawString(this.font, preparedEmptyMessage, layout.x(132), layout.y(88), COLOR_MUTED, false);
        }
    }

    private void drawDetails(GuiGraphics graphics) {
        QuestOverlayLayout.Rect details = layout.rect(
                128, DETAILS_TOP_OFFSET, PANEL_WIDTH - 20 - 128,
                DETAILS_BOTTOM_OFFSET - DETAILS_TOP_OFFSET);
        graphics.fill(details.x(), details.y(), details.right(), details.bottom(), 0x661A1A1A);
        graphics.fill(details.x(), details.y(), details.right(), layout.y(DETAILS_TOP_OFFSET + 16), 0x80202020);
        graphics.hLine(details.x(), details.right() - 1, details.y(), 0x6600C8D7);
        graphics.hLine(details.x(), details.right() - 1, layout.y(DETAILS_TOP_OFFSET + 16), 0x33111111);
        graphics.drawString(this.font, "Détails", layout.x(132), layout.y(DETAILS_TOP_OFFSET + 4), COLOR_MUTED, false);
        if (!preparedDetailTitle.isBlank()) {
            graphics.drawString(this.font, preparedDetailTitle, layout.x(176), layout.y(DETAILS_TOP_OFFSET + 4),
                    activeTab == Tab.HISTORY ? COLOR_GREEN : COLOR_TEXT, false);
        }

        float scale = 0.55f * layout.scale();
        int detailScroll = activeTab == Tab.HISTORY ? lastHistoryDetailScroll : lastActiveDetailScroll;
        graphics.pose().pushPose();
        graphics.pose().scale(scale, scale, 1.0f);
        int x = Math.round(layout.x(132) / scale);
        int y = Math.round(layout.y(DETAILS_TOP_OFFSET + 18) / scale);
        for (int row = 0; row < preparedDetailVisible; row++) {
            int index = detailScroll + row;
            if (index >= preparedDetailLines.size()) {
                break;
            }
            DetailLine line = preparedDetailLines.get(index);
            graphics.drawString(this.font, line.text(), x, y, line.color(), false);
            y += 14;
        }
        graphics.pose().popPose();
    }

    private void drawPreparedScrollbars(GuiGraphics graphics) {
        int categoryScroll = activeTab == Tab.HISTORY
                ? lastHistoryCategoryScroll : lastActiveCategoryScroll;
        int entryScroll = activeTab == Tab.HISTORY
                ? lastHistoryQuestScroll : lastActiveQuestScroll;
        int detailScroll = activeTab == Tab.HISTORY
                ? lastHistoryDetailScroll : lastActiveDetailScroll;
        drawScrollbar(graphics, layout.x(114), layout.y(68), layout.height(MAX_CAT_ROWS * ROW_STEP),
                categoryScroll, preparedCategoryTotal, MAX_CAT_ROWS);
        drawScrollbar(graphics, layout.x(PANEL_WIDTH - 16), layout.y(68), layout.height(MAX_QUEST_ROWS * ROW_STEP),
                entryScroll, preparedEntryTotal, MAX_QUEST_ROWS);
        drawScrollbar(graphics, layout.x(PANEL_WIDTH - 16), layout.y(DETAILS_TOP_OFFSET + 18), layout.height(55),
                detailScroll, preparedDetailTotal, preparedDetailVisible);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        captureSearchState();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        captureSearchState();
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        captureSearchState();
        int direction = delta > 0 ? -1 : 1;
        if (layout.rect(128, DETAILS_TOP_OFFSET, PANEL_WIDTH - 20 - 128,
                DETAILS_BOTTOM_OFFSET - DETAILS_TOP_OFFSET).contains(mouseX, mouseY)) {
            scrollDetails(direction);
            return true;
        }
        if (layout.rect(10, 68, 110, MAX_CAT_ROWS * ROW_STEP).contains(mouseX, mouseY)) {
            scrollCategories(direction);
            return true;
        }
        if (layout.rect(128, 68, PANEL_WIDTH - 20 - 128,
                MAX_QUEST_ROWS * ROW_STEP).contains(mouseX, mouseY)) {
            scrollEntries(direction);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void scrollDetails(int direction) {
        if (activeTab == Tab.HISTORY) {
            lastHistoryDetailScroll = QuestHistoryViewModel.clampScroll(
                    lastHistoryDetailScroll + direction, preparedDetailTotal, preparedDetailVisible);
        } else {
            lastActiveDetailScroll = QuestHistoryViewModel.clampScroll(
                    lastActiveDetailScroll + direction, preparedDetailTotal, preparedDetailVisible);
        }
    }

    private void scrollCategories(int direction) {
        if (activeTab == Tab.HISTORY) {
            lastHistoryCategoryScroll = QuestHistoryViewModel.clampScroll(
                    lastHistoryCategoryScroll + direction, preparedCategoryTotal, MAX_CAT_ROWS);
            updatePreparedHistoryWindow();
            scheduleRebuild(false);
        } else {
            lastActiveCategoryScroll = QuestHistoryViewModel.clampScroll(
                    lastActiveCategoryScroll + direction, preparedCategoryTotal, MAX_CAT_ROWS);
            scheduleRebuild(true);
        }
    }

    private void scrollEntries(int direction) {
        if (activeTab == Tab.HISTORY) {
            lastHistoryQuestScroll = QuestHistoryViewModel.clampScroll(
                    lastHistoryQuestScroll + direction, preparedEntryTotal, MAX_QUEST_ROWS);
            updatePreparedHistoryWindow();
            scheduleRebuild(false);
        } else {
            lastActiveQuestScroll = QuestHistoryViewModel.clampScroll(
                    lastActiveQuestScroll + direction, preparedEntryTotal, MAX_QUEST_ROWS);
            scheduleRebuild(true);
        }
    }

    private void switchTab(Tab tab) {
        if (activeTab == tab) {
            return;
        }
        activeTab = tab;
        lastTab = tab;
        filterDirty = false;
        scheduleRebuild(true);
    }

    private void selectCategory(String category) {
        if (activeTab == Tab.HISTORY) {
            lastHistoryCategory = category;
            lastHistoryOccurrenceId = null;
            lastHistoryQuestScroll = 0;
            lastHistoryDetailScroll = 0;
        } else {
            lastActiveCategory = category;
            lastActiveQuestId = null;
            lastActiveQuestScroll = 0;
            lastActiveDetailScroll = 0;
        }
        scheduleRebuild(true);
    }

    private void selectActiveQuest(QuestEntry quest) {
        lastActiveQuestId = quest.id();
        lastActiveDetailScroll = 0;
        if (quest.isFollowed()) {
            questState.setActiveQuest(quest.id());
        }
        scheduleRebuild(true);
    }

    private void toggleFollow(QuestEntry quest) {
        boolean follow = !quest.isFollowed();
        questState.setFollowed(quest.id(), follow);
        if (follow) {
            lastActiveQuestId = quest.id();
            questState.setActiveQuest(quest.id());
        }
        scheduleRebuild(true);
    }

    private void selectHistoryOccurrence(String occurrenceId) {
        preparedHistory = QuestHistoryViewModel.withSelection(preparedHistory, occurrenceId);
        lastHistoryOccurrenceId = preparedHistory.selectedOccurrenceId();
        lastHistoryDetailScroll = 0;
        prepareHistoryDetails(preparedHistory.detail());
        scheduleRebuild(false);
    }

    private void updatePreparedHistoryWindow() {
        preparedHistory = QuestHistoryViewModel.withScroll(
                preparedHistory,
                lastHistoryCategoryScroll,
                lastHistoryQuestScroll,
                MAX_CAT_ROWS,
                MAX_QUEST_ROWS
        );
        lastHistoryCategoryScroll = preparedHistory.categoryScroll();
        lastHistoryQuestScroll = preparedHistory.entryScroll();
        preparedCategories = preparedHistory.visibleCategories();
    }

    private void scheduleRebuild(boolean prepare) {
        rebuildQueue.request(prepare);
    }

    @Override
    public void tick() {
        super.tick();
        if (searchBox != null) {
            searchBox.tick();
        }
        if (filterDirty && System.currentTimeMillis() - filterDirtyAtMs >= 175L) {
            filterDirty = false;
            scheduleRebuild(true);
        }
        if (activeTab == Tab.HISTORY && historyState.entries() != historySourceIdentity) {
            scheduleRebuild(true);
        }
        QuestOverlayRebuildQueue.Request request = rebuildQueue.poll();
        if (request != null) {
            rebuildPreparedWidgets(request.prepare());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        persistUiState();
        Minecraft.getInstance().setScreen(null);
    }

    private void persistUiState() {
        lastTab = activeTab;
    }

    private void captureSearchState() {
        if (searchBox == null || searchBoxTab == null) {
            return;
        }
        if (searchBoxTab == Tab.HISTORY) {
            lastHistoryFilterFocused = searchBox.isFocused();
            lastHistoryFilterCursor = searchBox.getCursorPosition();
        } else {
            lastActiveFilterFocused = searchBox.isFocused();
            lastActiveFilterCursor = searchBox.getCursorPosition();
        }
    }

    private String selectedCategory() {
        return activeTab == Tab.HISTORY ? lastHistoryCategory : lastActiveCategory;
    }

    private float scaledText(float baseScale) {
        return Math.max(0.35f, baseScale * layout.scale());
    }

    private String fitTitle(String title, int maximumWidth) {
        String safeTitle = Objects.requireNonNullElse(title, "");
        if (this.font.width(safeTitle) <= maximumWidth) {
            return safeTitle;
        }
        return this.font.plainSubstrByWidth(safeTitle, Math.max(0, maximumWidth - this.font.width("…"))) + "…";
    }

    private void addSplitLines(List<DetailLine> output, String text, int width, int color) {
        for (FormattedCharSequence line : this.font.split(Component.literal(text), width)) {
            output.add(new DetailLine(line, color));
        }
    }

    private static String searchableText(QuestEntry quest) {
        return searchable(quest.category() + " " + quest.title() + " " + quest.logText()
                + " " + String.join(" ", quest.objectives()));
    }

    private static String searchable(String value) {
        String lower = Objects.requireNonNullElse(value, "").trim().toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    private static String displayCategory(String category) {
        String normalized = Objects.requireNonNullElse(category, "").trim();
        return normalized.isEmpty() ? "Général" : normalized;
    }

    private static boolean isCompletedObjective(String raw) {
        if (raw == null) {
            return false;
        }
        String text = raw.trim();
        if (text.isBlank()) {
            return false;
        }
        if (text.toLowerCase(Locale.ROOT).contains(": termin")) {
            return true;
        }
        int slash = text.lastIndexOf('/');
        if (slash <= 0 || slash >= text.length() - 1) {
            return false;
        }
        String left = trailingDigits(text.substring(0, slash));
        String right = leadingDigits(text.substring(slash + 1).stripLeading());
        try {
            return !left.isEmpty() && !right.isEmpty()
                    && Integer.parseInt(right) > 0
                    && Integer.parseInt(left) >= Integer.parseInt(right);
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static String trailingDigits(String value) {
        int index = value.length();
        while (index > 0 && Character.isDigit(value.charAt(index - 1))) {
            index--;
        }
        return value.substring(index);
    }

    private static String leadingDigits(String value) {
        int index = 0;
        while (index < value.length() && Character.isDigit(value.charAt(index))) {
            index++;
        }
        return value.substring(0, index);
    }

    private static <T> List<T> window(List<T> values, int start, int size) {
        if (values.isEmpty() || size <= 0) {
            return List.of();
        }
        int from = Math.min(Math.max(0, start), values.size());
        int to = Math.min(values.size(), from + size);
        return List.copyOf(values.subList(from, to));
    }

    private static void drawScrollbar(
            GuiGraphics graphics,
            int x,
            int y,
            int height,
            int scroll,
            int totalItems,
            int visibleItems
    ) {
        if (visibleItems <= 0 || totalItems <= visibleItems || height <= 0) {
            return;
        }
        graphics.fill(x, y, x + 4, y + height, 0x33000000);
        int maximumScroll = Math.max(1, totalItems - visibleItems);
        int thumbHeight = Math.min(height, Math.max(10, Math.round(height * (visibleItems / (float) totalItems))));
        int travel = Math.max(0, height - thumbHeight);
        int thumbY = y + Math.round(travel * (scroll / (float) maximumScroll));
        graphics.fill(x, thumbY, x + 4, thumbY + thumbHeight, 0xAA00E5FF);
        graphics.hLine(x, x + 3, thumbY, 0xCC0B0B0B);
        graphics.hLine(x, x + 3, thumbY + thumbHeight - 1, 0xCC0B0B0B);
    }

    private static QuestHistoryViewModel.View emptyHistoryView() {
        return QuestHistoryViewModel.create(
                List.of(), "", ALL_CATEGORIES, null, 0, 0,
                MAX_CAT_ROWS, MAX_QUEST_ROWS, ZoneId.systemDefault()
        );
    }
}
