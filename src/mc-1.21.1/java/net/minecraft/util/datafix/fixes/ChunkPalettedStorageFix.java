package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.util.CrudeIncrementalIntIdentityHashBiMap;
import net.minecraft.util.datafix.PackedBitStorage;
import org.slf4j.Logger;

public class ChunkPalettedStorageFix extends DataFix {
    private static final int NORTH_WEST_MASK = 128;
    private static final int WEST_MASK = 64;
    private static final int SOUTH_WEST_MASK = 32;
    private static final int SOUTH_MASK = 16;
    private static final int SOUTH_EAST_MASK = 8;
    private static final int EAST_MASK = 4;
    private static final int NORTH_EAST_MASK = 2;
    private static final int NORTH_MASK = 1;
    static final Logger LOGGER = LogUtils.getLogger();
    static final BitSet VIRTUAL = new BitSet(256);
    static final BitSet FIX = new BitSet(256);
    static final Dynamic<?> PUMPKIN = BlockStateData.parse("{Name:'minecraft:pumpkin'}");
    static final Dynamic<?> SNOWY_PODZOL = BlockStateData.parse("{Name:'minecraft:podzol',Properties:{snowy:'true'}}");
    static final Dynamic<?> SNOWY_GRASS = BlockStateData.parse("{Name:'minecraft:grass_block',Properties:{snowy:'true'}}");
    static final Dynamic<?> SNOWY_MYCELIUM = BlockStateData.parse("{Name:'minecraft:mycelium',Properties:{snowy:'true'}}");
    static final Dynamic<?> UPPER_SUNFLOWER = BlockStateData.parse("{Name:'minecraft:sunflower',Properties:{half:'upper'}}");
    static final Dynamic<?> UPPER_LILAC = BlockStateData.parse("{Name:'minecraft:lilac',Properties:{half:'upper'}}");
    static final Dynamic<?> UPPER_TALL_GRASS = BlockStateData.parse("{Name:'minecraft:tall_grass',Properties:{half:'upper'}}");
    static final Dynamic<?> UPPER_LARGE_FERN = BlockStateData.parse("{Name:'minecraft:large_fern',Properties:{half:'upper'}}");
    static final Dynamic<?> UPPER_ROSE_BUSH = BlockStateData.parse("{Name:'minecraft:rose_bush',Properties:{half:'upper'}}");
    static final Dynamic<?> UPPER_PEONY = BlockStateData.parse("{Name:'minecraft:peony',Properties:{half:'upper'}}");
    static final Map<String, Dynamic<?>> FLOWER_POT_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
        map.put("minecraft:air0", BlockStateData.parse("{Name:'minecraft:flower_pot'}"));
        map.put("minecraft:red_flower0", BlockStateData.parse("{Name:'minecraft:potted_poppy'}"));
        map.put("minecraft:red_flower1", BlockStateData.parse("{Name:'minecraft:potted_blue_orchid'}"));
        map.put("minecraft:red_flower2", BlockStateData.parse("{Name:'minecraft:potted_allium'}"));
        map.put("minecraft:red_flower3", BlockStateData.parse("{Name:'minecraft:potted_azure_bluet'}"));
        map.put("minecraft:red_flower4", BlockStateData.parse("{Name:'minecraft:potted_red_tulip'}"));
        map.put("minecraft:red_flower5", BlockStateData.parse("{Name:'minecraft:potted_orange_tulip'}"));
        map.put("minecraft:red_flower6", BlockStateData.parse("{Name:'minecraft:potted_white_tulip'}"));
        map.put("minecraft:red_flower7", BlockStateData.parse("{Name:'minecraft:potted_pink_tulip'}"));
        map.put("minecraft:red_flower8", BlockStateData.parse("{Name:'minecraft:potted_oxeye_daisy'}"));
        map.put("minecraft:yellow_flower0", BlockStateData.parse("{Name:'minecraft:potted_dandelion'}"));
        map.put("minecraft:sapling0", BlockStateData.parse("{Name:'minecraft:potted_oak_sapling'}"));
        map.put("minecraft:sapling1", BlockStateData.parse("{Name:'minecraft:potted_spruce_sapling'}"));
        map.put("minecraft:sapling2", BlockStateData.parse("{Name:'minecraft:potted_birch_sapling'}"));
        map.put("minecraft:sapling3", BlockStateData.parse("{Name:'minecraft:potted_jungle_sapling'}"));
        map.put("minecraft:sapling4", BlockStateData.parse("{Name:'minecraft:potted_acacia_sapling'}"));
        map.put("minecraft:sapling5", BlockStateData.parse("{Name:'minecraft:potted_dark_oak_sapling'}"));
        map.put("minecraft:red_mushroom0", BlockStateData.parse("{Name:'minecraft:potted_red_mushroom'}"));
        map.put("minecraft:brown_mushroom0", BlockStateData.parse("{Name:'minecraft:potted_brown_mushroom'}"));
        map.put("minecraft:deadbush0", BlockStateData.parse("{Name:'minecraft:potted_dead_bush'}"));
        map.put("minecraft:tallgrass2", BlockStateData.parse("{Name:'minecraft:potted_fern'}"));
        map.put("minecraft:cactus0", BlockStateData.getTag(2240));
    });
    static final Map<String, Dynamic<?>> SKULL_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
        mapSkull(map, 0, "skeleton", "skull");
        mapSkull(map, 1, "wither_skeleton", "skull");
        mapSkull(map, 2, "zombie", "head");
        mapSkull(map, 3, "player", "head");
        mapSkull(map, 4, "creeper", "head");
        mapSkull(map, 5, "dragon", "head");
    });
    static final Map<String, Dynamic<?>> DOOR_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
        mapDoor(map, "oak_door", 1024);
        mapDoor(map, "iron_door", 1136);
        mapDoor(map, "spruce_door", 3088);
        mapDoor(map, "birch_door", 3104);
        mapDoor(map, "jungle_door", 3120);
        mapDoor(map, "acacia_door", 3136);
        mapDoor(map, "dark_oak_door", 3152);
    });
    static final Map<String, Dynamic<?>> NOTE_BLOCK_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
        for (int i = 0; i < 26; i++) {
            map.put("true" + i, BlockStateData.parse("{Name:'minecraft:note_block',Properties:{powered:'true',note:'" + i + "'}}"));
            map.put("false" + i, BlockStateData.parse("{Name:'minecraft:note_block',Properties:{powered:'false',note:'" + i + "'}}"));
        }
    });
    private static final Int2ObjectMap<String> DYE_COLOR_MAP = DataFixUtils.make(new Int2ObjectOpenHashMap<>(), map -> {
        map.put(0, "white");
        map.put(1, "orange");
        map.put(2, "magenta");
        map.put(3, "light_blue");
        map.put(4, "yellow");
        map.put(5, "lime");
        map.put(6, "pink");
        map.put(7, "gray");
        map.put(8, "light_gray");
        map.put(9, "cyan");
        map.put(10, "purple");
        map.put(11, "blue");
        map.put(12, "brown");
        map.put(13, "green");
        map.put(14, "red");
        map.put(15, "black");
    });
    static final Map<String, Dynamic<?>> BED_BLOCK_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
        for (Entry<String> entry : DYE_COLOR_MAP.int2ObjectEntrySet()) {
            if (!Objects.equals(entry.getValue(), "red")) {
                addBeds(map, entry.getIntKey(), entry.getValue());
            }
        }
    });
    static final Map<String, Dynamic<?>> BANNER_BLOCK_MAP = DataFixUtils.make(Maps.newHashMap(), map -> {
        for (Entry<String> entry : DYE_COLOR_MAP.int2ObjectEntrySet()) {
            if (!Objects.equals(entry.getValue(), "white")) {
                addBanners(map, 15 - entry.getIntKey(), entry.getValue());
            }
        }
    });
    static final Dynamic<?> AIR = BlockStateData.getTag(0);
    private static final int SIZE = 4096;

    public ChunkPalettedStorageFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    private static void mapSkull(Map<String, Dynamic<?>> map, int id, String skullType, String suffix) {
        map.put(id + "north", BlockStateData.parse("{Name:'minecraft:" + skullType + "_wall_" + suffix + "',Properties:{facing:'north'}}"));
        map.put(id + "east", BlockStateData.parse("{Name:'minecraft:" + skullType + "_wall_" + suffix + "',Properties:{facing:'east'}}"));
        map.put(id + "south", BlockStateData.parse("{Name:'minecraft:" + skullType + "_wall_" + suffix + "',Properties:{facing:'south'}}"));
        map.put(id + "west", BlockStateData.parse("{Name:'minecraft:" + skullType + "_wall_" + suffix + "',Properties:{facing:'west'}}"));

        for (int i = 0; i < 16; i++) {
            map.put("" + id + i, BlockStateData.parse("{Name:'minecraft:" + skullType + "_" + suffix + "',Properties:{rotation:'" + i + "'}}"));
        }
    }

    private static void mapDoor(Map<String, Dynamic<?>> map, String doorId, int id) {
        map.put(
            "minecraft:" + doorId + "eastlowerleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "eastlowerleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "eastlowerlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "eastlowerlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "eastlowerrightfalsefalse", BlockStateData.getTag(id));
        map.put(
            "minecraft:" + doorId + "eastlowerrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "eastlowerrighttruefalse", BlockStateData.getTag(id + 4));
        map.put(
            "minecraft:" + doorId + "eastlowerrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'lower',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "eastupperleftfalsefalse", BlockStateData.getTag(id + 8));
        map.put("minecraft:" + doorId + "eastupperleftfalsetrue", BlockStateData.getTag(id + 10));
        map.put(
            "minecraft:" + doorId + "eastupperlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'upper',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "eastupperlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'upper',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "eastupperrightfalsefalse", BlockStateData.getTag(id + 9));
        map.put("minecraft:" + doorId + "eastupperrightfalsetrue", BlockStateData.getTag(id + 11));
        map.put(
            "minecraft:" + doorId + "eastupperrighttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'upper',hinge:'right',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "eastupperrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'east',half:'upper',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northlowerleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northlowerleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northlowerlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northlowerlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "northlowerrightfalsefalse", BlockStateData.getTag(id + 3));
        map.put(
            "minecraft:" + doorId + "northlowerrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "northlowerrighttruefalse", BlockStateData.getTag(id + 7));
        map.put(
            "minecraft:" + doorId + "northlowerrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'lower',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperrightfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'right',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperrighttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'right',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "northupperrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'north',half:'upper',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southlowerleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southlowerleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southlowerlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southlowerlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "southlowerrightfalsefalse", BlockStateData.getTag(id + 1));
        map.put(
            "minecraft:" + doorId + "southlowerrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "southlowerrighttruefalse", BlockStateData.getTag(id + 5));
        map.put(
            "minecraft:" + doorId + "southlowerrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'lower',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperrightfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'right',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperrighttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'right',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "southupperrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'south',half:'upper',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westlowerleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westlowerleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westlowerlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westlowerlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "westlowerrightfalsefalse", BlockStateData.getTag(id + 2));
        map.put(
            "minecraft:" + doorId + "westlowerrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put("minecraft:" + doorId + "westlowerrighttruefalse", BlockStateData.getTag(id + 6));
        map.put(
            "minecraft:" + doorId + "westlowerrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'lower',hinge:'right',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperleftfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'left',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperleftfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'left',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperlefttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'left',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperlefttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'left',open:'true',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperrightfalsefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'right',open:'false',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperrightfalsetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'right',open:'false',powered:'true'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperrighttruefalse",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'right',open:'true',powered:'false'}}")
        );
        map.put(
            "minecraft:" + doorId + "westupperrighttruetrue",
            BlockStateData.parse("{Name:'minecraft:" + doorId + "',Properties:{facing:'west',half:'upper',hinge:'right',open:'true',powered:'true'}}")
        );
    }

    private static void addBeds(Map<String, Dynamic<?>> map, int id, String bedColor) {
        map.put("southfalsefoot" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'south',occupied:'false',part:'foot'}}"));
        map.put("westfalsefoot" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'west',occupied:'false',part:'foot'}}"));
        map.put("northfalsefoot" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'north',occupied:'false',part:'foot'}}"));
        map.put("eastfalsefoot" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'east',occupied:'false',part:'foot'}}"));
        map.put("southfalsehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'south',occupied:'false',part:'head'}}"));
        map.put("westfalsehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'west',occupied:'false',part:'head'}}"));
        map.put("northfalsehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'north',occupied:'false',part:'head'}}"));
        map.put("eastfalsehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'east',occupied:'false',part:'head'}}"));
        map.put("southtruehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'south',occupied:'true',part:'head'}}"));
        map.put("westtruehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'west',occupied:'true',part:'head'}}"));
        map.put("northtruehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'north',occupied:'true',part:'head'}}"));
        map.put("easttruehead" + id, BlockStateData.parse("{Name:'minecraft:" + bedColor + "_bed',Properties:{facing:'east',occupied:'true',part:'head'}}"));
    }

    private static void addBanners(Map<String, Dynamic<?>> map, int id, String bannerColor) {
        for (int i = 0; i < 16; i++) {
            map.put(i + "_" + id, BlockStateData.parse("{Name:'minecraft:" + bannerColor + "_banner',Properties:{rotation:'" + i + "'}}"));
        }

        map.put("north_" + id, BlockStateData.parse("{Name:'minecraft:" + bannerColor + "_wall_banner',Properties:{facing:'north'}}"));
        map.put("south_" + id, BlockStateData.parse("{Name:'minecraft:" + bannerColor + "_wall_banner',Properties:{facing:'south'}}"));
        map.put("west_" + id, BlockStateData.parse("{Name:'minecraft:" + bannerColor + "_wall_banner',Properties:{facing:'west'}}"));
        map.put("east_" + id, BlockStateData.parse("{Name:'minecraft:" + bannerColor + "_wall_banner',Properties:{facing:'east'}}"));
    }

    public static String getName(Dynamic<?> data) {
        return data.get("Name").asString("");
    }

    public static String getProperty(Dynamic<?> data, String key) {
        return data.get("Properties").get(key).asString("");
    }

    public static int idFor(CrudeIncrementalIntIdentityHashBiMap<Dynamic<?>> palette, Dynamic<?> data) {
        int i = palette.getId(data);
        if (i == -1) {
            i = palette.add(data);
        }

        return i;
    }

    private Dynamic<?> fix(Dynamic<?> dynamic) {
        Optional<? extends Dynamic<?>> optional = dynamic.get("Level").result();
        return optional.isPresent() && optional.get().get("Sections").asStreamOpt().result().isPresent()
            ? dynamic.set("Level", new ChunkPalettedStorageFix.UpgradeChunk((Dynamic<?>)optional.get()).write())
            : dynamic;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        Type<?> type2 = this.getOutputSchema().getType(References.CHUNK);
        return this.writeFixAndRead("ChunkPalettedStorageFix", type, type2, this::fix);
    }

    public static int getSideMask(boolean west, boolean east, boolean north, boolean south) {
        int i = 0;
        if (north) {
            if (east) {
                i |= 2;
            } else if (west) {
                i |= 128;
            } else {
                i |= 1;
            }
        } else if (south) {
            if (west) {
                i |= 32;
            } else if (east) {
                i |= 8;
            } else {
                i |= 16;
            }
        } else if (east) {
            i |= 4;
        } else if (west) {
            i |= 64;
        }

        return i;
    }

    static {
        FIX.set(2);
        FIX.set(3);
        FIX.set(110);
        FIX.set(140);
        FIX.set(144);
        FIX.set(25);
        FIX.set(86);
        FIX.set(26);
        FIX.set(176);
        FIX.set(177);
        FIX.set(175);
        FIX.set(64);
        FIX.set(71);
        FIX.set(193);
        FIX.set(194);
        FIX.set(195);
        FIX.set(196);
        FIX.set(197);
        VIRTUAL.set(54);
        VIRTUAL.set(146);
        VIRTUAL.set(25);
        VIRTUAL.set(26);
        VIRTUAL.set(51);
        VIRTUAL.set(53);
        VIRTUAL.set(67);
        VIRTUAL.set(108);
        VIRTUAL.set(109);
        VIRTUAL.set(114);
        VIRTUAL.set(128);
        VIRTUAL.set(134);
        VIRTUAL.set(135);
        VIRTUAL.set(136);
        VIRTUAL.set(156);
        VIRTUAL.set(163);
        VIRTUAL.set(164);
        VIRTUAL.set(180);
        VIRTUAL.set(203);
        VIRTUAL.set(55);
        VIRTUAL.set(85);
        VIRTUAL.set(113);
        VIRTUAL.set(188);
        VIRTUAL.set(189);
        VIRTUAL.set(190);
        VIRTUAL.set(191);
        VIRTUAL.set(192);
        VIRTUAL.set(93);
        VIRTUAL.set(94);
        VIRTUAL.set(101);
        VIRTUAL.set(102);
        VIRTUAL.set(160);
        VIRTUAL.set(106);
        VIRTUAL.set(107);
        VIRTUAL.set(183);
        VIRTUAL.set(184);
        VIRTUAL.set(185);
        VIRTUAL.set(186);
        VIRTUAL.set(187);
        VIRTUAL.set(132);
        VIRTUAL.set(139);
        VIRTUAL.set(199);
    }

    static class DataLayer {
        private static final int SIZE = 2048;
        private static final int NIBBLE_SIZE = 4;
        private final byte[] data;

        public DataLayer() {
            this.data = new byte[2048];
        }

        public DataLayer(byte[] data) {
            this.data = data;
            if (data.length != 2048) {
                throw new IllegalArgumentException("ChunkNibbleArrays should be 2048 bytes not: " + data.length);
            }
        }

        public int get(int i, int j, int k) {
            int l = this.getPosition(j << 8 | k << 4 | i);
            return this.isFirst(j << 8 | k << 4 | i) ? this.data[l] & 15 : this.data[l] >> 4 & 15;
        }

        private boolean isFirst(int i) {
            return (i & 1) == 0;
        }

        private int getPosition(int i) {
            return i >> 1;
        }
    }

    public enum Direction {
        DOWN(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.Y),
        UP(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.Y),
        NORTH(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.Z),
        SOUTH(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.Z),
        WEST(ChunkPalettedStorageFix.Direction.AxisDirection.NEGATIVE, ChunkPalettedStorageFix.Direction.Axis.X),
        EAST(ChunkPalettedStorageFix.Direction.AxisDirection.POSITIVE, ChunkPalettedStorageFix.Direction.Axis.X);

        private final ChunkPalettedStorageFix.Direction.Axis axis;
        private final ChunkPalettedStorageFix.Direction.AxisDirection axisDirection;

        Direction(final ChunkPalettedStorageFix.Direction.AxisDirection axisDirection, final ChunkPalettedStorageFix.Direction.Axis axis) {
            this.axis = axis;
            this.axisDirection = axisDirection;
        }

        public ChunkPalettedStorageFix.Direction.AxisDirection getAxisDirection() {
            return this.axisDirection;
        }

        public ChunkPalettedStorageFix.Direction.Axis getAxis() {
            return this.axis;
        }

        public enum Axis {
            X,
            Y,
            Z;
        }

        public enum AxisDirection {
            POSITIVE(1),
            NEGATIVE(-1);

            private final int step;

            AxisDirection(final int step) {
                this.step = step;
            }

            public int getStep() {
                return this.step;
            }
        }
    }

    static class Section {
        private final CrudeIncrementalIntIdentityHashBiMap<Dynamic<?>> palette = CrudeIncrementalIntIdentityHashBiMap.create(32);
        private final List<Dynamic<?>> listTag;
        private final Dynamic<?> section;
        private final boolean hasData;
        final Int2ObjectMap<IntList> toFix = new Int2ObjectLinkedOpenHashMap<>();
        final IntList update = new IntArrayList();
        public final int y;
        private final Set<Dynamic<?>> seen = Sets.newIdentityHashSet();
        private final int[] buffer = new int[4096];

        public Section(Dynamic<?> section) {
            this.listTag = Lists.newArrayList();
            this.section = section;
            this.y = section.get("Y").asInt(0);
            this.hasData = section.get("Blocks").result().isPresent();
        }

        public Dynamic<?> getBlock(int id) {
            if (id >= 0 && id <= 4095) {
                Dynamic<?> dynamic = this.palette.byId(this.buffer[id]);
                return dynamic == null ? ChunkPalettedStorageFix.AIR : dynamic;
            } else {
                return ChunkPalettedStorageFix.AIR;
            }
        }

        public void setBlock(int index, Dynamic<?> data) {
            if (this.seen.add(data)) {
                this.listTag.add("%%FILTER_ME%%".equals(ChunkPalettedStorageFix.getName(data)) ? ChunkPalettedStorageFix.AIR : data);
            }

            this.buffer[index] = ChunkPalettedStorageFix.idFor(this.palette, data);
        }

        public int upgrade(int i) {
            if (!this.hasData) {
                return i;
            }

            ByteBuffer byteBuffer = this.section.get("Blocks").asByteBufferOpt().result().get();
            ChunkPalettedStorageFix.DataLayer dataLayer = this.section
                .get("Data")
                .asByteBufferOpt()
                .map(byteBufferx -> new ChunkPalettedStorageFix.DataLayer(DataFixUtils.toArray(byteBufferx)))
                .result()
                .orElseGet(ChunkPalettedStorageFix.DataLayer::new);
            ChunkPalettedStorageFix.DataLayer dataLayer2 = this.section
                .get("Add")
                .asByteBufferOpt()
                .map(byteBufferx -> new ChunkPalettedStorageFix.DataLayer(DataFixUtils.toArray(byteBufferx)))
                .result()
                .orElseGet(ChunkPalettedStorageFix.DataLayer::new);
            this.seen.add(ChunkPalettedStorageFix.AIR);
            ChunkPalettedStorageFix.idFor(this.palette, ChunkPalettedStorageFix.AIR);
            this.listTag.add(ChunkPalettedStorageFix.AIR);

            for (int j = 0; j < 4096; j++) {
                int k = j & 15;
                int l = j >> 8 & 15;
                int m = j >> 4 & 15;
                int n = dataLayer2.get(k, l, m) << 12 | (byteBuffer.get(j) & 255) << 4 | dataLayer.get(k, l, m);
                if (ChunkPalettedStorageFix.FIX.get(n >> 4)) {
                    this.addFix(n >> 4, j);
                }

                if (ChunkPalettedStorageFix.VIRTUAL.get(n >> 4)) {
                    int o = ChunkPalettedStorageFix.getSideMask(k == 0, k == 15, m == 0, m == 15);
                    if (o == 0) {
                        this.update.add(j);
                    } else {
                        i |= o;
                    }
                }

                this.setBlock(j, BlockStateData.getTag(n));
            }

            return i;
        }

        private void addFix(int i, int j) {
            IntList intList = this.toFix.get(i);
            if (intList == null) {
                intList = new IntArrayList();
                this.toFix.put(i, intList);
            }

            intList.add(j);
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.section;
            if (!this.hasData) {
                return dynamic;
            }

            dynamic = dynamic.set("Palette", dynamic.createList(this.listTag.stream()));
            int i = Math.max(4, DataFixUtils.ceillog2(this.seen.size()));
            PackedBitStorage packedBitStorage = new PackedBitStorage(i, 4096);

            for (int j = 0; j < this.buffer.length; j++) {
                packedBitStorage.set(j, this.buffer[j]);
            }

            dynamic = dynamic.set("BlockStates", dynamic.createLongList(Arrays.stream(packedBitStorage.getRaw())));
            dynamic = dynamic.remove("Blocks");
            dynamic = dynamic.remove("Data");
            return dynamic.remove("Add");
        }
    }

    static final class UpgradeChunk {
        private int sides;
        private final ChunkPalettedStorageFix.Section[] sections = new ChunkPalettedStorageFix.Section[16];
        private final Dynamic<?> level;
        private final int x;
        private final int z;
        private final Int2ObjectMap<Dynamic<?>> blockEntities = new Int2ObjectLinkedOpenHashMap<>(16);

        public UpgradeChunk(Dynamic<?> level) {
            this.level = level;
            this.x = level.get("xPos").asInt(0) << 4;
            this.z = level.get("zPos").asInt(0) << 4;
            level.get("TileEntities").asStreamOpt().ifSuccess(stream -> stream.forEach(dynamicx -> {
                int ix = dynamicx.get("x").asInt(0) - this.x & 15;
                int jx = dynamicx.get("y").asInt(0);
                int k = dynamicx.get("z").asInt(0) - this.z & 15;
                int l = jx << 8 | k << 4 | ix;
                if (this.blockEntities.put(l, dynamicx) != null) {
                    ChunkPalettedStorageFix.LOGGER.warn("In chunk: {}x{} found a duplicate block entity at position: [{}, {}, {}]", this.x, this.z, ix, jx, k);
                }
            }));
            boolean bl = level.get("convertedFromAlphaFormat").asBoolean(false);
            level.get("Sections").asStreamOpt().ifSuccess(stream -> stream.forEach(dynamicx -> {
                ChunkPalettedStorageFix.Section sectionx = new ChunkPalettedStorageFix.Section(dynamicx);
                this.sides = sectionx.upgrade(this.sides);
                this.sections[sectionx.y] = sectionx;
            }));

            for (ChunkPalettedStorageFix.Section section : this.sections) {
                if (section != null) {
                    for (java.util.Map.Entry<Integer, IntList> entry : section.toFix.entrySet()) {
                        int i = section.y << 12;
                        switch (entry.getKey()) {
                            case 2:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.getBlock(j);
                                    if ("minecraft:grass_block".equals(ChunkPalettedStorageFix.getName(dynamic))) {
                                        String string = ChunkPalettedStorageFix.getName(this.getBlock(relative(j, ChunkPalettedStorageFix.Direction.UP)));
                                        if ("minecraft:snow".equals(string) || "minecraft:snow_layer".equals(string)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.SNOWY_GRASS);
                                        }
                                    }
                                }
                                break;
                            case 3:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.getBlock(j);
                                    if ("minecraft:podzol".equals(ChunkPalettedStorageFix.getName(dynamic))) {
                                        String string = ChunkPalettedStorageFix.getName(this.getBlock(relative(j, ChunkPalettedStorageFix.Direction.UP)));
                                        if ("minecraft:snow".equals(string) || "minecraft:snow_layer".equals(string)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.SNOWY_PODZOL);
                                        }
                                    }
                                }
                                break;
                            case 25:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.removeBlockEntity(j);
                                    if (dynamic != null) {
                                        String string = Boolean.toString(dynamic.get("powered").asBoolean(false))
                                            + (byte)Math.min(Math.max(dynamic.get("note").asInt(0), 0), 24);
                                        this.setBlock(
                                            j,
                                            ChunkPalettedStorageFix.NOTE_BLOCK_MAP.getOrDefault(string, ChunkPalettedStorageFix.NOTE_BLOCK_MAP.get("false0"))
                                        );
                                    }
                                }
                                break;
                            case 26:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.getBlockEntity(j);
                                    Dynamic<?> dynamic2 = this.getBlock(j);
                                    if (dynamic != null) {
                                        int k = dynamic.get("color").asInt(0);
                                        if (k != 14 && k >= 0 && k < 16) {
                                            String string2 = ChunkPalettedStorageFix.getProperty(dynamic2, "facing")
                                                + ChunkPalettedStorageFix.getProperty(dynamic2, "occupied")
                                                + ChunkPalettedStorageFix.getProperty(dynamic2, "part")
                                                + k;
                                            if (ChunkPalettedStorageFix.BED_BLOCK_MAP.containsKey(string2)) {
                                                this.setBlock(j, ChunkPalettedStorageFix.BED_BLOCK_MAP.get(string2));
                                            }
                                        }
                                    }
                                }
                                break;
                            case 64:
                            case 71:
                            case 193:
                            case 194:
                            case 195:
                            case 196:
                            case 197:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.getBlock(j);
                                    if (ChunkPalettedStorageFix.getName(dynamic).endsWith("_door")) {
                                        Dynamic<?> dynamic2 = this.getBlock(j);
                                        if ("lower".equals(ChunkPalettedStorageFix.getProperty(dynamic2, "half"))) {
                                            int k = relative(j, ChunkPalettedStorageFix.Direction.UP);
                                            Dynamic<?> dynamic3 = this.getBlock(k);
                                            String string4 = ChunkPalettedStorageFix.getName(dynamic2);
                                            if (string4.equals(ChunkPalettedStorageFix.getName(dynamic3))) {
                                                String string5 = ChunkPalettedStorageFix.getProperty(dynamic2, "facing");
                                                String string6 = ChunkPalettedStorageFix.getProperty(dynamic2, "open");
                                                String string7 = bl ? "left" : ChunkPalettedStorageFix.getProperty(dynamic3, "hinge");
                                                String string8 = bl ? "false" : ChunkPalettedStorageFix.getProperty(dynamic3, "powered");
                                                this.setBlock(
                                                    j, ChunkPalettedStorageFix.DOOR_MAP.get(string4 + string5 + "lower" + string7 + string6 + string8)
                                                );
                                                this.setBlock(
                                                    k, ChunkPalettedStorageFix.DOOR_MAP.get(string4 + string5 + "upper" + string7 + string6 + string8)
                                                );
                                            }
                                        }
                                    }
                                }
                                break;
                            case 86:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.getBlock(j);
                                    if ("minecraft:carved_pumpkin".equals(ChunkPalettedStorageFix.getName(dynamic))) {
                                        String string = ChunkPalettedStorageFix.getName(this.getBlock(relative(j, ChunkPalettedStorageFix.Direction.DOWN)));
                                        if ("minecraft:grass_block".equals(string) || "minecraft:dirt".equals(string)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.PUMPKIN);
                                        }
                                    }
                                }
                                break;
                            case 110:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.getBlock(j);
                                    if ("minecraft:mycelium".equals(ChunkPalettedStorageFix.getName(dynamic))) {
                                        String string = ChunkPalettedStorageFix.getName(this.getBlock(relative(j, ChunkPalettedStorageFix.Direction.UP)));
                                        if ("minecraft:snow".equals(string) || "minecraft:snow_layer".equals(string)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.SNOWY_MYCELIUM);
                                        }
                                    }
                                }
                                break;
                            case 140:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.removeBlockEntity(j);
                                    if (dynamic != null) {
                                        String string = dynamic.get("Item").asString("") + dynamic.get("Data").asInt(0);
                                        this.setBlock(
                                            j,
                                            ChunkPalettedStorageFix.FLOWER_POT_MAP
                                                .getOrDefault(string, ChunkPalettedStorageFix.FLOWER_POT_MAP.get("minecraft:air0"))
                                        );
                                    }
                                }
                                break;
                            case 144:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.getBlockEntity(j);
                                    if (dynamic != null) {
                                        String string = String.valueOf(dynamic.get("SkullType").asInt(0));
                                        String string3 = ChunkPalettedStorageFix.getProperty(this.getBlock(j), "facing");
                                        String string2;
                                        if (!"up".equals(string3) && !"down".equals(string3)) {
                                            string2 = string + string3;
                                        } else {
                                            string2 = string + dynamic.get("Rot").asInt(0);
                                        }

                                        dynamic.remove("SkullType");
                                        dynamic.remove("facing");
                                        dynamic.remove("Rot");
                                        this.setBlock(
                                            j, ChunkPalettedStorageFix.SKULL_MAP.getOrDefault(string2, ChunkPalettedStorageFix.SKULL_MAP.get("0north"))
                                        );
                                    }
                                }
                                break;
                            case 175:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.getBlock(j);
                                    if ("upper".equals(ChunkPalettedStorageFix.getProperty(dynamic, "half"))) {
                                        Dynamic<?> dynamic2 = this.getBlock(relative(j, ChunkPalettedStorageFix.Direction.DOWN));
                                        String string3 = ChunkPalettedStorageFix.getName(dynamic2);
                                        if ("minecraft:sunflower".equals(string3)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.UPPER_SUNFLOWER);
                                        } else if ("minecraft:lilac".equals(string3)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.UPPER_LILAC);
                                        } else if ("minecraft:tall_grass".equals(string3)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.UPPER_TALL_GRASS);
                                        } else if ("minecraft:large_fern".equals(string3)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.UPPER_LARGE_FERN);
                                        } else if ("minecraft:rose_bush".equals(string3)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.UPPER_ROSE_BUSH);
                                        } else if ("minecraft:peony".equals(string3)) {
                                            this.setBlock(j, ChunkPalettedStorageFix.UPPER_PEONY);
                                        }
                                    }
                                }
                                break;
                            case 176:
                            case 177:
                                for (int j : entry.getValue()) {
                                    j |= i;
                                    Dynamic<?> dynamic = this.getBlockEntity(j);
                                    Dynamic<?> dynamic2 = this.getBlock(j);
                                    if (dynamic != null) {
                                        int k = dynamic.get("Base").asInt(0);
                                        if (k != 15 && k >= 0 && k < 16) {
                                            String string2 = ChunkPalettedStorageFix.getProperty(dynamic2, entry.getKey() == 176 ? "rotation" : "facing")
                                                + "_"
                                                + k;
                                            if (ChunkPalettedStorageFix.BANNER_BLOCK_MAP.containsKey(string2)) {
                                                this.setBlock(j, ChunkPalettedStorageFix.BANNER_BLOCK_MAP.get(string2));
                                            }
                                        }
                                    }
                                }
                        }
                    }
                }
            }
        }

        @Nullable
        private Dynamic<?> getBlockEntity(int index) {
            return this.blockEntities.get(index);
        }

        @Nullable
        private Dynamic<?> removeBlockEntity(int index) {
            return this.blockEntities.remove(index);
        }

        public static int relative(int i, ChunkPalettedStorageFix.Direction direction) {
            switch (direction.getAxis()) {
                case X:
                    int j = (i & 15) + direction.getAxisDirection().getStep();
                    return j >= 0 && j <= 15 ? i & -16 | j : -1;
                case Y:
                    int k = (i >> 8) + direction.getAxisDirection().getStep();
                    return k >= 0 && k <= 255 ? i & 0xFF | k << 8 : -1;
                case Z:
                    int l = (i >> 4 & 15) + direction.getAxisDirection().getStep();
                    return l >= 0 && l <= 15 ? i & -241 | l << 4 : -1;
                default:
                    return -1;
            }
        }

        private void setBlock(int i, Dynamic<?> dynamic) {
            if (i >= 0 && i <= 65535) {
                ChunkPalettedStorageFix.Section section = this.getSection(i);
                if (section != null) {
                    section.setBlock(i & 4095, dynamic);
                }
            }
        }

        @Nullable
        private ChunkPalettedStorageFix.Section getSection(int i) {
            int j = i >> 12;
            return j < this.sections.length ? this.sections[j] : null;
        }

        public Dynamic<?> getBlock(int i) {
            if (i >= 0 && i <= 65535) {
                ChunkPalettedStorageFix.Section section = this.getSection(i);
                return section == null ? ChunkPalettedStorageFix.AIR : section.getBlock(i & 4095);
            } else {
                return ChunkPalettedStorageFix.AIR;
            }
        }

        public Dynamic<?> write() {
            Dynamic<?> dynamic = this.level;
            if (this.blockEntities.isEmpty()) {
                dynamic = dynamic.remove("TileEntities");
            } else {
                dynamic = dynamic.set("TileEntities", dynamic.createList(this.blockEntities.values().stream()));
            }

            Dynamic<?> dynamic2 = dynamic.emptyMap();
            List<Dynamic<?>> list = Lists.newArrayList();

            for (ChunkPalettedStorageFix.Section section : this.sections) {
                if (section != null) {
                    list.add(section.write());
                    dynamic2 = dynamic2.set(String.valueOf(section.y), dynamic2.createIntList(Arrays.stream(section.update.toIntArray())));
                }
            }

            Dynamic<?> dynamic3 = dynamic.emptyMap();
            dynamic3 = dynamic3.set("Sides", dynamic3.createByte((byte)this.sides));
            dynamic3 = dynamic3.set("Indices", dynamic2);
            return dynamic.set("UpgradeData", dynamic3).set("Sections", dynamic3.createList(list.stream()));
        }
    }
}
