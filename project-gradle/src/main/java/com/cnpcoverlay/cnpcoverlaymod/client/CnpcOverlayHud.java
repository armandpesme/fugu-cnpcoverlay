package com.cnpcoverlay.cnpcoverlaymod.client;

import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestEntry;
import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestTrackerState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.List;

public final class CnpcOverlayHud {
    public static final IGuiOverlay OVERLAY = CnpcOverlayHud::render;

    private CnpcOverlayHud() {
    }

    private static void render(net.minecraftforge.client.gui.overlay.ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!OverlayState.isEnabled() || Minecraft.getInstance().options.hideGui) {
            return;
        }

        var state = QuestTrackerState.get();
        HudDirectionalRenderer.render(guiGraphics, screenWidth, screenHeight, state.getDesiredMarkers());
        QuestEntry active = state.getActiveQuest().orElse(null);
        List<QuestEntry> followed = new ArrayList<>(state.getFollowedQuests());
        List<QuestEntry> quests = new ArrayList<>();
        if (active != null) {
            quests.add(active);
        }
        for (QuestEntry q : followed) {
            if (active != null && active.id().equals(q.id())) {
                continue;
            }
            quests.add(q);
        }

        if (quests.isEmpty()) {
            return;
        }

        int maxQuests = 3;
        if (quests.size() > maxQuests) {
            quests = new ArrayList<>(quests.subList(0, maxQuests));
        }

        var mc = Minecraft.getInstance();
        var font = mc.font;

        // Style: bleu turquoise + transparence
        // Très translucide (alpha faible)
        int bg = 0x1A00C8D7;     // ARGB
        int bg2 = 0x22001E22;
        int border = 0x6600E5FF;
        int text = 0xFFEFFFFF;
        int muted = 0xFFBFEFF2;
        int green = 0xFF55FF55;

        // Construire les lignes: titre + objectifs (détails)
        class HudLine {
            final String left;
            final String right;
            final boolean objective;
            final boolean completed;

            HudLine(String left, String right, boolean objective, boolean completed) {
                this.left = left;
                this.right = right;
                this.objective = objective;
                this.completed = completed;
            }
        }

        List<HudLine> lines = new ArrayList<>();
        for (QuestEntry quest : quests) {
            float p = clamp01(quest.progress());
            String percent = Math.round(p * 100.0f) + "%";
            String marker = (active != null && active.id().equals(quest.id())) ? "> " : "";
            boolean questDone = p >= 0.999f;
            lines.add(new HudLine(marker + quest.title(), percent, false, questDone));

            var turnInInstruction = quest.turnInInstruction();
            if (turnInInstruction.isPresent()) {
                lines.add(new HudLine("  " + turnInInstruction.get(), "", false, true));
            } else {
                for (String raw : quest.objectives()) {
                    if (raw == null || raw.isBlank()) {
                        continue;
                    }
                    if (isGenericObjective(raw)) {
                        continue;
                    }
                    String formatted = formatObjectiveStatus(raw);
                    boolean done = formatted.startsWith("✓");
                    lines.add(new HudLine("  " + formatted, "", true, done));
                }
            }
        }

        int headerH = 11;
        int lineH = 9;

        // Mesurer largeur/hauteur non-scalées
        int paddingX = 8;
        int contentW = 0;
        for (HudLine l : lines) {
            int lw = font.width(l.left == null ? "" : l.left);
            int rw = font.width(l.right == null ? "" : l.right);
            contentW = Math.max(contentW, lw + (rw > 0 ? (6 + rw) : 0));
        }

        int w = Math.max(220, Math.min(320, contentW + (paddingX * 2)));
        int h = headerH + (lines.size() * lineH) + 10;

        // Pas d'auto-scale (ne pas rétrécir): on laisse grandir vers le bas.
        float scale = 0.52f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);

        int sw = (int) (screenWidth / scale);
        int sh = (int) (screenHeight / scale);

        // Tronquer uniquement si ça dépasse l'écran (et éviter d'empiéter sur la zone du chat).
        int chatReservePx = Math.max(90, getChatHeightSafePx(mc));
        int safeBottomPx = Math.max(0, screenHeight - 6 - chatReservePx);
        int safeBottomUnscaled = Math.max(0, Math.round(safeBottomPx / scale));
        int maxBottom = Math.min(sh - 6, safeBottomUnscaled);
        int maxPanelH = Math.max(0, maxBottom - 6);
        int maxLines = Math.max(1, (maxPanelH - headerH - 10) / lineH);
        if (lines.size() > maxLines) {
            lines = new ArrayList<>(lines.subList(0, Math.max(1, maxLines - 1)));
            lines.add(new HudLine("…", "", true, false));
            h = headerH + (lines.size() * lineH) + 10;
        }

        // Ancré en haut à droite (idéalement sous la minimap), position configurable.
        int marginRight = Math.round(OverlayState.getHudRightPx() / scale);
        int x = sw - marginRight - w;
        int y = Math.round(OverlayState.getHudTopPx() / scale);
        y = Math.max(6, y);

        // clamp gauche/droite
        x = Math.max(6, Math.min(sw - 6 - w, x));

        // clamp bas "safe" (avant zone chat)
        if (y + h > maxBottom) {
            y = Math.max(6, maxBottom - h);
        }

        guiGraphics.fill(x, y, x + w, y + h, bg);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg2);
        guiGraphics.hLine(x, x + w - 1, y, border);
        guiGraphics.hLine(x, x + w - 1, y + h - 1, border);
        guiGraphics.vLine(x, y, y + h - 1, border);
        guiGraphics.vLine(x + w - 1, y, y + h - 1, border);

        guiGraphics.drawString(font, "Qu\u00eates", x + 8, y + 3, muted, false);

        int cy = y + headerH;
        for (HudLine l : lines) {
            String leftText = l.left == null ? "" : l.left;
            String rightText = l.right == null ? "" : l.right;

            int color;
            if (l.objective) {
                color = l.completed ? green : muted;
            } else {
                color = l.completed ? green : text;
            }
            String leftDraw = truncateToWidth(font, leftText, w - 16 - (rightText.isEmpty() ? 0 : (font.width(rightText) + 6)));
            guiGraphics.drawString(font, leftDraw, x + 8, cy, color, false);

            if (!rightText.isEmpty()) {
                guiGraphics.drawString(font, rightText, x + w - 8 - font.width(rightText), cy, text, false);
            }

            cy += lineH;
        }

        guiGraphics.pose().popPose();
    }

    private static int getChatHeightSafePx(Minecraft mc) {
        try {
            // ChatComponent#getHeight est en pixels écran.
            return Math.max(0, mc.gui.getChat().getHeight());
        } catch (Throwable ignored) {
            return 120;
        }
    }

    private static String formatObjectiveStatus(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isBlank()) {
            return "";
        }

        String lower = t.toLowerCase();
        if (lower.contains(": termin")) {
            return "✓ " + t;
        }

        // Si on voit x/y et que x >= y, on coche.
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
                    if (b > 0 && a >= b) {
                        return "✓ " + t;
                    }
                } catch (NumberFormatException ignored) {
                    // ignore
                }
            }
        }

        return "• " + t;
    }

    private static float clamp01(float v) {
        return v < 0 ? 0 : (v > 1 ? 1 : v);
    }

    private static boolean isGenericObjective(String s) {
        if (s == null) {
            return true;
        }
        String t = s.trim();
        if (t.isBlank()) {
            return true;
        }
        String lower = t.toLowerCase();
        return lower.startsWith("objectif") || lower.startsWith("objectifs") || lower.startsWith("objective") || lower.startsWith("objectives");
    }

    private static String truncateToWidth(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        String out = text;
        while (font.width(out) > maxWidth && out.length() > 3) {
            out = out.substring(0, out.length() - 2) + "…";
        }
        return out;
    }
}
