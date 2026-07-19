package com.cnpcoverlay.cnpcoverlaymod.common.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class CnpcNetwork {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("cnpcoverlay", "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;
    private CnpcNetwork() {}
    public static void register() {
        if (registered) return;
        registered = true;
        CHANNEL.messageBuilder(QuestHistorySyncS2CPacket.class, 0, net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QuestHistorySyncS2CPacket::encode).decoder(QuestHistorySyncS2CPacket::decode)
                .consumerMainThread(QuestHistorySyncS2CPacket::handle).add();
    }
}
