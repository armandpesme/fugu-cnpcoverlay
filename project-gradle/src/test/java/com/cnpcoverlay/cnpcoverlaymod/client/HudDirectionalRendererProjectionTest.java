package com.cnpcoverlay.cnpcoverlaymod.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class HudDirectionalRendererProjectionTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void projectsTargetsAboveAndBelowUsingTheCameraUpAxis() {
        HudDirectionalRenderer.CameraProjection above = HudDirectionalRenderer.project(
                new Vec3(0.0, 10.0, 10.0), new Vec3(0.0, 0.0, 1.0));
        HudDirectionalRenderer.CameraProjection below = HudDirectionalRenderer.project(
                new Vec3(0.0, -10.0, 10.0), new Vec3(0.0, 0.0, 1.0));

        assertEquals(10.0, above.up(), EPSILON);
        assertEquals(-10.0, below.up(), EPSILON);
        assertEquals(10.0, above.forward(), EPSILON);
        assertEquals(10.0, below.forward(), EPSILON);
    }

    @Test
    void keepsTargetOnScreenCenterWhenCameraLooksUp() {
        Vec3 forward = new Vec3(0.0, Math.sqrt(0.5), Math.sqrt(0.5));

        HudDirectionalRenderer.CameraProjection projection = HudDirectionalRenderer.project(forward.scale(20.0), forward);

        assertEquals(20.0, projection.forward(), EPSILON);
        assertEquals(0.0, projection.right(), EPSILON);
        assertEquals(0.0, projection.up(), EPSILON);
    }

    @Test
    void keepsTargetOnScreenCenterWhenCameraLooksDown() {
        Vec3 forward = new Vec3(0.0, -Math.sqrt(0.5), Math.sqrt(0.5));

        HudDirectionalRenderer.CameraProjection projection = HudDirectionalRenderer.project(forward.scale(20.0), forward);

        assertEquals(20.0, projection.forward(), EPSILON);
        assertEquals(0.0, projection.right(), EPSILON);
        assertEquals(0.0, projection.up(), EPSILON);
    }
}
