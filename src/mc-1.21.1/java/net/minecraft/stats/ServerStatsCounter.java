package net.minecraft.stats;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.internal.Streams;
import com.google.gson.stream.JsonReader;
import com.mojang.datafixers.DataFixer;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.player.Player;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

/**
 * Server-side implementation of {@link net.minecraft.stats.StatsCounter}; handles counting, serialising, and de-serialising statistics, as well as sending them to connected clients via the {@linkplain net.minecraft.network.protocol.game.ClientboundAwardStatsPacket award stats packet}.
 */
public class ServerStatsCounter extends StatsCounter {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final File file;
    private final Set<Stat<?>> dirty = Sets.newHashSet();

    public ServerStatsCounter(MinecraftServer server, File file) {
        this.server = server;
        this.file = file;
        if (file.isFile()) {
            try {
                this.parseLocal(server.getFixerUpper(), FileUtils.readFileToString(file));
            } catch (IOException iOException) {
                LOGGER.error("Couldn't read statistics file {}", file, iOException);
            } catch (JsonParseException jsonParseException) {
                LOGGER.error("Couldn't parse statistics file {}", file, jsonParseException);
            }
        }
    }

    public void save() {
        try {
            FileUtils.writeStringToFile(this.file, this.toJson());
        } catch (IOException iOException) {
            LOGGER.error("Couldn't save stats", iOException);
        }
    }

    @Override
    public void setValue(Player player, Stat<?> stat, int value) {
        super.setValue(player, stat, value);
        this.dirty.add(stat);
    }

    private Set<Stat<?>> getDirty() {
        Set<Stat<?>> set = Sets.newHashSet(this.dirty);
        this.dirty.clear();
        return set;
    }

    public void parseLocal(DataFixer fixerUpper, String json) {
        try (JsonReader jsonReader = new JsonReader(new StringReader(json))) {
            jsonReader.setLenient(false);
            JsonElement jsonElement = Streams.parse(jsonReader);
            if (!jsonElement.isJsonNull()) {
                CompoundTag compoundTag = fromJson(jsonElement.getAsJsonObject());
                compoundTag = DataFixTypes.STATS.updateToCurrentVersion(fixerUpper, compoundTag, NbtUtils.getDataVersion(compoundTag, 1343));
                if (compoundTag.contains("stats", 10)) {
                    CompoundTag compoundTag2 = compoundTag.getCompound("stats");

                    for (String string : compoundTag2.getAllKeys()) {
                        if (compoundTag2.contains(string, 10)) {
                            Util.ifElse(
                                BuiltInRegistries.STAT_TYPE.getOptional(ResourceLocation.parse(string)),
                                type -> {
                                    CompoundTag compoundTag2x = compoundTag2.getCompound(string);

                                    for (String string2 : compoundTag2x.getAllKeys()) {
                                        if (compoundTag2x.contains(string2, 99)) {
                                            Util.ifElse(
                                                this.getStat(type, string2),
                                                stat -> this.stats.put(stat, compoundTag2x.getInt(string2)),
                                                () -> LOGGER.warn("Invalid statistic in {}: Don't know what {} is", this.file, string2)
                                            );
                                        } else {
                                            LOGGER.warn(
                                                "Invalid statistic value in {}: Don't know what {} is for key {}",
                                                this.file,
                                                compoundTag2x.get(string2),
                                                string2
                                            );
                                        }
                                    }
                                },
                                () -> LOGGER.warn("Invalid statistic type in {}: Don't know what {} is", this.file, string)
                            );
                        }
                    }
                }
            } else {
                LOGGER.error("Unable to parse Stat data from {}", this.file);
            }
        } catch (JsonParseException | IOException exception) {
            LOGGER.error("Unable to parse Stat data from {}", this.file, exception);
        }
    }

    private <T> Optional<Stat<T>> getStat(StatType<T> type, String location) {
        return Optional.ofNullable(ResourceLocation.tryParse(location)).flatMap(type.getRegistry()::getOptional).map(type::get);
    }

    private static CompoundTag fromJson(JsonObject json) {
        CompoundTag compoundTag = new CompoundTag();

        for (Entry<String, JsonElement> entry : json.entrySet()) {
            JsonElement jsonElement = entry.getValue();
            if (jsonElement.isJsonObject()) {
                compoundTag.put(entry.getKey(), fromJson(jsonElement.getAsJsonObject()));
            } else if (jsonElement.isJsonPrimitive()) {
                JsonPrimitive jsonPrimitive = jsonElement.getAsJsonPrimitive();
                if (jsonPrimitive.isNumber()) {
                    compoundTag.putInt(entry.getKey(), jsonPrimitive.getAsInt());
                }
            }
        }

        return compoundTag;
    }

    protected String toJson() {
        Map<StatType<?>, JsonObject> map = Maps.newHashMap();

        for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Stat<?>> entry : this.stats.object2IntEntrySet()) {
            Stat<?> stat = entry.getKey();
            map.computeIfAbsent(stat.getType(), type -> new JsonObject()).addProperty(getKey(stat).toString(), entry.getIntValue());
        }

        JsonObject jsonObject = new JsonObject();

        for (Entry<StatType<?>, JsonObject> entry2 : map.entrySet()) {
            jsonObject.add(BuiltInRegistries.STAT_TYPE.getKey(entry2.getKey()).toString(), entry2.getValue());
        }

        JsonObject jsonObject2 = new JsonObject();
        jsonObject2.add("stats", jsonObject);
        jsonObject2.addProperty("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return jsonObject2.toString();
    }

    private static <T> ResourceLocation getKey(Stat<T> stat) {
        return stat.getType().getRegistry().getKey(stat.getValue());
    }

    public void markAllDirty() {
        this.dirty.addAll(this.stats.keySet());
    }

    public void sendStats(ServerPlayer player) {
        Object2IntMap<Stat<?>> object2IntMap = new Object2IntOpenHashMap<>();

        for (Stat<?> stat : this.getDirty()) {
            object2IntMap.put(stat, this.getValue(stat));
        }

        player.connection.send(new ClientboundAwardStatsPacket(object2IntMap));
    }
}
