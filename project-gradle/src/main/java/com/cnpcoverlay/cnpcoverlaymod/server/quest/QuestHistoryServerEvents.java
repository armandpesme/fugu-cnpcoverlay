package com.cnpcoverlay.cnpcoverlaymod.server.quest;

import com.cnpcoverlay.cnpcoverlaymod.CnpcOverlayMod;
import com.cnpcoverlay.cnpcoverlaymod.common.network.CnpcNetwork;
import com.cnpcoverlay.cnpcoverlaymod.common.network.QuestHistorySyncS2CPacket;
import com.cnpcoverlay.cnpcoverlaymod.common.quest.history.QuestHistoryEntry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.lang.reflect.Method;
import java.util.*;

@Mod.EventBusSubscriber(modid=CnpcOverlayMod.MODID, bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class QuestHistoryServerEvents {
    private static Object customBus;
    private static Object listener;
    private QuestHistoryServerEvents() {}
    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }
    @SubscribeEvent public static void onChanged(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) sync(p); }
    @SubscribeEvent public static void onServerStarting(net.minecraftforge.event.server.ServerStartingEvent e) {
        if (!ModList.get().isLoaded("customnpcs")) return;
        try { Class<?> api=Class.forName("noppes.npcs.api.NpcAPI"); Object inst=api.getMethod("Instance").invoke(null); customBus=api.getMethod("events").invoke(inst); listener=new CustomListener(); customBus.getClass().getMethod("register",Object.class).invoke(customBus,listener); } catch (ReflectiveOperationException ex) { CnpcOverlayMod.LOGGER.warn("CustomNPCs server events unavailable",ex); }
    }
    @SubscribeEvent public static void onCommands(net.minecraftforge.event.RegisterCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("cnpcoverlay")
                .then(net.minecraft.commands.Commands.literal("history").requires(s -> s.hasPermission(2))
                        .then(net.minecraft.commands.Commands.argument("player", net.minecraft.commands.arguments.EntityArgument.player())
                                .executes(c -> { ServerPlayer p=net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player"); int n=QuestHistorySavedData.get(p.serverLevel()).entries(p.getUUID()).size(); c.getSource().sendSuccess(()->net.minecraft.network.chat.Component.literal("Historique serveur de "+p.getName().getString()+" : "+n+                                        " remise(s) valide(s)."), true); return n; }))));
    }
    public static void sync(ServerPlayer p) { QuestHistorySavedData d=QuestHistorySavedData.get(p.serverLevel()); CnpcNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new QuestHistorySyncS2CPacket(Long.MAX_VALUE,d.entries(p.getUUID()))); }
    private static final class CustomListener {
        @SubscribeEvent public void onCustomNpc(Event event) {
            if (!event.getClass().getName().equals("noppes.npcs.api.event.QuestEvent$QuestTurnedInEvent")) return;
            try { Object player=event.getClass().getField("player").get(event), quest=event.getClass().getField("quest").get(event); ServerPlayer p=(ServerPlayer)player.getClass().getMethod("getMCEntity").invoke(player); 
                String id=String.valueOf(quest.getClass().getMethod("getId").invoke(quest)), title=String.valueOf(quest.getClass().getMethod("getName").invoke(quest));
                String cat=String.valueOf(quest.getClass().getMethod("getCategory").invoke(quest)); String log=""; try {log=String.valueOf(quest.getClass().getMethod("getLogText").invoke(quest));}catch(ReflectiveOperationException ignored){}
                QuestHistorySavedData d=QuestHistorySavedData.get(p.serverLevel()); QuestHistoryEntryShim.send(d,p,id,cat,title,log); }
            catch (ReflectiveOperationException | ClassCastException ignored) {}
        }
    }
    private static final class QuestHistoryEntryShim { static void send(QuestHistorySavedData d,ServerPlayer p,String id,String cat,String title,String log){var e=d.append(p.getUUID(),id,cat,title,log,List.of(),p.serverLevel().getGameTime()); CnpcNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new QuestHistorySyncS2CPacket(e.sequence(),d.entries(p.getUUID())));}}
}
