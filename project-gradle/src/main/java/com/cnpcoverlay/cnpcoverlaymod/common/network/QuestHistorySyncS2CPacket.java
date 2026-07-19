package com.cnpcoverlay.cnpcoverlaymod.common.network;

import com.cnpcoverlay.cnpcoverlaymod.common.quest.history.QuestHistoryEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record QuestHistorySyncS2CPacket(long sequence, List<QuestHistoryEntry> entries) {
    private static final int MAX = 512;
    public void encode(FriendlyByteBuf b) {
        b.writeVarLong(Math.max(0, sequence));
        List<QuestHistoryEntry> safe = entries == null ? List.of() : entries.subList(0, Math.min(MAX, entries.size()));
        b.writeVarInt(safe.size());
        for (QuestHistoryEntry e : safe) {
            b.writeUtf(e.occurrenceId(), 128); b.writeUtf(e.questId(), 128); b.writeUtf(e.category(), 128);
            b.writeUtf(e.title(), 256); b.writeUtf(e.displayLogText(), 4096);
            b.writeVarInt(Math.min(64, e.objectives().size()));
            for (int i = 0; i < Math.min(64, e.objectives().size()); i++) b.writeUtf(e.objectives().get(i), 512);
            b.writeLong(e.completedAtEpochMillis()); b.writeLong(e.sourceFinishedStamp()); b.writeVarLong(e.sequence());
        }
    }
    public static QuestHistorySyncS2CPacket decode(FriendlyByteBuf b) {
        long seq = b.readVarLong(); int count = Math.min(MAX, b.readVarInt());
        List<QuestHistoryEntry> out = new ArrayList<>(count);
        for (int i=0;i<count;i++) {
            String occurrence=b.readUtf(128), id=b.readUtf(128), cat=b.readUtf(128), title=b.readUtf(256), log=b.readUtf(4096);
            int n=Math.min(64,b.readVarInt()); List<String> obj=new ArrayList<>(n); for(int j=0;j<n;j++) obj.add(b.readUtf(512));
            out.add(new QuestHistoryEntry(occurrence,id,cat,title,log,obj,b.readLong(),b.readLong(),b.readVarLong()));
        }
        return new QuestHistorySyncS2CPacket(seq,out);
    }
    public void handle(Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context c=s.get(); c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.safeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.cnpcoverlay.cnpcoverlaymod.client.quest.history.QuestHistoryState.get().replaceFromServer(entries)));
        c.setPacketHandled(true);
    }
}
