package com.cnpcoverlay.cnpcoverlaymod.client;

import com.cnpcoverlay.cnpcoverlaymod.client.quest.QuestMarkerPlanner;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Rendu HUD des destinations ; aucun parsing, accès disque ou réflexion dans cette boucle. */
public final class HudDirectionalRenderer {
    private static final ResourceLocation QUEST_ARROW = new ResourceLocation("cnpcoverlay", "textures/markers/quest_arrow.png");
    private static final ResourceLocation QUEST_ICON = new ResourceLocation("cnpcoverlay", "textures/markers/quest_icon.png");
    private static final ResourceLocation SIDE_QUEST_ICON = new ResourceLocation("cnpcoverlay", "textures/markers/side_quest_1.png");
    private static final Map<String, Boolean> visibleState = new HashMap<>();

    private HudDirectionalRenderer() {}

    public static void render(GuiGraphics graphics, int width, int height, List<QuestMarkerPlanner.DesiredMarker> markers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.screen != null || markers.isEmpty()) {
            return;
        }
        var camera = minecraft.gameRenderer.getMainCamera();
        Vec3 cameraPosition = camera.getPosition();
        Vec3 forward = Vec3.directionFromRotation(camera.getXRot(), camera.getYRot());
        Vec3 horizontalForward = new Vec3(forward.x, 0.0, forward.z).normalize();
        if (horizontalForward.lengthSqr() < 1.0e-6) {
            horizontalForward = new Vec3(0.0, 0.0, 1.0);
        }
        Vec3 right = new Vec3(-horizontalForward.z, 0.0, horizontalForward.x);
        double fovRadians = Math.toRadians(minecraft.options.fov().get());
        double focalX = width / (2.0 * Math.tan(fovRadians / 2.0));
        double focalY = focalX;
        for (QuestMarkerPlanner.DesiredMarker marker : markers) {
            if (marker.type() == QuestMarkerPlanner.MarkerType.RADIUS) {
                continue;
            }
            Vec3 target = Vec3.atCenterOf(new BlockPos(marker.x(), marker.y(), marker.z()));
            Vec3 delta = target.subtract(cameraPosition);
            double forwardDot = delta.dot(horizontalForward);
            double rightDot = delta.dot(right);
            double vertical = delta.y;
            double horizontalAngle = Math.abs(Math.atan2(rightDot, forwardDot));
            boolean previouslyVisible = visibleState.getOrDefault(marker.id(), false);
            double threshold = fovRadians / 2.0 + (previouslyVisible ? Math.toRadians(2.0) : -Math.toRadians(2.0));
            boolean visible = forwardDot > 0.01 && horizontalAngle <= Math.max(0.0, threshold)
                    && Math.abs(vertical / forwardDot) <= (height / 2.0) / focalY + 0.10;
            visibleState.put(marker.id(), visible);
            int distance = Mth.floor(minecraft.player.position().distanceTo(target));
            String label = marker.type() == QuestMarkerPlanner.MarkerType.TURN_IN ? "?" : "!" + marker.objectiveIndex();
            if (visible) {
                int x = Mth.floor(width / 2.0 + rightDot / forwardDot * focalX);
                int y = Mth.floor(height / 2.0 - vertical / forwardDot * focalY);
                drawIcon(graphics, x, y, marker.type() == QuestMarkerPlanner.MarkerType.TURN_IN ? QUEST_ICON : SIDE_QUEST_ICON);
                graphics.drawCenteredString(minecraft.font, label + " " + distance + " m", x, y + 10, 0xFFFFFFFF);
            } else {
                double length = Math.hypot(rightDot, forwardDot);
                double dx = length < 1.0e-6 ? 0.0 : rightDot / length;
                double dy = length < 1.0e-6 ? 1.0 : -forwardDot / length; // une cible derrière reste en bas, sans saut à 180°
                int centerX = width / 2;
                int centerY = height / 2;
                int radiusX = Math.max(48, width / 2 - 42);
                int radiusY = Math.max(42, height / 2 - 82);
                int x = centerX + Mth.floor(dx * radiusX);
                int y = centerY + Mth.floor(dy * radiusY);
                drawArrow(graphics, x, y, Math.toDegrees(Math.atan2(dy, dx)) + 90.0);
                graphics.drawCenteredString(minecraft.font, label + " " + distance + " m", x, y + 12, 0xFFFFFFFF);
            }
        }
    }

    private static void drawIcon(GuiGraphics graphics, int x, int y, ResourceLocation texture) {
        graphics.blit(texture, x - 8, y - 8, 0, 0, 16, 16, 16, 16);
    }

    private static void drawArrow(GuiGraphics graphics, int x, int y, double degrees) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees((float) degrees));
        graphics.blit(QUEST_ARROW, -8, -8, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
    }

    public static void clearSessionState() {
        visibleState.clear();
    }
}
