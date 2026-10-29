package net.salutaryfish.versionunbound.testmod.registries;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.common.extensions.IHolderSetExtension;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.GameData;
import net.neoforged.neoforge.registries.ModifyRegistriesEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;
import net.neoforged.neoforge.registries.callback.AddCallback;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.neoforged.neoforge.registries.holdersets.AnyHolderSet;
import net.neoforged.neoforge.registries.holdersets.OrHolderSet;
import org.slf4j.Logger;

/**
 * Checks NeoForge's registry API: deferred registers and holders, the registry events and their order, custom
 * registries with callbacks, aliases, sync and id limits, NeoForge's own registries, data map types and datapack
 * registries. Registration checks summarize at load complete; datapack registries are checked when a world loads (see
 * the mixin), on servers only.
 *
 * <p>Calls such as {@code registry.addAlias(...)} compile to the same bytecode as in a mod built with NeoForge's
 * toolchain (see the testmod class tweaker).
 */
@Mod(RegistriesMod.MOD_ID)
public class RegistriesMod {
    public static final String MOD_ID = "registries";
    static final Logger LOGGER = LogUtils.getLogger();
    static final Checks CHECKS = new Checks("registration checks");

    record Shape(String name) {}

    record Color(int rgb) {}

    record Spell(int power) {
        static final Codec<Spell> CODEC = Codec.INT.xmap(Spell::new, Spell::power);
    }

    static final ResourceKey<Registry<Shape>> SHAPES_KEY = ResourceKey.createRegistryKey(id("shapes"));
    static final ResourceKey<Registry<Color>> COLORS_KEY = ResourceKey.createRegistryKey(id("colors"));
    static final ResourceKey<Registry<Spell>> SPELLS_KEY = ResourceKey.createRegistryKey(id("spells"));
    static final ResourceKey<Registry<Spell>> RUNES_KEY = ResourceKey.createRegistryKey(id("runes"));

    /** Ids each add callback saw, by entry name. */
    private static final Map<Identifier, Integer> SHAPES_ADDED = new ConcurrentHashMap<>();
    private static final Map<Identifier, Integer> ITEMS_ADDED = new ConcurrentHashMap<>();
    private static final AtomicInteger SHAPES_BAKED = new AtomicInteger();
    /** The registries RegisterEvent fired for, in order. */
    private static final List<Identifier> REGISTER_ORDER = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean ACTIVE_CONTAINER_WAS_THIS_MOD = new AtomicBoolean(true);
    private static final AtomicBoolean CHARGE_BOUND_FOR_ITEMS = new AtomicBoolean();
    private static final AtomicBoolean DATA_MAP_TYPES_REGISTERED = new AtomicBoolean();

    private static final DeferredRegister<Shape> SHAPES = DeferredRegister.create(SHAPES_KEY, MOD_ID);
    static final Registry<Shape> SHAPE_REGISTRY = SHAPES.makeRegistry(builder -> builder
            .sync(true)
            .defaultKey(id("circle"))
            .onAdd((registry, id, key, value) -> SHAPES_ADDED.put(key.identifier(), id))
            // Runs at the registry freeze, and again whenever registry sync remaps ids (joining and leaving a server).
            .onBake(registry -> LOGGER.info("[registries] shapes registry baked, {} time(s); ids {}", SHAPES_BAKED.incrementAndGet(),
                    registry.keySet().stream().sorted().map(key -> key.getPath() + "=" + registry.getId(key)).toList())));
    static final DeferredHolder<Shape, Shape> CIRCLE = SHAPES.register("circle", () -> new Shape("circle"));
    static final DeferredHolder<Shape, Shape> SQUARE = SHAPES.register("square", () -> new Shape("square"));

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    static final DeferredBlock<Block> PROBE_BLOCK = BLOCKS.registerSimpleBlock("probe_block");

    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);
    static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGE = COMPONENTS.registerComponentType("charge", builder -> builder.persistent(Codec.INT));

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    static final DeferredItem<Item> PROBE_ITEM = ITEMS.registerSimpleItem("probe_item");
    // Data components register before items (NeoForge fixes vanilla's order), so items can use them.
    static final DeferredItem<Item> CHARGED_ITEM = ITEMS.registerItem("charged_item", Item::new, properties -> {
        CHARGE_BOUND_FOR_ITEMS.set(CHARGE.isBound());
        return properties.component(CHARGE.get(), 3);
    });
    static final DeferredItem<BlockItem> PROBE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(PROBE_BLOCK);

    private static final DeferredRegister.Entities ENTITIES = DeferredRegister.createEntities(MOD_ID);
    static final DeferredHolder<EntityType<?>, EntityType<ItemEntity>> PROBE_ENTITY = ENTITIES.registerEntityType("probe_entity", ItemEntity::new, MobCategory.MISC);

    private static final DeferredRegister<PoiType> POI_TYPES = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, MOD_ID);
    static final DeferredHolder<PoiType, PoiType> PROBE_POI = POI_TYPES.register("probe_poi",
            () -> new PoiType(ImmutableSet.copyOf(PROBE_BLOCK.get().getStateDefinition().getPossibleStates()), 1, 1));

    static final DataMapType<Item, Integer> WEIGHT = DataMapType.builder(id("weight"), Registries.ITEM, Codec.INT).build();

    private static Registry<Color> colorRegistry;

    public RegistriesMod(IEventBus modBus) {
        CHECKS.check("DeferredHolder unbound before its registry's RegisterEvent", !PROBE_ITEM.isBound() && PROBE_ITEM.asOptional().isEmpty());
        CHECKS.check("DeferredRegister names entries in its namespace", PROBE_ITEM.getId(), id("probe_item"));
        CHECKS.check("makeRegistry builds the registry right away", SHAPE_REGISTRY.key(), SHAPES_KEY);
        CHECKS.check("a registry's id limit stops registration", RegistriesMod::idLimitEnforced);

        ITEMS.addAlias(id("old_probe_item"), id("probe_item"));

        SHAPES.register(modBus);
        BLOCKS.register(modBus);
        COMPONENTS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        POI_TYPES.register(modBus);
        // Added after the deferred registers, so these listeners run once their entries are in.
        modBus.addListener(NewRegistryEvent.class, event -> colorRegistry = event.create(new RegistryBuilder<>(COLORS_KEY).maxId(15)));
        modBus.addListener(ModifyRegistriesEvent.class, this::modifyRegistries);
        modBus.addListener(DataPackRegistryEvent.NewRegistry.class, this::newDataPackRegistries);
        modBus.addListener(RegisterEvent.class, this::register);
        modBus.addListener(RegisterDataMapTypesEvent.class, event -> {
            event.register(WEIGHT);
            DATA_MAP_TYPES_REGISTERED.set(true);
        });
        modBus.addListener(FMLCommonSetupEvent.class, this::commonSetup);
        modBus.addListener(FMLLoadCompleteEvent.class, this::loadComplete);
    }

    static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static boolean idLimitEnforced() {
        Registry<Color> tiny = new RegistryBuilder<>(ResourceKey.<Color>createRegistryKey(id("tiny"))).maxId(1).disableRegistrationCheck().create();
        Registry.register(tiny, id("first"), new Color(1));
        Registry.register(tiny, id("second"), new Color(2));
        try {
            Registry.register(tiny, id("third"), new Color(3));
            return false;
        } catch (IllegalStateException e) {
            return e.getMessage().contains("maximum id range");
        }
    }

    private void modifyRegistries(ModifyRegistriesEvent event) {
        CHECKS.check("ModifyRegistriesEvent sees the new registries", event.getRegistry(SHAPES_KEY) == SHAPE_REGISTRY);
        event.getRegistry(Registries.ITEM).addCallback(AddCallback.class, (registry, id, key, value) -> {
            if (key.identifier().getNamespace().equals(MOD_ID)) ITEMS_ADDED.put(key.identifier(), id);
        });
    }

    private void newDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SPELLS_KEY, Spell.CODEC, Spell.CODEC);
        event.dataPackRegistry(RUNES_KEY, Spell.CODEC, null, builder -> builder.maxId(5));
    }

    private void register(RegisterEvent event) {
        REGISTER_ORDER.add(event.getRegistryKey().identifier());
        if (!ModLoadingContext.get().getActiveContainer().getModId().equals(MOD_ID)) {
            ACTIVE_CONTAINER_WAS_THIS_MOD.set(false);
        }
        event.register(COLORS_KEY, helper -> {
            helper.register(id("red"), new Color(0xFF0000));
            helper.register(id("green"), new Color(0x00FF00));
        });
        event.register(Registries.SOUND_EVENT, id("probe_sound"), () -> SoundEvent.createVariableRangeEvent(id("probe_sound")));

        if (event.getRegistryKey().equals(SHAPES_KEY)) {
            SHAPE_REGISTRY.addAlias(id("box"), id("square"));
        } else if (event.getRegistryKey().equals(Registries.ITEM)) {
            CHECKS.check("DeferredItem bound by its registry's RegisterEvent", PROBE_ITEM.isBound());
            CHECKS.check("entries can be looked up while registering", BuiltInRegistries.ITEM.getValue(id("probe_item")) == PROBE_ITEM.get());
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        // The event order NeoForge's GameData.getRegistrationOrder gives.
        var order = List.copyOf(REGISTER_ORDER);
        CHECKS.check("registration starts with attributes, data components and particles", order.subList(0, 3),
                List.of(Registries.ATTRIBUTE.identifier(), Registries.DATA_COMPONENT_TYPE.identifier(), Registries.PARTICLE_TYPE.identifier()));
        CHECKS.check("blocks register before items, items before points of interest",
                order.indexOf(Registries.BLOCK.identifier()) < order.indexOf(Registries.ITEM.identifier())
                        && order.indexOf(Registries.ITEM.identifier()) < order.indexOf(Registries.POINT_OF_INTEREST_TYPE.identifier()));
        int lastVanilla = order.indexOf(Registries.ITEM.identifier());
        for (int i = 0; i < order.size(); i++) {
            if (order.get(i).getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) lastVanilla = i;
        }
        var modded = order.subList(lastVanilla + 1, order.size()).stream()
                .filter(id -> id.getNamespace().equals("neoforge") || id.getNamespace().equals(MOD_ID)).toList();
        CHECKS.check("modded registries register after vanilla ones, by namespace", modded, List.of(
                NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS.identifier(), NeoForgeRegistries.Keys.HOLDER_SET_TYPES.identifier(),
                COLORS_KEY.identifier(), SHAPES_KEY.identifier()));
        CHECKS.check("the registering mod is the active container during RegisterEvent", ACTIVE_CONTAINER_WAS_THIS_MOD.get());
        CHECKS.check("data components bound when items register", CHARGE_BOUND_FOR_ITEMS.get());

        // Deferred holders and registry references.
        Holder.Reference<Item> reference = BuiltInRegistries.ITEM.get(PROBE_ITEM.getKey()).orElseThrow();
        CHECKS.check("DeferredHolder holds the registered value", PROBE_ITEM.get() == reference.value());
        CHECKS.check("DeferredHolder and its reference are equal both ways", PROBE_ITEM.equals(reference) && reference.equals(PROBE_ITEM) && reference.hashCode() == PROBE_ITEM.hashCode());
        CHECKS.check("DeferredHolder delegates to the registry reference", PROBE_ITEM.getDelegate() == reference);
        CHECKS.check("Holder.Reference.getKey without allocating", reference.getKey() == PROBE_ITEM.getKey());
        CHECKS.check("registry codecs encode a DeferredHolder", () -> BuiltInRegistries.ITEM.holderByNameCodec().encodeStart(JsonOps.INSTANCE, PROBE_ITEM)
                .result().map(JsonElement::getAsString).orElse(null).equals("registries:probe_item"));
        CHECKS.check("blocks map to their block items", PROBE_BLOCK.asItem() == PROBE_BLOCK_ITEM.get());
        CHECKS.check("block states get ids, in GameData's map", GameData.getBlockStateIDMap() == Block.BLOCK_STATE_REGISTRY
                && Block.BLOCK_STATE_REGISTRY.getId(PROBE_BLOCK.get().defaultBlockState()) >= 0);
        CHECKS.check("entity type registered", BuiltInRegistries.ENTITY_TYPE.getValue(id("probe_entity")) == PROBE_ENTITY.get());
        CHECKS.check("point of interest type claims its block states", PoiTypes.forState(PROBE_BLOCK.get().defaultBlockState()).map(poi -> poi.is(PROBE_POI.getKey())).orElse(false));
        CHECKS.check("item add callback saw this mod's items with their ids", ITEMS_ADDED, Map.of(
                id("probe_item"), BuiltInRegistries.ITEM.getId(PROBE_ITEM.get()),
                id("charged_item"), BuiltInRegistries.ITEM.getId(CHARGED_ITEM.get()),
                id("probe_block"), BuiltInRegistries.ITEM.getId(PROBE_BLOCK_ITEM.get())));

        // Aliases.
        CHECKS.check("DeferredRegister alias resolves", BuiltInRegistries.ITEM.getValue(id("old_probe_item")) == PROBE_ITEM.get()
                && BuiltInRegistries.ITEM.resolve(id("old_probe_item")).equals(id("probe_item")));
        CHECKS.check("a registered name resolves to itself", BuiltInRegistries.ITEM.resolve(id("probe_item")).equals(id("probe_item")));
        CHECKS.check("Registry.addAlias resolves", SHAPE_REGISTRY.getValue(id("box")) == SQUARE.get());

        // Custom registries.
        CHECKS.check("custom registry in the root registry", BuiltInRegistries.REGISTRY.getValue(SHAPES_KEY.identifier()) == SHAPE_REGISTRY
                && SHAPES.getRegistry().get() == SHAPE_REGISTRY);
        CHECKS.check("add callback saw each entry with its id", SHAPES_ADDED, Map.of(
                id("circle"), SHAPE_REGISTRY.getId(CIRCLE.get()), id("square"), SHAPE_REGISTRY.getId(SQUARE.get())));
        CHECKS.check("bake callback ran when the registries froze", SHAPES_BAKED.get() >= 1);
        var stray = new Shape("stray");
        CHECKS.check("defaulted registry falls back to its default", SHAPE_REGISTRY.getValue(id("missing")) == CIRCLE.get()
                && id("circle").equals(SHAPE_REGISTRY.getKey(stray)));
        CHECKS.check("getKeyOrNull skips the default", SHAPE_REGISTRY.getKeyOrNull(stray) == null && id("square").equals(SHAPE_REGISTRY.getKeyOrNull(SQUARE.get())));
        CHECKS.check("containsValue", SHAPE_REGISTRY.containsValue(SQUARE.get()) && !SHAPE_REGISTRY.containsValue(new Shape("square")));
        CHECKS.check("ids by key and by name", SHAPE_REGISTRY.getId(SQUARE.getKey()) == SHAPE_REGISTRY.getId(SQUARE.get())
                && SHAPE_REGISTRY.getId(id("square")) == SHAPE_REGISTRY.getId(SQUARE.get()));
        CHECKS.check("RegisterEvent helper filled the NewRegistryEvent registry", colorRegistry.size() == 2
                && colorRegistry.getValue(id("red")).equals(new Color(0xFF0000)));
        CHECKS.check("registry sync flags", SHAPE_REGISTRY.doesSync() && !colorRegistry.doesSync()
                && BuiltInRegistries.SOUND_EVENT.doesSync() && !BuiltInRegistries.CHUNK_STATUS.doesSync());
        CHECKS.check("registry id limits", colorRegistry.getMaxId() == 15 && SHAPE_REGISTRY.getMaxId() == Integer.MAX_VALUE - 1);

        // NeoForge's own registries and holder sets.
        CHECKS.check("NeoForge's registries are registered", BuiltInRegistries.REGISTRY.containsKey(NeoForgeRegistries.Keys.ENTITY_DATA_SERIALIZERS.identifier())
                && BuiltInRegistries.REGISTRY.getValue(NeoForgeRegistries.Keys.HOLDER_SET_TYPES.identifier()) == NeoForgeRegistries.HOLDER_SET_TYPES);
        CHECKS.check("NeoForge registered its holder set types", NeoForgeRegistries.HOLDER_SET_TYPES.keySet().stream().map(Identifier::toString).sorted().toList(),
                List.of("neoforge:and", "neoforge:any", "neoforge:not", "neoforge:or"));
        CHECKS.check("custom holder sets", () -> new AnyHolderSet<>(BuiltInRegistries.ITEM).size() == BuiltInRegistries.ITEM.size()
                && new OrHolderSet<>(HolderSet.direct(PROBE_ITEM.getDelegate()), HolderSet.direct(BuiltInRegistries.ITEM.wrapAsHolder(CHARGED_ITEM.get()))).size() == 2);
        CHECKS.check("holder set serialization types", () -> new AnyHolderSet<>(BuiltInRegistries.ITEM).serializationType() == IHolderSetExtension.SerializationType.OBJECT
                && HolderSet.direct(PROBE_ITEM.getDelegate()).serializationType() == IHolderSetExtension.SerializationType.STRING);

        // What the fabricprobe testmod saw from Fabric's side, while Fabric initialized its mods.
        CHECKS.check("Fabric mods can register after NeoForge mods", Boolean.getBoolean("versionunbound.testprobe.fabricItemRegistered")
                && BuiltInRegistries.ITEM.containsKey(Identifier.fromNamespaceAndPath("versionunbound_fabricprobe", "probe_item")));
        CHECKS.check("Fabric mods see NeoForge mods' entries", Boolean.getBoolean("versionunbound.testprobe.sawNeoForgeItem"));
        CHECKS.check("a synced custom registry is synced and modded for Fabric", Boolean.getBoolean("versionunbound.testprobe.shapesSyncAttributes"));
        CHECKS.check("Fabric's point of interest helper still works", Boolean.getBoolean("versionunbound.testprobe.fabricPoiRegistered")
                && PoiTypes.forState(Blocks.DRIED_KELP_BLOCK.defaultBlockState()).isPresent());
    }

    /** Called by the mixin when a server is about to load its world. */
    public static void checkWorld(MinecraftServer server) {
        var checks = new Checks("world checks");
        checks.check("synced datapack registry loaded", server.registryAccess().lookup(SPELLS_KEY).isPresent());
        checks.check("datapack registry built with its RegistryBuilder options", () -> server.registryAccess().lookupOrThrow(RUNES_KEY).getMaxId() == 5);
        // Item components are bound once datapacks have loaded, so stacks can only be made from here on.
        checks.check("DeferredBlock makes stacks of its block item", () -> PROBE_BLOCK.toStack().is(PROBE_BLOCK_ITEM.get()));
        checks.check("tag holder sets take invalidation listeners", () -> {
            HolderSet.Named<Item> logs = server.registryAccess().lookupOrThrow(Registries.ITEM).getOrThrow(ItemTags.LOGS);
            logs.addInvalidationListener(() -> {});
            return !logs.isImmediatelyResolvable();
        });
        checks.summarize();
    }

    private void loadComplete(FMLLoadCompleteEvent event) {
        CHECKS.check("RegisterDataMapTypesEvent fired", DATA_MAP_TYPES_REGISTERED.get());
        CHECKS.check("data maps read through registries and holders", () -> BuiltInRegistries.ITEM.getDataMap(WEIGHT).isEmpty()
                && PROBE_ITEM.getData(WEIGHT) == null && PROBE_ITEM.getDelegate().getData(WEIGHT) == null);
        CHECKS.summarize();
    }
}
