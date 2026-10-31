package net.minecraft.world.level.block.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.flag.FeatureElement;
import net.minecraft.world.flag.FeatureFlag;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public abstract class BlockBehaviour implements FeatureElement {
    protected static final Direction[] UPDATE_SHAPE_ORDER = new Direction[]{
        Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH, Direction.DOWN, Direction.UP
    };
    protected final boolean hasCollision;
    protected final float explosionResistance;
    /**
     * Whether this blocks receives random ticks
     */
    protected final boolean isRandomlyTicking;
    protected final SoundType soundType;
    /**
     * Determines how much velocity is maintained while moving on top of this block
     */
    protected final float friction;
    protected final float speedFactor;
    protected final float jumpFactor;
    protected final boolean dynamicShape;
    protected final FeatureFlagSet requiredFeatures;
    protected final BlockBehaviour.Properties properties;
    @Nullable
    protected ResourceKey<LootTable> drops;

    public BlockBehaviour(BlockBehaviour.Properties properties) {
        this.hasCollision = properties.hasCollision;
        this.drops = properties.drops;
        this.explosionResistance = properties.explosionResistance;
        this.isRandomlyTicking = properties.isRandomlyTicking;
        this.soundType = properties.soundType;
        this.friction = properties.friction;
        this.speedFactor = properties.speedFactor;
        this.jumpFactor = properties.jumpFactor;
        this.dynamicShape = properties.dynamicShape;
        this.requiredFeatures = properties.requiredFeatures;
        this.properties = properties;
    }

    public BlockBehaviour.Properties properties() {
        return this.properties;
    }

    protected abstract MapCodec<? extends Block> codec();

    protected static <B extends Block> RecordCodecBuilder<B, BlockBehaviour.Properties> propertiesCodec() {
        return BlockBehaviour.Properties.CODEC.fieldOf("properties").forGetter(BlockBehaviour::properties);
    }

    public static <B extends Block> MapCodec<B> simpleCodec(Function<BlockBehaviour.Properties, B> factory) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(propertiesCodec()).apply(instance, factory));
    }

    /**
     * Updates the shapes of indirect neighbors of this block. This method is analogous to
     * {@link net.minecraft.world.level.block.state.BlockBehaviour$BlockStateBase#updateNeighbourShapes}
     * but where that method affects the 6 direct neighbors of this block, this method affects
     * the indirect neighbors, if any.
     *
     * <p>
     * Currently the only implementation of this method is {@link net.minecraft.world.level.block.RedStoneWireBlock#updateIndirectNeighbourShapes}
     * where it is used to validate diagonal connections of redstone wire blocks.
     */
    protected void updateIndirectNeighbourShapes(BlockState state, LevelAccessor level, BlockPos pos, int flags, int recursionLeft) {
    }

    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        switch (pathComputationType) {
            case LAND:
                return !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            case WATER:
                return state.getFluidState().is(FluidTags.WATER);
            case AIR:
                return !state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            default:
                return false;
        }
    }

    /**
     * Update the provided state given the provided neighbor direction and neighbor state, returning a new state.
     * For example, fences make their connections to the passed in state if possible, and wet concrete powder immediately returns its solidified counterpart.
     * Note that this method should ideally consider only the specific direction passed in.
     */
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state;
    }

    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction direction) {
        return false;
    }

    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        DebugPackets.sendNeighborsUpdatePacket(level, pos);
    }

    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    }

    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (state.hasBlockEntity() && !state.is(newState.getBlock())) {
            level.removeBlockEntity(pos);
        }
    }

    protected void onExplosionHit(BlockState state, Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (!state.isAir() && explosion.getBlockInteraction() != Explosion.BlockInteraction.TRIGGER_BLOCK) {
            Block block = state.getBlock();
            boolean bl = explosion.getIndirectSourceEntity() instanceof Player;
            if (block.dropFromExplosion(explosion) && level instanceof ServerLevel serverLevel) {
                BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                LootParams.Builder builder = new LootParams.Builder(serverLevel)
                    .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                    .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                    .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                    .withOptionalParameter(LootContextParams.THIS_ENTITY, explosion.getDirectSourceEntity());
                if (explosion.getBlockInteraction() == Explosion.BlockInteraction.DESTROY_WITH_DECAY) {
                    builder.withParameter(LootContextParams.EXPLOSION_RADIUS, explosion.radius());
                }

                state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, bl);
                state.getDrops(builder).forEach(itemStack -> dropConsumer.accept(itemStack, pos));
            }

            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            block.wasExploded(level, pos, explosion);
        }
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    protected ItemInteractionResult useItemOn(
        ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * Called on server when {@link net.minecraft.world.level.Level#blockEvent} is called. If server returns true, then also called on the client. On the Server, this may perform additional changes to the world, like pistons replacing the block with an extended base. On the client, the update may involve replacing block entities or effects such as sounds or particles
     */
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int id, int param) {
        return false;
    }

    /**
     * The type of render function called. MODEL for mixed tesr and static model, MODELBLOCK_ANIMATED for TESR-only, LIQUID for vanilla liquids, INVISIBLE to skip all rendering
     */
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    protected boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }

    /**
     * Returns whether this block is capable of emitting redstone signals.
     *
     */
    protected boolean isSignalSource(BlockState state) {
        return false;
    }

    protected FluidState getFluidState(BlockState state) {
        return Fluids.EMPTY.defaultFluidState();
    }

    protected boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }

    protected float getMaxHorizontalOffset() {
        return 0.25F;
    }

    protected float getMaxVerticalOffset() {
        return 0.2F;
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.requiredFeatures;
    }

    /**
     * Returns the blockstate with the given rotation from the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state;
    }

    /**
     * Returns the blockstate with the given mirror of the passed blockstate. If inapplicable, returns the passed blockstate.
     */
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state;
    }

    protected boolean canBeReplaced(BlockState state, BlockPlaceContext useContext) {
        return state.canBeReplaced() && (useContext.getItemInHand().isEmpty() || !useContext.getItemInHand().is(this.asItem()));
    }

    protected boolean canBeReplaced(BlockState state, Fluid fluid) {
        return state.canBeReplaced() || !state.isSolid();
    }

    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        ResourceKey<LootTable> resourceKey = this.getLootTable();
        if (resourceKey == BuiltInLootTables.EMPTY) {
            return Collections.emptyList();
        }

        LootParams lootParams = params.withParameter(LootContextParams.BLOCK_STATE, state).create(LootContextParamSets.BLOCK);
        ServerLevel serverLevel = lootParams.getLevel();
        LootTable lootTable = serverLevel.getServer().reloadableRegistries().getLootTable(resourceKey);
        return lootTable.getRandomItems(lootParams);
    }

    /**
     * Return a random long to be passed to {@link net.minecraft.client.resources.model.BakedModel#getQuads}, used for random model rotations
     */
    protected long getSeed(BlockState state, BlockPos pos) {
        return Mth.getSeed(pos);
    }

    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getShape(level, pos);
    }

    protected VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return this.getCollisionShape(state, level, pos, CollisionContext.empty());
    }

    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isSolidRender(level, pos)) {
            return level.getMaxLightLevel();
        } else {
            return state.propagatesSkylightDown(level, pos) ? 0 : 1;
        }
    }

    @Nullable
    protected MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        return null;
    }

    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return state.isCollisionShapeFullBlock(level, pos) ? 0.2F : 1.0F;
    }

    /**
     * Returns the analog signal this block emits. This is the signal a comparator can read from it.
     *
     */
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return 0;
    }

    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.hasCollision ? state.getShape(level, pos) : Shapes.empty();
    }

    protected boolean isCollisionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return Block.isShapeFullBlock(state.getCollisionShape(level, pos));
    }

    protected boolean isOcclusionShapeFullBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return Block.isShapeFullBlock(state.getOcclusionShape(level, pos));
    }

    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.getCollisionShape(state, level, pos, context);
    }

    /**
     * Performs a random tick on a block.
     */
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    /**
     * Get the hardness of this Block relative to the ability of the given player
     */
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        float f = state.getDestroySpeed(level, pos);
        if (f == -1.0F) {
            return 0.0F;
        }

        int i = player.hasCorrectToolForDrops(state) ? 30 : 100;
        return player.getDestroySpeed(state) / f / i;
    }

    /**
     * Perform side-effects from block dropping, such as creating silverfish
     */
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
    }

    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
    }

    /**
     * Returns the signal this block emits in the given direction.
     *
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so this method
     * checks for the signal emitted in the <i>opposite</i> direction of the one given.
     *
     */
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
    }

    /**
     * Returns the direct signal this block emits in the given direction.
     *
     * <p>
     * NOTE: directions in redstone signal related methods are backwards, so this method
     * checks for the signal emitted in the <i>opposite</i> direction of the one given.
     *
     */
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    public final ResourceKey<LootTable> getLootTable() {
        if (this.drops == null) {
            ResourceLocation resourceLocation = BuiltInRegistries.BLOCK.getKey(this.asBlock());
            this.drops = ResourceKey.create(Registries.LOOT_TABLE, resourceLocation.withPrefix("blocks/"));
        }

        return this.drops;
    }

    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
    }

    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !Block.isShapeFullBlock(state.getShape(level, pos)) && state.getFluidState().isEmpty();
    }

    protected boolean isRandomlyTicking(BlockState state) {
        return this.isRandomlyTicking;
    }

    protected SoundType getSoundType(BlockState state) {
        return this.soundType;
    }

    public abstract Item asItem();

    protected abstract Block asBlock();

    public MapColor defaultMapColor() {
        return this.properties.mapColor.apply(this.asBlock().defaultBlockState());
    }

    public float defaultDestroyTime() {
        return this.properties.destroyTime;
    }

    public abstract static class BlockStateBase extends StateHolder<Block, BlockState> {
        private final int lightEmission;
        private final boolean useShapeForLightOcclusion;
        private final boolean isAir;
        private final boolean ignitedByLava;
        @Deprecated
        private final boolean liquid;
        @Deprecated
        private boolean legacySolid;
        private final PushReaction pushReaction;
        private final MapColor mapColor;
        private final float destroySpeed;
        private final boolean requiresCorrectToolForDrops;
        private final boolean canOcclude;
        private final BlockBehaviour.StatePredicate isRedstoneConductor;
        private final BlockBehaviour.StatePredicate isSuffocating;
        private final BlockBehaviour.StatePredicate isViewBlocking;
        private final BlockBehaviour.StatePredicate hasPostProcess;
        private final BlockBehaviour.StatePredicate emissiveRendering;
        @Nullable
        private final BlockBehaviour.OffsetFunction offsetFunction;
        private final boolean spawnTerrainParticles;
        private final NoteBlockInstrument instrument;
        private final boolean replaceable;
        @Nullable
        protected BlockBehaviour.BlockStateBase.Cache cache;
        private FluidState fluidState = Fluids.EMPTY.defaultFluidState();
        private boolean isRandomlyTicking;

        protected BlockStateBase(Block owner, Reference2ObjectArrayMap<Property<?>, Comparable<?>> values, MapCodec<BlockState> propertiesCodec) {
            super(owner, values, propertiesCodec);
            BlockBehaviour.Properties properties = owner.properties;
            this.lightEmission = properties.lightEmission.applyAsInt(this.asState());
            this.useShapeForLightOcclusion = owner.useShapeForLightOcclusion(this.asState());
            this.isAir = properties.isAir;
            this.ignitedByLava = properties.ignitedByLava;
            this.liquid = properties.liquid;
            this.pushReaction = properties.pushReaction;
            this.mapColor = properties.mapColor.apply(this.asState());
            this.destroySpeed = properties.destroyTime;
            this.requiresCorrectToolForDrops = properties.requiresCorrectToolForDrops;
            this.canOcclude = properties.canOcclude;
            this.isRedstoneConductor = properties.isRedstoneConductor;
            this.isSuffocating = properties.isSuffocating;
            this.isViewBlocking = properties.isViewBlocking;
            this.hasPostProcess = properties.hasPostProcess;
            this.emissiveRendering = properties.emissiveRendering;
            this.offsetFunction = properties.offsetFunction;
            this.spawnTerrainParticles = properties.spawnTerrainParticles;
            this.instrument = properties.instrument;
            this.replaceable = properties.replaceable;
        }

        private boolean calculateSolid() {
            if (this.owner.properties.forceSolidOn) {
                return true;
            }

            if (this.owner.properties.forceSolidOff) {
                return false;
            }

            if (this.cache == null) {
                return false;
            }

            VoxelShape voxelShape = this.cache.collisionShape;
            if (voxelShape.isEmpty()) {
                return false;
            }

            AABB aABB = voxelShape.bounds();
            return aABB.getSize() >= 0.7291666666666666 ? true : aABB.getYsize() >= 1.0;
        }

        public void initCache() {
            this.fluidState = this.owner.getFluidState(this.asState());
            this.isRandomlyTicking = this.owner.isRandomlyTicking(this.asState());
            if (!this.getBlock().hasDynamicShape()) {
                this.cache = new BlockBehaviour.BlockStateBase.Cache(this.asState());
            }

            this.legacySolid = this.calculateSolid();
        }

        public Block getBlock() {
            return this.owner;
        }

        public Holder<Block> getBlockHolder() {
            return this.owner.builtInRegistryHolder();
        }

        @Deprecated
        public boolean blocksMotion() {
            Block block = this.getBlock();
            return block != Blocks.COBWEB && block != Blocks.BAMBOO_SAPLING && this.isSolid();
        }

        @Deprecated
        public boolean isSolid() {
            return this.legacySolid;
        }

        public boolean isValidSpawn(BlockGetter level, BlockPos pos, EntityType<?> entityType) {
            return this.getBlock().properties.isValidSpawn.test(this.asState(), level, pos, entityType);
        }

        public boolean propagatesSkylightDown(BlockGetter level, BlockPos pos) {
            return this.cache != null ? this.cache.propagatesSkylightDown : this.getBlock().propagatesSkylightDown(this.asState(), level, pos);
        }

        public int getLightBlock(BlockGetter level, BlockPos pos) {
            return this.cache != null ? this.cache.lightBlock : this.getBlock().getLightBlock(this.asState(), level, pos);
        }

        public VoxelShape getFaceOcclusionShape(BlockGetter level, BlockPos pos, Direction direction) {
            return this.cache != null && this.cache.occlusionShapes != null
                ? this.cache.occlusionShapes[direction.ordinal()]
                : Shapes.getFaceShape(this.getOcclusionShape(level, pos), direction);
        }

        public VoxelShape getOcclusionShape(BlockGetter level, BlockPos pos) {
            return this.getBlock().getOcclusionShape(this.asState(), level, pos);
        }

        public boolean hasLargeCollisionShape() {
            return this.cache == null || this.cache.largeCollisionShape;
        }

        public boolean useShapeForLightOcclusion() {
            return this.useShapeForLightOcclusion;
        }

        public int getLightEmission() {
            return this.lightEmission;
        }

        public boolean isAir() {
            return this.isAir;
        }

        public boolean ignitedByLava() {
            return this.ignitedByLava;
        }

        @Deprecated
        public boolean liquid() {
            return this.liquid;
        }

        public MapColor getMapColor(BlockGetter level, BlockPos pos) {
            return this.mapColor;
        }

        /**
         * @return the blockstate with the given rotation. If inapplicable, returns itself.
         */
        public BlockState rotate(Rotation rotation) {
            return this.getBlock().rotate(this.asState(), rotation);
        }

        /**
         * @return the blockstate mirrored in the given way. If inapplicable, returns itself.
         */
        public BlockState mirror(Mirror mirror) {
            return this.getBlock().mirror(this.asState(), mirror);
        }

        public RenderShape getRenderShape() {
            return this.getBlock().getRenderShape(this.asState());
        }

        public boolean emissiveRendering(BlockGetter level, BlockPos pos) {
            return this.emissiveRendering.test(this.asState(), level, pos);
        }

        public float getShadeBrightness(BlockGetter level, BlockPos pos) {
            return this.getBlock().getShadeBrightness(this.asState(), level, pos);
        }

        public boolean isRedstoneConductor(BlockGetter level, BlockPos pos) {
            return this.isRedstoneConductor.test(this.asState(), level, pos);
        }

        public boolean isSignalSource() {
            return this.getBlock().isSignalSource(this.asState());
        }

        public int getSignal(BlockGetter level, BlockPos pos, Direction direction) {
            return this.getBlock().getSignal(this.asState(), level, pos, direction);
        }

        public boolean hasAnalogOutputSignal() {
            return this.getBlock().hasAnalogOutputSignal(this.asState());
        }

        public int getAnalogOutputSignal(Level level, BlockPos pos) {
            return this.getBlock().getAnalogOutputSignal(this.asState(), level, pos);
        }

        public float getDestroySpeed(BlockGetter level, BlockPos pos) {
            return this.destroySpeed;
        }

        public float getDestroyProgress(Player player, BlockGetter level, BlockPos pos) {
            return this.getBlock().getDestroyProgress(this.asState(), player, level, pos);
        }

        public int getDirectSignal(BlockGetter level, BlockPos pos, Direction direction) {
            return this.getBlock().getDirectSignal(this.asState(), level, pos, direction);
        }

        public PushReaction getPistonPushReaction() {
            return this.pushReaction;
        }

        public boolean isSolidRender(BlockGetter level, BlockPos pos) {
            if (this.cache != null) {
                return this.cache.solidRender;
            }

            BlockState blockState = this.asState();
            return blockState.canOcclude() ? Block.isShapeFullBlock(blockState.getOcclusionShape(level, pos)) : false;
        }

        public boolean canOcclude() {
            return this.canOcclude;
        }

        public boolean skipRendering(BlockState state, Direction face) {
            return this.getBlock().skipRendering(this.asState(), state, face);
        }

        public VoxelShape getShape(BlockGetter level, BlockPos pos) {
            return this.getShape(level, pos, CollisionContext.empty());
        }

        public VoxelShape getShape(BlockGetter level, BlockPos pos, CollisionContext context) {
            return this.getBlock().getShape(this.asState(), level, pos, context);
        }

        public VoxelShape getCollisionShape(BlockGetter level, BlockPos pos) {
            return this.cache != null ? this.cache.collisionShape : this.getCollisionShape(level, pos, CollisionContext.empty());
        }

        public VoxelShape getCollisionShape(BlockGetter level, BlockPos pos, CollisionContext context) {
            return this.getBlock().getCollisionShape(this.asState(), level, pos, context);
        }

        public VoxelShape getBlockSupportShape(BlockGetter level, BlockPos pos) {
            return this.getBlock().getBlockSupportShape(this.asState(), level, pos);
        }

        public VoxelShape getVisualShape(BlockGetter level, BlockPos pos, CollisionContext context) {
            return this.getBlock().getVisualShape(this.asState(), level, pos, context);
        }

        public VoxelShape getInteractionShape(BlockGetter level, BlockPos pos) {
            return this.getBlock().getInteractionShape(this.asState(), level, pos);
        }

        public final boolean entityCanStandOn(BlockGetter level, BlockPos pos, Entity entity) {
            return this.entityCanStandOnFace(level, pos, entity, Direction.UP);
        }

        /**
         * @return true if the collision box of this state covers the entire upper face of the blockspace
         */
        public final boolean entityCanStandOnFace(BlockGetter level, BlockPos pos, Entity entity, Direction face) {
            return Block.isFaceFull(this.getCollisionShape(level, pos, CollisionContext.of(entity)), face);
        }

        public Vec3 getOffset(BlockGetter level, BlockPos pos) {
            BlockBehaviour.OffsetFunction offsetFunction = this.offsetFunction;
            return offsetFunction != null ? offsetFunction.evaluate(this.asState(), level, pos) : Vec3.ZERO;
        }

        public boolean hasOffsetFunction() {
            return this.offsetFunction != null;
        }

        public boolean triggerEvent(Level level, BlockPos pos, int id, int param) {
            return this.getBlock().triggerEvent(this.asState(), level, pos, id, param);
        }

        public void handleNeighborChanged(Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
            this.getBlock().neighborChanged(this.asState(), level, pos, block, fromPos, isMoving);
        }

        public final void updateNeighbourShapes(LevelAccessor level, BlockPos pos, int flags) {
            this.updateNeighbourShapes(level, pos, flags, 512);
        }

        public final void updateNeighbourShapes(LevelAccessor level, BlockPos pos, int flags, int recursionLeft) {
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (Direction direction : BlockBehaviour.UPDATE_SHAPE_ORDER) {
                mutableBlockPos.setWithOffset(pos, direction);
                level.neighborShapeChanged(direction.getOpposite(), this.asState(), mutableBlockPos, pos, flags, recursionLeft);
            }
        }

        public final void updateIndirectNeighbourShapes(LevelAccessor level, BlockPos pos, int flags) {
            this.updateIndirectNeighbourShapes(level, pos, flags, 512);
        }

        public void updateIndirectNeighbourShapes(LevelAccessor level, BlockPos pos, int flags, int recursionLeft) {
            this.getBlock().updateIndirectNeighbourShapes(this.asState(), level, pos, flags, recursionLeft);
        }

        public void onPlace(Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
            this.getBlock().onPlace(this.asState(), level, pos, oldState, movedByPiston);
        }

        public void onRemove(Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
            this.getBlock().onRemove(this.asState(), level, pos, newState, movedByPiston);
        }

        public void onExplosionHit(Level level, BlockPos pos, Explosion explosion, BiConsumer<ItemStack, BlockPos> dropConsumer) {
            this.getBlock().onExplosionHit(this.asState(), level, pos, explosion, dropConsumer);
        }

        public void tick(ServerLevel level, BlockPos pos, RandomSource random) {
            this.getBlock().tick(this.asState(), level, pos, random);
        }

        public void randomTick(ServerLevel level, BlockPos pos, RandomSource random) {
            this.getBlock().randomTick(this.asState(), level, pos, random);
        }

        public void entityInside(Level level, BlockPos pos, Entity entity) {
            this.getBlock().entityInside(this.asState(), level, pos, entity);
        }

        public void spawnAfterBreak(ServerLevel level, BlockPos pos, ItemStack stack, boolean dropExperience) {
            this.getBlock().spawnAfterBreak(this.asState(), level, pos, stack, dropExperience);
        }

        public List<ItemStack> getDrops(LootParams.Builder lootParams) {
            return this.getBlock().getDrops(this.asState(), lootParams);
        }

        public ItemInteractionResult useItemOn(ItemStack stack, Level level, Player player, InteractionHand hand, BlockHitResult hitResult) {
            return this.getBlock().useItemOn(stack, this.asState(), level, hitResult.getBlockPos(), player, hand, hitResult);
        }

        public InteractionResult useWithoutItem(Level level, Player player, BlockHitResult hitResult) {
            return this.getBlock().useWithoutItem(this.asState(), level, hitResult.getBlockPos(), player, hitResult);
        }

        public void attack(Level level, BlockPos pos, Player player) {
            this.getBlock().attack(this.asState(), level, pos, player);
        }

        public boolean isSuffocating(BlockGetter level, BlockPos pos) {
            return this.isSuffocating.test(this.asState(), level, pos);
        }

        public boolean isViewBlocking(BlockGetter level, BlockPos pos) {
            return this.isViewBlocking.test(this.asState(), level, pos);
        }

        public BlockState updateShape(Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
            return this.getBlock().updateShape(this.asState(), direction, neighborState, level, pos, neighborPos);
        }

        public boolean isPathfindable(PathComputationType pathComputationType) {
            return this.getBlock().isPathfindable(this.asState(), pathComputationType);
        }

        public boolean canBeReplaced(BlockPlaceContext useContext) {
            return this.getBlock().canBeReplaced(this.asState(), useContext);
        }

        public boolean canBeReplaced(Fluid fluid) {
            return this.getBlock().canBeReplaced(this.asState(), fluid);
        }

        public boolean canBeReplaced() {
            return this.replaceable;
        }

        public boolean canSurvive(LevelReader level, BlockPos pos) {
            return this.getBlock().canSurvive(this.asState(), level, pos);
        }

        public boolean hasPostProcess(BlockGetter level, BlockPos pos) {
            return this.hasPostProcess.test(this.asState(), level, pos);
        }

        @Nullable
        public MenuProvider getMenuProvider(Level level, BlockPos pos) {
            return this.getBlock().getMenuProvider(this.asState(), level, pos);
        }

        public boolean is(TagKey<Block> tag) {
            return this.getBlock().builtInRegistryHolder().is(tag);
        }

        public boolean is(TagKey<Block> tag, Predicate<BlockBehaviour.BlockStateBase> predicate) {
            return this.is(tag) && predicate.test(this);
        }

        public boolean is(HolderSet<Block> holder) {
            return holder.contains(this.getBlock().builtInRegistryHolder());
        }

        public boolean is(Holder<Block> block) {
            return this.is(block.value());
        }

        public Stream<TagKey<Block>> getTags() {
            return this.getBlock().builtInRegistryHolder().tags();
        }

        public boolean hasBlockEntity() {
            return this.getBlock() instanceof EntityBlock;
        }

        @Nullable
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockEntityType<T> blockEntityType) {
            return this.getBlock() instanceof EntityBlock ? ((EntityBlock)this.getBlock()).getTicker(level, this.asState(), blockEntityType) : null;
        }

        public boolean is(Block block) {
            return this.getBlock() == block;
        }

        public boolean is(ResourceKey<Block> block) {
            return this.getBlock().builtInRegistryHolder().is(block);
        }

        public FluidState getFluidState() {
            return this.fluidState;
        }

        public boolean isRandomlyTicking() {
            return this.isRandomlyTicking;
        }

        public long getSeed(BlockPos pos) {
            return this.getBlock().getSeed(this.asState(), pos);
        }

        public SoundType getSoundType() {
            return this.getBlock().getSoundType(this.asState());
        }

        public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
            this.getBlock().onProjectileHit(level, state, hit, projectile);
        }

        public boolean isFaceSturdy(BlockGetter level, BlockPos pos, Direction direction) {
            return this.isFaceSturdy(level, pos, direction, SupportType.FULL);
        }

        public boolean isFaceSturdy(BlockGetter level, BlockPos pos, Direction face, SupportType supportType) {
            return this.cache != null ? this.cache.isFaceSturdy(face, supportType) : supportType.isSupporting(this.asState(), level, pos, face);
        }

        public boolean isCollisionShapeFullBlock(BlockGetter level, BlockPos pos) {
            return this.cache != null ? this.cache.isCollisionShapeFullBlock : this.getBlock().isCollisionShapeFullBlock(this.asState(), level, pos);
        }

        protected abstract BlockState asState();

        public boolean requiresCorrectToolForDrops() {
            return this.requiresCorrectToolForDrops;
        }

        public boolean shouldSpawnTerrainParticles() {
            return this.spawnTerrainParticles;
        }

        public NoteBlockInstrument instrument() {
            return this.instrument;
        }

        static final class Cache {
            private static final Direction[] DIRECTIONS = Direction.values();
            private static final int SUPPORT_TYPE_COUNT = SupportType.values().length;
            protected final boolean solidRender;
            final boolean propagatesSkylightDown;
            final int lightBlock;
            @Nullable
            final VoxelShape[] occlusionShapes;
            protected final VoxelShape collisionShape;
            protected final boolean largeCollisionShape;
            private final boolean[] faceSturdy;
            protected final boolean isCollisionShapeFullBlock;

            Cache(BlockState state) {
                Block block = state.getBlock();
                this.solidRender = state.isSolidRender(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                this.propagatesSkylightDown = block.propagatesSkylightDown(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                this.lightBlock = block.getLightBlock(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                if (!state.canOcclude()) {
                    this.occlusionShapes = null;
                } else {
                    this.occlusionShapes = new VoxelShape[DIRECTIONS.length];
                    VoxelShape voxelShape = block.getOcclusionShape(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO);

                    for (Direction direction : DIRECTIONS) {
                        this.occlusionShapes[direction.ordinal()] = Shapes.getFaceShape(voxelShape, direction);
                    }
                }

                this.collisionShape = block.getCollisionShape(state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
                if (!this.collisionShape.isEmpty() && state.hasOffsetFunction()) {
                    throw new IllegalStateException(
                        String.format(
                            Locale.ROOT,
                            "%s has a collision shape and an offset type, but is not marked as dynamicShape in its properties.",
                            BuiltInRegistries.BLOCK.getKey(block)
                        )
                    );
                }

                this.largeCollisionShape = Arrays.stream(Direction.Axis.values())
                    .anyMatch(dir -> this.collisionShape.min(dir) < 0.0 || this.collisionShape.max(dir) > 1.0);
                this.faceSturdy = new boolean[DIRECTIONS.length * SUPPORT_TYPE_COUNT];

                for (Direction direction2 : DIRECTIONS) {
                    for (SupportType supportType : SupportType.values()) {
                        this.faceSturdy[getFaceSupportIndex(direction2, supportType)] = supportType.isSupporting(
                            state, EmptyBlockGetter.INSTANCE, BlockPos.ZERO, direction2
                        );
                    }
                }

                this.isCollisionShapeFullBlock = Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            }

            public boolean isFaceSturdy(Direction direction, SupportType supportType) {
                return this.faceSturdy[getFaceSupportIndex(direction, supportType)];
            }

            private static int getFaceSupportIndex(Direction direction, SupportType supportType) {
                return direction.ordinal() * SUPPORT_TYPE_COUNT + supportType.ordinal();
            }
        }
    }

    public interface OffsetFunction {
        Vec3 evaluate(BlockState var1, BlockGetter var2, BlockPos var3);
    }

    public enum OffsetType {
        NONE,
        XZ,
        XYZ;
    }

    public static class Properties {
        public static final Codec<BlockBehaviour.Properties> CODEC = Codec.unit(() -> of());
        Function<BlockState, MapColor> mapColor = blockState -> MapColor.NONE;
        boolean hasCollision = true;
        SoundType soundType = SoundType.STONE;
        ToIntFunction<BlockState> lightEmission = blockState -> 0;
        float explosionResistance;
        float destroyTime;
        boolean requiresCorrectToolForDrops;
        boolean isRandomlyTicking;
        float friction = 0.6F;
        float speedFactor = 1.0F;
        float jumpFactor = 1.0F;
        /**
         * Sets loot table information
         */
        ResourceKey<LootTable> drops;
        boolean canOcclude = true;
        boolean isAir;
        boolean ignitedByLava;
        @Deprecated
        boolean liquid;
        @Deprecated
        boolean forceSolidOff;
        boolean forceSolidOn;
        PushReaction pushReaction = PushReaction.NORMAL;
        boolean spawnTerrainParticles = true;
        NoteBlockInstrument instrument = NoteBlockInstrument.HARP;
        boolean replaceable;
        BlockBehaviour.StateArgumentPredicate<EntityType<?>> isValidSpawn = (blockState, blockGetter, blockPos, entityType) -> blockState.isFaceSturdy(
                blockGetter, blockPos, Direction.UP
            )
            && blockState.getLightEmission() < 14;
        BlockBehaviour.StatePredicate isRedstoneConductor = (state, level, pos) -> state.isCollisionShapeFullBlock(level, pos);
        BlockBehaviour.StatePredicate isSuffocating = (state, level, pos) -> state.blocksMotion() && state.isCollisionShapeFullBlock(level, pos);
        /**
         * If it blocks vision on the client side.
         */
        BlockBehaviour.StatePredicate isViewBlocking = this.isSuffocating;
        BlockBehaviour.StatePredicate hasPostProcess = (state, level, pos) -> false;
        BlockBehaviour.StatePredicate emissiveRendering = (blockState, blockGetter, blockPos) -> false;
        boolean dynamicShape;
        FeatureFlagSet requiredFeatures = FeatureFlags.VANILLA_SET;
        @Nullable
        BlockBehaviour.OffsetFunction offsetFunction;

        private Properties() {
        }

        public static BlockBehaviour.Properties of() {
            return new BlockBehaviour.Properties();
        }

        public static BlockBehaviour.Properties ofFullCopy(BlockBehaviour blockBehaviour) {
            BlockBehaviour.Properties properties = ofLegacyCopy(blockBehaviour);
            BlockBehaviour.Properties properties2 = blockBehaviour.properties;
            properties.jumpFactor = properties2.jumpFactor;
            properties.isRedstoneConductor = properties2.isRedstoneConductor;
            properties.isValidSpawn = properties2.isValidSpawn;
            properties.hasPostProcess = properties2.hasPostProcess;
            properties.isSuffocating = properties2.isSuffocating;
            properties.isViewBlocking = properties2.isViewBlocking;
            properties.drops = properties2.drops;
            return properties;
        }

        @Deprecated
        public static BlockBehaviour.Properties ofLegacyCopy(BlockBehaviour blockBehaviour) {
            BlockBehaviour.Properties properties = new BlockBehaviour.Properties();
            BlockBehaviour.Properties properties2 = blockBehaviour.properties;
            properties.destroyTime = properties2.destroyTime;
            properties.explosionResistance = properties2.explosionResistance;
            properties.hasCollision = properties2.hasCollision;
            properties.isRandomlyTicking = properties2.isRandomlyTicking;
            properties.lightEmission = properties2.lightEmission;
            properties.mapColor = properties2.mapColor;
            properties.soundType = properties2.soundType;
            properties.friction = properties2.friction;
            properties.speedFactor = properties2.speedFactor;
            properties.dynamicShape = properties2.dynamicShape;
            properties.canOcclude = properties2.canOcclude;
            properties.isAir = properties2.isAir;
            properties.ignitedByLava = properties2.ignitedByLava;
            properties.liquid = properties2.liquid;
            properties.forceSolidOff = properties2.forceSolidOff;
            properties.forceSolidOn = properties2.forceSolidOn;
            properties.pushReaction = properties2.pushReaction;
            properties.requiresCorrectToolForDrops = properties2.requiresCorrectToolForDrops;
            properties.offsetFunction = properties2.offsetFunction;
            properties.spawnTerrainParticles = properties2.spawnTerrainParticles;
            properties.requiredFeatures = properties2.requiredFeatures;
            properties.emissiveRendering = properties2.emissiveRendering;
            properties.instrument = properties2.instrument;
            properties.replaceable = properties2.replaceable;
            return properties;
        }

        public BlockBehaviour.Properties mapColor(DyeColor mapColor) {
            this.mapColor = blockState -> mapColor.getMapColor();
            return this;
        }

        public BlockBehaviour.Properties mapColor(MapColor mapColor) {
            this.mapColor = blockState -> mapColor;
            return this;
        }

        public BlockBehaviour.Properties mapColor(Function<BlockState, MapColor> mapColor) {
            this.mapColor = mapColor;
            return this;
        }

        public BlockBehaviour.Properties noCollission() {
            this.hasCollision = false;
            this.canOcclude = false;
            return this;
        }

        public BlockBehaviour.Properties noOcclusion() {
            this.canOcclude = false;
            return this;
        }

        public BlockBehaviour.Properties friction(float friction) {
            this.friction = friction;
            return this;
        }

        public BlockBehaviour.Properties speedFactor(float speedFactor) {
            this.speedFactor = speedFactor;
            return this;
        }

        public BlockBehaviour.Properties jumpFactor(float jumpFactor) {
            this.jumpFactor = jumpFactor;
            return this;
        }

        public BlockBehaviour.Properties sound(SoundType soundType) {
            this.soundType = soundType;
            return this;
        }

        public BlockBehaviour.Properties lightLevel(ToIntFunction<BlockState> lightEmission) {
            this.lightEmission = lightEmission;
            return this;
        }

        public BlockBehaviour.Properties strength(float destroyTime, float explosionResistance) {
            return this.destroyTime(destroyTime).explosionResistance(explosionResistance);
        }

        public BlockBehaviour.Properties instabreak() {
            return this.strength(0.0F);
        }

        public BlockBehaviour.Properties strength(float strength) {
            this.strength(strength, strength);
            return this;
        }

        public BlockBehaviour.Properties randomTicks() {
            this.isRandomlyTicking = true;
            return this;
        }

        public BlockBehaviour.Properties dynamicShape() {
            this.dynamicShape = true;
            return this;
        }

        public BlockBehaviour.Properties noLootTable() {
            this.drops = BuiltInLootTables.EMPTY;
            return this;
        }

        public BlockBehaviour.Properties dropsLike(Block block) {
            this.drops = block.getLootTable();
            return this;
        }

        public BlockBehaviour.Properties ignitedByLava() {
            this.ignitedByLava = true;
            return this;
        }

        public BlockBehaviour.Properties liquid() {
            this.liquid = true;
            return this;
        }

        public BlockBehaviour.Properties forceSolidOn() {
            this.forceSolidOn = true;
            return this;
        }

        @Deprecated
        public BlockBehaviour.Properties forceSolidOff() {
            this.forceSolidOff = true;
            return this;
        }

        public BlockBehaviour.Properties pushReaction(PushReaction pushReaction) {
            this.pushReaction = pushReaction;
            return this;
        }

        public BlockBehaviour.Properties air() {
            this.isAir = true;
            return this;
        }

        public BlockBehaviour.Properties isValidSpawn(BlockBehaviour.StateArgumentPredicate<EntityType<?>> isValidSpawn) {
            this.isValidSpawn = isValidSpawn;
            return this;
        }

        public BlockBehaviour.Properties isRedstoneConductor(BlockBehaviour.StatePredicate isRedstoneConductor) {
            this.isRedstoneConductor = isRedstoneConductor;
            return this;
        }

        public BlockBehaviour.Properties isSuffocating(BlockBehaviour.StatePredicate isSuffocating) {
            this.isSuffocating = isSuffocating;
            return this;
        }

        /**
         * If it blocks vision on the client side.
         */
        public BlockBehaviour.Properties isViewBlocking(BlockBehaviour.StatePredicate isViewBlocking) {
            this.isViewBlocking = isViewBlocking;
            return this;
        }

        public BlockBehaviour.Properties hasPostProcess(BlockBehaviour.StatePredicate hasPostProcess) {
            this.hasPostProcess = hasPostProcess;
            return this;
        }

        public BlockBehaviour.Properties emissiveRendering(BlockBehaviour.StatePredicate emissiveRendering) {
            this.emissiveRendering = emissiveRendering;
            return this;
        }

        public BlockBehaviour.Properties requiresCorrectToolForDrops() {
            this.requiresCorrectToolForDrops = true;
            return this;
        }

        public BlockBehaviour.Properties destroyTime(float destroyTime) {
            this.destroyTime = destroyTime;
            return this;
        }

        public BlockBehaviour.Properties explosionResistance(float explosionResistance) {
            this.explosionResistance = Math.max(0.0F, explosionResistance);
            return this;
        }

        public BlockBehaviour.Properties offsetType(BlockBehaviour.OffsetType offsetType) {
            this.offsetFunction = switch (offsetType) {
                case NONE -> null;
                case XZ -> (blockState, blockGetter, blockPos) -> {
                    Block block = blockState.getBlock();
                    long l = Mth.getSeed(blockPos.getX(), 0, blockPos.getZ());
                    float f = block.getMaxHorizontalOffset();
                    double d = Mth.clamp(((float)(l & 15L) / 15.0F - 0.5) * 0.5, -f, f);
                    double e = Mth.clamp(((float)(l >> 8 & 15L) / 15.0F - 0.5) * 0.5, -f, f);
                    return new Vec3(d, 0.0, e);
                };
                case XYZ -> (blockState, blockGetter, blockPos) -> {
                    Block block = blockState.getBlock();
                    long l = Mth.getSeed(blockPos.getX(), 0, blockPos.getZ());
                    double d = ((float)(l >> 4 & 15L) / 15.0F - 1.0) * block.getMaxVerticalOffset();
                    float f = block.getMaxHorizontalOffset();
                    double e = Mth.clamp(((float)(l & 15L) / 15.0F - 0.5) * 0.5, -f, f);
                    double g = Mth.clamp(((float)(l >> 8 & 15L) / 15.0F - 0.5) * 0.5, -f, f);
                    return new Vec3(e, d, g);
                };
            };
            return this;
        }

        public BlockBehaviour.Properties noTerrainParticles() {
            this.spawnTerrainParticles = false;
            return this;
        }

        public BlockBehaviour.Properties requiredFeatures(FeatureFlag... requiredFeatures) {
            this.requiredFeatures = FeatureFlags.REGISTRY.subset(requiredFeatures);
            return this;
        }

        public BlockBehaviour.Properties instrument(NoteBlockInstrument instrument) {
            this.instrument = instrument;
            return this;
        }

        public BlockBehaviour.Properties replaceable() {
            this.replaceable = true;
            return this;
        }
    }

    public interface StateArgumentPredicate<A> {
        boolean test(BlockState var1, BlockGetter var2, BlockPos var3, A var4);
    }

    public interface StatePredicate {
        boolean test(BlockState var1, BlockGetter var2, BlockPos var3);
    }
}
