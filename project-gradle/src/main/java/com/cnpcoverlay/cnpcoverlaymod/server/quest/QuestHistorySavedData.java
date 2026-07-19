package com.cnpcoverlay.cnpcoverlaymod.server.quest;

import com.cnpcoverlay.cnpcoverlaymod.common.quest.history.QuestHistoryEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Source de vérité serveur, stockée dans le dossier data du monde. */
public final class QuestHistorySavedData extends SavedData {
    public static final String NAME = "cnpcoverlay_quest_history";
    private final Map<UUID, List<QuestHistoryEntry>> histories = new HashMap<>();
    private long nextSequence;
    public static QuestHistorySavedData load(CompoundTag tag) {
        QuestHistorySavedData data = new QuestHistorySavedData(); data.nextSequence = tag.getLong("nextSequence");
        ListTag players = tag.getList("players", Tag.TAG_COMPOUND);
        for (Tag raw : players) { CompoundTag p=(CompoundTag)raw; try { UUID id=UUID.fromString(p.getString("uuid")); List<QuestHistoryEntry> list=new ArrayList<>();
            for(Tag eRaw:p.getList("entries",Tag.TAG_COMPOUND)){CompoundTag e=(CompoundTag)eRaw; List<String> obj=new ArrayList<>(); for(Tag o:e.getList("objectives",Tag.TAG_STRING)) obj.add(o.getAsString()); list.add(new QuestHistoryEntry(e.getString("occurrence"),e.getString("quest"),e.getString("category"),e.getString("title"),e.getString("log"),obj,e.getLong("completed"),e.getLong("stamp"),e.getLong("sequence")));} data.histories.put(id,list);
        } catch (IllegalArgumentException ignored) {} }
        return data;
    }
    public CompoundTag save(CompoundTag tag) { tag.putLong("nextSequence", nextSequence); ListTag players=new ListTag(); histories.forEach((id,list)->{CompoundTag p=new CompoundTag();p.putString("uuid",id.toString());ListTag es=new ListTag();for(QuestHistoryEntry e:list){CompoundTag x=new CompoundTag();x.putString("occurrence",e.occurrenceId());x.putString("questId",e.questId());x.putString("quest",e.questId());x.putString("category",e.category());x.putString("title",e.title());x.putString("log",e.displayLogText());x.putLong("completed",e.completedAtEpochMillis());x.putLong("stamp",e.sourceFinishedStamp());x.putLong("sequence",e.sequence());ListTag os=new ListTag();e.objectives().forEach(o->os.add(StringTag.valueOf(o)));x.put("objectives",os);es.add(x);}p.put("entries",es);players.add(p);});tag.put("players",players);return tag; }
    public QuestHistoryEntry append(UUID player, String questId, String category, String title, String log, List<String> objectives, long stamp) {
        long seq=++nextSequence, now=System.currentTimeMillis(); String occurrence=player+":"+seq;
        QuestHistoryEntry e=new QuestHistoryEntry(occurrence,questId,category,title,log,objectives,now,stamp,seq);
        histories.computeIfAbsent(player,k->new ArrayList<>()).add(e); setDirty(); return e;
    }
    public List<QuestHistoryEntry> entries(UUID player) { return List.copyOf(histories.getOrDefault(player,List.of())); }
    public static QuestHistorySavedData get(ServerLevel level) { return level.getServer().overworld().getDataStorage().computeIfAbsent(QuestHistorySavedData::load, QuestHistorySavedData::new, NAME); }
}
