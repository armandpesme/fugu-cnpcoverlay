package com.cnpcoverlay.cnpcoverlaymod.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class OverlayStateTest {
    @Test
    void remainsEnabledAfterEveryLegacyMutation() {
        OverlayState.setEnabled(false);
        assertTrue(OverlayState.isEnabled());

        assertTrue(OverlayState.toggle());
        assertTrue(OverlayState.isEnabled());
    }
}
