package com.cnpcoverlay.cnpcoverlaymod.client;

import com.cnpcoverlay.cnpcoverlaymod.CnpcOverlayMod;
import com.cnpcoverlay.cnpcoverlaymod.client.ui.CnpcOverlayScreen;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = CnpcOverlayMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientCommandRegistration {
    private ClientCommandRegistration() {
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("cnpcoverlay")
                        .executes(ctx -> open())
                        .then(Commands.literal("open").executes(ctx -> open()))
                        .then(Commands.literal("hud")
                                .executes(ctx -> hudToggle())
                                .then(Commands.literal("toggle").executes(ctx -> hudToggle()))
                                .then(Commands.literal("on").executes(ctx -> hudSet(true)))
                                .then(Commands.literal("off").executes(ctx -> hudSet(false)))
                    .then(Commands.literal("y")
                        .then(Commands.argument("px", IntegerArgumentType.integer(0, 2000))
                            .executes(ctx -> hudY(IntegerArgumentType.getInteger(ctx, "px"))))
                    )
                    .then(Commands.literal("yreset").executes(ctx -> hudY(190)))

                    // Décalage depuis le bord droit (utile pour s'aligner sous une minimap)
                    .then(Commands.literal("x")
                        .then(Commands.argument("px", IntegerArgumentType.integer(0, 2000))
                            .executes(ctx -> hudX(IntegerArgumentType.getInteger(ctx, "px"))))
                    )
                    .then(Commands.literal("xreset").executes(ctx -> hudX(6)))
                        )
        );
    }

    private static int open() {
        var mc = Minecraft.getInstance();
        mc.execute(() -> mc.setScreen(new CnpcOverlayScreen()));
        return 1;
    }

    private static int hudToggle() {
        sendHudStatus(OverlayState.toggle());
        return 1;
    }

    private static int hudSet(boolean enabled) {
        OverlayState.setEnabled(enabled);
        sendHudStatus(OverlayState.isEnabled());
        return 1;
    }

    private static void sendHudStatus(boolean enabled) {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("CNPC Overlay: " + (enabled ? "ON" : "OFF")), true);
        }
    }

    private static int hudY(int px) {
        OverlayState.setHudTopPx(px);
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("CNPC Overlay HUD Y: " + px + "px"), true);
        }
        return 1;
    }

    private static int hudX(int px) {
        OverlayState.setHudRightPx(px);
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("CNPC Overlay HUD X (right): " + px + "px"), true);
        }
        return 1;
    }
}
