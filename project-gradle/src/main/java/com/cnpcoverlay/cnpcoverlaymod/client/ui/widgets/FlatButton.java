package com.cnpcoverlay.cnpcoverlaymod.client.ui.widgets;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.function.BooleanSupplier;

public final class FlatButton extends AbstractButton {
    @FunctionalInterface
    public interface PressAction {
        void onPress(FlatButton button);
    }

    private final PressAction onPress;
    private final BooleanSupplier selected;
    private final float textScale;

    public FlatButton(int x, int y, int width, int height, Component message, PressAction onPress) {
        this(x, y, width, height, message, onPress, () -> false, 1.0f);
    }

    public FlatButton(int x, int y, int width, int height, Component message, PressAction onPress, BooleanSupplier selected) {
        this(x, y, width, height, message, onPress, selected, 1.0f);
    }

    public FlatButton(int x, int y, int width, int height, Component message, PressAction onPress, float textScale) {
        this(x, y, width, height, message, onPress, () -> false, textScale);
    }

    public FlatButton(int x, int y, int width, int height, Component message, PressAction onPress, BooleanSupplier selected, float textScale) {
        super(x, y, width, height, message);
        this.onPress = Objects.requireNonNull(onPress);
        this.selected = Objects.requireNonNullElse(selected, () -> false);
        this.textScale = (textScale <= 0.0f) ? 1.0f : textScale;
    }

    @Override
    public void onPress() {
        if (!this.active) {
            return;
        }
        onPress.onPress(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        boolean hovered = this.isHoveredOrFocused();
        boolean selected = this.selected.getAsBoolean();

        int border = 0xAA00E5FF;
        int bg = selected ? 0x5520F5FF : 0x40101010;
        int bg2 = hovered ? 0x80202020 : 0x661A1A1A;

        if (!this.active) {
            border = 0x553A3A3A;
            bg = 0x220B0B0B;
            bg2 = 0x22101010;
        }

        g.fill(x, y, x + w, y + h, bg);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, bg2);

        g.hLine(x, x + w - 1, y, border);
        g.hLine(x, x + w - 1, y + h - 1, border);
        g.vLine(x, y, y + h - 1, border);
        g.vLine(x + w - 1, y, y + h - 1, border);

        int textColor = this.active ? 0xFFEFFFFF : 0xFF909090;
        var font = Minecraft.getInstance().font;

        // Texte éventuellement plus petit, sans changer la hitbox du bouton.
        float s = this.textScale;
        g.pose().pushPose();
        g.pose().scale(s, s, 1.0f);

        int cx = Math.round((x + (w / 2.0f)) / s);
        int ty = Math.round((y + (h - font.lineHeight) / 2.0f) / s);
        g.drawCenteredString(font, this.getMessage(), cx, ty, textColor);

        g.pose().popPose();
    }
}
