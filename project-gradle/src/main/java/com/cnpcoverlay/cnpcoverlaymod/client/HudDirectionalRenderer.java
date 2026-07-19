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
        double fovRadians = Math.toRadians(minecraft.options.fov().get());
        double focalX = width / (2.0 * Math.tan(fovRadians / 2.0));
        double focalY = focalX;
        for (QuestMarkerPlanner.DesiredMarker marker : markers) {
            if (marker.type() == QuestMarkerPlanner.MarkerType.RADIUS) {
                continue;
            }
            Vec3 target = Vec3.atCenterOf(new BlockPos(marker.x(), marker.y(), marker.z()));
            Vec3 delta = target.subtract(cameraPosition);
            CameraProjection projection = project(delta, forward);
            double forwardDot = projection.forward();
            double rightDot = projection.right();
            double upDot = projection.up();
            double horizontalAngle = Math.abs(Math.atan2(rightDot, forwardDot));
            boolean previouslyVisible = visibleState.getOrDefault(marker.id(), false);
            double threshold = fovRadians / 2.0 + (previouslyVisible ? Math.toRadians(2.0) : -Math.toRadians(2.0));
            boolean visible = forwardDot > 0.01 && horizontalAngle <= Math.max(0.0, threshold)
                    && Math.abs(upDot / forwardDot) <= (height / 2.0) / focalY + 0.10;
            visibleState.put(marker.id(), visible);
            int distance = Mth.floor(minecraft.player.position().distanceTo(target));
            String label = marker.type() == QuestMarkerPlanner.MarkerType.TURN_IN ? "?" : "!" + marker.objectiveIndex();
            if (visible) {
                int x = Mth.floor(width / 2.0 + rightDot / forwardDot * focalX);
                int y = Mth.floor(height / 2.0 - upDot / forwardDot * focalY);
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

    static CameraProjection project(Vec3 delta, Vec3 forward) {
        Vec3 normalizedForward = forward.normalize();
        Vec3 right = new Vec3(-normalizedForward.z, 0.0, normalizedForward.x).normalize();
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(-1.0, 0.0, 0.0);
        }
        Vec3 up = right.cross(normalizedForward).normalize();
        return new CameraProjection(delta.dot(normalizedForward), delta.dot(right), delta.dot(up));
    }

    record CameraProjection(double forward, double right, double up) {}

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
