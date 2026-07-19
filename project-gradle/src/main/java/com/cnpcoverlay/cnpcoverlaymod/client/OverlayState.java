package com.cnpcoverlay.cnpcoverlaymod.client;

public final class OverlayState {
    /** Décalage vertical du HUD, en pixels écran (avant application de l'échelle du HUD). */
    private static int hudTopPx = 190;

    /** Décalage à droite du HUD, en pixels écran (avant application de l'échelle du HUD). */
    private static int hudRightPx = 6;

    private OverlayState() {
    }

    public static boolean isEnabled() {
        return true;
    }

    public static boolean toggle() {
        return true;
    }

    public static void setEnabled(boolean enabled) {
        // Compatibilité des anciens appels : le HUD global reste toujours actif.
    }

    public static int getHudTopPx() {
        return hudTopPx;
    }

    public static void setHudTopPx(int px) {
        hudTopPx = Math.max(0, Math.min(2000, px));
    }

    public static int getHudRightPx() {
        return hudRightPx;
    }

    public static void setHudRightPx(int px) {
        hudRightPx = Math.max(0, Math.min(2000, px));
    }
}
