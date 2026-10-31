package net.salutaryfish.versionunbound.testmod.registries;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge's three added {@code BlockEntityType} constructors, reached the way a mod's {@code new} reaches them (this
 * testmod compiles against vanilla, which lacks them; a public lookup resolves them as the JVM resolves a mod's call),
 * NeoForge's {@code onlyOpCanSetNbt} flag and {@code getValidBlocks}, and {@link BlockEntityTypeAddBlocksEvent}.
 */
final class BlockEntityTypeChecks {
    private BlockEntityTypeChecks() {}

    /** The blocks the types below are for; the add-blocks event only takes blocks of the types' blocks' common class. */
    static final class ProbeEntityBlock extends Block {
        ProbeEntityBlock(BlockBehaviour.Properties properties) {
            super(properties);
        }
    }

    private static final BlockEntityType.BlockEntitySupplier<BlockEntity> NO_BLOCK_ENTITY = (pos, state) -> {
        throw new UnsupportedOperationException("the probe types make no block entities");
    };

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(RegistriesMod.MOD_ID);
    static final DeferredBlock<ProbeEntityBlock> FIRST = BLOCKS.registerBlock("entity_block_first", ProbeEntityBlock::new);
    static final DeferredBlock<ProbeEntityBlock> SECOND = BLOCKS.registerBlock("entity_block_second", ProbeEntityBlock::new);
    static final DeferredBlock<ProbeEntityBlock> THIRD = BLOCKS.registerBlock("entity_block_third", ProbeEntityBlock::new);

    private static final DeferredRegister<BlockEntityType<?>> TYPES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, RegistriesMod.MOD_ID);
    /** {@code new BlockEntityType<>(supplier, FIRST)}. */
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> VARARGS = TYPES.register("varargs",
            () -> construct(MethodType.methodType(void.class, BlockEntityType.BlockEntitySupplier.class, Block[].class), NO_BLOCK_ENTITY, new Block[] {FIRST.get()}));
    /** {@code new BlockEntityType<>(supplier, true, SECOND)}. */
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> OP_ONLY_VARARGS = TYPES.register("op_only_varargs",
            () -> construct(MethodType.methodType(void.class, BlockEntityType.BlockEntitySupplier.class, boolean.class, Block[].class), NO_BLOCK_ENTITY, true,
                    new Block[] {SECOND.get()}));
    /** {@code new BlockEntityType<>(supplier, Set.of(THIRD), true)}. */
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> OP_ONLY_SET = TYPES.register("op_only_set",
            () -> construct(MethodType.methodType(void.class, BlockEntityType.BlockEntitySupplier.class, Set.class, boolean.class), NO_BLOCK_ENTITY, Set.of(THIRD.get()), true));
    /** {@code new BlockEntityType<>(supplier, Set.of(THIRD), false)}. */
    static final DeferredHolder<BlockEntityType<?>, BlockEntityType<?>> SET = TYPES.register("set",
            () -> construct(MethodType.methodType(void.class, BlockEntityType.BlockEntitySupplier.class, Set.class, boolean.class), NO_BLOCK_ENTITY, Set.of(THIRD.get()), false));

    private static int addBlocksEvents;
    private static final AtomicReference<String> REFUSAL = new AtomicReference<>();

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        TYPES.register(modBus);
        modBus.addListener(BlockEntityTypeAddBlocksEvent.class, event -> {
            addBlocksEvents++;
            event.modify(VARARGS.get(), SECOND.get());
            event.modify(OP_ONLY_SET.getKey(), FIRST.get(), SECOND.get());
            // Stone is a plain Block, not a ProbeEntityBlock like the type's blocks.
            try {
                event.modify(SET.get(), Blocks.STONE);
            } catch (IllegalArgumentException e) {
                REFUSAL.set(e.getMessage());
            }
        });
    }

    private static BlockEntityType<?> construct(MethodType type, Object... arguments) {
        try {
            return (BlockEntityType<?>) MethodHandles.publicLookup().findConstructor(BlockEntityType.class, type).asFixedArity().invokeWithArguments(arguments);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<Block> validBlocks(BlockEntityType<?> type) throws Throwable {
        return (Set<Block>) MethodHandles.publicLookup().findVirtual(BlockEntityType.class, "getValidBlocks", MethodType.methodType(Set.class)).invoke(type);
    }

    static void checkRegistration(Checks checks) {
        checks.check("the BlockEntityType(supplier, Block...) constructor", () -> VARARGS.get().isValid(FIRST.get().defaultBlockState())
                && !VARARGS.get().isValid(THIRD.get().defaultBlockState()) && !VARARGS.get().onlyOpCanSetNbt());
        checks.check("the BlockEntityType(supplier, boolean, Block...) constructor", () -> OP_ONLY_VARARGS.get().isValid(SECOND.get().defaultBlockState())
                && OP_ONLY_VARARGS.get().onlyOpCanSetNbt());
        checks.check("the BlockEntityType(supplier, Set, boolean) constructor", () -> OP_ONLY_SET.get().onlyOpCanSetNbt() && !SET.get().onlyOpCanSetNbt()
                && SET.get().isValid(THIRD.get().defaultBlockState()));
        checks.check("a vanilla block entity type is still op-only by vanilla's list", () -> net.minecraft.world.level.block.entity.BlockEntityTypes.COMMAND_BLOCK.onlyOpCanSetNbt()
                && !net.minecraft.world.level.block.entity.BlockEntityTypes.FURNACE.onlyOpCanSetNbt());
        // Fabric API's BlockEntityTypeMixin swaps the valid blocks for a HashSet at the end of vanilla's constructor
        // (for its addValidBlock); NeoForge's constructors all call vanilla's, so they get it too. Layer A's class
        // tweaker makes the field public at run time. OP_ONLY_VARARGS was built with Set.of and not modified since.
        checks.check("NeoForge's BlockEntityType constructors run Fabric API's constructor mixin", () ->
                BlockEntityType.class.getField("validBlocks").get(OP_ONLY_VARARGS.get()) instanceof java.util.HashSet<?>);
        checks.check("the varargs BlockEntityType constructor refuses no blocks", () -> {
            try {
                construct(MethodType.methodType(void.class, BlockEntityType.BlockEntitySupplier.class, Block[].class), NO_BLOCK_ENTITY, new Block[0]);
                return false;
            } catch (IllegalArgumentException e) {
                return e.getMessage().startsWith("Block entity type instantiated without valid blocks");
            }
        });
        checks.check("BlockEntityTypeAddBlocksEvent fired once", addBlocksEvents, 1);
        checks.check("BlockEntityTypeAddBlocksEvent adds blocks to a type", () -> VARARGS.get().isValid(SECOND.get().defaultBlockState())
                && validBlocks(VARARGS.get()).equals(Set.of(FIRST.get(), SECOND.get())));
        checks.check("BlockEntityTypeAddBlocksEvent adds blocks to a type by its key", () -> validBlocks(OP_ONLY_SET.get()).equals(Set.of(FIRST.get(), SECOND.get(), THIRD.get())));
        checks.check("BlockEntityTypeAddBlocksEvent refuses a block outside the blocks' common class", () -> REFUSAL.get() != null
                && REFUSAL.get().contains(ProbeEntityBlock.class.getName()) && !SET.get().isValid(Blocks.STONE.defaultBlockState()));
        checks.check("getValidBlocks can't be changed", () -> {
            try {
                validBlocks(SET.get()).add(Blocks.STONE);
                return false;
            } catch (UnsupportedOperationException e) {
                return true;
            }
        });
    }
}
