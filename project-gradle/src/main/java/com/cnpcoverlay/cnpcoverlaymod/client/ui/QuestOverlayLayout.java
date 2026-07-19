package com.cnpcoverlay.cnpcoverlaymod.client.ui;

/**
 * Projection pure du panneau virtuel 380 x 260 vers la surface GUI disponible.
 */
final class QuestOverlayLayout {
    static final int VIRTUAL_WIDTH = 380;
    static final int VIRTUAL_HEIGHT = 260;
    private static final int PREFERRED_MARGIN = 2;

    private final int left;
    private final int top;
    private final int width;
    private final int height;
    private final float scale;

    private QuestOverlayLayout(int left, int top, int width, int height, float scale) {
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.scale = scale;
    }

    static QuestOverlayLayout forScreen(int screenWidth, int screenHeight) {
        int safeWidth = Math.max(1, screenWidth);
        int safeHeight = Math.max(1, screenHeight);
        int horizontalMargin = safeWidth >= PREFERRED_MARGIN * 2 ? PREFERRED_MARGIN : 0;
        int verticalMargin = safeHeight >= PREFERRED_MARGIN * 2 ? PREFERRED_MARGIN : 0;
        int availableWidth = Math.max(1, safeWidth - horizontalMargin * 2);
        int availableHeight = Math.max(1, safeHeight - verticalMargin * 2);
        float scale = Math.min(1.0f, Math.min(
                availableWidth / (float) VIRTUAL_WIDTH,
                availableHeight / (float) VIRTUAL_HEIGHT
        ));
        int panelWidth = Math.max(1, Math.round(VIRTUAL_WIDTH * scale));
        int panelHeight = Math.max(1, Math.round(VIRTUAL_HEIGHT * scale));
        int left = Math.max(0, (safeWidth - panelWidth) / 2);
        int top = Math.max(0, (safeHeight - panelHeight) / 2);
        return new QuestOverlayLayout(left, top, panelWidth, panelHeight, scale);
    }

    Rect panel() {
        return new Rect(left, top, width, height);
    }

    float scale() {
        return scale;
    }

    int x(int virtualOffset) {
        int mapped = left + Math.round(virtualOffset * scale);
        return Math.max(left, Math.min(left + width - 1, mapped));
    }

    int y(int virtualOffset) {
        int mapped = top + Math.round(virtualOffset * scale);
        return Math.max(top, Math.min(top + height - 1, mapped));
    }

    int width(int virtualWidth) {
        return Math.max(1, Math.round(virtualWidth * scale));
    }

    int height(int virtualHeight) {
        return Math.max(1, Math.round(virtualHeight * scale));
    }

    Rect rect(int virtualX, int virtualY, int virtualWidth, int virtualHeight) {
        int rectX = x(virtualX);
        int rectY = y(virtualY);
        int rectWidth = Math.min(width(virtualWidth), left + width - rectX);
        int rectHeight = Math.min(height(virtualHeight), top + height - rectY);
        return new Rect(rectX, rectY, Math.max(1, rectWidth), Math.max(1, rectHeight));
    }

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < right() && pointY >= y && pointY < bottom();
        }
    }
}
