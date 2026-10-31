package net.minecraft.util.datafix.fixes;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.OptionalDynamic;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import net.minecraft.Util;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableObject;

public class ChunkHeightAndBiomeFix extends DataFix {
    public static final String DATAFIXER_CONTEXT_TAG = "__context";
    private static final String NAME = "ChunkHeightAndBiomeFix";
    private static final int OLD_SECTION_COUNT = 16;
    private static final int NEW_SECTION_COUNT = 24;
    private static final int NEW_MIN_SECTION_Y = -4;
    public static final int BLOCKS_PER_SECTION = 4096;
    private static final int LONGS_PER_SECTION = 64;
    private static final int HEIGHTMAP_BITS = 9;
    private static final long HEIGHTMAP_MASK = 511L;
    private static final int HEIGHTMAP_OFFSET = 64;
    private static final String[] HEIGHTMAP_TYPES = new String[]{
        "WORLD_SURFACE_WG", "WORLD_SURFACE", "WORLD_SURFACE_IGNORE_SNOW", "OCEAN_FLOOR_WG", "OCEAN_FLOOR", "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES"
    };
    private static final Set<String> STATUS_IS_OR_AFTER_SURFACE = Set.of(
        "surface", "carvers", "liquid_carvers", "features", "light", "spawn", "heightmaps", "full"
    );
    private static final Set<String> STATUS_IS_OR_AFTER_NOISE = Set.of(
        "noise", "surface", "carvers", "liquid_carvers", "features", "light", "spawn", "heightmaps", "full"
    );
    private static final Set<String> BLOCKS_BEFORE_FEATURE_STATUS = Set.of(
        "minecraft:air",
        "minecraft:basalt",
        "minecraft:bedrock",
        "minecraft:blackstone",
        "minecraft:calcite",
        "minecraft:cave_air",
        "minecraft:coarse_dirt",
        "minecraft:crimson_nylium",
        "minecraft:dirt",
        "minecraft:end_stone",
        "minecraft:grass_block",
        "minecraft:gravel",
        "minecraft:ice",
        "minecraft:lava",
        "minecraft:mycelium",
        "minecraft:nether_wart_block",
        "minecraft:netherrack",
        "minecraft:orange_terracotta",
        "minecraft:packed_ice",
        "minecraft:podzol",
        "minecraft:powder_snow",
        "minecraft:red_sand",
        "minecraft:red_sandstone",
        "minecraft:sand",
        "minecraft:sandstone",
        "minecraft:snow_block",
        "minecraft:soul_sand",
        "minecraft:soul_soil",
        "minecraft:stone",
        "minecraft:terracotta",
        "minecraft:warped_nylium",
        "minecraft:warped_wart_block",
        "minecraft:water",
        "minecraft:white_terracotta"
    );
    private static final int BIOME_CONTAINER_LAYER_SIZE = 16;
    private static final int BIOME_CONTAINER_SIZE = 64;
    private static final int BIOME_CONTAINER_TOP_LAYER_OFFSET = 1008;
    public static final String DEFAULT_BIOME = "minecraft:plains";
    private static final Int2ObjectMap<String> BIOMES_BY_ID = new Int2ObjectOpenHashMap<>();

    public ChunkHeightAndBiomeFix(Schema outputSchema) {
        super(outputSchema, true);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("Level");
        OpticFinder<?> opticFinder2 = opticFinder.type().findField("Sections");
        Schema schema = this.getOutputSchema();
        Type<?> type2 = schema.getType(References.CHUNK);
        Type<?> type3 = type2.findField("Level").type();
        Type<?> type4 = type3.findField("Sections").type();
        return this.fixTypeEverywhereTyped(
            "ChunkHeightAndBiomeFix",
            type,
            type2,
            typed -> typed.updateTyped(
                opticFinder,
                type3,
                typed2 -> {
                    Dynamic<?> dynamic = typed2.get(DSL.remainderFinder());
                    OptionalDynamic<?> optionalDynamic = typed.get(DSL.remainderFinder()).get("__context");
                    String string = optionalDynamic.get("dimension").asString().result().orElse("");
                    String string2 = optionalDynamic.get("generator").asString().result().orElse("");
                    boolean bl = "minecraft:overworld".equals(string);
                    MutableBoolean mutableBoolean = new MutableBoolean();
                    int i = bl ? -4 : 0;
                    Dynamic<?>[] dynamics = getBiomeContainers(dynamic, bl, i, mutableBoolean);
                    Dynamic<?> dynamic2 = makePalettedContainer(
                        dynamic.createList(Stream.of(dynamic.createMap(ImmutableMap.of(dynamic.createString("Name"), dynamic.createString("minecraft:air")))))
                    );
                    Set<String> set = Sets.newHashSet();
                    MutableObject<Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer>> mutableObject = new MutableObject<>(() -> null);
                    typed2 = typed2.updateTyped(opticFinder2, type4, typedxx -> {
                        IntSet intSet = new IntOpenHashSet();
                        Dynamic<?> dynamic3 = (Dynamic<?>)typedxx.write()
                            .result()
                            .orElseThrow(() -> new IllegalStateException("Malformed Chunk.Level.Sections"));
                        List<Dynamic<?>> list = dynamic3.asStream().map(dynamic2xx -> {
                            int jx = dynamic2xx.get("Y").asInt(0);
                            Dynamic<?> dynamic3x = DataFixUtils.orElse(dynamic2xx.get("Palette").result().flatMap(dynamic2xxx -> {
                                dynamic2xxx.asStream().map(dynamicxxxx -> dynamicxxxx.get("Name").asString("minecraft:air")).forEach(set::add);
                                return dynamic2xx.get("BlockStates").result().map(dynamic2xxxx -> makeOptimizedPalettedContainer(dynamic2xxx, dynamic2xxxx));
                            }), dynamic2);
                            Dynamic<?> dynamic4x = dynamic2xx;
                            int kx = jx - i;
                            if (kx >= 0 && kx < dynamics.length) {
                                dynamic4x = dynamic4x.set("biomes", dynamics[kx]);
                            }

                            intSet.add(jx);
                            if (dynamic2xx.get("Y").asInt(Integer.MAX_VALUE) == 0) {
                                mutableObject.setValue(() -> {
                                    List<? extends Dynamic<?>> listx = dynamic3x.get("palette").asList(Function.identity());
                                    long[] ls = dynamic3x.get("data").asLongStream().toArray();
                                    return new ChunkProtoTickListFix.PoorMansPalettedContainer(listx, ls);
                                });
                            }

                            return dynamic4x.set("block_states", dynamic3x).remove("Palette").remove("BlockStates");
                        }).collect(Collectors.toCollection(ArrayList::new));

                        for (int j = 0; j < dynamics.length; j++) {
                            int k = j + i;
                            if (intSet.add(k)) {
                                Dynamic<?> dynamic4 = dynamic.createMap(Map.of(dynamic.createString("Y"), dynamic.createInt(k)));
                                dynamic4 = dynamic4.set("block_states", dynamic2);
                                dynamic4 = dynamic4.set("biomes", dynamics[j]);
                                list.add(dynamic4);
                            }
                        }

                        return Util.readTypedOrThrow(type4, dynamic.createList(list.stream()));
                    });
                    return typed2.update(DSL.remainderFinder(), dynamicx -> {
                        if (bl) {
                            dynamicx = this.predictChunkStatusBeforeSurface(dynamicx, set);
                        }

                        return updateChunkTag(dynamicx, bl, mutableBoolean.booleanValue(), "minecraft:noise".equals(string2), mutableObject.getValue());
                    });
                }
            )
        );
    }

    private Dynamic<?> predictChunkStatusBeforeSurface(Dynamic<?> data, Set<String> blockPalette) {
        return data.update("Status", dynamic -> {
            String string = dynamic.asString("empty");
            if (STATUS_IS_OR_AFTER_SURFACE.contains(string)) {
                return dynamic;
            } else {
                blockPalette.remove("minecraft:air");
                boolean bl = !blockPalette.isEmpty();
                blockPalette.removeAll(BLOCKS_BEFORE_FEATURE_STATUS);
                boolean bl2 = !blockPalette.isEmpty();
                if (bl2) {
                    return dynamic.createString("liquid_carvers");
                } else if ("noise".equals(string) || bl) {
                    return dynamic.createString("noise");
                } else {
                    return "biomes".equals(string) ? dynamic.createString("structure_references") : dynamic;
                }
            }
        });
    }

    private static Dynamic<?>[] getBiomeContainers(Dynamic<?> data, boolean overworld, int lowestY, MutableBoolean isTallChunk) {
        Dynamic<?>[] dynamics = new Dynamic[overworld ? 24 : 16];
        int[] is = data.get("Biomes").asIntStreamOpt().result().map(IntStream::toArray).orElse(null);
        if (is != null && is.length == 1536) {
            isTallChunk.setValue(true);

            for (int i = 0; i < 24; i++) {
                int j = i;
                dynamics[i] = makeBiomeContainer(data, jx -> getOldBiome(is, j * 64 + jx));
            }
        } else if (is != null && is.length == 1024) {
            for (int i = 0; i < 16; i++) {
                int j = i - lowestY;
                int k = i;
                dynamics[j] = makeBiomeContainer(data, jx -> getOldBiome(is, k * 64 + jx));
            }

            if (overworld) {
                Dynamic<?> dynamic = makeBiomeContainer(data, i -> getOldBiome(is, i % 16));
                Dynamic<?> dynamic2 = makeBiomeContainer(data, i -> getOldBiome(is, i % 16 + 1008));

                for (int k = 0; k < 4; k++) {
                    dynamics[k] = dynamic;
                }

                for (int k = 20; k < 24; k++) {
                    dynamics[k] = dynamic2;
                }
            }
        } else {
            Arrays.fill(dynamics, makePalettedContainer(data.createList(Stream.of(data.createString("minecraft:plains")))));
        }

        return dynamics;
    }

    private static int getOldBiome(int[] biomes, int index) {
        return biomes[index] & 0xFF;
    }

    private static Dynamic<?> updateChunkTag(
        Dynamic<?> chunkTag,
        boolean overworld,
        boolean isTallChunk,
        boolean isNoiseGenerator,
        Supplier<ChunkProtoTickListFix.PoorMansPalettedContainer> paletteSupplier
    ) {
        chunkTag = chunkTag.remove("Biomes");
        if (!overworld) {
            return updateCarvingMasks(chunkTag, 16, 0);
        }

        if (isTallChunk) {
            return updateCarvingMasks(chunkTag, 24, 0);
        }

        chunkTag = updateHeightmaps(chunkTag);
        chunkTag = addPaddingEntries(chunkTag, "LiquidsToBeTicked");
        chunkTag = addPaddingEntries(chunkTag, "PostProcessing");
        chunkTag = addPaddingEntries(chunkTag, "ToBeTicked");
        chunkTag = updateCarvingMasks(chunkTag, 24, 4);
        chunkTag = chunkTag.update("UpgradeData", ChunkHeightAndBiomeFix::shiftUpgradeData);
        if (!isNoiseGenerator) {
            return chunkTag;
        }

        Optional<? extends Dynamic<?>> optional = chunkTag.get("Status").result();
        if (optional.isPresent()) {
            Dynamic<?> dynamic = (Dynamic<?>)optional.get();
            String string = dynamic.asString("");
            if (!"empty".equals(string)) {
                chunkTag = chunkTag.set(
                    "blending_data",
                    chunkTag.createMap(ImmutableMap.of(chunkTag.createString("old_noise"), chunkTag.createBoolean(STATUS_IS_OR_AFTER_NOISE.contains(string))))
                );
                ChunkProtoTickListFix.PoorMansPalettedContainer poorMansPalettedContainer = paletteSupplier.get();
                if (poorMansPalettedContainer != null) {
                    BitSet bitSet = new BitSet(256);
                    boolean bl = string.equals("noise");

                    for (int i = 0; i < 16; i++) {
                        for (int j = 0; j < 16; j++) {
                            Dynamic<?> dynamic2 = poorMansPalettedContainer.get(j, 0, i);
                            boolean bl2 = dynamic2 != null && "minecraft:bedrock".equals(dynamic2.get("Name").asString(""));
                            boolean bl3 = dynamic2 != null && "minecraft:air".equals(dynamic2.get("Name").asString(""));
                            if (bl3) {
                                bitSet.set(i * 16 + j);
                            }

                            bl |= bl2;
                        }
                    }

                    if (bl && bitSet.cardinality() != bitSet.size()) {
                        Dynamic<?> dynamic3 = "full".equals(string) ? chunkTag.createString("heightmaps") : dynamic;
                        chunkTag = chunkTag.set(
                            "below_zero_retrogen",
                            chunkTag.createMap(
                                ImmutableMap.of(
                                    chunkTag.createString("target_status"),
                                    dynamic3,
                                    chunkTag.createString("missing_bedrock"),
                                    chunkTag.createLongList(LongStream.of(bitSet.toLongArray()))
                                )
                            )
                        );
                        chunkTag = chunkTag.set("Status", chunkTag.createString("empty"));
                    }

                    chunkTag = chunkTag.set("isLightOn", chunkTag.createBoolean(false));
                }
            }
        }

        return chunkTag;
    }

    private static <T> Dynamic<T> shiftUpgradeData(Dynamic<T> data) {
        return data.update("Indices", dynamic -> {
            Map<Dynamic<?>, Dynamic<?>> map = new HashMap<>();
            dynamic.getMapValues().ifSuccess(map2 -> map2.forEach((dynamicx, dynamic2) -> {
                try {
                    dynamicx.asString().result().map(Integer::parseInt).ifPresent(integer -> {
                        int i = integer - -4;
                        map.put(dynamicx.createString(Integer.toString(i)), (Dynamic<?>)dynamic2);
                    });
                } catch (NumberFormatException var4) {
                }
            }));
            return dynamic.createMap(map);
        });
    }

    private static Dynamic<?> updateCarvingMasks(Dynamic<?> data, int sectionCount, int offset) {
        Dynamic<?> dynamic = data.get("CarvingMasks").orElseEmptyMap();
        dynamic = dynamic.updateMapValues(pair -> {
            long[] ls = BitSet.valueOf(pair.getSecond().asByteBuffer().array()).toLongArray();
            long[] ms = new long[64 * sectionCount];
            System.arraycopy(ls, 0, ms, 64 * offset, ls.length);
            return Pair.of(pair.getFirst(), data.createLongList(LongStream.of(ms)));
        });
        return data.set("CarvingMasks", dynamic);
    }

    private static Dynamic<?> addPaddingEntries(Dynamic<?> data, String key) {
        List<Dynamic<?>> list = data.get(key).orElseEmptyList().asStream().collect(Collectors.toCollection(ArrayList::new));
        if (list.size() == 24) {
            return data;
        }

        Dynamic<?> dynamic = data.emptyList();

        for (int i = 0; i < 4; i++) {
            list.add(0, dynamic);
            list.add(dynamic);
        }

        return data.set(key, data.createList(list.stream()));
    }

    private static Dynamic<?> updateHeightmaps(Dynamic<?> data) {
        return data.update("Heightmaps", dynamic -> {
            for (String string : HEIGHTMAP_TYPES) {
                dynamic = dynamic.update(string, ChunkHeightAndBiomeFix::getFixedHeightmap);
            }

            return dynamic;
        });
    }

    private static Dynamic<?> getFixedHeightmap(Dynamic<?> dynamic) {
        return dynamic.createLongList(dynamic.asLongStream().map(l -> {
            long m = 0L;

            for (int i = 0; i + 9 <= 64; i += 9) {
                long n = l >> i & 511L;
                long o;
                if (n == 0L) {
                    o = 0L;
                } else {
                    o = Math.min(n + 64L, 511L);
                }

                m |= o << i;
            }

            return m;
        }));
    }

    private static Dynamic<?> makeBiomeContainer(Dynamic<?> data, Int2IntFunction oldBiomeGetter) {
        Int2IntMap int2IntMap = new Int2IntLinkedOpenHashMap();

        for (int i = 0; i < 64; i++) {
            int j = oldBiomeGetter.applyAsInt(i);
            if (!int2IntMap.containsKey(j)) {
                int2IntMap.put(j, int2IntMap.size());
            }
        }

        Dynamic<?> dynamic = data.createList(
            int2IntMap.keySet().stream().map(integer -> data.createString(BIOMES_BY_ID.getOrDefault(integer.intValue(), "minecraft:plains")))
        );
        int j = ceillog2(int2IntMap.size());
        if (j == 0) {
            return makePalettedContainer(dynamic);
        }

        int k = 64 / j;
        int l = (64 + k - 1) / k;
        long[] ls = new long[l];
        int m = 0;
        int n = 0;

        for (int o = 0; o < 64; o++) {
            int p = oldBiomeGetter.applyAsInt(o);
            ls[m] |= (long)int2IntMap.get(p) << n;
            n += j;
            if (n + j > 64) {
                m++;
                n = 0;
            }
        }

        Dynamic<?> dynamic2 = data.createLongList(Arrays.stream(ls));
        return makePalettedContainer(dynamic, dynamic2);
    }

    private static Dynamic<?> makePalettedContainer(Dynamic<?> palette) {
        return palette.createMap(ImmutableMap.of(palette.createString("palette"), palette));
    }

    private static Dynamic<?> makePalettedContainer(Dynamic<?> palette, Dynamic<?> blockStates) {
        return palette.createMap(ImmutableMap.of(palette.createString("palette"), palette, palette.createString("data"), blockStates));
    }

    private static Dynamic<?> makeOptimizedPalettedContainer(Dynamic<?> palette, Dynamic<?> blockStates) {
        List<Dynamic<?>> list = palette.asStream().collect(Collectors.toCollection(ArrayList::new));
        if (list.size() == 1) {
            return makePalettedContainer(palette);
        }

        palette = padPaletteEntries(palette, blockStates, list);
        return makePalettedContainer(palette, blockStates);
    }

    private static Dynamic<?> padPaletteEntries(Dynamic<?> palette, Dynamic<?> blockStates, List<Dynamic<?>> paletteEntries) {
        long l = blockStates.asLongStream().count() * 64L;
        long m = l / 4096L;
        int i = paletteEntries.size();
        int j = ceillog2(i);
        if (m <= j) {
            return palette;
        }

        Dynamic<?> dynamic = palette.createMap(ImmutableMap.of(palette.createString("Name"), palette.createString("minecraft:air")));
        int k = (1 << (int)(m - 1L)) + 1;
        int n = k - i;

        for (int o = 0; o < n; o++) {
            paletteEntries.add(dynamic);
        }

        return palette.createList(paletteEntries.stream());
    }

    public static int ceillog2(int value) {
        return value == 0 ? 0 : (int)Math.ceil(Math.log(value) / Math.log(2.0));
    }

    static {
        BIOMES_BY_ID.put(0, "minecraft:ocean");
        BIOMES_BY_ID.put(1, "minecraft:plains");
        BIOMES_BY_ID.put(2, "minecraft:desert");
        BIOMES_BY_ID.put(3, "minecraft:mountains");
        BIOMES_BY_ID.put(4, "minecraft:forest");
        BIOMES_BY_ID.put(5, "minecraft:taiga");
        BIOMES_BY_ID.put(6, "minecraft:swamp");
        BIOMES_BY_ID.put(7, "minecraft:river");
        BIOMES_BY_ID.put(8, "minecraft:nether_wastes");
        BIOMES_BY_ID.put(9, "minecraft:the_end");
        BIOMES_BY_ID.put(10, "minecraft:frozen_ocean");
        BIOMES_BY_ID.put(11, "minecraft:frozen_river");
        BIOMES_BY_ID.put(12, "minecraft:snowy_tundra");
        BIOMES_BY_ID.put(13, "minecraft:snowy_mountains");
        BIOMES_BY_ID.put(14, "minecraft:mushroom_fields");
        BIOMES_BY_ID.put(15, "minecraft:mushroom_field_shore");
        BIOMES_BY_ID.put(16, "minecraft:beach");
        BIOMES_BY_ID.put(17, "minecraft:desert_hills");
        BIOMES_BY_ID.put(18, "minecraft:wooded_hills");
        BIOMES_BY_ID.put(19, "minecraft:taiga_hills");
        BIOMES_BY_ID.put(20, "minecraft:mountain_edge");
        BIOMES_BY_ID.put(21, "minecraft:jungle");
        BIOMES_BY_ID.put(22, "minecraft:jungle_hills");
        BIOMES_BY_ID.put(23, "minecraft:jungle_edge");
        BIOMES_BY_ID.put(24, "minecraft:deep_ocean");
        BIOMES_BY_ID.put(25, "minecraft:stone_shore");
        BIOMES_BY_ID.put(26, "minecraft:snowy_beach");
        BIOMES_BY_ID.put(27, "minecraft:birch_forest");
        BIOMES_BY_ID.put(28, "minecraft:birch_forest_hills");
        BIOMES_BY_ID.put(29, "minecraft:dark_forest");
        BIOMES_BY_ID.put(30, "minecraft:snowy_taiga");
        BIOMES_BY_ID.put(31, "minecraft:snowy_taiga_hills");
        BIOMES_BY_ID.put(32, "minecraft:giant_tree_taiga");
        BIOMES_BY_ID.put(33, "minecraft:giant_tree_taiga_hills");
        BIOMES_BY_ID.put(34, "minecraft:wooded_mountains");
        BIOMES_BY_ID.put(35, "minecraft:savanna");
        BIOMES_BY_ID.put(36, "minecraft:savanna_plateau");
        BIOMES_BY_ID.put(37, "minecraft:badlands");
        BIOMES_BY_ID.put(38, "minecraft:wooded_badlands_plateau");
        BIOMES_BY_ID.put(39, "minecraft:badlands_plateau");
        BIOMES_BY_ID.put(40, "minecraft:small_end_islands");
        BIOMES_BY_ID.put(41, "minecraft:end_midlands");
        BIOMES_BY_ID.put(42, "minecraft:end_highlands");
        BIOMES_BY_ID.put(43, "minecraft:end_barrens");
        BIOMES_BY_ID.put(44, "minecraft:warm_ocean");
        BIOMES_BY_ID.put(45, "minecraft:lukewarm_ocean");
        BIOMES_BY_ID.put(46, "minecraft:cold_ocean");
        BIOMES_BY_ID.put(47, "minecraft:deep_warm_ocean");
        BIOMES_BY_ID.put(48, "minecraft:deep_lukewarm_ocean");
        BIOMES_BY_ID.put(49, "minecraft:deep_cold_ocean");
        BIOMES_BY_ID.put(50, "minecraft:deep_frozen_ocean");
        BIOMES_BY_ID.put(127, "minecraft:the_void");
        BIOMES_BY_ID.put(129, "minecraft:sunflower_plains");
        BIOMES_BY_ID.put(130, "minecraft:desert_lakes");
        BIOMES_BY_ID.put(131, "minecraft:gravelly_mountains");
        BIOMES_BY_ID.put(132, "minecraft:flower_forest");
        BIOMES_BY_ID.put(133, "minecraft:taiga_mountains");
        BIOMES_BY_ID.put(134, "minecraft:swamp_hills");
        BIOMES_BY_ID.put(140, "minecraft:ice_spikes");
        BIOMES_BY_ID.put(149, "minecraft:modified_jungle");
        BIOMES_BY_ID.put(151, "minecraft:modified_jungle_edge");
        BIOMES_BY_ID.put(155, "minecraft:tall_birch_forest");
        BIOMES_BY_ID.put(156, "minecraft:tall_birch_hills");
        BIOMES_BY_ID.put(157, "minecraft:dark_forest_hills");
        BIOMES_BY_ID.put(158, "minecraft:snowy_taiga_mountains");
        BIOMES_BY_ID.put(160, "minecraft:giant_spruce_taiga");
        BIOMES_BY_ID.put(161, "minecraft:giant_spruce_taiga_hills");
        BIOMES_BY_ID.put(162, "minecraft:modified_gravelly_mountains");
        BIOMES_BY_ID.put(163, "minecraft:shattered_savanna");
        BIOMES_BY_ID.put(164, "minecraft:shattered_savanna_plateau");
        BIOMES_BY_ID.put(165, "minecraft:eroded_badlands");
        BIOMES_BY_ID.put(166, "minecraft:modified_wooded_badlands_plateau");
        BIOMES_BY_ID.put(167, "minecraft:modified_badlands_plateau");
        BIOMES_BY_ID.put(168, "minecraft:bamboo_jungle");
        BIOMES_BY_ID.put(169, "minecraft:bamboo_jungle_hills");
        BIOMES_BY_ID.put(170, "minecraft:soul_sand_valley");
        BIOMES_BY_ID.put(171, "minecraft:crimson_forest");
        BIOMES_BY_ID.put(172, "minecraft:warped_forest");
        BIOMES_BY_ID.put(173, "minecraft:basalt_deltas");
        BIOMES_BY_ID.put(174, "minecraft:dripstone_caves");
        BIOMES_BY_ID.put(175, "minecraft:lush_caves");
        BIOMES_BY_ID.put(177, "minecraft:meadow");
        BIOMES_BY_ID.put(178, "minecraft:grove");
        BIOMES_BY_ID.put(179, "minecraft:snowy_slopes");
        BIOMES_BY_ID.put(180, "minecraft:snowcapped_peaks");
        BIOMES_BY_ID.put(181, "minecraft:lofty_peaks");
        BIOMES_BY_ID.put(182, "minecraft:stony_peaks");
    }
}
