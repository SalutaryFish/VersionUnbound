package net.minecraft.world.level.saveddata.maps;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

public class MapItemSavedData extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAP_SIZE = 128;
    private static final int HALF_MAP_SIZE = 64;
    public static final int MAX_SCALE = 4;
    public static final int TRACKED_DECORATION_LIMIT = 256;
    private static final String FRAME_PREFIX = "frame-";
    public final int centerX;
    public final int centerZ;
    public final ResourceKey<Level> dimension;
    private final boolean trackingPosition;
    private final boolean unlimitedTracking;
    public final byte scale;
    public byte[] colors = new byte[16384];
    public final boolean locked;
    private final List<MapItemSavedData.HoldingPlayer> carriedBy = Lists.newArrayList();
    private final Map<Player, MapItemSavedData.HoldingPlayer> carriedByPlayers = Maps.newHashMap();
    private final Map<String, MapBanner> bannerMarkers = Maps.newHashMap();
    final Map<String, MapDecoration> decorations = Maps.newLinkedHashMap();
    private final Map<String, MapFrame> frameMarkers = Maps.newHashMap();
    private int trackedDecorationCount;

    public static SavedData.Factory<MapItemSavedData> factory() {
        return new SavedData.Factory<>(() -> {
            throw new IllegalStateException("Should never create an empty map saved data");
        }, MapItemSavedData::load, DataFixTypes.SAVED_DATA_MAP_DATA);
    }

    private MapItemSavedData(int x, int z, byte scale, boolean trackingPosition, boolean unlimitedTracking, boolean locked, ResourceKey<Level> dimension) {
        this.scale = scale;
        this.centerX = x;
        this.centerZ = z;
        this.dimension = dimension;
        this.trackingPosition = trackingPosition;
        this.unlimitedTracking = unlimitedTracking;
        this.locked = locked;
        this.setDirty();
    }

    public static MapItemSavedData createFresh(
        double x, double z, byte scale, boolean trackingPosition, boolean unlimitedTracking, ResourceKey<Level> dimension
    ) {
        int i = 128 * (1 << scale);
        int j = Mth.floor((x + 64.0) / i);
        int k = Mth.floor((z + 64.0) / i);
        int l = j * i + i / 2 - 64;
        int m = k * i + i / 2 - 64;
        return new MapItemSavedData(l, m, scale, trackingPosition, unlimitedTracking, false, dimension);
    }

    public static MapItemSavedData createForClient(byte scale, boolean locked, ResourceKey<Level> dimension) {
        return new MapItemSavedData(0, 0, scale, false, false, locked, dimension);
    }

    public static MapItemSavedData load(CompoundTag tag, HolderLookup.Provider levelRegistry) {
        ResourceKey<Level> resourceKey = DimensionType.parseLegacy(new Dynamic<>(NbtOps.INSTANCE, tag.get("dimension")))
            .resultOrPartial(LOGGER::error)
            .orElseThrow(() -> new IllegalArgumentException("Invalid map dimension: " + tag.get("dimension")));
        int i = tag.getInt("xCenter");
        int j = tag.getInt("zCenter");
        byte b = (byte)Mth.clamp(tag.getByte("scale"), 0, 4);
        boolean bl = !tag.contains("trackingPosition", 1) || tag.getBoolean("trackingPosition");
        boolean bl2 = tag.getBoolean("unlimitedTracking");
        boolean bl3 = tag.getBoolean("locked");
        MapItemSavedData mapItemSavedData = new MapItemSavedData(i, j, b, bl, bl2, bl3, resourceKey);
        byte[] bs = tag.getByteArray("colors");
        if (bs.length == 16384) {
            mapItemSavedData.colors = bs;
        }

        RegistryOps<Tag> registryOps = levelRegistry.createSerializationContext(NbtOps.INSTANCE);

        for (MapBanner mapBanner : MapBanner.LIST_CODEC
            .parse(registryOps, tag.get("banners"))
            .resultOrPartial(string -> LOGGER.warn("Failed to parse map banner: '{}'", string))
            .orElse(List.of())) {
            mapItemSavedData.bannerMarkers.put(mapBanner.getId(), mapBanner);
            mapItemSavedData.addDecoration(
                mapBanner.getDecoration(), null, mapBanner.getId(), mapBanner.pos().getX(), mapBanner.pos().getZ(), 180.0, mapBanner.name().orElse(null)
            );
        }

        ListTag listTag = tag.getList("frames", 10);

        for (int k = 0; k < listTag.size(); k++) {
            MapFrame mapFrame = MapFrame.load(listTag.getCompound(k));
            if (mapFrame != null) {
                mapItemSavedData.frameMarkers.put(mapFrame.getId(), mapFrame);
                mapItemSavedData.addDecoration(
                    MapDecorationTypes.FRAME,
                    null,
                    getFrameKey(mapFrame.getEntityId()),
                    mapFrame.getPos().getX(),
                    mapFrame.getPos().getZ(),
                    mapFrame.getRotation(),
                    null
                );
            }
        }

        return mapItemSavedData;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ResourceLocation.CODEC
            .encodeStart(NbtOps.INSTANCE, this.dimension.location())
            .resultOrPartial(LOGGER::error)
            .ifPresent(tagx -> tag.put("dimension", tagx));
        tag.putInt("xCenter", this.centerX);
        tag.putInt("zCenter", this.centerZ);
        tag.putByte("scale", this.scale);
        tag.putByteArray("colors", this.colors);
        tag.putBoolean("trackingPosition", this.trackingPosition);
        tag.putBoolean("unlimitedTracking", this.unlimitedTracking);
        tag.putBoolean("locked", this.locked);
        RegistryOps<Tag> registryOps = registries.createSerializationContext(NbtOps.INSTANCE);
        tag.put("banners", MapBanner.LIST_CODEC.encodeStart(registryOps, List.copyOf(this.bannerMarkers.values())).getOrThrow());
        ListTag listTag = new ListTag();

        for (MapFrame mapFrame : this.frameMarkers.values()) {
            listTag.add(mapFrame.save());
        }

        tag.put("frames", listTag);
        return tag;
    }

    public MapItemSavedData locked() {
        MapItemSavedData mapItemSavedData = new MapItemSavedData(
            this.centerX, this.centerZ, this.scale, this.trackingPosition, this.unlimitedTracking, true, this.dimension
        );
        mapItemSavedData.bannerMarkers.putAll(this.bannerMarkers);
        mapItemSavedData.decorations.putAll(this.decorations);
        mapItemSavedData.trackedDecorationCount = this.trackedDecorationCount;
        System.arraycopy(this.colors, 0, mapItemSavedData.colors, 0, this.colors.length);
        mapItemSavedData.setDirty();
        return mapItemSavedData;
    }

    public MapItemSavedData scaled() {
        return createFresh(this.centerX, this.centerZ, (byte)Mth.clamp(this.scale + 1, 0, 4), this.trackingPosition, this.unlimitedTracking, this.dimension);
    }

    private static Predicate<ItemStack> mapMatcher(ItemStack stack) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        return itemStack2 -> itemStack2 == stack ? true : itemStack2.is(stack.getItem()) && Objects.equals(mapId, itemStack2.get(DataComponents.MAP_ID));
    }

    /**
     * Adds the player passed to the list of visible players and checks to see which players are visible
     */
    public void tickCarriedBy(Player player, ItemStack mapStack) {
        if (!this.carriedByPlayers.containsKey(player)) {
            MapItemSavedData.HoldingPlayer holdingPlayer = new MapItemSavedData.HoldingPlayer(player);
            this.carriedByPlayers.put(player, holdingPlayer);
            this.carriedBy.add(holdingPlayer);
        }

        Predicate<ItemStack> predicate = mapMatcher(mapStack);
        if (!player.getInventory().contains(predicate)) {
            this.removeDecoration(player.getName().getString());
        }

        for (int i = 0; i < this.carriedBy.size(); i++) {
            MapItemSavedData.HoldingPlayer holdingPlayer2 = this.carriedBy.get(i);
            String string = holdingPlayer2.player.getName().getString();
            if (!holdingPlayer2.player.isRemoved() && (holdingPlayer2.player.getInventory().contains(predicate) || mapStack.isFramed())) {
                if (!mapStack.isFramed() && holdingPlayer2.player.level().dimension() == this.dimension && this.trackingPosition) {
                    this.addDecoration(
                        MapDecorationTypes.PLAYER,
                        holdingPlayer2.player.level(),
                        string,
                        holdingPlayer2.player.getX(),
                        holdingPlayer2.player.getZ(),
                        holdingPlayer2.player.getYRot(),
                        null
                    );
                }
            } else {
                this.carriedByPlayers.remove(holdingPlayer2.player);
                this.carriedBy.remove(holdingPlayer2);
                this.removeDecoration(string);
            }
        }

        if (mapStack.isFramed() && this.trackingPosition) {
            ItemFrame itemFrame = mapStack.getFrame();
            BlockPos blockPos = itemFrame.getPos();
            MapFrame mapFrame = this.frameMarkers.get(MapFrame.frameId(blockPos));
            if (mapFrame != null && itemFrame.getId() != mapFrame.getEntityId() && this.frameMarkers.containsKey(mapFrame.getId())) {
                this.removeDecoration(getFrameKey(mapFrame.getEntityId()));
            }

            MapFrame mapFrame2 = new MapFrame(blockPos, itemFrame.getDirection().get2DDataValue() * 90, itemFrame.getId());
            this.addDecoration(
                MapDecorationTypes.FRAME,
                player.level(),
                getFrameKey(itemFrame.getId()),
                blockPos.getX(),
                blockPos.getZ(),
                itemFrame.getDirection().get2DDataValue() * 90,
                null
            );
            this.frameMarkers.put(mapFrame2.getId(), mapFrame2);
        }

        MapDecorations mapDecorations = mapStack.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
        if (!this.decorations.keySet().containsAll(mapDecorations.decorations().keySet())) {
            mapDecorations.decorations().forEach((string, entry) -> {
                if (!this.decorations.containsKey(string)) {
                    this.addDecoration(entry.type(), player.level(), string, entry.x(), entry.z(), entry.rotation(), null);
                }
            });
        }
    }

    private void removeDecoration(String identifier) {
        MapDecoration mapDecoration = this.decorations.remove(identifier);
        if (mapDecoration != null && mapDecoration.type().value().trackCount()) {
            this.trackedDecorationCount--;
        }

        this.setDecorationsDirty();
    }

    public static void addTargetDecoration(ItemStack stack, BlockPos pos, String type, Holder<MapDecorationType> mapDecorationType) {
        MapDecorations.Entry entry = new MapDecorations.Entry(mapDecorationType, pos.getX(), pos.getZ(), 180.0F);
        stack.update(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY, mapDecorations -> mapDecorations.withDecoration(type, entry));
        if (mapDecorationType.value().hasMapColor()) {
            stack.set(DataComponents.MAP_COLOR, new MapItemColor(mapDecorationType.value().mapColor()));
        }
    }

    private void addDecoration(
        Holder<MapDecorationType> decorationType, @Nullable LevelAccessor level, String id, double x, double z, double yRot, @Nullable Component displayName
    ) {
        int i = 1 << this.scale;
        float f = (float)(x - this.centerX) / i;
        float g = (float)(z - this.centerZ) / i;
        byte b = (byte)(f * 2.0F + 0.5);
        byte c = (byte)(g * 2.0F + 0.5);
        int j = 63;
        byte d;
        if (f >= -63.0F && g >= -63.0F && f <= 63.0F && g <= 63.0F) {
            yRot += yRot < 0.0 ? -8.0 : 8.0;
            d = (byte)(yRot * 16.0 / 360.0);
            if (this.dimension == Level.NETHER && level != null) {
                int k = (int)(level.getLevelData().getDayTime() / 10L);
                d = (byte)(k * k * 34187121 + k * 121 >> 15 & 15);
            }
        } else {
            if (!decorationType.is(MapDecorationTypes.PLAYER)) {
                this.removeDecoration(id);
                return;
            }

            int k = 320;
            if (Math.abs(f) < 320.0F && Math.abs(g) < 320.0F) {
                decorationType = MapDecorationTypes.PLAYER_OFF_MAP;
            } else {
                if (!this.unlimitedTracking) {
                    this.removeDecoration(id);
                    return;
                }

                decorationType = MapDecorationTypes.PLAYER_OFF_LIMITS;
            }

            d = 0;
            if (f <= -63.0F) {
                b = -128;
            }

            if (g <= -63.0F) {
                c = -128;
            }

            if (f >= 63.0F) {
                b = 127;
            }

            if (g >= 63.0F) {
                c = 127;
            }
        }

        MapDecoration mapDecoration = new MapDecoration(decorationType, b, c, d, Optional.ofNullable(displayName));
        MapDecoration mapDecoration2 = this.decorations.put(id, mapDecoration);
        if (!mapDecoration.equals(mapDecoration2)) {
            if (mapDecoration2 != null && mapDecoration2.type().value().trackCount()) {
                this.trackedDecorationCount--;
            }

            if (decorationType.value().trackCount()) {
                this.trackedDecorationCount++;
            }

            this.setDecorationsDirty();
        }
    }

    @Nullable
    public Packet<?> getUpdatePacket(MapId mapId, Player player) {
        MapItemSavedData.HoldingPlayer holdingPlayer = this.carriedByPlayers.get(player);
        return holdingPlayer == null ? null : holdingPlayer.nextUpdatePacket(mapId);
    }

    private void setColorsDirty(int x, int z) {
        this.setDirty();

        for (MapItemSavedData.HoldingPlayer holdingPlayer : this.carriedBy) {
            holdingPlayer.markColorsDirty(x, z);
        }
    }

    private void setDecorationsDirty() {
        this.setDirty();
        this.carriedBy.forEach(MapItemSavedData.HoldingPlayer::markDecorationsDirty);
    }

    public MapItemSavedData.HoldingPlayer getHoldingPlayer(Player player) {
        MapItemSavedData.HoldingPlayer holdingPlayer = this.carriedByPlayers.get(player);
        if (holdingPlayer == null) {
            holdingPlayer = new MapItemSavedData.HoldingPlayer(player);
            this.carriedByPlayers.put(player, holdingPlayer);
            this.carriedBy.add(holdingPlayer);
        }

        return holdingPlayer;
    }

    public boolean toggleBanner(LevelAccessor accessor, BlockPos pos) {
        double d = pos.getX() + 0.5;
        double e = pos.getZ() + 0.5;
        int i = 1 << this.scale;
        double f = (d - this.centerX) / i;
        double g = (e - this.centerZ) / i;
        int j = 63;
        if (f >= -63.0 && g >= -63.0 && f <= 63.0 && g <= 63.0) {
            MapBanner mapBanner = MapBanner.fromWorld(accessor, pos);
            if (mapBanner == null) {
                return false;
            }

            if (this.bannerMarkers.remove(mapBanner.getId(), mapBanner)) {
                this.removeDecoration(mapBanner.getId());
                return true;
            }

            if (!this.isTrackedCountOverLimit(256)) {
                this.bannerMarkers.put(mapBanner.getId(), mapBanner);
                this.addDecoration(mapBanner.getDecoration(), accessor, mapBanner.getId(), d, e, 180.0, mapBanner.name().orElse(null));
                return true;
            }
        }

        return false;
    }

    public void checkBanners(BlockGetter reader, int x, int z) {
        Iterator<MapBanner> iterator = this.bannerMarkers.values().iterator();

        while (iterator.hasNext()) {
            MapBanner mapBanner = iterator.next();
            if (mapBanner.pos().getX() == x && mapBanner.pos().getZ() == z) {
                MapBanner mapBanner2 = MapBanner.fromWorld(reader, mapBanner.pos());
                if (!mapBanner.equals(mapBanner2)) {
                    iterator.remove();
                    this.removeDecoration(mapBanner.getId());
                }
            }
        }
    }

    public Collection<MapBanner> getBanners() {
        return this.bannerMarkers.values();
    }

    public void removedFromFrame(BlockPos pos, int entityId) {
        this.removeDecoration(getFrameKey(entityId));
        this.frameMarkers.remove(MapFrame.frameId(pos));
    }

    public boolean updateColor(int x, int z, byte color) {
        byte b = this.colors[x + z * 128];
        if (b != color) {
            this.setColor(x, z, color);
            return true;
        } else {
            return false;
        }
    }

    public void setColor(int x, int z, byte color) {
        this.colors[x + z * 128] = color;
        this.setColorsDirty(x, z);
    }

    public boolean isExplorationMap() {
        for (MapDecoration mapDecoration : this.decorations.values()) {
            if (mapDecoration.type().value().explorationMapElement()) {
                return true;
            }
        }

        return false;
    }

    public void addClientSideDecorations(List<MapDecoration> decorations) {
        this.decorations.clear();
        this.trackedDecorationCount = 0;

        for (int i = 0; i < decorations.size(); i++) {
            MapDecoration mapDecoration = decorations.get(i);
            this.decorations.put("icon-" + i, mapDecoration);
            if (mapDecoration.type().value().trackCount()) {
                this.trackedDecorationCount++;
            }
        }
    }

    public Iterable<MapDecoration> getDecorations() {
        return this.decorations.values();
    }

    public boolean isTrackedCountOverLimit(int trackedCount) {
        return this.trackedDecorationCount >= trackedCount;
    }

    private static String getFrameKey(int entityId) {
        return "frame-" + entityId;
    }

    public class HoldingPlayer {
        public final Player player;
        private boolean dirtyData = true;
        /**
         * The lowest dirty x value
         */
        private int minDirtyX;
        /**
         * The lowest dirty z value
         */
        private int minDirtyY;
        /**
         * The highest dirty x value
         */
        private int maxDirtyX = 127;
        /**
         * The highest dirty z value
         */
        private int maxDirtyY = 127;
        private boolean dirtyDecorations = true;
        private int tick;
        public int step;

        HoldingPlayer(final Player player) {
            this.player = player;
        }

        private MapItemSavedData.MapPatch createPatch() {
            int i = this.minDirtyX;
            int j = this.minDirtyY;
            int k = this.maxDirtyX + 1 - this.minDirtyX;
            int l = this.maxDirtyY + 1 - this.minDirtyY;
            byte[] bs = new byte[k * l];

            for (int m = 0; m < k; m++) {
                for (int n = 0; n < l; n++) {
                    bs[m + n * k] = MapItemSavedData.this.colors[i + m + (j + n) * 128];
                }
            }

            return new MapItemSavedData.MapPatch(i, j, k, l, bs);
        }

        @Nullable
        Packet<?> nextUpdatePacket(MapId mapId) {
            MapItemSavedData.MapPatch mapPatch;
            if (this.dirtyData) {
                this.dirtyData = false;
                mapPatch = this.createPatch();
            } else {
                mapPatch = null;
            }

            Collection<MapDecoration> collection;
            if (this.dirtyDecorations && this.tick++ % 5 == 0) {
                this.dirtyDecorations = false;
                collection = MapItemSavedData.this.decorations.values();
            } else {
                collection = null;
            }

            return collection == null && mapPatch == null
                ? null
                : new ClientboundMapItemDataPacket(mapId, MapItemSavedData.this.scale, MapItemSavedData.this.locked, collection, mapPatch);
        }

        void markColorsDirty(int x, int z) {
            if (this.dirtyData) {
                this.minDirtyX = Math.min(this.minDirtyX, x);
                this.minDirtyY = Math.min(this.minDirtyY, z);
                this.maxDirtyX = Math.max(this.maxDirtyX, x);
                this.maxDirtyY = Math.max(this.maxDirtyY, z);
            } else {
                this.dirtyData = true;
                this.minDirtyX = x;
                this.minDirtyY = z;
                this.maxDirtyX = x;
                this.maxDirtyY = z;
            }
        }

        private void markDecorationsDirty() {
            this.dirtyDecorations = true;
        }
    }

    public record MapPatch(int startX, int startY, int width, int height, byte[] mapColors) {
        public static final StreamCodec<ByteBuf, Optional<MapItemSavedData.MapPatch>> STREAM_CODEC = StreamCodec.of(
            MapItemSavedData.MapPatch::write, MapItemSavedData.MapPatch::read
        );

        private static void write(ByteBuf buffer, Optional<MapItemSavedData.MapPatch> mapPatch) {
            if (mapPatch.isPresent()) {
                MapItemSavedData.MapPatch mapPatch2 = mapPatch.get();
                buffer.writeByte(mapPatch2.width);
                buffer.writeByte(mapPatch2.height);
                buffer.writeByte(mapPatch2.startX);
                buffer.writeByte(mapPatch2.startY);
                FriendlyByteBuf.writeByteArray(buffer, mapPatch2.mapColors);
            } else {
                buffer.writeByte(0);
            }
        }

        private static Optional<MapItemSavedData.MapPatch> read(ByteBuf buffer) {
            int i = buffer.readUnsignedByte();
            if (i > 0) {
                int j = buffer.readUnsignedByte();
                int k = buffer.readUnsignedByte();
                int l = buffer.readUnsignedByte();
                byte[] bs = FriendlyByteBuf.readByteArray(buffer);
                return Optional.of(new MapItemSavedData.MapPatch(k, l, i, j, bs));
            } else {
                return Optional.empty();
            }
        }

        public void applyToMap(MapItemSavedData savedData) {
            for (int i = 0; i < this.width; i++) {
                for (int j = 0; j < this.height; j++) {
                    savedData.setColor(this.startX + i, this.startY + j, this.mapColors[i + j * this.width]);
                }
            }
        }
    }
}
