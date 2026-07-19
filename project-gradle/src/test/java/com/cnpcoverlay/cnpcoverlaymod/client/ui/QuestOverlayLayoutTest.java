package com.cnpcoverlay.cnpcoverlaymod.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class QuestOverlayLayoutTest {
    @Test
    void preservesTheVirtualPanelAtNormalResolution() {
        QuestOverlayLayout layout = QuestOverlayLayout.forScreen(1920, 1080);

        assertEquals(1.0f, layout.scale());
        assertEquals(new QuestOverlayLayout.Rect(770, 410, 380, 260), layout.panel());
        assertEquals(new QuestOverlayLayout.Rect(974, 420, 76, 14),
                layout.rect(204, 10, 76, 14));
    }

    @Test
    void keepsPanelTabsCloseButtonAndScrollZonesInside320By240() {
        QuestOverlayLayout layout = QuestOverlayLayout.forScreen(320, 240);
        var panel = layout.panel();
        var activeTab = layout.rect(204, 10, 76, 14);
        var historyTab = layout.rect(286, 10, 84, 14);
        var close = layout.rect(310, 236, 60, 14);
        var categories = layout.rect(10, 68, 110, 64);
        var quests = layout.rect(128, 68, 232, 64);
        var details = layout.rect(128, 142, 232, 80);

        assertTrue(layout.scale() < 1.0f);
        assertContained(panel, 320, 240);
        assertContained(activeTab, 320, 240);
        assertContained(historyTab, 320, 240);
        assertContained(close, 320, 240);
        assertContained(categories, 320, 240);
        assertContained(quests, 320, 240);
        assertContained(details, 320, 240);
    }

    @Test
    void clampsMappedControlsEvenOnDegenerateSurfaces() {
        QuestOverlayLayout layout = QuestOverlayLayout.forScreen(1, 1);

        assertContained(layout.panel(), 1, 1);
        assertContained(layout.rect(310, 236, 60, 14), 1, 1);
    }

    private static void assertContained(QuestOverlayLayout.Rect rect, int width, int height) {
        assertTrue(rect.x() >= 0);
        assertTrue(rect.y() >= 0);
        assertTrue(rect.right() <= width);
        assertTrue(rect.bottom() <= height);
    }
}
