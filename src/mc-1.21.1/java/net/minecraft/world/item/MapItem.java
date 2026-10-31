package net.minecraft.world.item;

import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultiset;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.MapPostProcessing;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

public class MapItem extends ComplexItem {
    public static final int IMAGE_WIDTH = 128;
    public static final int IMAGE_HEIGHT = 128;

    public MapItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemStack create(Level level, int levelX, int levelZ, byte scale, boolean trackingPosition, boolean unlimitedTracking) {
        ItemStack itemStack = new ItemStack(Items.FILLED_MAP);
        MapId mapId = createNewSavedData(level, levelX, levelZ, scale, trackingPosition, unlimitedTracking, level.dimension());
        itemStack.set(DataComponents.MAP_ID, mapId);
        return itemStack;
    }

    @Nullable
    public static MapItemSavedData getSavedData(@Nullable MapId mapId, Level level) {
        return mapId == null ? null : level.getMapData(mapId);
    }

    @Nullable
    public static MapItemSavedData getSavedData(ItemStack stack, Level level) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        return getSavedData(mapId, level);
    }

    private static MapId createNewSavedData(
        Level level, int x, int z, int scale, boolean trackingPosition, boolean unlimitedTracking, ResourceKey<Level> dimension
    ) {
        MapItemSavedData mapItemSavedData = MapItemSavedData.createFresh(x, z, (byte)scale, trackingPosition, unlimitedTracking, dimension);
        MapId mapId = level.getFreeMapId();
        level.setMapData(mapId, mapItemSavedData);
        return mapId;
    }

    public void update(Level level, Entity viewer, MapItemSavedData data) {
        if (level.dimension() == data.dimension && viewer instanceof Player) {
            int i = 1 << data.scale;
            int j = data.centerX;
            int k = data.centerZ;
            int l = Mth.floor(viewer.getX() - j) / i + 64;
            int m = Mth.floor(viewer.getZ() - k) / i + 64;
            int n = 128 / i;
            if (level.dimensionType().hasCeiling()) {
                n /= 2;
            }

            MapItemSavedData.HoldingPlayer holdingPlayer = data.getHoldingPlayer((Player)viewer);
            holdingPlayer.step++;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos mutableBlockPos2 = new BlockPos.MutableBlockPos();
            boolean bl = false;

            for (int o = l - n + 1; o < l + n; o++) {
                if ((o & 15) == (holdingPlayer.step & 15) || bl) {
                    bl = false;
                    double d = 0.0;

                    for (int p = m - n - 1; p < m + n; p++) {
                        if (o >= 0 && p >= -1 && o < 128 && p < 128) {
                            int q = Mth.square(o - l) + Mth.square(p - m);
                            boolean bl2 = q > (n - 2) * (n - 2);
                            int r = (j / i + o - 64) * i;
                            int s = (k / i + p - 64) * i;
                            Multiset<MapColor> multiset = LinkedHashMultiset.create();
                            LevelChunk levelChunk = level.getChunk(SectionPos.blockToSectionCoord(r), SectionPos.blockToSectionCoord(s));
                            if (!levelChunk.isEmpty()) {
                                int t = 0;
                                double e = 0.0;
                                if (level.dimensionType().hasCeiling()) {
                                    int u = r + s * 231871;
                                    u = u * u * 31287121 + u * 11;
                                    if ((u >> 20 & 1) == 0) {
                                        multiset.add(Blocks.DIRT.defaultBlockState().getMapColor(level, BlockPos.ZERO), 10);
                                    } else {
                                        multiset.add(Blocks.STONE.defaultBlockState().getMapColor(level, BlockPos.ZERO), 100);
                                    }

                                    e = 100.0;
                                } else {
                                    for (int u = 0; u < i; u++) {
                                        for (int v = 0; v < i; v++) {
                                            mutableBlockPos.set(r + u, 0, s + v);
                                            int w = levelChunk.getHeight(Heightmap.Types.WORLD_SURFACE, mutableBlockPos.getX(), mutableBlockPos.getZ()) + 1;
                                            BlockState blockState;
                                            if (w <= level.getMinBuildHeight() + 1) {
                                                blockState = Blocks.BEDROCK.defaultBlockState();
                                            } else {
                                                do {
                                                    mutableBlockPos.setY(--w);
                                                    blockState = levelChunk.getBlockState(mutableBlockPos);
                                                } while (blockState.getMapColor(level, mutableBlockPos) == MapColor.NONE && w > level.getMinBuildHeight());

                                                if (w > level.getMinBuildHeight() && !blockState.getFluidState().isEmpty()) {
                                                    int x = w - 1;
                                                    mutableBlockPos2.set(mutableBlockPos);

                                                    BlockState blockState2;
                                                    do {
                                                        mutableBlockPos2.setY(x--);
                                                        blockState2 = levelChunk.getBlockState(mutableBlockPos2);
                                                        t++;
                                                    } while (x > level.getMinBuildHeight() && !blockState2.getFluidState().isEmpty());

                                                    blockState = this.getCorrectStateForFluidBlock(level, blockState, mutableBlockPos);
                                                }
                                            }

                                            data.checkBanners(level, mutableBlockPos.getX(), mutableBlockPos.getZ());
                                            e += (double)w / (i * i);
                                            multiset.add(blockState.getMapColor(level, mutableBlockPos));
                                        }
                                    }
                                }

                                t /= i * i;
                                MapColor mapColor = Iterables.getFirst(Multisets.copyHighestCountFirst(multiset), MapColor.NONE);
                                MapColor.Brightness brightness;
                                if (mapColor == MapColor.WATER) {
                                    double f = t * 0.1 + (o + p & 1) * 0.2;
                                    if (f < 0.5) {
                                        brightness = MapColor.Brightness.HIGH;
                                    } else if (f > 0.9) {
                                        brightness = MapColor.Brightness.LOW;
                                    } else {
                                        brightness = MapColor.Brightness.NORMAL;
                                    }
                                } else {
                                    double f = (e - d) * 4.0 / (i + 4) + ((o + p & 1) - 0.5) * 0.4;
                                    if (f > 0.6) {
                                        brightness = MapColor.Brightness.HIGH;
                                    } else if (f < -0.6) {
                                        brightness = MapColor.Brightness.LOW;
                                    } else {
                                        brightness = MapColor.Brightness.NORMAL;
                                    }
                                }

                                d = e;
                                if (p >= 0 && q < n * n && (!bl2 || (o + p & 1) != 0)) {
                                    bl |= data.updateColor(o, p, mapColor.getPackedId(brightness));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private BlockState getCorrectStateForFluidBlock(Level level, BlockState state, BlockPos pos) {
        FluidState fluidState = state.getFluidState();
        return !fluidState.isEmpty() && !state.isFaceSturdy(level, pos, Direction.UP) ? fluidState.createLegacyBlock() : state;
    }

    private static boolean isBiomeWatery(boolean[] wateryMap, int xSample, int zSample) {
        return wateryMap[zSample * 128 + xSample];
    }

    public static void renderBiomePreviewMap(ServerLevel serverLevel, ItemStack stack) {
        MapItemSavedData mapItemSavedData = getSavedData(stack, serverLevel);
        if (mapItemSavedData != null) {
            if (serverLevel.dimension() == mapItemSavedData.dimension) {
                int i = 1 << mapItemSavedData.scale;
                int j = mapItemSavedData.centerX;
                int k = mapItemSavedData.centerZ;
                boolean[] bls = new boolean[16384];
                int l = j / i - 64;
                int m = k / i - 64;
                BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

                for (int n = 0; n < 128; n++) {
                    for (int o = 0; o < 128; o++) {
                        Holder<Biome> holder = serverLevel.getBiome(mutableBlockPos.set((l + o) * i, 0, (m + n) * i));
                        bls[n * 128 + o] = holder.is(BiomeTags.WATER_ON_MAP_OUTLINES);
                    }
                }

                for (int n = 1; n < 127; n++) {
                    for (int o = 1; o < 127; o++) {
                        int p = 0;

                        for (int q = -1; q < 2; q++) {
                            for (int r = -1; r < 2; r++) {
                                if ((q != 0 || r != 0) && isBiomeWatery(bls, n + q, o + r)) {
                                    p++;
                                }
                            }
                        }

                        MapColor.Brightness brightness = MapColor.Brightness.LOWEST;
                        MapColor mapColor = MapColor.NONE;
                        if (isBiomeWatery(bls, n, o)) {
                            mapColor = MapColor.COLOR_ORANGE;
                            if (p > 7 && o % 2 == 0) {
                                switch ((n + (int)(Mth.sin(o + 0.0F) * 7.0F)) / 8 % 5) {
                                    case 0:
                                    case 4:
                                        brightness = MapColor.Brightness.LOW;
                                        break;
                                    case 1:
                                    case 3:
                                        brightness = MapColor.Brightness.NORMAL;
                                        break;
                                    case 2:
                                        brightness = MapColor.Brightness.HIGH;
                                }
                            } else if (p > 7) {
                                mapColor = MapColor.NONE;
                            } else if (p > 5) {
                                brightness = MapColor.Brightness.NORMAL;
                            } else if (p > 3) {
                                brightness = MapColor.Brightness.LOW;
                            } else if (p > 1) {
                                brightness = MapColor.Brightness.LOW;
                            }
                        } else if (p > 0) {
                            mapColor = MapColor.COLOR_BROWN;
                            if (p > 3) {
                                brightness = MapColor.Brightness.NORMAL;
                            } else {
                                brightness = MapColor.Brightness.LOWEST;
                            }
                        }

                        if (mapColor != MapColor.NONE) {
                            mapItemSavedData.setColor(n, o, mapColor.getPackedId(brightness));
                        }
                    }
                }
            }
        }
    }

    /**
     * Called each tick as long the item is in a player's inventory. Used by maps to check if it's in a player's hand and update its contents.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int itemSlot, boolean isSelected) {
        if (!level.isClientSide) {
            MapItemSavedData mapItemSavedData = getSavedData(stack, level);
            if (mapItemSavedData != null) {
                if (entity instanceof Player player) {
                    mapItemSavedData.tickCarriedBy(player, stack);
                }

                if (!mapItemSavedData.locked && (isSelected || entity instanceof Player && ((Player)entity).getOffhandItem() == stack)) {
                    this.update(level, entity, mapItemSavedData);
                }
            }
        }
    }

    @Nullable
    @Override
    public Packet<?> getUpdatePacket(ItemStack stack, Level level, Player player) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapItemSavedData = getSavedData(mapId, level);
        return mapItemSavedData != null ? mapItemSavedData.getUpdatePacket(mapId, player) : null;
    }

    @Override
    public void onCraftedPostProcess(ItemStack stack, Level level) {
        MapPostProcessing mapPostProcessing = stack.remove(DataComponents.MAP_POST_PROCESSING);
        if (mapPostProcessing != null) {
            switch (mapPostProcessing) {
                case LOCK:
                    lockMap(level, stack);
                    break;
                case SCALE:
                    scaleMap(stack, level);
            }
        }
    }

    private static void scaleMap(ItemStack stack, Level level) {
        MapItemSavedData mapItemSavedData = getSavedData(stack, level);
        if (mapItemSavedData != null) {
            MapId mapId = level.getFreeMapId();
            level.setMapData(mapId, mapItemSavedData.scaled());
            stack.set(DataComponents.MAP_ID, mapId);
        }
    }

    public static void lockMap(Level level, ItemStack stack) {
        MapItemSavedData mapItemSavedData = getSavedData(stack, level);
        if (mapItemSavedData != null) {
            MapId mapId = level.getFreeMapId();
            MapItemSavedData mapItemSavedData2 = mapItemSavedData.locked();
            level.setMapData(mapId, mapItemSavedData2);
            stack.set(DataComponents.MAP_ID, mapId);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapItemSavedData = mapId != null ? context.mapData(mapId) : null;
        MapPostProcessing mapPostProcessing = stack.get(DataComponents.MAP_POST_PROCESSING);
        if (mapItemSavedData != null && (mapItemSavedData.locked || mapPostProcessing == MapPostProcessing.LOCK)) {
            tooltipComponents.add(Component.translatable("filled_map.locked", mapId.id()).withStyle(ChatFormatting.GRAY));
        }

        if (tooltipFlag.isAdvanced()) {
            if (mapItemSavedData != null) {
                if (mapPostProcessing == null) {
                    tooltipComponents.add(getTooltipForId(mapId));
                }

                int i = mapPostProcessing == MapPostProcessing.SCALE ? 1 : 0;
                int j = Math.min(mapItemSavedData.scale + i, 4);
                tooltipComponents.add(Component.translatable("filled_map.scale", 1 << j).withStyle(ChatFormatting.GRAY));
                tooltipComponents.add(Component.translatable("filled_map.level", j, 4).withStyle(ChatFormatting.GRAY));
            } else {
                tooltipComponents.add(Component.translatable("filled_map.unknown").withStyle(ChatFormatting.GRAY));
            }
        }
    }

    public static Component getTooltipForId(MapId mapId) {
        return Component.translatable("filled_map.id", mapId.id()).withStyle(ChatFormatting.GRAY);
    }

    /**
     * Called when this item is used when targeting a Block
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState blockState = context.getLevel().getBlockState(context.getClickedPos());
        if (blockState.is(BlockTags.BANNERS)) {
            if (!context.getLevel().isClientSide) {
                MapItemSavedData mapItemSavedData = getSavedData(context.getItemInHand(), context.getLevel());
                if (mapItemSavedData != null && !mapItemSavedData.toggleBanner(context.getLevel(), context.getClickedPos())) {
                    return InteractionResult.FAIL;
                }
            }

            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        } else {
            return super.useOn(context);
        }
    }
}
