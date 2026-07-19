package com.cnpcoverlay.cnpcoverlaymod.client.ui;

import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestEntry;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestTrackerState;
import com.cnpcoverlay.cnpcoverlaymod.client.ui.widgets.FlatButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CnpcOverlayScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 260;

    private static final int ROW_H = 14;
    private static final int ROW_GAP = 2;
    private static final int ROW_STEP = ROW_H + ROW_GAP;
    private static final int MAX_QUEST_ROWS = 4;
    private static final int MAX_CAT_ROWS = 8;

    // garder l'état UI entre rebuilds (ex: molette)
    private static String LAST_SELECTED_CATEGORY = null;
    private static String LAST_SELECTED_QUEST_ID = null;
    private static int LAST_CAT_SCROLL = 0;
    private static int LAST_QUEST_SCROLL = 0;
    private static int LAST_DETAIL_SCROLL = 0;
    private static String LAST_FILTER = "";
    private static boolean LAST_FILTER_FOCUSED = false;
    private static int LAST_FILTER_CURSOR = 0;

    private final QuestTrackerState questState = QuestTrackerState.get();

    private EditBox searchBox;

    private boolean filterDirty = false;
    private long filterDirtyAtMs = 0L;

    private String selectedCategory = null;
    private String selectedQuestId = null;

    private int catScroll = 0;
    private int questScroll = 0;
    private int detailScroll = 0;

    private int lastDetailTotalLines = 0;
    private int lastDetailVisibleLines = 0;

    private final List<FlatButton> categoryButtons = new ArrayList<>();
    private final List<FlatButton> questButtons = new ArrayList<>();
    private final List<FlatButton> followButtons = new ArrayList<>();

    private static final int COLOR_TEXT = 0xFFEFFFFF;
    private static final int COLOR_MUTED = 0xFFBFEFF2;
    private static final int COLOR_GREEN = 0xFF55FF55;

    private record DetailLine(FormattedCharSequence text, int color) {
    }

    public CnpcOverlayScreen() {
        super(Component.literal("CNPC Overlay"));
    }

    @Override
    protected void init() {
        this.categoryButtons.clear();
        this.questButtons.clear();
        this.followButtons.clear();

        // garantir un refresh récent (1x) à l'ouverture
        var player = Minecraft.getInstance().player;
        if (player != null) {
            questState.refresh(player);
        }

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        // restaurer l'état UI
        if (selectedCategory == null) {
            selectedCategory = LAST_SELECTED_CATEGORY;
        }
        if (selectedQuestId == null) {
            selectedQuestId = LAST_SELECTED_QUEST_ID;
        }
        catScroll = LAST_CAT_SCROLL;
        questScroll = LAST_QUEST_SCROLL;
        detailScroll = LAST_DETAIL_SCROLL;

        // Constantes layout (éviter tout chevauchement)
        int controlsY = top + 32;
        int listLabelY = top + 58;
        int listTopY = top + 68;

        // Recherche (filtre)
        int searchX = left + 10;
        int searchW = PANEL_WIDTH - 20;
        this.searchBox = this.addRenderableWidget(new EditBox(this.font, searchX, controlsY, searchW, 14, Component.literal("Recherche")));
        this.searchBox.setValue(LAST_FILTER);
        this.searchBox.setResponder(v -> {
            LAST_FILTER = v == null ? "" : v;
            LAST_FILTER_FOCUSED = true;
            LAST_FILTER_CURSOR = this.searchBox.getCursorPosition();
            this.filterDirty = true;
            this.filterDirtyAtMs = System.currentTimeMillis();
        });
        if (LAST_FILTER_FOCUSED) {
            this.setFocused(this.searchBox);
            this.searchBox.setFocused(true);
            this.searchBox.setCursorPosition(Math.min(LAST_FILTER_CURSOR, this.searchBox.getValue().length()));
        }


        // Appliquer filtre
        String filter = LAST_FILTER == null ? "" : LAST_FILTER.trim();
        List<QuestEntry> filteredQuests = questState.getQuests();
        if (!filter.isBlank()) {
            String needle = filter.toLowerCase();
            filteredQuests = questState.getQuests().stream().filter(q -> matchesFilter(q, needle)).toList();
        }

        // Construire catégories -> quêtes
        Map<String, List<QuestEntry>> byCategory = buildCategoryMap(filteredQuests);
        if (selectedCategory == null || !byCategory.containsKey(selectedCategory)) {
            selectedCategory = byCategory.keySet().stream().findFirst().orElse("Général");
        }

        // Clamp scrolls
        catScroll = clampScroll(catScroll, byCategory.size(), MAX_CAT_ROWS);
        List<QuestEntry> allInCategory = byCategory.getOrDefault(selectedCategory, List.of());
        questScroll = clampScroll(questScroll, allInCategory.size(), MAX_QUEST_ROWS);

        // Colonne catégories
        int catX = left + 10;
        int catY = listTopY;
        int catW = 110;

        List<String> categories = new ArrayList<>(byCategory.keySet());
        for (int row = 0; row < Math.min(MAX_CAT_ROWS, categories.size()); row++) {
            int idx = catScroll + row;
            if (idx >= categories.size()) {
                break;
            }
            String cat = categories.get(idx);
            int y = catY + (row * ROW_STEP);
            FlatButton b = this.addRenderableWidget(new FlatButton(
                    catX,
                    y,
                    catW,
                    ROW_H,
                    Component.literal(cat),
                    btn -> {
                        selectedCategory = cat;
                        selectedQuestId = null;
                        questScroll = 0;
                        detailScroll = 0;
                        rebuild();
                    },
                    () -> cat.equals(selectedCategory),
                        0.85f
            ));
            categoryButtons.add(b);
        }

        // Liste quêtes (dans la catégorie)
        List<QuestEntry> list = allInCategory;
        if (selectedQuestId == null) {
            selectedQuestId = list.stream().findFirst().map(QuestEntry::id).orElse(null);
        }
        int qX = left + 128;
        int qY = listTopY;
        int followW = 16;
        int qW = PANEL_WIDTH - 20 - (qX - left) - followW - 4;
        for (int row = 0; row < Math.min(MAX_QUEST_ROWS, list.size()); row++) {
            int idx = questScroll + row;
            if (idx >= list.size()) {
                break;
            }
            QuestEntry q = list.get(idx);
            int y = qY + (row * ROW_STEP);

            FlatButton follow = this.addRenderableWidget(new FlatButton(
                    qX,
                    y,
                    followW,
                    ROW_H,
                    Component.literal(q.isFollowed() ? "✓" : ""),
                    btn -> {
                        questState.setFollowed(q.id(), !q.isFollowed());
                        if (!q.isFollowed()) {
                            selectedQuestId = q.id();
                            questState.setActiveQuest(q.id());
                        }
                        rebuild();
                    },
                    q::isFollowed,
                        0.85f
            ));
            followButtons.add(follow);

            FlatButton qb = this.addRenderableWidget(new FlatButton(
                    qX + followW + 4,
                    y,
                    qW,
                    ROW_H,
                    Component.literal(q.title()),
                    btn -> {
                        selectedQuestId = q.id();
                        detailScroll = 0;
                        if (q.isFollowed()) {
                            questState.setActiveQuest(q.id());
                        }
                        rebuild();
                    },
                    () -> q.id().equals(selectedQuestId),
                        0.85f
            ));
            questButtons.add(qb);
        }

        this.addRenderableWidget(new FlatButton(
            left + PANEL_WIDTH - 70 - 10,
            top + PANEL_HEIGHT - 24 - 10,
            60,
            14,
            Component.literal("Fermer"),
            btn -> onClose(),
            0.70f
        ));

        persistUiState();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        int right = left + PANEL_WIDTH;
        int bottom = top + PANEL_HEIGHT;

        // Fond panneau (inspiré UI type CustomNPC: sombre + bordure claire)
        guiGraphics.fill(left, top, right, bottom, 0xCC0B0B0B);
        guiGraphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0xCC1A1A1A);

        // Bordure
        int border = 0xFFB8B8B8;
        guiGraphics.hLine(left, right - 1, top, border);
        guiGraphics.hLine(left, right - 1, bottom - 1, border);
        guiGraphics.vLine(left, top, bottom - 1, border);
        guiGraphics.vLine(right - 1, top, bottom - 1, border);

        // Header
        guiGraphics.fill(left + 6, top + 6, right - 6, top + 28, 0xFF111111);
        guiGraphics.drawString(this.font, this.title, left + 12, top + 13, 0xFFECECEC, false);

        // Accent turquoise/transparence pour rappel HUD
        int cyan = 0xAA00E5FF;
        guiGraphics.hLine(left + 6, right - 7, top + 30, cyan);

        int controlsY = top + 32;
        int listLabelY = top + 58;
        int listTopY = top + 68;

        guiGraphics.drawString(this.font, "Catégories", left + 12, listLabelY, COLOR_MUTED, false);
        guiGraphics.drawString(this.font, "Quêtes", left + 128, listLabelY, COLOR_MUTED, false);
        float rs = 0.75f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(rs, rs, 1.0f);
        guiGraphics.drawString(this.font, "Recherche", Math.round((left + 140) / rs), Math.round((controlsY - 9) / rs), COLOR_MUTED, false);
        guiGraphics.pose().popPose();

        // Zones UI (doivent matcher init())
        int catX = left + 10;
        int catY = listTopY;
        int catW = 110;
        int catH = MAX_CAT_ROWS * ROW_STEP;

        int qX = left + 128;
        int qY = listTopY;
        int followW = 18;
        int qBtnW = PANEL_WIDTH - 20 - (qX - left) - followW - 4;
        int listW = followW + 4 + qBtnW;
        int qH = MAX_QUEST_ROWS * ROW_STEP;

        // Panneau détails (droite bas)
        int detailsX = left + 128;
        int detailsY = listTopY + (MAX_QUEST_ROWS * ROW_STEP) + 10;
        int detailsW = PANEL_WIDTH - 20 - (detailsX - left);
        int detailsH = top + PANEL_HEIGHT - 10 - detailsY;
        int detailsBg = 0x661A1A1A;
        int detailsHeaderBg = 0x80202020;
        int accent = 0x6600C8D7;
        guiGraphics.fill(detailsX, detailsY, detailsX + detailsW, detailsY + detailsH, detailsBg);
        guiGraphics.fill(detailsX, detailsY, detailsX + detailsW, detailsY + 16, detailsHeaderBg);
        guiGraphics.hLine(detailsX, detailsX + detailsW - 1, detailsY, accent);
        guiGraphics.hLine(detailsX, detailsX + detailsW - 1, detailsY + 16, 0x33111111);

        QuestEntry selected = findSelected();
        if (selected != null) {
            boolean questDone = selected.progress() >= 0.999f;

            // Texte Détails légèrement plus grand (compact mais plus lisible)
            float s = 0.55f;
            int scaledDetailsX = Math.round(detailsX / s);
            int scaledDetailsY = Math.round(detailsY / s);
            int scaledDetailsW = Math.round(detailsW / s);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(s, s, 1.0f);

            guiGraphics.drawString(this.font, "Détails", scaledDetailsX + 8, scaledDetailsY + 4, COLOR_MUTED, false);
            guiGraphics.drawString(this.font, selected.title(), scaledDetailsX + 62, scaledDetailsY + 4, questDone ? COLOR_GREEN : COLOR_TEXT, false);

            List<DetailLine> detailLines = buildDetailLines(selected, scaledDetailsW - 16);
            int textStartY = detailsY + 18;
            int scaledTextStartY = Math.round(textStartY / s);
            int lineH = 14;
            int lineHPx = Math.max(1, Math.round(lineH * s));
            int visibleH = Math.max(0, detailsH - 18 - 16);
            int visibleLines = Math.max(0, visibleH / lineHPx);
            this.lastDetailTotalLines = detailLines.size();
            this.lastDetailVisibleLines = visibleLines;
            this.detailScroll = clampScroll(this.detailScroll, this.lastDetailTotalLines, this.lastDetailVisibleLines);

            int y = scaledTextStartY;
            for (int i = 0; i < visibleLines; i++) {
                int idx = detailScroll + i;
                if (idx >= detailLines.size()) {
                    break;
                }
                DetailLine dl = detailLines.get(idx);
                guiGraphics.drawString(this.font, dl.text(), scaledDetailsX + 8, y, dl.color(), false);
                y += lineH;
            }

            guiGraphics.pose().popPose();

            // Scrollbar visuelle (track + thumb) en pixels écran
            int trackX = detailsX + detailsW - 6;
            int trackY = textStartY;
            int trackH = visibleLines * lineHPx;
            drawScrollbar(guiGraphics, trackX, trackY, trackH, detailScroll, lastDetailTotalLines, lastDetailVisibleLines);

            // Progression (demi-taille + vert si terminé)
            String percent = Math.round(selected.progress() * 100.0f) + "%";
            guiGraphics.pose().pushPose();
            guiGraphics.pose().scale(s, s, 1.0f);
            int scaledProgY = Math.round((detailsY + detailsH - 14) / s);
            guiGraphics.drawString(this.font, "Progression: " + percent, scaledDetailsX + 8, scaledProgY, questDone ? COLOR_GREEN : COLOR_MUTED, false);
            guiGraphics.pose().popPose();
        } else {
            guiGraphics.drawString(this.font, "Détails", detailsX + 8, detailsY + 4, COLOR_MUTED, false);
            guiGraphics.drawString(this.font, "Aucune quête", detailsX + 8, detailsY + 22, 0xFFBFBFBF, false);
        }

        // Scrollbars colonnes (Catégories/Quêtes)
        Map<String, List<QuestEntry>> byCategory = buildCategoryMap(questState.getQuests());
        List<String> categories = new ArrayList<>(byCategory.keySet());
        List<QuestEntry> inCat = byCategory.getOrDefault(selectedCategory, List.of());

        drawScrollbar(guiGraphics, catX + catW - 6, catY, catH, catScroll, categories.size(), MAX_CAT_ROWS);
        drawScrollbar(guiGraphics, qX + listW - 6, qY, qH, questScroll, inCat.size(), MAX_QUEST_ROWS);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }


    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        int catX = left + 10;
        int catY = top + 68;
        int catW = 110;
        int catH = MAX_CAT_ROWS * ROW_STEP;

        int qX = left + 128;
        int qY = top + 68;
        int qW = PANEL_WIDTH - 20 - (qX - left);
        int qH = MAX_QUEST_ROWS * ROW_STEP;

        int detailsX = left + 128;
        int detailsY = top + 68 + (MAX_QUEST_ROWS * ROW_STEP) + 10;
        int detailsW = PANEL_WIDTH - 20 - (detailsX - left);
        int detailsH = top + PANEL_HEIGHT - 10 - detailsY;

        int dir = (delta > 0) ? -1 : 1;

        // détails: scroll sans rebuild
        if (isIn(mouseX, mouseY, detailsX, detailsY, detailsW, detailsH)) {
            detailScroll = clampScroll(detailScroll + dir, lastDetailTotalLines, lastDetailVisibleLines);
            LAST_DETAIL_SCROLL = detailScroll;
            return true;
        }

        // catégories: scroll + rebuild widgets
        if (isIn(mouseX, mouseY, catX, catY, catW, catH)) {
            catScroll = catScroll + dir;
            LAST_CAT_SCROLL = catScroll;
            rebuild();
            return true;
        }

        // quêtes: scroll + rebuild widgets
        if (isIn(mouseX, mouseY, qX, qY, qW, qH)) {
            questScroll = questScroll + dir;
            LAST_QUEST_SCROLL = questScroll;
            rebuild();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }

    private String activeMarker(QuestEntry quest) {
        return questState.getActiveQuest().map(a -> a.id().equals(quest.id()) ? "> " : "  ").orElse("  ");
    }

    private Map<String, List<QuestEntry>> buildCategoryMap(List<QuestEntry> entries) {
        Map<String, List<QuestEntry>> map = new LinkedHashMap<>();
        entries.stream()
                .sorted(Comparator.comparing(QuestEntry::category).thenComparing(QuestEntry::title))
                .forEach(q -> map.computeIfAbsent(q.category().isBlank() ? "Général" : q.category(), k -> new ArrayList<>()).add(q));
        if (map.isEmpty()) {
            map.put("Général", List.of());
        }
        return map;
    }

    private QuestEntry findSelected() {
        if (selectedQuestId == null) {
            return questState.getActiveQuest().orElse(null);
        }
        for (QuestEntry q : questState.getQuests()) {
            if (q.id().equals(selectedQuestId)) {
                return q;
            }
        }
        return null;
    }

    private List<DetailLine> buildDetailLines(QuestEntry q, int width) {
        List<DetailLine> lines = new ArrayList<>();

        if (q == null) {
            return lines;
        }

        String displayLogText = q.displayLogText();
        if (!displayLogText.isBlank()) {
            addSplitLines(lines, displayLogText, width, COLOR_MUTED);
            lines.add(new DetailLine(FormattedCharSequence.EMPTY, COLOR_MUTED));
        }

        addSplitLines(lines, "Objectifs:", width, COLOR_MUTED);
        for (String obj : q.objectives()) {
            if (obj == null || obj.isBlank()) {
                continue;
            }
            boolean done = isCompletedObjective(obj);
            String prefix = done ? "- ✓ " : "- ";
            addSplitLines(lines, prefix + obj, width, done ? COLOR_GREEN : COLOR_MUTED);
        }

        return lines;
    }

    private void addSplitLines(List<DetailLine> out, String text, int width, int color) {
        for (FormattedCharSequence seq : this.font.split(Component.literal(text), width)) {
            out.add(new DetailLine(seq, color));
        }
    }

    private static boolean isCompletedObjective(String raw) {
        if (raw == null) {
            return false;
        }
        String t = raw.trim();
        if (t.isBlank()) {
            return false;
        }

        String lower = t.toLowerCase();
        if (lower.contains(": termin")) {
            return true;
        }

        // x/y avec x >= y => terminé
        int slash = t.lastIndexOf('/');
        if (slash > 0 && slash < t.length() - 1) {
            int leftEnd = slash;
            int rightStart = slash + 1;

            int i = leftEnd - 1;
            while (i >= 0 && Character.isDigit(t.charAt(i))) {
                i--;
            }
            int leftStart = i + 1;

            int j = rightStart;
            while (j < t.length() && Character.isWhitespace(t.charAt(j))) {
                j++;
            }
            int k = j;
            while (k < t.length() && Character.isDigit(t.charAt(k))) {
                k++;
            }

            if (leftStart < leftEnd && j < k) {
                try {
                    int a = Integer.parseInt(t.substring(leftStart, leftEnd).trim());
                    int b = Integer.parseInt(t.substring(j, k).trim());
                    return b > 0 && a >= b;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }

        return false;
    }

    private static void drawScrollbar(GuiGraphics g, int x, int y, int h, int scroll, int totalItems, int visibleItems) {
        if (visibleItems <= 0 || totalItems <= visibleItems || h <= 0) {
            return;
        }

        int trackW = 4;
        int trackBg = 0x33000000;
        int thumbBg = 0xAA00E5FF;
        int thumbBorder = 0xCC0B0B0B;

        g.fill(x, y, x + trackW, y + h, trackBg);

        int maxScroll = Math.max(1, totalItems - visibleItems);
        int thumbH = Math.max(10, Math.round((h * (visibleItems / (float) totalItems))));
        thumbH = Math.min(h, thumbH);

        int travel = Math.max(0, h - thumbH);
        int thumbY = y + Math.round(travel * (scroll / (float) maxScroll));

        g.fill(x, thumbY, x + trackW, thumbY + thumbH, thumbBg);
        g.hLine(x, x + trackW - 1, thumbY, thumbBorder);
        g.hLine(x, x + trackW - 1, thumbY + thumbH - 1, thumbBorder);
    }

    private static int clampScroll(int value, int totalItems, int visibleItems) {
        if (visibleItems <= 0) {
            return 0;
        }
        int max = Math.max(0, totalItems - visibleItems);
        return Math.max(0, Math.min(max, value));
    }

    private static boolean isIn(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < (x + w) && my >= y && my < (y + h);
    }

    private void persistUiState() {
        LAST_SELECTED_CATEGORY = selectedCategory;
        LAST_SELECTED_QUEST_ID = selectedQuestId;
        LAST_CAT_SCROLL = catScroll;
        LAST_QUEST_SCROLL = questScroll;
        LAST_DETAIL_SCROLL = detailScroll;
        LAST_FILTER_FOCUSED = this.searchBox != null && this.searchBox.isFocused();
        LAST_FILTER_CURSOR = this.searchBox != null ? this.searchBox.getCursorPosition() : 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (searchBox != null) {
            searchBox.tick();
        }
        if (filterDirty && (System.currentTimeMillis() - filterDirtyAtMs) > 175L) {
            filterDirty = false;
            rebuild();
        }
    }

    private static boolean matchesFilter(QuestEntry q, String needleLower) {
        if (q == null || needleLower == null || needleLower.isBlank()) {
            return true;
        }
        String hay = (
                safeLower(q.category()) + " " +
                safeLower(q.title()) + " " +
                safeLower(q.logText()) + " " +
                safeLower(String.join(" ", q.objectives()))
        );
        return hay.contains(needleLower);
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase();
    }

    private void rebuild() {
        persistUiState();
        var mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new CnpcOverlayScreen()));
    }
}
