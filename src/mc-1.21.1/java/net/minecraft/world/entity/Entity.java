package net.minecraft.world.entity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.ImmutableList.Builder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.floats.FloatArraySet;
import it.unimi.dsi.fastutil.floats.FloatArrays;
import it.unimi.dsi.fastutil.floats.FloatSet;
import it.unimi.dsi.fastutil.objects.Object2DoubleArrayMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.VecDeltaCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.Nameable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityInLevelCallback;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;

public abstract class Entity implements SyncedDataHolder, Nameable, EntityAccess, CommandSource, ScoreHolder {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final String ID_TAG = "id";
    public static final String PASSENGERS_TAG = "Passengers";
    private static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    public static final int CONTENTS_SLOT_INDEX = 0;
    public static final int BOARDING_COOLDOWN = 60;
    public static final int TOTAL_AIR_SUPPLY = 300;
    public static final int MAX_ENTITY_TAG_COUNT = 1024;
    public static final float DELTA_AFFECTED_BY_BLOCKS_BELOW_0_2 = 0.2F;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_0_5 = 0.500001;
    public static final double DELTA_AFFECTED_BY_BLOCKS_BELOW_1_0 = 0.999999;
    public static final int BASE_TICKS_REQUIRED_TO_FREEZE = 140;
    public static final int FREEZE_HURT_FREQUENCY = 40;
    public static final int BASE_SAFE_FALL_DISTANCE = 3;
    private static final AABB INITIAL_AABB = new AABB(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
    private static final double WATER_FLOW_SCALE = 0.014;
    private static final double LAVA_FAST_FLOW_SCALE = 0.007;
    private static final double LAVA_SLOW_FLOW_SCALE = 0.0023333333333333335;
    public static final String UUID_TAG = "UUID";
    private static double viewScale = 1.0;
    private final EntityType<?> type;
    private int id = ENTITY_COUNTER.incrementAndGet();
    public boolean blocksBuilding;
    private ImmutableList<Entity> passengers = ImmutableList.of();
    protected int boardingCooldown;
    @Nullable
    private Entity vehicle;
    private Level level;
    public double xo;
    public double yo;
    public double zo;
    private Vec3 position;
    private BlockPos blockPosition;
    private ChunkPos chunkPosition;
    private Vec3 deltaMovement = Vec3.ZERO;
    private float yRot;
    private float xRot;
    public float yRotO;
    public float xRotO;
    private AABB bb = INITIAL_AABB;
    private boolean onGround;
    public boolean horizontalCollision;
    public boolean verticalCollision;
    public boolean verticalCollisionBelow;
    public boolean minorHorizontalCollision;
    public boolean hurtMarked;
    protected Vec3 stuckSpeedMultiplier = Vec3.ZERO;
    @Nullable
    private Entity.RemovalReason removalReason;
    public static final float DEFAULT_BB_WIDTH = 0.6F;
    public static final float DEFAULT_BB_HEIGHT = 1.8F;
    public float walkDistO;
    public float walkDist;
    public float moveDist;
    public float flyDist;
    public float fallDistance;
    private float nextStep = 1.0F;
    public double xOld;
    public double yOld;
    public double zOld;
    public boolean noPhysics;
    protected final RandomSource random = RandomSource.create();
    public int tickCount;
    private int remainingFireTicks = -this.getFireImmuneTicks();
    protected boolean wasTouchingWater;
    protected Object2DoubleMap<TagKey<Fluid>> fluidHeight = new Object2DoubleArrayMap<>(2);
    protected boolean wasEyeInWater;
    private final Set<TagKey<Fluid>> fluidOnEyes = new HashSet<>();
    public int invulnerableTime;
    protected boolean firstTick = true;
    protected final SynchedEntityData entityData;
    protected static final EntityDataAccessor<Byte> DATA_SHARED_FLAGS_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BYTE);
    protected static final int FLAG_ONFIRE = 0;
    private static final int FLAG_SHIFT_KEY_DOWN = 1;
    private static final int FLAG_SPRINTING = 3;
    private static final int FLAG_SWIMMING = 4;
    private static final int FLAG_INVISIBLE = 5;
    protected static final int FLAG_GLOWING = 6;
    protected static final int FLAG_FALL_FLYING = 7;
    private static final EntityDataAccessor<Integer> DATA_AIR_SUPPLY_ID = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<Component>> DATA_CUSTOM_NAME = SynchedEntityData.defineId(
        Entity.class, EntityDataSerializers.OPTIONAL_COMPONENT
    );
    private static final EntityDataAccessor<Boolean> DATA_CUSTOM_NAME_VISIBLE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SILENT = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_NO_GRAVITY = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Pose> DATA_POSE = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.POSE);
    private static final EntityDataAccessor<Integer> DATA_TICKS_FROZEN = SynchedEntityData.defineId(Entity.class, EntityDataSerializers.INT);
    private EntityInLevelCallback levelCallback = EntityInLevelCallback.NULL;
    private final VecDeltaCodec packetPositionCodec = new VecDeltaCodec();
    public boolean noCulling;
    public boolean hasImpulse;
    @Nullable
    public PortalProcessor portalProcess;
    private int portalCooldown;
    private boolean invulnerable;
    protected UUID uuid = Mth.createInsecureUUID(this.random);
    protected String stringUUID = this.uuid.toString();
    private boolean hasGlowingTag;
    private final Set<String> tags = Sets.newHashSet();
    private final double[] pistonDeltas = new double[]{0.0, 0.0, 0.0};
    private long pistonDeltasGameTime;
    private EntityDimensions dimensions;
    private float eyeHeight;
    public boolean isInPowderSnow;
    public boolean wasInPowderSnow;
    public boolean wasOnFire;
    public Optional<BlockPos> mainSupportingBlockPos = Optional.empty();
    private boolean onGroundNoBlocks = false;
    private float crystalSoundIntensity;
    private int lastCrystalSoundPlayTick;
    private boolean hasVisualFire;
    @Nullable
    private BlockState inBlockState = null;

    public Entity(EntityType<?> entityType, Level level) {
        this.type = entityType;
        this.level = level;
        this.dimensions = entityType.getDimensions();
        this.position = Vec3.ZERO;
        this.blockPosition = BlockPos.ZERO;
        this.chunkPosition = ChunkPos.ZERO;
        SynchedEntityData.Builder builder = new SynchedEntityData.Builder(this);
        builder.define(DATA_SHARED_FLAGS_ID, (byte)0);
        builder.define(DATA_AIR_SUPPLY_ID, this.getMaxAirSupply());
        builder.define(DATA_CUSTOM_NAME_VISIBLE, false);
        builder.define(DATA_CUSTOM_NAME, Optional.empty());
        builder.define(DATA_SILENT, false);
        builder.define(DATA_NO_GRAVITY, false);
        builder.define(DATA_POSE, Pose.STANDING);
        builder.define(DATA_TICKS_FROZEN, 0);
        this.defineSynchedData(builder);
        this.entityData = builder.build();
        this.setPos(0.0, 0.0, 0.0);
        this.eyeHeight = this.dimensions.eyeHeight();
    }

    public boolean isColliding(BlockPos pos, BlockState state) {
        VoxelShape voxelShape = state.getCollisionShape(this.level(), pos, CollisionContext.of(this));
        VoxelShape voxelShape2 = voxelShape.move(pos.getX(), pos.getY(), pos.getZ());
        return Shapes.joinIsNotEmpty(voxelShape2, Shapes.create(this.getBoundingBox()), BooleanOp.AND);
    }

    public int getTeamColor() {
        Team team = this.getTeam();
        return team != null && team.getColor().getColor() != null ? team.getColor().getColor() : 16777215;
    }

    public boolean isSpectator() {
        return false;
    }

    public final void unRide() {
        if (this.isVehicle()) {
            this.ejectPassengers();
        }

        if (this.isPassenger()) {
            this.stopRiding();
        }
    }

    public void syncPacketPositionCodec(double x, double y, double z) {
        this.packetPositionCodec.setBase(new Vec3(x, y, z));
    }

    public VecDeltaCodec getPositionCodec() {
        return this.packetPositionCodec;
    }

    public EntityType<?> getType() {
        return this.type;
    }

    @Override
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Set<String> getTags() {
        return this.tags;
    }

    public boolean addTag(String tag) {
        return this.tags.size() >= 1024 ? false : this.tags.add(tag);
    }

    public boolean removeTag(String tag) {
        return this.tags.remove(tag);
    }

    public void kill() {
        this.remove(Entity.RemovalReason.KILLED);
        this.gameEvent(GameEvent.ENTITY_DIE);
    }

    public final void discard() {
        this.remove(Entity.RemovalReason.DISCARDED);
    }

    protected abstract void defineSynchedData(SynchedEntityData.Builder var1);

    public SynchedEntityData getEntityData() {
        return this.entityData;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof Entity ? ((Entity)object).id == this.id : false;
    }

    @Override
    public int hashCode() {
        return this.id;
    }

    public void remove(Entity.RemovalReason reason) {
        this.setRemoved(reason);
    }

    public void onClientRemoval() {
    }

    public void setPose(Pose pose) {
        this.entityData.set(DATA_POSE, pose);
    }

    public Pose getPose() {
        return this.entityData.get(DATA_POSE);
    }

    public boolean hasPose(Pose pose) {
        return this.getPose() == pose;
    }

    public boolean closerThan(Entity entity, double distance) {
        return this.position().closerThan(entity.position(), distance);
    }

    public boolean closerThan(Entity entity, double horizontalDistance, double verticalDistance) {
        double d = entity.getX() - this.getX();
        double e = entity.getY() - this.getY();
        double f = entity.getZ() - this.getZ();
        return Mth.lengthSquared(d, f) < Mth.square(horizontalDistance) && Mth.square(e) < Mth.square(verticalDistance);
    }

    /**
     * Sets the rotation of the entity.
     */
    protected void setRot(float yRot, float xRot) {
        this.setYRot(yRot % 360.0F);
        this.setXRot(xRot % 360.0F);
    }

    public final void setPos(Vec3 pos) {
        this.setPos(pos.x(), pos.y(), pos.z());
    }

    /**
     * Sets the x,y,z of the entity from the given parameters. Also seems to set up a bounding box.
     */
    public void setPos(double x, double y, double z) {
        this.setPosRaw(x, y, z);
        this.setBoundingBox(this.makeBoundingBox());
    }

    protected AABB makeBoundingBox() {
        return this.dimensions.makeBoundingBox(this.position);
    }

    protected void reapplyPosition() {
        this.setPos(this.position.x, this.position.y, this.position.z);
    }

    public void turn(double yRot, double xRot) {
        float f = (float)xRot * 0.15F;
        float g = (float)yRot * 0.15F;
        this.setXRot(this.getXRot() + f);
        this.setYRot(this.getYRot() + g);
        this.setXRot(Mth.clamp(this.getXRot(), -90.0F, 90.0F));
        this.xRotO += f;
        this.yRotO += g;
        this.xRotO = Mth.clamp(this.xRotO, -90.0F, 90.0F);
        if (this.vehicle != null) {
            this.vehicle.onPassengerTurned(this);
        }
    }

    public void tick() {
        this.baseTick();
    }

    public void baseTick() {
        this.level().getProfiler().push("entityBaseTick");
        this.inBlockState = null;
        if (this.isPassenger() && this.getVehicle().isRemoved()) {
            this.stopRiding();
        }

        if (this.boardingCooldown > 0) {
            this.boardingCooldown--;
        }

        this.walkDistO = this.walkDist;
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
        this.handlePortal();
        if (this.canSpawnSprintParticle()) {
            this.spawnSprintParticle();
        }

        this.wasInPowderSnow = this.isInPowderSnow;
        this.isInPowderSnow = false;
        this.updateInWaterStateAndDoFluidPushing();
        this.updateFluidOnEyes();
        this.updateSwimming();
        if (this.level().isClientSide) {
            this.clearFire();
        } else if (this.remainingFireTicks > 0) {
            if (this.fireImmune()) {
                this.setRemainingFireTicks(this.remainingFireTicks - 4);
                if (this.remainingFireTicks < 0) {
                    this.clearFire();
                }
            } else {
                if (this.remainingFireTicks % 20 == 0 && !this.isInLava()) {
                    this.hurt(this.damageSources().onFire(), 1.0F);
                }

                this.setRemainingFireTicks(this.remainingFireTicks - 1);
            }

            if (this.getTicksFrozen() > 0) {
                this.setTicksFrozen(0);
                this.level().levelEvent(null, 1009, this.blockPosition, 1);
            }
        }

        if (this.isInLava()) {
            this.lavaHurt();
            this.fallDistance *= 0.5F;
        }

        this.checkBelowWorld();
        if (!this.level().isClientSide) {
            this.setSharedFlagOnFire(this.remainingFireTicks > 0);
        }

        this.firstTick = false;
        if (!this.level().isClientSide && this instanceof Leashable) {
            Leashable.tickLeash((Entity & Leashable)this);
        }

        this.level().getProfiler().pop();
    }

    public void setSharedFlagOnFire(boolean isOnFire) {
        this.setSharedFlag(0, isOnFire || this.hasVisualFire);
    }

    public void checkBelowWorld() {
        if (this.getY() < this.level().getMinBuildHeight() - 64) {
            this.onBelowWorld();
        }
    }

    public void setPortalCooldown() {
        this.portalCooldown = this.getDimensionChangingDelay();
    }

    public void setPortalCooldown(int portalCooldown) {
        this.portalCooldown = portalCooldown;
    }

    public int getPortalCooldown() {
        return this.portalCooldown;
    }

    public boolean isOnPortalCooldown() {
        return this.portalCooldown > 0;
    }

    protected void processPortalCooldown() {
        if (this.isOnPortalCooldown()) {
            this.portalCooldown--;
        }
    }

    public void lavaHurt() {
        if (!this.fireImmune()) {
            this.igniteForSeconds(15.0F);
            if (this.hurt(this.damageSources().lava(), 4.0F)) {
                this.playSound(SoundEvents.GENERIC_BURN, 0.4F, 2.0F + this.random.nextFloat() * 0.4F);
            }
        }
    }

    public final void igniteForSeconds(float seconds) {
        this.igniteForTicks(Mth.floor(seconds * 20.0F));
    }

    public void igniteForTicks(int ticks) {
        if (this.remainingFireTicks < ticks) {
            this.setRemainingFireTicks(ticks);
        }
    }

    public void setRemainingFireTicks(int remainingFireTicks) {
        this.remainingFireTicks = remainingFireTicks;
    }

    public int getRemainingFireTicks() {
        return this.remainingFireTicks;
    }

    public void clearFire() {
        this.setRemainingFireTicks(0);
    }

    protected void onBelowWorld() {
        this.discard();
    }

    /**
     * Checks if the offset position from the entity's current position has a collision with a block or a liquid.
     */
    public boolean isFree(double x, double y, double z) {
        return this.isFree(this.getBoundingBox().move(x, y, z));
    }

    /**
     * Determines if the entity has no collision with a block or a liquid within the specified bounding box.
     */
    private boolean isFree(AABB box) {
        return this.level().noCollision(this, box) && !this.level().containsAnyLiquid(box);
    }

    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        this.checkSupportingBlock(onGround, null);
    }

    public void setOnGroundWithMovement(boolean onGround, Vec3 movement) {
        this.onGround = onGround;
        this.checkSupportingBlock(onGround, movement);
    }

    public boolean isSupportedBy(BlockPos pos) {
        return this.mainSupportingBlockPos.isPresent() && this.mainSupportingBlockPos.get().equals(pos);
    }

    protected void checkSupportingBlock(boolean onGround, @Nullable Vec3 movement) {
        if (onGround) {
            AABB aABB = this.getBoundingBox();
            AABB aABB2 = new AABB(aABB.minX, aABB.minY - 1.0E-6, aABB.minZ, aABB.maxX, aABB.minY, aABB.maxZ);
            Optional<BlockPos> optional = this.level.findSupportingBlock(this, aABB2);
            if (optional.isPresent() || this.onGroundNoBlocks) {
                this.mainSupportingBlockPos = optional;
            } else if (movement != null) {
                AABB aABB3 = aABB2.move(-movement.x, 0.0, -movement.z);
                optional = this.level.findSupportingBlock(this, aABB3);
                this.mainSupportingBlockPos = optional;
            }

            this.onGroundNoBlocks = optional.isEmpty();
        } else {
            this.onGroundNoBlocks = false;
            if (this.mainSupportingBlockPos.isPresent()) {
                this.mainSupportingBlockPos = Optional.empty();
            }
        }
    }

    public boolean onGround() {
        return this.onGround;
    }

    public void move(MoverType type, Vec3 pos) {
        if (this.noPhysics) {
            this.setPos(this.getX() + pos.x, this.getY() + pos.y, this.getZ() + pos.z);
        } else {
            this.wasOnFire = this.isOnFire();
            if (type == MoverType.PISTON) {
                pos = this.limitPistonMovement(pos);
                if (pos.equals(Vec3.ZERO)) {
                    return;
                }
            }

            this.level().getProfiler().push("move");
            if (this.stuckSpeedMultiplier.lengthSqr() > 1.0E-7) {
                pos = pos.multiply(this.stuckSpeedMultiplier);
                this.stuckSpeedMultiplier = Vec3.ZERO;
                this.setDeltaMovement(Vec3.ZERO);
            }

            pos = this.maybeBackOffFromEdge(pos, type);
            Vec3 vec3 = this.collide(pos);
            double d = vec3.lengthSqr();
            if (d > 1.0E-7) {
                if (this.fallDistance != 0.0F && d >= 1.0) {
                    BlockHitResult blockHitResult = this.level()
                        .clip(
                            new ClipContext(this.position(), this.position().add(vec3), ClipContext.Block.FALLDAMAGE_RESETTING, ClipContext.Fluid.WATER, this)
                        );
                    if (blockHitResult.getType() != HitResult.Type.MISS) {
                        this.resetFallDistance();
                    }
                }

                this.setPos(this.getX() + vec3.x, this.getY() + vec3.y, this.getZ() + vec3.z);
            }

            this.level().getProfiler().pop();
            this.level().getProfiler().push("rest");
            boolean bl = !Mth.equal(pos.x, vec3.x);
            boolean bl2 = !Mth.equal(pos.z, vec3.z);
            this.horizontalCollision = bl || bl2;
            this.verticalCollision = pos.y != vec3.y;
            this.verticalCollisionBelow = this.verticalCollision && pos.y < 0.0;
            if (this.horizontalCollision) {
                this.minorHorizontalCollision = this.isHorizontalCollisionMinor(vec3);
            } else {
                this.minorHorizontalCollision = false;
            }

            this.setOnGroundWithMovement(this.verticalCollisionBelow, vec3);
            BlockPos blockPos = this.getOnPosLegacy();
            BlockState blockState = this.level().getBlockState(blockPos);
            this.checkFallDamage(vec3.y, this.onGround(), blockState, blockPos);
            if (this.isRemoved()) {
                this.level().getProfiler().pop();
            } else {
                if (this.horizontalCollision) {
                    Vec3 vec32 = this.getDeltaMovement();
                    this.setDeltaMovement(bl ? 0.0 : vec32.x, vec32.y, bl2 ? 0.0 : vec32.z);
                }

                Block block = blockState.getBlock();
                if (pos.y != vec3.y) {
                    block.updateEntityAfterFallOn(this.level(), this);
                }

                if (this.onGround()) {
                    block.stepOn(this.level(), blockPos, blockState, this);
                }

                Entity.MovementEmission movementEmission = this.getMovementEmission();
                if (movementEmission.emitsAnything() && !this.isPassenger()) {
                    double e = vec3.x;
                    double f = vec3.y;
                    double g = vec3.z;
                    this.flyDist = this.flyDist + (float)(vec3.length() * 0.6);
                    BlockPos blockPos2 = this.getOnPos();
                    BlockState blockState2 = this.level().getBlockState(blockPos2);
                    boolean bl3 = this.isStateClimbable(blockState2);
                    if (!bl3) {
                        f = 0.0;
                    }

                    this.walkDist = this.walkDist + (float)vec3.horizontalDistance() * 0.6F;
                    this.moveDist = this.moveDist + (float)Math.sqrt(e * e + f * f + g * g) * 0.6F;
                    if (this.moveDist > this.nextStep && !blockState2.isAir()) {
                        boolean bl4 = blockPos2.equals(blockPos);
                        boolean bl5 = this.vibrationAndSoundEffectsFromBlock(blockPos, blockState, movementEmission.emitsSounds(), bl4, pos);
                        if (!bl4) {
                            bl5 |= this.vibrationAndSoundEffectsFromBlock(blockPos2, blockState2, false, movementEmission.emitsEvents(), pos);
                        }

                        if (bl5) {
                            this.nextStep = this.nextStep();
                        } else if (this.isInWater()) {
                            this.nextStep = this.nextStep();
                            if (movementEmission.emitsSounds()) {
                                this.waterSwimSound();
                            }

                            if (movementEmission.emitsEvents()) {
                                this.gameEvent(GameEvent.SWIM);
                            }
                        }
                    } else if (blockState2.isAir()) {
                        this.processFlappingMovement();
                    }
                }

                this.tryCheckInsideBlocks();
                float h = this.getBlockSpeedFactor();
                this.setDeltaMovement(this.getDeltaMovement().multiply(h, 1.0, h));
                if (this.level()
                    .getBlockStatesIfLoaded(this.getBoundingBox().deflate(1.0E-6))
                    .noneMatch(loadedState -> loadedState.is(BlockTags.FIRE) || loadedState.is(Blocks.LAVA))) {
                    if (this.remainingFireTicks <= 0) {
                        this.setRemainingFireTicks(-this.getFireImmuneTicks());
                    }

                    if (this.wasOnFire && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                        this.playEntityOnFireExtinguishedSound();
                    }
                }

                if (this.isOnFire() && (this.isInPowderSnow || this.isInWaterRainOrBubble())) {
                    this.setRemainingFireTicks(-this.getFireImmuneTicks());
                }

                this.level().getProfiler().pop();
            }
        }
    }

    private boolean isStateClimbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE) || state.is(Blocks.POWDER_SNOW);
    }

    private boolean vibrationAndSoundEffectsFromBlock(BlockPos pos, BlockState state, boolean playStepSound, boolean broadcastGameEvent, Vec3 entityPos) {
        if (state.isAir()) {
            return false;
        }

        boolean bl = this.isStateClimbable(state);
        if ((this.onGround() || bl || this.isCrouching() && entityPos.y == 0.0 || this.isOnRails()) && !this.isSwimming()) {
            if (playStepSound) {
                this.walkingStepSound(pos, state);
            }

            if (broadcastGameEvent) {
                this.level().gameEvent(GameEvent.STEP, this.position(), GameEvent.Context.of(this, state));
            }

            return true;
        } else {
            return false;
        }
    }

    protected boolean isHorizontalCollisionMinor(Vec3 deltaMovement) {
        return false;
    }

    protected void tryCheckInsideBlocks() {
        try {
            this.checkInsideBlocks();
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Checking entity block collision");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being checked for collision");
            this.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    protected void playEntityOnFireExtinguishedSound() {
        this.playSound(SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.7F, 1.6F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    public void extinguishFire() {
        if (!this.level().isClientSide && this.wasOnFire) {
            this.playEntityOnFireExtinguishedSound();
        }

        this.clearFire();
    }

    protected void processFlappingMovement() {
        if (this.isFlapping()) {
            this.onFlap();
            if (this.getMovementEmission().emitsEvents()) {
                this.gameEvent(GameEvent.FLAP);
            }
        }
    }

    @Deprecated
    public BlockPos getOnPosLegacy() {
        return this.getOnPos(0.2F);
    }

    public BlockPos getBlockPosBelowThatAffectsMyMovement() {
        return this.getOnPos(0.500001F);
    }

    public BlockPos getOnPos() {
        return this.getOnPos(1.0E-5F);
    }

    protected BlockPos getOnPos(float yOffset) {
        if (this.mainSupportingBlockPos.isPresent()) {
            BlockPos blockPos = this.mainSupportingBlockPos.get();
            if (!(yOffset > 1.0E-5F)) {
                return blockPos;
            }

            BlockState blockState = this.level().getBlockState(blockPos);
            return (!(yOffset <= 0.5) || !blockState.is(BlockTags.FENCES))
                    && !blockState.is(BlockTags.WALLS)
                    && !(blockState.getBlock() instanceof FenceGateBlock)
                ? blockPos.atY(Mth.floor(this.position.y - yOffset))
                : blockPos;
        } else {
            int i = Mth.floor(this.position.x);
            int j = Mth.floor(this.position.y - yOffset);
            int k = Mth.floor(this.position.z);
            return new BlockPos(i, j, k);
        }
    }

    protected float getBlockJumpFactor() {
        float f = this.level().getBlockState(this.blockPosition()).getBlock().getJumpFactor();
        float g = this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getJumpFactor();
        return f == 1.0 ? g : f;
    }

    protected float getBlockSpeedFactor() {
        BlockState blockState = this.level().getBlockState(this.blockPosition());
        float f = blockState.getBlock().getSpeedFactor();
        if (!blockState.is(Blocks.WATER) && !blockState.is(Blocks.BUBBLE_COLUMN)) {
            return f == 1.0 ? this.level().getBlockState(this.getBlockPosBelowThatAffectsMyMovement()).getBlock().getSpeedFactor() : f;
        } else {
            return f;
        }
    }

    protected Vec3 maybeBackOffFromEdge(Vec3 vec, MoverType mover) {
        return vec;
    }

    protected Vec3 limitPistonMovement(Vec3 pos) {
        if (pos.lengthSqr() <= 1.0E-7) {
            return pos;
        }

        long l = this.level().getGameTime();
        if (l != this.pistonDeltasGameTime) {
            Arrays.fill(this.pistonDeltas, 0.0);
            this.pistonDeltasGameTime = l;
        }

        if (pos.x != 0.0) {
            double d = this.applyPistonMovementRestriction(Direction.Axis.X, pos.x);
            return Math.abs(d) <= 1.0E-5F ? Vec3.ZERO : new Vec3(d, 0.0, 0.0);
        } else if (pos.y != 0.0) {
            double d = this.applyPistonMovementRestriction(Direction.Axis.Y, pos.y);
            return Math.abs(d) <= 1.0E-5F ? Vec3.ZERO : new Vec3(0.0, d, 0.0);
        } else if (pos.z != 0.0) {
            double d = this.applyPistonMovementRestriction(Direction.Axis.Z, pos.z);
            return Math.abs(d) <= 1.0E-5F ? Vec3.ZERO : new Vec3(0.0, 0.0, d);
        } else {
            return Vec3.ZERO;
        }
    }

    private double applyPistonMovementRestriction(Direction.Axis axis, double distance) {
        int i = axis.ordinal();
        double d = Mth.clamp(distance + this.pistonDeltas[i], -0.51, 0.51);
        distance = d - this.pistonDeltas[i];
        this.pistonDeltas[i] = d;
        return distance;
    }

    /**
     * Given a motion vector, return an updated vector that takes into account restrictions such as collisions (from all directions) and step-up from stepHeight
     */
    private Vec3 collide(Vec3 vec) {
        AABB aABB = this.getBoundingBox();
        List<VoxelShape> list = this.level().getEntityCollisions(this, aABB.expandTowards(vec));
        Vec3 vec3 = vec.lengthSqr() == 0.0 ? vec : collideBoundingBox(this, vec, aABB, this.level(), list);
        boolean bl = vec.x != vec3.x;
        boolean bl2 = vec.y != vec3.y;
        boolean bl3 = vec.z != vec3.z;
        boolean bl4 = bl2 && vec.y < 0.0;
        if (this.maxUpStep() > 0.0F && (bl4 || this.onGround()) && (bl || bl3)) {
            AABB aABB2 = bl4 ? aABB.move(0.0, vec3.y, 0.0) : aABB;
            AABB aABB3 = aABB2.expandTowards(vec.x, this.maxUpStep(), vec.z);
            if (!bl4) {
                aABB3 = aABB3.expandTowards(0.0, -1.0E-5F, 0.0);
            }

            List<VoxelShape> list2 = collectColliders(this, this.level, list, aABB3);
            float f = (float)vec3.y;
            float[] fs = collectCandidateStepUpHeights(aABB2, list2, this.maxUpStep(), f);

            for (float g : fs) {
                Vec3 vec32 = collideWithShapes(new Vec3(vec.x, g, vec.z), aABB2, list2);
                if (vec32.horizontalDistanceSqr() > vec3.horizontalDistanceSqr()) {
                    double d = aABB.minY - aABB2.minY;
                    return vec32.add(0.0, -d, 0.0);
                }
            }
        }

        return vec3;
    }

    private static float[] collectCandidateStepUpHeights(AABB box, List<VoxelShape> colliders, float deltaY, float maxUpStep) {
        FloatSet floatSet = new FloatArraySet(4);

        for (VoxelShape voxelShape : colliders) {
            for (double d : voxelShape.getCoords(Direction.Axis.Y)) {
                float f = (float)(d - box.minY);
                if (!(f < 0.0F) && f != maxUpStep) {
                    if (f > deltaY) {
                        break;
                    }

                    floatSet.add(f);
                }
            }
        }

        float[] fs = floatSet.toFloatArray();
        FloatArrays.unstableSort(fs);
        return fs;
    }

    public static Vec3 collideBoundingBox(@Nullable Entity entity, Vec3 vec, AABB collisionBox, Level level, List<VoxelShape> potentialHits) {
        List<VoxelShape> list = collectColliders(entity, level, potentialHits, collisionBox.expandTowards(vec));
        return collideWithShapes(vec, collisionBox, list);
    }

    private static List<VoxelShape> collectColliders(@Nullable Entity entity, Level level, List<VoxelShape> collisions, AABB boundingBox) {
        Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(collisions.size() + 1);
        if (!collisions.isEmpty()) {
            builder.addAll(collisions);
        }

        WorldBorder worldBorder = level.getWorldBorder();
        boolean bl = entity != null && worldBorder.isInsideCloseToBorder(entity, boundingBox);
        if (bl) {
            builder.add(worldBorder.getCollisionShape());
        }

        builder.addAll(level.getBlockCollisions(entity, boundingBox));
        return builder.build();
    }

    private static Vec3 collideWithShapes(Vec3 deltaMovement, AABB entityBB, List<VoxelShape> shapes) {
        if (shapes.isEmpty()) {
            return deltaMovement;
        }

        double d = deltaMovement.x;
        double e = deltaMovement.y;
        double f = deltaMovement.z;
        if (e != 0.0) {
            e = Shapes.collide(Direction.Axis.Y, entityBB, shapes, e);
            if (e != 0.0) {
                entityBB = entityBB.move(0.0, e, 0.0);
            }
        }

        boolean bl = Math.abs(d) < Math.abs(f);
        if (bl && f != 0.0) {
            f = Shapes.collide(Direction.Axis.Z, entityBB, shapes, f);
            if (f != 0.0) {
                entityBB = entityBB.move(0.0, 0.0, f);
            }
        }

        if (d != 0.0) {
            d = Shapes.collide(Direction.Axis.X, entityBB, shapes, d);
            if (!bl && d != 0.0) {
                entityBB = entityBB.move(d, 0.0, 0.0);
            }
        }

        if (!bl && f != 0.0) {
            f = Shapes.collide(Direction.Axis.Z, entityBB, shapes, f);
        }

        return new Vec3(d, e, f);
    }

    protected float nextStep() {
        return (int)this.moveDist + 1;
    }

    protected SoundEvent getSwimSound() {
        return SoundEvents.GENERIC_SWIM;
    }

    protected SoundEvent getSwimSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    protected SoundEvent getSwimHighSpeedSplashSound() {
        return SoundEvents.GENERIC_SPLASH;
    }

    protected void checkInsideBlocks() {
        AABB aABB = this.getBoundingBox();
        BlockPos blockPos = BlockPos.containing(aABB.minX + 1.0E-7, aABB.minY + 1.0E-7, aABB.minZ + 1.0E-7);
        BlockPos blockPos2 = BlockPos.containing(aABB.maxX - 1.0E-7, aABB.maxY - 1.0E-7, aABB.maxZ - 1.0E-7);
        if (this.level().hasChunksAt(blockPos, blockPos2)) {
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

            for (int i = blockPos.getX(); i <= blockPos2.getX(); i++) {
                for (int j = blockPos.getY(); j <= blockPos2.getY(); j++) {
                    for (int k = blockPos.getZ(); k <= blockPos2.getZ(); k++) {
                        if (!this.isAlive()) {
                            return;
                        }

                        mutableBlockPos.set(i, j, k);
                        BlockState blockState = this.level().getBlockState(mutableBlockPos);

                        try {
                            blockState.entityInside(this.level(), mutableBlockPos, this);
                            this.onInsideBlock(blockState);
                        } catch (Throwable throwable) {
                            CrashReport crashReport = CrashReport.forThrowable(throwable, "Colliding entity with block");
                            CrashReportCategory crashReportCategory = crashReport.addCategory("Block being collided with");
                            CrashReportCategory.populateBlockDetails(crashReportCategory, this.level(), mutableBlockPos, blockState);
                            throw new ReportedException(crashReport);
                        }
                    }
                }
            }
        }
    }

    protected void onInsideBlock(BlockState state) {
    }

    public BlockPos adjustSpawnLocation(ServerLevel level, BlockPos pos) {
        BlockPos blockPos = level.getSharedSpawnPos();
        Vec3 vec3 = blockPos.getCenter();
        int i = level.getChunkAt(blockPos).getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockPos.getX(), blockPos.getZ()) + 1;
        return BlockPos.containing(vec3.x, i, vec3.z);
    }

    public void gameEvent(Holder<GameEvent> gameEvent, @Nullable Entity entity) {
        this.level().gameEvent(entity, gameEvent, this.position);
    }

    public void gameEvent(Holder<GameEvent> gameEvent) {
        this.gameEvent(gameEvent, this);
    }

    private void walkingStepSound(BlockPos pos, BlockState state) {
        this.playStepSound(pos, state);
        if (this.shouldPlayAmethystStepSound(state)) {
            this.playAmethystStepSound();
        }
    }

    protected void waterSwimSound() {
        Entity entity = Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.35F : 0.4F;
        Vec3 vec3 = entity.getDeltaMovement();
        float g = Math.min(1.0F, (float)Math.sqrt(vec3.x * vec3.x * 0.2F + vec3.y * vec3.y + vec3.z * vec3.z * 0.2F) * f);
        this.playSwimSound(g);
    }

    protected BlockPos getPrimaryStepSoundBlockPos(BlockPos pos) {
        BlockPos blockPos = pos.above();
        BlockState blockState = this.level().getBlockState(blockPos);
        return !blockState.is(BlockTags.INSIDE_STEP_SOUND_BLOCKS) && !blockState.is(BlockTags.COMBINATION_STEP_SOUND_BLOCKS) ? pos : blockPos;
    }

    protected void playCombinationStepSounds(BlockState primaryState, BlockState secondaryState) {
        SoundType soundType = primaryState.getSoundType();
        this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.15F, soundType.getPitch());
        this.playMuffledStepSound(secondaryState);
    }

    protected void playMuffledStepSound(BlockState state) {
        SoundType soundType = state.getSoundType();
        this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.05F, soundType.getPitch() * 0.8F);
    }

    protected void playStepSound(BlockPos pos, BlockState state) {
        SoundType soundType = state.getSoundType();
        this.playSound(soundType.getStepSound(), soundType.getVolume() * 0.15F, soundType.getPitch());
    }

    private boolean shouldPlayAmethystStepSound(BlockState state) {
        return state.is(BlockTags.CRYSTAL_SOUND_BLOCKS) && this.tickCount >= this.lastCrystalSoundPlayTick + 20;
    }

    private void playAmethystStepSound() {
        this.crystalSoundIntensity = this.crystalSoundIntensity * (float)Math.pow(0.997, this.tickCount - this.lastCrystalSoundPlayTick);
        this.crystalSoundIntensity = Math.min(1.0F, this.crystalSoundIntensity + 0.07F);
        float f = 0.5F + this.crystalSoundIntensity * this.random.nextFloat() * 1.2F;
        float g = 0.1F + this.crystalSoundIntensity * 1.2F;
        this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, g, f);
        this.lastCrystalSoundPlayTick = this.tickCount;
    }

    protected void playSwimSound(float volume) {
        this.playSound(this.getSwimSound(), volume, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
    }

    protected void onFlap() {
    }

    protected boolean isFlapping() {
        return false;
    }

    public void playSound(SoundEvent sound, float volume, float pitch) {
        if (!this.isSilent()) {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch);
        }
    }

    public void playSound(SoundEvent sound) {
        if (!this.isSilent()) {
            this.playSound(sound, 1.0F, 1.0F);
        }
    }

    public boolean isSilent() {
        return this.entityData.get(DATA_SILENT);
    }

    /**
     * When set to true the entity will not play sounds.
     */
    public void setSilent(boolean isSilent) {
        this.entityData.set(DATA_SILENT, isSilent);
    }

    public boolean isNoGravity() {
        return this.entityData.get(DATA_NO_GRAVITY);
    }

    public void setNoGravity(boolean noGravity) {
        this.entityData.set(DATA_NO_GRAVITY, noGravity);
    }

    protected double getDefaultGravity() {
        return 0.0;
    }

    public final double getGravity() {
        return this.isNoGravity() ? 0.0 : this.getDefaultGravity();
    }

    protected void applyGravity() {
        double d = this.getGravity();
        if (d != 0.0) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -d, 0.0));
        }
    }

    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.ALL;
    }

    public boolean dampensVibrations() {
        return false;
    }

    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (onGround) {
            if (this.fallDistance > 0.0F) {
                state.getBlock().fallOn(this.level(), state, pos, this, this.fallDistance);
                this.level()
                    .gameEvent(
                        GameEvent.HIT_GROUND,
                        this.position,
                        GameEvent.Context.of(this, this.mainSupportingBlockPos.<BlockState>map(blockPos -> this.level().getBlockState(blockPos)).orElse(state))
                    );
            }

            this.resetFallDistance();
        } else if (y < 0.0) {
            this.fallDistance -= (float)y;
        }
    }

    public boolean fireImmune() {
        return this.getType().fireImmune();
    }

    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (this.type.is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return false;
        }

        if (this.isVehicle()) {
            for (Entity entity : this.getPassengers()) {
                entity.causeFallDamage(fallDistance, multiplier, source);
            }
        }

        return false;
    }

    public boolean isInWater() {
        return this.wasTouchingWater;
    }

    private boolean isInRain() {
        BlockPos blockPos = this.blockPosition();
        return this.level().isRainingAt(blockPos)
            || this.level().isRainingAt(BlockPos.containing(blockPos.getX(), this.getBoundingBox().maxY, blockPos.getZ()));
    }

    private boolean isInBubbleColumn() {
        return this.getInBlockState().is(Blocks.BUBBLE_COLUMN);
    }

    public boolean isInWaterOrRain() {
        return this.isInWater() || this.isInRain();
    }

    public boolean isInWaterRainOrBubble() {
        return this.isInWater() || this.isInRain() || this.isInBubbleColumn();
    }

    public boolean isInWaterOrBubble() {
        return this.isInWater() || this.isInBubbleColumn();
    }

    public boolean isInLiquid() {
        return this.isInWaterOrBubble() || this.isInLava();
    }

    public boolean isUnderWater() {
        return this.wasEyeInWater && this.isInWater();
    }

    public void updateSwimming() {
        if (this.isSwimming()) {
            this.setSwimming(this.isSprinting() && this.isInWater() && !this.isPassenger());
        } else {
            this.setSwimming(
                this.isSprinting() && this.isUnderWater() && !this.isPassenger() && this.level().getFluidState(this.blockPosition).is(FluidTags.WATER)
            );
        }
    }

    protected boolean updateInWaterStateAndDoFluidPushing() {
        this.fluidHeight.clear();
        this.updateInWaterStateAndDoWaterCurrentPushing();
        double d = this.level().dimensionType().ultraWarm() ? 0.007 : 0.0023333333333333335;
        boolean bl = this.updateFluidHeightAndDoFluidPushing(FluidTags.LAVA, d);
        return this.isInWater() || bl;
    }

    void updateInWaterStateAndDoWaterCurrentPushing() {
        if (this.getVehicle() instanceof Boat boat && !boat.isUnderWater()) {
            this.wasTouchingWater = false;
        } else if (this.updateFluidHeightAndDoFluidPushing(FluidTags.WATER, 0.014)) {
            if (!this.wasTouchingWater && !this.firstTick) {
                this.doWaterSplashEffect();
            }

            this.resetFallDistance();
            this.wasTouchingWater = true;
            this.clearFire();
        } else {
            this.wasTouchingWater = false;
        }
    }

    private void updateFluidOnEyes() {
        this.wasEyeInWater = this.isEyeInFluid(FluidTags.WATER);
        this.fluidOnEyes.clear();
        double d = this.getEyeY();
        if (!(this.getVehicle() instanceof Boat boat && !boat.isUnderWater() && boat.getBoundingBox().maxY >= d && boat.getBoundingBox().minY <= d)) {
            BlockPos blockPos = BlockPos.containing(this.getX(), d, this.getZ());
            FluidState fluidState = this.level().getFluidState(blockPos);
            double e = blockPos.getY() + fluidState.getHeight(this.level(), blockPos);
            if (e > d) {
                fluidState.getTags().forEach(this.fluidOnEyes::add);
            }
        }
    }

    protected void doWaterSplashEffect() {
        Entity entity = Objects.requireNonNullElse(this.getControllingPassenger(), this);
        float f = entity == this ? 0.2F : 0.9F;
        Vec3 vec3 = entity.getDeltaMovement();
        float g = Math.min(1.0F, (float)Math.sqrt(vec3.x * vec3.x * 0.2F + vec3.y * vec3.y + vec3.z * vec3.z * 0.2F) * f);
        if (g < 0.25F) {
            this.playSound(this.getSwimSplashSound(), g, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        } else {
            this.playSound(this.getSwimHighSpeedSplashSound(), g, 1.0F + (this.random.nextFloat() - this.random.nextFloat()) * 0.4F);
        }

        float h = Mth.floor(this.getY());

        for (int i = 0; i < 1.0F + this.dimensions.width() * 20.0F; i++) {
            double d = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
            double e = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
            this.level()
                .addParticle(ParticleTypes.BUBBLE, this.getX() + d, h + 1.0F, this.getZ() + e, vec3.x, vec3.y - this.random.nextDouble() * 0.2F, vec3.z);
        }

        for (int i = 0; i < 1.0F + this.dimensions.width() * 20.0F; i++) {
            double d = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
            double e = (this.random.nextDouble() * 2.0 - 1.0) * this.dimensions.width();
            this.level().addParticle(ParticleTypes.SPLASH, this.getX() + d, h + 1.0F, this.getZ() + e, vec3.x, vec3.y, vec3.z);
        }

        this.gameEvent(GameEvent.SPLASH);
    }

    @Deprecated
    protected BlockState getBlockStateOnLegacy() {
        return this.level().getBlockState(this.getOnPosLegacy());
    }

    public BlockState getBlockStateOn() {
        return this.level().getBlockState(this.getOnPos());
    }

    public boolean canSpawnSprintParticle() {
        return this.isSprinting() && !this.isInWater() && !this.isSpectator() && !this.isCrouching() && !this.isInLava() && this.isAlive();
    }

    protected void spawnSprintParticle() {
        BlockPos blockPos = this.getOnPosLegacy();
        BlockState blockState = this.level().getBlockState(blockPos);
        if (blockState.getRenderShape() != RenderShape.INVISIBLE) {
            Vec3 vec3 = this.getDeltaMovement();
            BlockPos blockPos2 = this.blockPosition();
            double d = this.getX() + (this.random.nextDouble() - 0.5) * this.dimensions.width();
            double e = this.getZ() + (this.random.nextDouble() - 0.5) * this.dimensions.width();
            if (blockPos2.getX() != blockPos.getX()) {
                d = Mth.clamp(d, blockPos.getX(), blockPos.getX() + 1.0);
            }

            if (blockPos2.getZ() != blockPos.getZ()) {
                e = Mth.clamp(e, blockPos.getZ(), blockPos.getZ() + 1.0);
            }

            this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockState), d, this.getY() + 0.1, e, vec3.x * -4.0, 1.5, vec3.z * -4.0);
        }
    }

    public boolean isEyeInFluid(TagKey<Fluid> fluidTag) {
        return this.fluidOnEyes.contains(fluidTag);
    }

    public boolean isInLava() {
        return !this.firstTick && this.fluidHeight.getDouble(FluidTags.LAVA) > 0.0;
    }

    public void moveRelative(float amount, Vec3 relative) {
        Vec3 vec3 = getInputVector(relative, amount, this.getYRot());
        this.setDeltaMovement(this.getDeltaMovement().add(vec3));
    }

    private static Vec3 getInputVector(Vec3 relative, float motionScaler, float facing) {
        double d = relative.lengthSqr();
        if (d < 1.0E-7) {
            return Vec3.ZERO;
        }

        Vec3 vec3 = (d > 1.0 ? relative.normalize() : relative).scale(motionScaler);
        float f = Mth.sin(facing * (float) (Math.PI / 180.0));
        float g = Mth.cos(facing * (float) (Math.PI / 180.0));
        return new Vec3(vec3.x * g - vec3.z * f, vec3.y, vec3.z * g + vec3.x * f);
    }

    @Deprecated
    public float getLightLevelDependentMagicValue() {
        return this.level().hasChunkAt(this.getBlockX(), this.getBlockZ())
            ? this.level().getLightLevelDependentMagicValue(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ()))
            : 0.0F;
    }

    /**
     * Sets position and rotation, clamping and wrapping params to valid values. Used by network code.
     */
    public void absMoveTo(double x, double y, double z, float yRot, float xRot) {
        this.absMoveTo(x, y, z);
        this.absRotateTo(yRot, xRot);
    }

    public void absRotateTo(float yRot, float xRot) {
        this.setYRot(yRot % 360.0F);
        this.setXRot(Mth.clamp(xRot, -90.0F, 90.0F) % 360.0F);
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public void absMoveTo(double x, double y, double z) {
        double d = Mth.clamp(x, -3.0E7, 3.0E7);
        double e = Mth.clamp(z, -3.0E7, 3.0E7);
        this.xo = d;
        this.yo = y;
        this.zo = e;
        this.setPos(d, y, e);
    }

    public void moveTo(Vec3 vec) {
        this.moveTo(vec.x, vec.y, vec.z);
    }

    public void moveTo(double x, double y, double z) {
        this.moveTo(x, y, z, this.getYRot(), this.getXRot());
    }

    public void moveTo(BlockPos pos, float yRot, float xRot) {
        this.moveTo(pos.getBottomCenter(), yRot, xRot);
    }

    public void moveTo(Vec3 pos, float yRot, float xRot) {
        this.moveTo(pos.x, pos.y, pos.z, yRot, xRot);
    }

    /**
     * Sets the location and rotation of the entity in the world.
     */
    public void moveTo(double x, double y, double z, float yRot, float xRot) {
        this.setPosRaw(x, y, z);
        this.setYRot(yRot);
        this.setXRot(xRot);
        this.setOldPosAndRot();
        this.reapplyPosition();
    }

    public final void setOldPosAndRot() {
        double d = this.getX();
        double e = this.getY();
        double f = this.getZ();
        this.xo = d;
        this.yo = e;
        this.zo = f;
        this.xOld = d;
        this.yOld = e;
        this.zOld = f;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    /**
     * Returns the distance to the entity.
     */
    public float distanceTo(Entity entity) {
        float f = (float)(this.getX() - entity.getX());
        float g = (float)(this.getY() - entity.getY());
        float h = (float)(this.getZ() - entity.getZ());
        return Mth.sqrt(f * f + g * g + h * h);
    }

    /**
     * Gets the squared distance to the position.
     */
    public double distanceToSqr(double x, double y, double z) {
        double d = this.getX() - x;
        double e = this.getY() - y;
        double f = this.getZ() - z;
        return d * d + e * e + f * f;
    }

    /**
     * Returns the squared distance to the entity.
     */
    public double distanceToSqr(Entity entity) {
        return this.distanceToSqr(entity.position());
    }

    public double distanceToSqr(Vec3 vec) {
        double d = this.getX() - vec.x;
        double e = this.getY() - vec.y;
        double f = this.getZ() - vec.z;
        return d * d + e * e + f * f;
    }

    /**
     * Called by a player entity when they collide with an entity
     */
    public void playerTouch(Player player) {
    }

    /**
     * Applies a velocity to the entities, to push them away from each other.
     */
    public void push(Entity entity) {
        if (!this.isPassengerOfSameVehicle(entity)) {
            if (!entity.noPhysics && !this.noPhysics) {
                double d = entity.getX() - this.getX();
                double e = entity.getZ() - this.getZ();
                double f = Mth.absMax(d, e);
                if (f >= 0.01F) {
                    f = Math.sqrt(f);
                    d /= f;
                    e /= f;
                    double g = 1.0 / f;
                    if (g > 1.0) {
                        g = 1.0;
                    }

                    d *= g;
                    e *= g;
                    d *= 0.05F;
                    e *= 0.05F;
                    if (!this.isVehicle() && this.isPushable()) {
                        this.push(-d, 0.0, -e);
                    }

                    if (!entity.isVehicle() && entity.isPushable()) {
                        entity.push(d, 0.0, e);
                    }
                }
            }
        }
    }

    public void push(Vec3 vector) {
        this.push(vector.x, vector.y, vector.z);
    }

    /**
     * Adds to the current velocity of the entity, and sets {@link #isAirBorne} to true.
     */
    public void push(double x, double y, double z) {
        this.setDeltaMovement(this.getDeltaMovement().add(x, y, z));
        this.hasImpulse = true;
    }

    protected void markHurt() {
        this.hurtMarked = true;
    }

    /**
     * Called when the entity is attacked.
     */
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }

        this.markHurt();
        return false;
    }

    /**
     * Gets the interpolated look vector.
     */
    public final Vec3 getViewVector(float partialTicks) {
        return this.calculateViewVector(this.getViewXRot(partialTicks), this.getViewYRot(partialTicks));
    }

    public Direction getNearestViewDirection() {
        return Direction.getNearest(this.getViewVector(1.0F));
    }

    /**
     * Returns the current X rotation of the entity.
     */
    public float getViewXRot(float partialTicks) {
        return partialTicks == 1.0F ? this.getXRot() : Mth.lerp(partialTicks, this.xRotO, this.getXRot());
    }

    /**
     * Returns the current Y rotation of the entity.
     */
    public float getViewYRot(float partialTick) {
        return partialTick == 1.0F ? this.getYRot() : Mth.lerp(partialTick, this.yRotO, this.getYRot());
    }

    /**
     * Calculates the view vector using the X and Y rotation of an entity.
     */
    public final Vec3 calculateViewVector(float xRot, float yRot) {
        float f = xRot * (float) (Math.PI / 180.0);
        float g = -yRot * (float) (Math.PI / 180.0);
        float h = Mth.cos(g);
        float i = Mth.sin(g);
        float j = Mth.cos(f);
        float k = Mth.sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    public final Vec3 getUpVector(float partialTicks) {
        return this.calculateUpVector(this.getViewXRot(partialTicks), this.getViewYRot(partialTicks));
    }

    protected final Vec3 calculateUpVector(float xRot, float yRot) {
        return this.calculateViewVector(xRot - 90.0F, yRot);
    }

    public final Vec3 getEyePosition() {
        return new Vec3(this.getX(), this.getEyeY(), this.getZ());
    }

    public final Vec3 getEyePosition(float partialTicks) {
        double d = Mth.lerp(partialTicks, this.xo, this.getX());
        double e = Mth.lerp(partialTicks, this.yo, this.getY()) + this.getEyeHeight();
        double f = Mth.lerp(partialTicks, this.zo, this.getZ());
        return new Vec3(d, e, f);
    }

    public Vec3 getLightProbePosition(float partialTicks) {
        return this.getEyePosition(partialTicks);
    }

    public final Vec3 getPosition(float partialTicks) {
        double d = Mth.lerp(partialTicks, this.xo, this.getX());
        double e = Mth.lerp(partialTicks, this.yo, this.getY());
        double f = Mth.lerp(partialTicks, this.zo, this.getZ());
        return new Vec3(d, e, f);
    }

    public HitResult pick(double hitDistance, float partialTicks, boolean hitFluids) {
        Vec3 vec3 = this.getEyePosition(partialTicks);
        Vec3 vec32 = this.getViewVector(partialTicks);
        Vec3 vec33 = vec3.add(vec32.x * hitDistance, vec32.y * hitDistance, vec32.z * hitDistance);
        return this.level().clip(new ClipContext(vec3, vec33, ClipContext.Block.OUTLINE, hitFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, this));
    }

    public boolean canBeHitByProjectile() {
        return this.isAlive() && this.isPickable();
    }

    public boolean isPickable() {
        return false;
    }

    public boolean isPushable() {
        return false;
    }

    public void awardKillScore(Entity killed, int scoreValue, DamageSource source) {
        if (killed instanceof ServerPlayer) {
            CriteriaTriggers.ENTITY_KILLED_PLAYER.trigger((ServerPlayer)killed, this, source);
        }
    }

    public boolean shouldRender(double x, double y, double z) {
        double d = this.getX() - x;
        double e = this.getY() - y;
        double f = this.getZ() - z;
        double g = d * d + e * e + f * f;
        return this.shouldRenderAtSqrDistance(g);
    }

    /**
     * Checks if the entity is in range to render.
     */
    public boolean shouldRenderAtSqrDistance(double distance) {
        double d = this.getBoundingBox().getSize();
        if (Double.isNaN(d)) {
            d = 1.0;
        }

        d *= 64.0 * viewScale;
        return distance < d * d;
    }

    /**
     * Writes this entity to NBT, unless it has been removed. Also writes this entity's passengers, and the entity type ID (so the produced NBT is sufficient to recreate the entity).
     *
     * Generally, {@link #writeUnlessPassenger} or {@link #writeWithoutTypeId} should be used instead of this method.
     *
     * @return True if the entity was written (and the passed compound should be saved)" false if the entity was not written.
     */
    public boolean saveAsPassenger(CompoundTag compound) {
        if (this.removalReason != null && !this.removalReason.shouldSave()) {
            return false;
        }

        String string = this.getEncodeId();
        if (string == null) {
            return false;
        }

        compound.putString("id", string);
        this.saveWithoutId(compound);
        return true;
    }

    /**
     * Writes this entity to NBT, unless it has been removed or it is a passenger. Also writes this entity's passengers, and the entity type ID (so the produced NBT is sufficient to recreate the entity).
     * To always write the entity, use {@link #writeWithoutTypeId}.
     *
     * @return True if the entity was written (and the passed compound should be saved)" false if the entity was not written.
     */
    public boolean save(CompoundTag compound) {
        return this.isPassenger() ? false : this.saveAsPassenger(compound);
    }

    /**
     * Writes this entity, including passengers, to NBT, regardless as to whether it is removed or a passenger. Does <b>not</b> include the entity's type ID, so the NBT is insufficient to recreate the entity using {@link AnvilChunkLoader#readWorldEntity}. Use {@link #writeUnlessPassenger} for that purpose.
     */
    public CompoundTag saveWithoutId(CompoundTag compound) {
        try {
            if (this.vehicle != null) {
                compound.put("Pos", this.newDoubleList(this.vehicle.getX(), this.getY(), this.vehicle.getZ()));
            } else {
                compound.put("Pos", this.newDoubleList(this.getX(), this.getY(), this.getZ()));
            }

            Vec3 vec3 = this.getDeltaMovement();
            compound.put("Motion", this.newDoubleList(vec3.x, vec3.y, vec3.z));
            compound.put("Rotation", this.newFloatList(this.getYRot(), this.getXRot()));
            compound.putFloat("FallDistance", this.fallDistance);
            compound.putShort("Fire", (short)this.remainingFireTicks);
            compound.putShort("Air", (short)this.getAirSupply());
            compound.putBoolean("OnGround", this.onGround());
            compound.putBoolean("Invulnerable", this.invulnerable);
            compound.putInt("PortalCooldown", this.portalCooldown);
            compound.putUUID("UUID", this.getUUID());
            Component component = this.getCustomName();
            if (component != null) {
                compound.putString("CustomName", Component.Serializer.toJson(component, this.registryAccess()));
            }

            if (this.isCustomNameVisible()) {
                compound.putBoolean("CustomNameVisible", this.isCustomNameVisible());
            }

            if (this.isSilent()) {
                compound.putBoolean("Silent", this.isSilent());
            }

            if (this.isNoGravity()) {
                compound.putBoolean("NoGravity", this.isNoGravity());
            }

            if (this.hasGlowingTag) {
                compound.putBoolean("Glowing", true);
            }

            int i = this.getTicksFrozen();
            if (i > 0) {
                compound.putInt("TicksFrozen", this.getTicksFrozen());
            }

            if (this.hasVisualFire) {
                compound.putBoolean("HasVisualFire", this.hasVisualFire);
            }

            if (!this.tags.isEmpty()) {
                ListTag listTag = new ListTag();

                for (String string : this.tags) {
                    listTag.add(StringTag.valueOf(string));
                }

                compound.put("Tags", listTag);
            }

            this.addAdditionalSaveData(compound);
            if (this.isVehicle()) {
                ListTag listTag = new ListTag();

                for (Entity entity : this.getPassengers()) {
                    CompoundTag compoundTag = new CompoundTag();
                    if (entity.saveAsPassenger(compoundTag)) {
                        listTag.add(compoundTag);
                    }
                }

                if (!listTag.isEmpty()) {
                    compound.put("Passengers", listTag);
                }
            }

            return compound;
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Saving entity NBT");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being saved");
            this.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    /**
     * Reads the entity from NBT (calls an abstract helper method to read specialized data)
     */
    public void load(CompoundTag compound) {
        try {
            ListTag listTag = compound.getList("Pos", 6);
            ListTag listTag2 = compound.getList("Motion", 6);
            ListTag listTag3 = compound.getList("Rotation", 5);
            double d = listTag2.getDouble(0);
            double e = listTag2.getDouble(1);
            double f = listTag2.getDouble(2);
            this.setDeltaMovement(Math.abs(d) > 10.0 ? 0.0 : d, Math.abs(e) > 10.0 ? 0.0 : e, Math.abs(f) > 10.0 ? 0.0 : f);
            double g = 3.0000512E7;
            this.setPosRaw(
                Mth.clamp(listTag.getDouble(0), -3.0000512E7, 3.0000512E7),
                Mth.clamp(listTag.getDouble(1), -2.0E7, 2.0E7),
                Mth.clamp(listTag.getDouble(2), -3.0000512E7, 3.0000512E7)
            );
            this.setYRot(listTag3.getFloat(0));
            this.setXRot(listTag3.getFloat(1));
            this.setOldPosAndRot();
            this.setYHeadRot(this.getYRot());
            this.setYBodyRot(this.getYRot());
            this.fallDistance = compound.getFloat("FallDistance");
            this.remainingFireTicks = compound.getShort("Fire");
            if (compound.contains("Air")) {
                this.setAirSupply(compound.getShort("Air"));
            }

            this.onGround = compound.getBoolean("OnGround");
            this.invulnerable = compound.getBoolean("Invulnerable");
            this.portalCooldown = compound.getInt("PortalCooldown");
            if (compound.hasUUID("UUID")) {
                this.uuid = compound.getUUID("UUID");
                this.stringUUID = this.uuid.toString();
            }

            if (!Double.isFinite(this.getX()) || !Double.isFinite(this.getY()) || !Double.isFinite(this.getZ())) {
                throw new IllegalStateException("Entity has invalid position");
            }

            if (Double.isFinite(this.getYRot()) && Double.isFinite(this.getXRot())) {
                this.reapplyPosition();
                this.setRot(this.getYRot(), this.getXRot());
                if (compound.contains("CustomName", 8)) {
                    String string = compound.getString("CustomName");

                    try {
                        this.setCustomName(Component.Serializer.fromJson(string, this.registryAccess()));
                    } catch (Exception exception) {
                        LOGGER.warn("Failed to parse entity custom name {}", string, exception);
                    }
                }

                this.setCustomNameVisible(compound.getBoolean("CustomNameVisible"));
                this.setSilent(compound.getBoolean("Silent"));
                this.setNoGravity(compound.getBoolean("NoGravity"));
                this.setGlowingTag(compound.getBoolean("Glowing"));
                this.setTicksFrozen(compound.getInt("TicksFrozen"));
                this.hasVisualFire = compound.getBoolean("HasVisualFire");
                if (compound.contains("Tags", 9)) {
                    this.tags.clear();
                    ListTag listTag4 = compound.getList("Tags", 8);
                    int i = Math.min(listTag4.size(), 1024);

                    for (int j = 0; j < i; j++) {
                        this.tags.add(listTag4.getString(j));
                    }
                }

                this.readAdditionalSaveData(compound);
                if (this.repositionEntityAfterLoad()) {
                    this.reapplyPosition();
                }
            } else {
                throw new IllegalStateException("Entity has invalid rotation");
            }
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Loading entity NBT");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Entity being loaded");
            this.fillCrashReportCategory(crashReportCategory);
            throw new ReportedException(crashReport);
        }
    }

    protected boolean repositionEntityAfterLoad() {
        return true;
    }

    @Nullable
    protected final String getEncodeId() {
        EntityType<?> entityType = this.getType();
        ResourceLocation resourceLocation = EntityType.getKey(entityType);
        return entityType.canSerialize() && resourceLocation != null ? resourceLocation.toString() : null;
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    protected abstract void readAdditionalSaveData(CompoundTag var1);

    protected abstract void addAdditionalSaveData(CompoundTag var1);

    /**
     * creates a NBT list from the array of doubles passed to this function
     */
    protected ListTag newDoubleList(double... numbers) {
        ListTag listTag = new ListTag();

        for (double d : numbers) {
            listTag.add(DoubleTag.valueOf(d));
        }

        return listTag;
    }

    /**
     * Returns a new NBTTagList filled with the specified floats
     */
    protected ListTag newFloatList(float... numbers) {
        ListTag listTag = new ListTag();

        for (float f : numbers) {
            listTag.add(FloatTag.valueOf(f));
        }

        return listTag;
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemLike item) {
        return this.spawnAtLocation(item, 0);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemLike item, int offsetY) {
        return this.spawnAtLocation(new ItemStack(item), offsetY);
    }

    @Nullable
    public ItemEntity spawnAtLocation(ItemStack stack) {
        return this.spawnAtLocation(stack, 0.0F);
    }

    /**
     * Drops an item at the position of the entity.
     */
    @Nullable
    public ItemEntity spawnAtLocation(ItemStack stack, float offsetY) {
        if (stack.isEmpty()) {
            return null;
        }

        if (this.level().isClientSide) {
            return null;
        }

        ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY() + offsetY, this.getZ(), stack);
        itemEntity.setDefaultPickUpDelay();
        this.level().addFreshEntity(itemEntity);
        return itemEntity;
    }

    public boolean isAlive() {
        return !this.isRemoved();
    }

    public boolean isInWall() {
        if (this.noPhysics) {
            return false;
        }

        float f = this.dimensions.width() * 0.8F;
        AABB aABB = AABB.ofSize(this.getEyePosition(), f, 1.0E-6, f);
        return BlockPos.betweenClosedStream(aABB)
            .anyMatch(
                blockPos -> {
                    BlockState blockState = this.level().getBlockState(blockPos);
                    return !blockState.isAir()
                        && blockState.isSuffocating(this.level(), blockPos)
                        && Shapes.joinIsNotEmpty(
                            blockState.getCollisionShape(this.level(), blockPos).move(blockPos.getX(), blockPos.getY(), blockPos.getZ()),
                            Shapes.create(aABB),
                            BooleanOp.AND
                        );
                }
            );
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        if (this.isAlive() && this instanceof Leashable leashable) {
            if (leashable.getLeashHolder() == player) {
                if (!this.level().isClientSide()) {
                    leashable.dropLeash(true, !player.hasInfiniteMaterials());
                    this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                }

                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }

            ItemStack itemStack = player.getItemInHand(hand);
            if (itemStack.is(Items.LEAD) && leashable.canHaveALeashAttachedToIt()) {
                if (!this.level().isClientSide()) {
                    leashable.setLeashedTo(player, true);
                }

                itemStack.shrink(1);
                return InteractionResult.sidedSuccess(this.level().isClientSide);
            }
        }

        return InteractionResult.PASS;
    }

    public boolean canCollideWith(Entity entity) {
        return entity.canBeCollidedWith() && !this.isPassengerOfSameVehicle(entity);
    }

    public boolean canBeCollidedWith() {
        return false;
    }

    public void rideTick() {
        this.setDeltaMovement(Vec3.ZERO);
        this.tick();
        if (this.isPassenger()) {
            this.getVehicle().positionRider(this);
        }
    }

    public final void positionRider(Entity passenger) {
        if (this.hasPassenger(passenger)) {
            this.positionRider(passenger, Entity::setPos);
        }
    }

    protected void positionRider(Entity passenger, Entity.MoveFunction callback) {
        Vec3 vec3 = this.getPassengerRidingPosition(passenger);
        Vec3 vec32 = passenger.getVehicleAttachmentPoint(this);
        callback.accept(passenger, vec3.x - vec32.x, vec3.y - vec32.y, vec3.z - vec32.z);
    }

    /**
     * Applies this entity's orientation to another entity. Used to update passenger orientation.
     */
    public void onPassengerTurned(Entity entityToUpdate) {
    }

    public Vec3 getVehicleAttachmentPoint(Entity entity) {
        return this.getAttachments().get(EntityAttachment.VEHICLE, 0, this.yRot);
    }

    public Vec3 getPassengerRidingPosition(Entity entity) {
        return this.position().add(this.getPassengerAttachmentPoint(entity, this.dimensions, 1.0F));
    }

    protected Vec3 getPassengerAttachmentPoint(Entity entity, EntityDimensions dimensions, float partialTick) {
        return getDefaultPassengerAttachmentPoint(this, entity, dimensions.attachments());
    }

    protected static Vec3 getDefaultPassengerAttachmentPoint(Entity vehicle, Entity passenger, EntityAttachments attachments) {
        int i = vehicle.getPassengers().indexOf(passenger);
        return attachments.getClamped(EntityAttachment.PASSENGER, i, vehicle.yRot);
    }

    public boolean startRiding(Entity vehicle) {
        return this.startRiding(vehicle, false);
    }

    public boolean showVehicleHealth() {
        return this instanceof LivingEntity;
    }

    public boolean startRiding(Entity vehicle, boolean force) {
        if (vehicle == this.vehicle) {
            return false;
        }

        if (!vehicle.couldAcceptPassenger()) {
            return false;
        }

        for (Entity entity = vehicle; entity.vehicle != null; entity = entity.vehicle) {
            if (entity.vehicle == this) {
                return false;
            }
        }

        if (force || this.canRide(vehicle) && vehicle.canAddPassenger(this)) {
            if (this.isPassenger()) {
                this.stopRiding();
            }

            this.setPose(Pose.STANDING);
            this.vehicle = vehicle;
            this.vehicle.addPassenger(this);
            vehicle.getIndirectPassengersStream()
                .filter(entityx -> entityx instanceof ServerPlayer)
                .forEach(entityx -> CriteriaTriggers.START_RIDING_TRIGGER.trigger((ServerPlayer)entityx));
            return true;
        } else {
            return false;
        }
    }

    protected boolean canRide(Entity vehicle) {
        return !this.isShiftKeyDown() && this.boardingCooldown <= 0;
    }

    public void ejectPassengers() {
        for (int i = this.passengers.size() - 1; i >= 0; i--) {
            this.passengers.get(i).stopRiding();
        }
    }

    public void removeVehicle() {
        if (this.vehicle != null) {
            Entity entity = this.vehicle;
            this.vehicle = null;
            entity.removePassenger(this);
        }
    }

    public void stopRiding() {
        this.removeVehicle();
    }

    protected void addPassenger(Entity passenger) {
        if (passenger.getVehicle() != this) {
            throw new IllegalStateException("Use x.startRiding(y), not y.addPassenger(x)");
        }

        if (this.passengers.isEmpty()) {
            this.passengers = ImmutableList.of(passenger);
        } else {
            List<Entity> list = Lists.newArrayList(this.passengers);
            if (!this.level().isClientSide && passenger instanceof Player && !(this.getFirstPassenger() instanceof Player)) {
                list.add(0, passenger);
            } else {
                list.add(passenger);
            }

            this.passengers = ImmutableList.copyOf(list);
        }

        this.gameEvent(GameEvent.ENTITY_MOUNT, passenger);
    }

    protected void removePassenger(Entity passenger) {
        if (passenger.getVehicle() == this) {
            throw new IllegalStateException("Use x.stopRiding(y), not y.removePassenger(x)");
        }

        if (this.passengers.size() == 1 && this.passengers.get(0) == passenger) {
            this.passengers = ImmutableList.of();
        } else {
            this.passengers = this.passengers.stream().filter(entity2 -> entity2 != passenger).collect(ImmutableList.toImmutableList());
        }

        passenger.boardingCooldown = 60;
        this.gameEvent(GameEvent.ENTITY_DISMOUNT, passenger);
    }

    protected boolean canAddPassenger(Entity passenger) {
        return this.passengers.isEmpty();
    }

    protected boolean couldAcceptPassenger() {
        return true;
    }

    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.setPos(x, y, z);
        this.setRot(yRot, xRot);
    }

    public double lerpTargetX() {
        return this.getX();
    }

    public double lerpTargetY() {
        return this.getY();
    }

    public double lerpTargetZ() {
        return this.getZ();
    }

    public float lerpTargetXRot() {
        return this.getXRot();
    }

    public float lerpTargetYRot() {
        return this.getYRot();
    }

    public void lerpHeadTo(float yaw, int pitch) {
        this.setYHeadRot(yaw);
    }

    public float getPickRadius() {
        return 0.0F;
    }

    public Vec3 getLookAngle() {
        return this.calculateViewVector(this.getXRot(), this.getYRot());
    }

    public Vec3 getHandHoldingItemAngle(Item item) {
        if (!(this instanceof Player player)) {
            return Vec3.ZERO;
        } else {
            boolean bl = player.getOffhandItem().is(item) && !player.getMainHandItem().is(item);
            HumanoidArm humanoidArm = bl ? player.getMainArm().getOpposite() : player.getMainArm();
            return this.calculateViewVector(0.0F, this.getYRot() + (humanoidArm == HumanoidArm.RIGHT ? 80 : -80)).scale(0.5);
        }
    }

    public Vec2 getRotationVector() {
        return new Vec2(this.getXRot(), this.getYRot());
    }

    public Vec3 getForward() {
        return Vec3.directionFromRotation(this.getRotationVector());
    }

    public void setAsInsidePortal(Portal portal, BlockPos pos) {
        if (this.isOnPortalCooldown()) {
            this.setPortalCooldown();
        } else {
            if (this.portalProcess != null && this.portalProcess.isSamePortal(portal)) {
                this.portalProcess.updateEntryPosition(pos.immutable());
                this.portalProcess.setAsInsidePortalThisTick(true);
            } else {
                this.portalProcess = new PortalProcessor(portal, pos.immutable());
            }
        }
    }

    protected void handlePortal() {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.processPortalCooldown();
            if (this.portalProcess != null) {
                if (this.portalProcess.processPortalTeleportation(serverLevel, this, this.canUsePortal(false))) {
                    serverLevel.getProfiler().push("portal");
                    this.setPortalCooldown();
                    DimensionTransition dimensionTransition = this.portalProcess.getPortalDestination(serverLevel, this);
                    if (dimensionTransition != null) {
                        ServerLevel serverLevel2 = dimensionTransition.newLevel();
                        if (serverLevel.getServer().isLevelEnabled(serverLevel2)
                            && (serverLevel2.dimension() == serverLevel.dimension() || this.canChangeDimensions(serverLevel, serverLevel2))) {
                            this.changeDimension(dimensionTransition);
                        }
                    }

                    serverLevel.getProfiler().pop();
                } else if (this.portalProcess.hasExpired()) {
                    this.portalProcess = null;
                }
            }
        }
    }

    public int getDimensionChangingDelay() {
        Entity entity = this.getFirstPassenger();
        return entity instanceof ServerPlayer ? entity.getDimensionChangingDelay() : 300;
    }

    /**
     * Updates the entity motion clientside, called by packets from the server
     */
    public void lerpMotion(double x, double y, double z) {
        this.setDeltaMovement(x, y, z);
    }

    public void handleDamageEvent(DamageSource damageSource) {
    }

    /**
     * Handles an entity event received from a {@link net.minecraft.network.protocol.game.ClientboundEntityEventPacket}.
     */
    public void handleEntityEvent(byte id) {
        switch (id) {
            case 53:
                HoneyBlock.showSlideParticles(this);
        }
    }

    public void animateHurt(float yaw) {
    }

    public boolean isOnFire() {
        boolean bl = this.level() != null && this.level().isClientSide;
        return !this.fireImmune() && (this.remainingFireTicks > 0 || bl && this.getSharedFlag(0));
    }

    public boolean isPassenger() {
        return this.getVehicle() != null;
    }

    public boolean isVehicle() {
        return !this.passengers.isEmpty();
    }

    public boolean dismountsUnderwater() {
        return this.getType().is(EntityTypeTags.DISMOUNTS_UNDERWATER);
    }

    public boolean canControlVehicle() {
        return !this.getType().is(EntityTypeTags.NON_CONTROLLING_RIDER);
    }

    public void setShiftKeyDown(boolean keyDown) {
        this.setSharedFlag(1, keyDown);
    }

    public boolean isShiftKeyDown() {
        return this.getSharedFlag(1);
    }

    public boolean isSteppingCarefully() {
        return this.isShiftKeyDown();
    }

    public boolean isSuppressingBounce() {
        return this.isShiftKeyDown();
    }

    public boolean isDiscrete() {
        return this.isShiftKeyDown();
    }

    public boolean isDescending() {
        return this.isShiftKeyDown();
    }

    public boolean isCrouching() {
        return this.hasPose(Pose.CROUCHING);
    }

    public boolean isSprinting() {
        return this.getSharedFlag(3);
    }

    /**
     * Set sprinting switch for Entity.
     */
    public void setSprinting(boolean sprinting) {
        this.setSharedFlag(3, sprinting);
    }

    public boolean isSwimming() {
        return this.getSharedFlag(4);
    }

    public boolean isVisuallySwimming() {
        return this.hasPose(Pose.SWIMMING);
    }

    public boolean isVisuallyCrawling() {
        return this.isVisuallySwimming() && !this.isInWater();
    }

    public void setSwimming(boolean swimming) {
        this.setSharedFlag(4, swimming);
    }

    public final boolean hasGlowingTag() {
        return this.hasGlowingTag;
    }

    public final void setGlowingTag(boolean hasGlowingTag) {
        this.hasGlowingTag = hasGlowingTag;
        this.setSharedFlag(6, this.isCurrentlyGlowing());
    }

    public boolean isCurrentlyGlowing() {
        return this.level().isClientSide() ? this.getSharedFlag(6) : this.hasGlowingTag;
    }

    public boolean isInvisible() {
        return this.getSharedFlag(5);
    }

    /**
     * Only used by renderer in EntityLivingBase subclasses.
     * Determines if an entity is visible or not to a specific player, if the entity is normally invisible.
     * For EntityLivingBase subclasses, returning false when invisible will render the entity semi-transparent.
     */
    public boolean isInvisibleTo(Player player) {
        if (player.isSpectator()) {
            return false;
        }

        Team team = this.getTeam();
        return team != null && player != null && player.getTeam() == team && team.canSeeFriendlyInvisibles() ? false : this.isInvisible();
    }

    public boolean isOnRails() {
        return false;
    }

    public void updateDynamicGameEventListener(BiConsumer<DynamicGameEventListener<?>, ServerLevel> listenerConsumer) {
    }

    @Nullable
    public PlayerTeam getTeam() {
        return this.level().getScoreboard().getPlayersTeam(this.getScoreboardName());
    }

    /**
     * Returns whether this Entity is on the same team as the given Entity.
     */
    public boolean isAlliedTo(Entity entity) {
        return this.isAlliedTo(entity.getTeam());
    }

    /**
     * Returns whether this Entity is on the given scoreboard team.
     */
    public boolean isAlliedTo(Team team) {
        return this.getTeam() != null ? this.getTeam().isAlliedTo(team) : false;
    }

    public void setInvisible(boolean invisible) {
        this.setSharedFlag(5, invisible);
    }

    /**
     * Returns {@code true} if the flag is active for the entity. Known flags: 0: burning 1: sneaking 2: unused 3: sprinting 4: swimming 5: invisible 6: glowing 7: elytra flying
     */
    protected boolean getSharedFlag(int flag) {
        return (this.entityData.get(DATA_SHARED_FLAGS_ID) & 1 << flag) != 0;
    }

    /**
     * Enable or disable an entity flag, see {@link #getEntityFlag} to read the known flags.
     */
    protected void setSharedFlag(int flag, boolean set) {
        byte b = this.entityData.get(DATA_SHARED_FLAGS_ID);
        if (set) {
            this.entityData.set(DATA_SHARED_FLAGS_ID, (byte)(b | 1 << flag));
        } else {
            this.entityData.set(DATA_SHARED_FLAGS_ID, (byte)(b & ~(1 << flag)));
        }
    }

    public int getMaxAirSupply() {
        return 300;
    }

    public int getAirSupply() {
        return this.entityData.get(DATA_AIR_SUPPLY_ID);
    }

    public void setAirSupply(int air) {
        this.entityData.set(DATA_AIR_SUPPLY_ID, air);
    }

    public int getTicksFrozen() {
        return this.entityData.get(DATA_TICKS_FROZEN);
    }

    public void setTicksFrozen(int ticksFrozen) {
        this.entityData.set(DATA_TICKS_FROZEN, ticksFrozen);
    }

    public float getPercentFrozen() {
        int i = this.getTicksRequiredToFreeze();
        return (float)Math.min(this.getTicksFrozen(), i) / i;
    }

    public boolean isFullyFrozen() {
        return this.getTicksFrozen() >= this.getTicksRequiredToFreeze();
    }

    public int getTicksRequiredToFreeze() {
        return 140;
    }

    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        this.setRemainingFireTicks(this.remainingFireTicks + 1);
        if (this.remainingFireTicks == 0) {
            this.igniteForSeconds(8.0F);
        }

        this.hurt(this.damageSources().lightningBolt(), 5.0F);
    }

    public void onAboveBubbleCol(boolean downwards) {
        Vec3 vec3 = this.getDeltaMovement();
        double d;
        if (downwards) {
            d = Math.max(-0.9, vec3.y - 0.03);
        } else {
            d = Math.min(1.8, vec3.y + 0.1);
        }

        this.setDeltaMovement(vec3.x, d, vec3.z);
    }

    public void onInsideBubbleColumn(boolean downwards) {
        Vec3 vec3 = this.getDeltaMovement();
        double d;
        if (downwards) {
            d = Math.max(-0.3, vec3.y - 0.03);
        } else {
            d = Math.min(0.7, vec3.y + 0.06);
        }

        this.setDeltaMovement(vec3.x, d, vec3.z);
        this.resetFallDistance();
    }

    public boolean killedEntity(ServerLevel level, LivingEntity entity) {
        return true;
    }

    public void checkSlowFallDistance() {
        if (this.getDeltaMovement().y() > -0.5 && this.fallDistance > 1.0F) {
            this.fallDistance = 1.0F;
        }
    }

    public void resetFallDistance() {
        this.fallDistance = 0.0F;
    }

    protected void moveTowardsClosestSpace(double x, double y, double z) {
        BlockPos blockPos = BlockPos.containing(x, y, z);
        Vec3 vec3 = new Vec3(x - blockPos.getX(), y - blockPos.getY(), z - blockPos.getZ());
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        Direction direction = Direction.UP;
        double d = Double.MAX_VALUE;

        for (Direction direction2 : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
            mutableBlockPos.setWithOffset(blockPos, direction2);
            if (!this.level().getBlockState(mutableBlockPos).isCollisionShapeFullBlock(this.level(), mutableBlockPos)) {
                double e = vec3.get(direction2.getAxis());
                double f = direction2.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - e : e;
                if (f < d) {
                    d = f;
                    direction = direction2;
                }
            }
        }

        float g = this.random.nextFloat() * 0.2F + 0.1F;
        float h = direction.getAxisDirection().getStep();
        Vec3 vec32 = this.getDeltaMovement().scale(0.75);
        if (direction.getAxis() == Direction.Axis.X) {
            this.setDeltaMovement(h * g, vec32.y, vec32.z);
        } else if (direction.getAxis() == Direction.Axis.Y) {
            this.setDeltaMovement(vec32.x, h * g, vec32.z);
        } else if (direction.getAxis() == Direction.Axis.Z) {
            this.setDeltaMovement(vec32.x, vec32.y, h * g);
        }
    }

    public void makeStuckInBlock(BlockState state, Vec3 motionMultiplier) {
        this.resetFallDistance();
        this.stuckSpeedMultiplier = motionMultiplier;
    }

    private static Component removeAction(Component name) {
        MutableComponent mutableComponent = name.plainCopy().setStyle(name.getStyle().withClickEvent(null));

        for (Component component : name.getSiblings()) {
            mutableComponent.append(removeAction(component));
        }

        return mutableComponent;
    }

    @Override
    public Component getName() {
        Component component = this.getCustomName();
        return component != null ? removeAction(component) : this.getTypeName();
    }

    protected Component getTypeName() {
        return this.type.getDescription();
    }

    /**
     * Returns {@code true} if Entity argument is equal to this Entity
     */
    public boolean is(Entity entity) {
        return this == entity;
    }

    public float getYHeadRot() {
        return 0.0F;
    }

    /**
     * Sets the head's Y rotation of the entity.
     */
    public void setYHeadRot(float yHeadRot) {
    }

    /**
     * Set the body Y rotation of the entity.
     */
    public void setYBodyRot(float yBodyRot) {
    }

    public boolean isAttackable() {
        return true;
    }

    /**
     * Called when a player attacks an entity. If this returns true the attack will not happen.
     */
    public boolean skipAttackInteraction(Entity entity) {
        return false;
    }

    @Override
    public String toString() {
        String string = this.level() == null ? "~NULL~" : this.level().toString();
        return this.removalReason != null
            ? String.format(
                Locale.ROOT,
                "%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f, removed=%s]",
                this.getClass().getSimpleName(),
                this.getName().getString(),
                this.id,
                string,
                this.getX(),
                this.getY(),
                this.getZ(),
                this.removalReason
            )
            : String.format(
                Locale.ROOT,
                "%s['%s'/%d, l='%s', x=%.2f, y=%.2f, z=%.2f]",
                this.getClass().getSimpleName(),
                this.getName().getString(),
                this.id,
                string,
                this.getX(),
                this.getY(),
                this.getZ()
            );
    }

    /**
     * Returns whether this Entity is invulnerable to the given DamageSource.
     */
    public boolean isInvulnerableTo(DamageSource source) {
        return this.isRemoved()
            || this.invulnerable && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.isCreativePlayer()
            || source.is(DamageTypeTags.IS_FIRE) && this.fireImmune()
            || source.is(DamageTypeTags.IS_FALL) && this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE);
    }

    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    /**
     * Sets whether this Entity is invulnerable.
     */
    public void setInvulnerable(boolean isInvulnerable) {
        this.invulnerable = isInvulnerable;
    }

    /**
     * Sets this entity's location and angles to the location and angles of the passed in entity.
     */
    public void copyPosition(Entity entity) {
        this.moveTo(entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), entity.getXRot());
    }

    /**
     * Prepares this entity in new dimension by copying NBT data from entity in old dimension
     */
    public void restoreFrom(Entity entity) {
        CompoundTag compoundTag = entity.saveWithoutId(new CompoundTag());
        compoundTag.remove("Dimension");
        this.load(compoundTag);
        this.portalCooldown = entity.portalCooldown;
        this.portalProcess = entity.portalProcess;
    }

    @Nullable
    public Entity changeDimension(DimensionTransition transition) {
        if (this.level() instanceof ServerLevel serverLevel && !this.isRemoved()) {
            ServerLevel serverLevel2 = transition.newLevel();
            List<Entity> list = this.getPassengers();
            this.unRide();
            List<Entity> list2 = new ArrayList<>();

            for (Entity entity : list) {
                Entity entity2 = entity.changeDimension(transition);
                if (entity2 != null) {
                    list2.add(entity2);
                }
            }

            serverLevel.getProfiler().push("changeDimension");
            Entity entity3 = serverLevel2.dimension() == serverLevel.dimension() ? this : this.getType().create(serverLevel2);
            if (entity3 != null) {
                if (this != entity3) {
                    entity3.restoreFrom(this);
                    this.removeAfterChangingDimensions();
                }

                entity3.moveTo(transition.pos().x, transition.pos().y, transition.pos().z, transition.yRot(), entity3.getXRot());
                entity3.setDeltaMovement(transition.speed());
                if (this != entity3) {
                    serverLevel2.addDuringTeleport(entity3);
                }

                for (Entity entity2 : list2) {
                    entity2.startRiding(entity3, true);
                }

                serverLevel.resetEmptyTime();
                serverLevel2.resetEmptyTime();
                transition.postDimensionTransition().onTransition(entity3);
            }

            serverLevel.getProfiler().pop();
            return entity3;
        } else {
            return null;
        }
    }

    public void placePortalTicket(BlockPos pos) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(pos), 3, pos);
        }
    }

    protected void removeAfterChangingDimensions() {
        this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
        if (this instanceof Leashable leashable) {
            leashable.dropLeash(true, false);
        }
    }

    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portal) {
        return PortalShape.getRelativePosition(portal, axis, this.position(), this.getDimensions(this.getPose()));
    }

    public boolean canUsePortal(boolean allowPassengers) {
        return (allowPassengers || !this.isPassenger()) && this.isAlive();
    }

    public boolean canChangeDimensions(Level oldLevel, Level newLevel) {
        return true;
    }

    /**
     * Explosion resistance of a block relative to this entity
     */
    public float getBlockExplosionResistance(
        Explosion explosion, BlockGetter level, BlockPos pos, BlockState blockState, FluidState fluidState, float explosionPower
    ) {
        return explosionPower;
    }

    public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState blockState, float explosionPower) {
        return true;
    }

    public int getMaxFallDistance() {
        return 3;
    }

    public boolean isIgnoringBlockTriggers() {
        return false;
    }

    public void fillCrashReportCategory(CrashReportCategory category) {
        category.setDetail("Entity Type", () -> EntityType.getKey(this.getType()) + " (" + this.getClass().getCanonicalName() + ")");
        category.setDetail("Entity ID", this.id);
        category.setDetail("Entity Name", () -> this.getName().getString());
        category.setDetail("Entity's Exact location", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", this.getX(), this.getY(), this.getZ()));
        category.setDetail(
            "Entity's Block location", CrashReportCategory.formatLocation(this.level(), Mth.floor(this.getX()), Mth.floor(this.getY()), Mth.floor(this.getZ()))
        );
        Vec3 vec3 = this.getDeltaMovement();
        category.setDetail("Entity's Momentum", String.format(Locale.ROOT, "%.2f, %.2f, %.2f", vec3.x, vec3.y, vec3.z));
        category.setDetail("Entity's Passengers", () -> this.getPassengers().toString());
        category.setDetail("Entity's Vehicle", () -> String.valueOf(this.getVehicle()));
    }

    public boolean displayFireAnimation() {
        return this.isOnFire() && !this.isSpectator();
    }

    public void setUUID(UUID uniqueId) {
        this.uuid = uniqueId;
        this.stringUUID = this.uuid.toString();
    }

    @Override
    public UUID getUUID() {
        return this.uuid;
    }

    public String getStringUUID() {
        return this.stringUUID;
    }

    @Override
    public String getScoreboardName() {
        return this.stringUUID;
    }

    public boolean isPushedByFluid() {
        return true;
    }

    public static double getViewScale() {
        return viewScale;
    }

    public static void setViewScale(double renderDistWeight) {
        viewScale = renderDistWeight;
    }

    @Override
    public Component getDisplayName() {
        return PlayerTeam.formatNameForTeam(this.getTeam(), this.getName())
            .withStyle(style -> style.withHoverEvent(this.createHoverEvent()).withInsertion(this.getStringUUID()));
    }

    public void setCustomName(@Nullable Component name) {
        this.entityData.set(DATA_CUSTOM_NAME, Optional.ofNullable(name));
    }

    @Nullable
    @Override
    public Component getCustomName() {
        return this.entityData.get(DATA_CUSTOM_NAME).orElse(null);
    }

    @Override
    public boolean hasCustomName() {
        return this.entityData.get(DATA_CUSTOM_NAME).isPresent();
    }

    public void setCustomNameVisible(boolean alwaysRenderNameTag) {
        this.entityData.set(DATA_CUSTOM_NAME_VISIBLE, alwaysRenderNameTag);
    }

    public boolean isCustomNameVisible() {
        return this.entityData.get(DATA_CUSTOM_NAME_VISIBLE);
    }

    public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements, float yRot, float xRot) {
        float f = Mth.clamp(xRot, -90.0F, 90.0F);
        if (level == this.level()) {
            this.moveTo(x, y, z, yRot, f);
            this.teleportPassengers();
            this.setYHeadRot(yRot);
        } else {
            this.unRide();
            Entity entity = this.getType().create(level);
            if (entity == null) {
                return false;
            }

            entity.restoreFrom(this);
            entity.moveTo(x, y, z, yRot, f);
            entity.setYHeadRot(yRot);
            this.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
            level.addDuringTeleport(entity);
        }

        return true;
    }

    public void dismountTo(double x, double y, double z) {
        this.teleportTo(x, y, z);
    }

    /**
     * Sets the position of the entity and updates the 'last' variables
     */
    public void teleportTo(double x, double y, double z) {
        if (this.level() instanceof ServerLevel) {
            this.moveTo(x, y, z, this.getYRot(), this.getXRot());
            this.teleportPassengers();
        }
    }

    private void teleportPassengers() {
        this.getSelfAndPassengers().forEach(entity -> {
            for (Entity entity2 : entity.passengers) {
                entity.positionRider(entity2, Entity::moveTo);
            }
        });
    }

    public void teleportRelative(double dx, double dy, double dz) {
        this.teleportTo(this.getX() + dx, this.getY() + dy, this.getZ() + dz);
    }

    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    @Override
    public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> dataValues) {
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_POSE.equals(key)) {
            this.refreshDimensions();
        }
    }

    @Deprecated
    protected void fixupDimensions() {
        Pose pose = this.getPose();
        EntityDimensions entityDimensions = this.getDimensions(pose);
        this.dimensions = entityDimensions;
        this.eyeHeight = entityDimensions.eyeHeight();
    }

    public void refreshDimensions() {
        EntityDimensions entityDimensions = this.dimensions;
        Pose pose = this.getPose();
        EntityDimensions entityDimensions2 = this.getDimensions(pose);
        this.dimensions = entityDimensions2;
        this.eyeHeight = entityDimensions2.eyeHeight();
        this.reapplyPosition();
        boolean bl = entityDimensions2.width() <= 4.0 && entityDimensions2.height() <= 4.0;
        if (!this.level.isClientSide
            && !this.firstTick
            && !this.noPhysics
            && bl
            && (entityDimensions2.width() > entityDimensions.width() || entityDimensions2.height() > entityDimensions.height())
            && !(this instanceof Player)) {
            this.fudgePositionAfterSizeChange(entityDimensions);
        }
    }

    public boolean fudgePositionAfterSizeChange(EntityDimensions dimensions) {
        EntityDimensions entityDimensions = this.getDimensions(this.getPose());
        Vec3 vec3 = this.position().add(0.0, dimensions.height() / 2.0, 0.0);
        double d = Math.max(0.0F, entityDimensions.width() - dimensions.width()) + 1.0E-6;
        double e = Math.max(0.0F, entityDimensions.height() - dimensions.height()) + 1.0E-6;
        VoxelShape voxelShape = Shapes.create(AABB.ofSize(vec3, d, e, d));
        Optional<Vec3> optional = this.level
            .findFreePosition(this, voxelShape, vec3, entityDimensions.width(), entityDimensions.height(), entityDimensions.width());
        if (optional.isPresent()) {
            this.setPos(optional.get().add(0.0, -entityDimensions.height() / 2.0, 0.0));
            return true;
        }

        if (entityDimensions.width() > dimensions.width() && entityDimensions.height() > dimensions.height()) {
            VoxelShape voxelShape2 = Shapes.create(AABB.ofSize(vec3, d, 1.0E-6, d));
            Optional<Vec3> optional2 = this.level
                .findFreePosition(this, voxelShape2, vec3, entityDimensions.width(), dimensions.height(), entityDimensions.width());
            if (optional2.isPresent()) {
                this.setPos(optional2.get().add(0.0, -dimensions.height() / 2.0 + 1.0E-6, 0.0));
                return true;
            }
        }

        return false;
    }

    public Direction getDirection() {
        return Direction.fromYRot(this.getYRot());
    }

    public Direction getMotionDirection() {
        return this.getDirection();
    }

    protected HoverEvent createHoverEvent() {
        return new HoverEvent(HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(this.getType(), this.getUUID(), this.getName()));
    }

    public boolean broadcastToPlayer(ServerPlayer player) {
        return true;
    }

    @Override
    public final AABB getBoundingBox() {
        return this.bb;
    }

    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox();
    }

    public final void setBoundingBox(AABB bb) {
        this.bb = bb;
    }

    public final float getEyeHeight(Pose pose) {
        return this.getDimensions(pose).eyeHeight();
    }

    public final float getEyeHeight() {
        return this.eyeHeight;
    }

    public Vec3 getLeashOffset(float partialTick) {
        return this.getLeashOffset();
    }

    protected Vec3 getLeashOffset() {
        return new Vec3(0.0, this.getEyeHeight(), this.getBbWidth() * 0.4F);
    }

    public SlotAccess getSlot(int slot) {
        return SlotAccess.NULL;
    }

    @Override
    public void sendSystemMessage(Component component) {
    }

    public Level getCommandSenderWorld() {
        return this.level();
    }

    @Nullable
    public MinecraftServer getServer() {
        return this.level().getServer();
    }

    /**
     * Applies the given player interaction to this Entity.
     */
    public InteractionResult interactAt(Player player, Vec3 vec, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public boolean ignoreExplosion(Explosion explosion) {
        return false;
    }

    /**
     * Add the given player to the list of players tracking this entity. For instance, a player may track a boss in order to view its associated boss bar.
     */
    public void startSeenByPlayer(ServerPlayer serverPlayer) {
    }

    /**
     * Removes the given player from the list of players tracking this entity. See {@link Entity#addTrackingPlayer} for more information on tracking.
     */
    public void stopSeenByPlayer(ServerPlayer serverPlayer) {
    }

    /**
     * Transforms the entity's current yaw with the given Rotation and returns it. This does not have a side-effect.
     */
    public float rotate(Rotation transformRotation) {
        float f = Mth.wrapDegrees(this.getYRot());
        switch (transformRotation) {
            case CLOCKWISE_180:
                return f + 180.0F;
            case COUNTERCLOCKWISE_90:
                return f + 270.0F;
            case CLOCKWISE_90:
                return f + 90.0F;
            default:
                return f;
        }
    }

    /**
     * Transforms the entity's current yaw with the given Mirror and returns it. This does not have a side-effect.
     */
    public float mirror(Mirror transformMirror) {
        float f = Mth.wrapDegrees(this.getYRot());
        switch (transformMirror) {
            case FRONT_BACK:
                return -f;
            case LEFT_RIGHT:
                return 180.0F - f;
            default:
                return f;
        }
    }

    public boolean onlyOpCanSetNbt() {
        return false;
    }

    public ProjectileDeflection deflection(Projectile projectile) {
        return this.getType().is(EntityTypeTags.DEFLECTS_PROJECTILES) ? ProjectileDeflection.REVERSE : ProjectileDeflection.NONE;
    }

    @Nullable
    public LivingEntity getControllingPassenger() {
        return null;
    }

    public final boolean hasControllingPassenger() {
        return this.getControllingPassenger() != null;
    }

    public final List<Entity> getPassengers() {
        return this.passengers;
    }

    @Nullable
    public Entity getFirstPassenger() {
        return this.passengers.isEmpty() ? null : this.passengers.get(0);
    }

    public boolean hasPassenger(Entity entity) {
        return this.passengers.contains(entity);
    }

    public boolean hasPassenger(Predicate<Entity> predicate) {
        for (Entity entity : this.passengers) {
            if (predicate.test(entity)) {
                return true;
            }
        }

        return false;
    }

    private Stream<Entity> getIndirectPassengersStream() {
        return this.passengers.stream().flatMap(Entity::getSelfAndPassengers);
    }

    @Override
    public Stream<Entity> getSelfAndPassengers() {
        return Stream.concat(Stream.of(this), this.getIndirectPassengersStream());
    }

    @Override
    public Stream<Entity> getPassengersAndSelf() {
        return Stream.concat(this.passengers.stream().flatMap(Entity::getPassengersAndSelf), Stream.of(this));
    }

    public Iterable<Entity> getIndirectPassengers() {
        return () -> this.getIndirectPassengersStream().iterator();
    }

    public int countPlayerPassengers() {
        return (int)this.getIndirectPassengersStream().filter(entity -> entity instanceof Player).count();
    }

    public boolean hasExactlyOnePlayerPassenger() {
        return this.countPlayerPassengers() == 1;
    }

    public Entity getRootVehicle() {
        Entity entity = this;

        while (entity.isPassenger()) {
            entity = entity.getVehicle();
        }

        return entity;
    }

    public boolean isPassengerOfSameVehicle(Entity entity) {
        return this.getRootVehicle() == entity.getRootVehicle();
    }

    public boolean hasIndirectPassenger(Entity entity) {
        if (!entity.isPassenger()) {
            return false;
        }

        Entity entity2 = entity.getVehicle();
        return entity2 == this ? true : this.hasIndirectPassenger(entity2);
    }

    public boolean isControlledByLocalInstance() {
        return this.getControllingPassenger() instanceof Player player ? player.isLocalPlayer() : this.isEffectiveAi();
    }

    public boolean isEffectiveAi() {
        return !this.level().isClientSide;
    }

    protected static Vec3 getCollisionHorizontalEscapeVector(double vehicleWidth, double passengerWidth, float yRot) {
        double d = (vehicleWidth + passengerWidth + 1.0E-5F) / 2.0;
        float f = -Mth.sin(yRot * (float) (Math.PI / 180.0));
        float g = Mth.cos(yRot * (float) (Math.PI / 180.0));
        float h = Math.max(Math.abs(f), Math.abs(g));
        return new Vec3(f * d / h, 0.0, g * d / h);
    }

    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return new Vec3(this.getX(), this.getBoundingBox().maxY, this.getZ());
    }

    @Nullable
    public Entity getVehicle() {
        return this.vehicle;
    }

    @Nullable
    public Entity getControlledVehicle() {
        return this.vehicle != null && this.vehicle.getControllingPassenger() == this ? this.vehicle : null;
    }

    public PushReaction getPistonPushReaction() {
        return PushReaction.NORMAL;
    }

    public SoundSource getSoundSource() {
        return SoundSource.NEUTRAL;
    }

    protected int getFireImmuneTicks() {
        return 1;
    }

    public CommandSourceStack createCommandSourceStack() {
        return new CommandSourceStack(
            this,
            this.position(),
            this.getRotationVector(),
            this.level() instanceof ServerLevel ? (ServerLevel)this.level() : null,
            this.getPermissionLevel(),
            this.getName().getString(),
            this.getDisplayName(),
            this.level().getServer(),
            this
        );
    }

    protected int getPermissionLevel() {
        return 0;
    }

    public boolean hasPermissions(int level) {
        return this.getPermissionLevel() >= level;
    }

    @Override
    public boolean acceptsSuccess() {
        return this.level().getGameRules().getBoolean(GameRules.RULE_SENDCOMMANDFEEDBACK);
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return true;
    }

    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 target) {
        Vec3 vec3 = anchor.apply(this);
        double d = target.x - vec3.x;
        double e = target.y - vec3.y;
        double f = target.z - vec3.z;
        double g = Math.sqrt(d * d + f * f);
        this.setXRot(Mth.wrapDegrees((float)(-(Mth.atan2(e, g) * 180.0F / (float)Math.PI))));
        this.setYRot(Mth.wrapDegrees((float)(Mth.atan2(f, d) * 180.0F / (float)Math.PI) - 90.0F));
        this.setYHeadRot(this.getYRot());
        this.xRotO = this.getXRot();
        this.yRotO = this.getYRot();
    }

    public float getPreciseBodyRotation(float partialTick) {
        return Mth.lerp(partialTick, this.yRotO, this.yRot);
    }

    public boolean updateFluidHeightAndDoFluidPushing(TagKey<Fluid> fluidTag, double motionScale) {
        if (this.touchingUnloadedChunk()) {
            return false;
        }

        AABB aABB = this.getBoundingBox().deflate(0.001);
        int i = Mth.floor(aABB.minX);
        int j = Mth.ceil(aABB.maxX);
        int k = Mth.floor(aABB.minY);
        int l = Mth.ceil(aABB.maxY);
        int m = Mth.floor(aABB.minZ);
        int n = Mth.ceil(aABB.maxZ);
        double d = 0.0;
        boolean bl = this.isPushedByFluid();
        boolean bl2 = false;
        Vec3 vec3 = Vec3.ZERO;
        int o = 0;
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for (int p = i; p < j; p++) {
            for (int q = k; q < l; q++) {
                for (int r = m; r < n; r++) {
                    mutableBlockPos.set(p, q, r);
                    FluidState fluidState = this.level().getFluidState(mutableBlockPos);
                    if (fluidState.is(fluidTag)) {
                        double e = q + fluidState.getHeight(this.level(), mutableBlockPos);
                        if (e >= aABB.minY) {
                            bl2 = true;
                            d = Math.max(e - aABB.minY, d);
                            if (bl) {
                                Vec3 vec32 = fluidState.getFlow(this.level(), mutableBlockPos);
                                if (d < 0.4) {
                                    vec32 = vec32.scale(d);
                                }

                                vec3 = vec3.add(vec32);
                                o++;
                            }
                        }
                    }
                }
            }
        }

        if (vec3.length() > 0.0) {
            if (o > 0) {
                vec3 = vec3.scale(1.0 / o);
            }

            if (!(this instanceof Player)) {
                vec3 = vec3.normalize();
            }

            Vec3 vec33 = this.getDeltaMovement();
            vec3 = vec3.scale(motionScale);
            double f = 0.003;
            if (Math.abs(vec33.x) < 0.003 && Math.abs(vec33.z) < 0.003 && vec3.length() < 0.0045000000000000005) {
                vec3 = vec3.normalize().scale(0.0045000000000000005);
            }

            this.setDeltaMovement(this.getDeltaMovement().add(vec3));
        }

        this.fluidHeight.put(fluidTag, d);
        return bl2;
    }

    public boolean touchingUnloadedChunk() {
        AABB aABB = this.getBoundingBox().inflate(1.0);
        int i = Mth.floor(aABB.minX);
        int j = Mth.ceil(aABB.maxX);
        int k = Mth.floor(aABB.minZ);
        int l = Mth.ceil(aABB.maxZ);
        return !this.level().hasChunksAt(i, k, j, l);
    }

    public double getFluidHeight(TagKey<Fluid> fluidTag) {
        return this.fluidHeight.getDouble(fluidTag);
    }

    public double getFluidJumpThreshold() {
        return this.getEyeHeight() < 0.4 ? 0.0 : 0.4;
    }

    public final float getBbWidth() {
        return this.dimensions.width();
    }

    public final float getBbHeight() {
        return this.dimensions.height();
    }

    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity entity) {
        return new ClientboundAddEntityPacket(this, entity);
    }

    public EntityDimensions getDimensions(Pose pose) {
        return this.type.getDimensions();
    }

    public final EntityAttachments getAttachments() {
        return this.dimensions.attachments();
    }

    public Vec3 position() {
        return this.position;
    }

    public Vec3 trackingPosition() {
        return this.position();
    }

    @Override
    public BlockPos blockPosition() {
        return this.blockPosition;
    }

    public BlockState getInBlockState() {
        if (this.inBlockState == null) {
            this.inBlockState = this.level().getBlockState(this.blockPosition());
        }

        return this.inBlockState;
    }

    public ChunkPos chunkPosition() {
        return this.chunkPosition;
    }

    public Vec3 getDeltaMovement() {
        return this.deltaMovement;
    }

    public void setDeltaMovement(Vec3 deltaMovement) {
        this.deltaMovement = deltaMovement;
    }

    public void addDeltaMovement(Vec3 addend) {
        this.setDeltaMovement(this.getDeltaMovement().add(addend));
    }

    public void setDeltaMovement(double x, double y, double z) {
        this.setDeltaMovement(new Vec3(x, y, z));
    }

    public final int getBlockX() {
        return this.blockPosition.getX();
    }

    public final double getX() {
        return this.position.x;
    }

    public double getX(double scale) {
        return this.position.x + this.getBbWidth() * scale;
    }

    public double getRandomX(double scale) {
        return this.getX((2.0 * this.random.nextDouble() - 1.0) * scale);
    }

    public final int getBlockY() {
        return this.blockPosition.getY();
    }

    public final double getY() {
        return this.position.y;
    }

    public double getY(double scale) {
        return this.position.y + this.getBbHeight() * scale;
    }

    public double getRandomY() {
        return this.getY(this.random.nextDouble());
    }

    public double getEyeY() {
        return this.position.y + this.eyeHeight;
    }

    public final int getBlockZ() {
        return this.blockPosition.getZ();
    }

    public final double getZ() {
        return this.position.z;
    }

    public double getZ(double scale) {
        return this.position.z + this.getBbWidth() * scale;
    }

    public double getRandomZ(double scale) {
        return this.getZ((2.0 * this.random.nextDouble() - 1.0) * scale);
    }

    /**
     * Directly updates the {@link #posX}, {@link posY}, and {@link posZ} fields, without performing any collision checks, updating the bounding box position, or sending any packets. In general, this is not what you want and {@link #setPosition} is better, as that handles the bounding box.
     */
    public final void setPosRaw(double x, double y, double z) {
        if (this.position.x != x || this.position.y != y || this.position.z != z) {
            this.position = new Vec3(x, y, z);
            int i = Mth.floor(x);
            int j = Mth.floor(y);
            int k = Mth.floor(z);
            if (i != this.blockPosition.getX() || j != this.blockPosition.getY() || k != this.blockPosition.getZ()) {
                this.blockPosition = new BlockPos(i, j, k);
                this.inBlockState = null;
                if (SectionPos.blockToSectionCoord(i) != this.chunkPosition.x || SectionPos.blockToSectionCoord(k) != this.chunkPosition.z) {
                    this.chunkPosition = new ChunkPos(this.blockPosition);
                }
            }

            this.levelCallback.onMove();
        }
    }

    public void checkDespawn() {
    }

    public Vec3 getRopeHoldPosition(float partialTicks) {
        return this.getPosition(partialTicks).add(0.0, this.eyeHeight * 0.7, 0.0);
    }

    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        int i = packet.getId();
        double d = packet.getX();
        double e = packet.getY();
        double f = packet.getZ();
        this.syncPacketPositionCodec(d, e, f);
        this.moveTo(d, e, f);
        this.setXRot(packet.getXRot());
        this.setYRot(packet.getYRot());
        this.setId(i);
        this.setUUID(packet.getUUID());
    }

    @Nullable
    public ItemStack getPickResult() {
        return null;
    }

    public void setIsInPowderSnow(boolean isInPowderSnow) {
        this.isInPowderSnow = isInPowderSnow;
    }

    public boolean canFreeze() {
        return !this.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES);
    }

    public boolean isFreezing() {
        return (this.isInPowderSnow || this.wasInPowderSnow) && this.canFreeze();
    }

    public float getYRot() {
        return this.yRot;
    }

    public float getVisualRotationYInDegrees() {
        return this.getYRot();
    }

    /**
     * Sets the rotation of this entity around the y-axis (the yaw) in degrees.
     */
    public void setYRot(float yRot) {
        if (!Float.isFinite(yRot)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + yRot + ", discarding.");
        } else {
            this.yRot = yRot;
        }
    }

    public float getXRot() {
        return this.xRot;
    }

    /**
     * Sets the rotation of this entity around the x-axis (the pitch) in degrees.
     */
    public void setXRot(float xRot) {
        if (!Float.isFinite(xRot)) {
            Util.logAndPauseIfInIde("Invalid entity rotation: " + xRot + ", discarding.");
        } else {
            this.xRot = xRot;
        }
    }

    public boolean canSprint() {
        return false;
    }

    public float maxUpStep() {
        return 0.0F;
    }

    public void onExplosionHit(@Nullable Entity entity) {
    }

    public final boolean isRemoved() {
        return this.removalReason != null;
    }

    @Nullable
    public Entity.RemovalReason getRemovalReason() {
        return this.removalReason;
    }

    @Override
    public final void setRemoved(Entity.RemovalReason removalReason) {
        if (this.removalReason == null) {
            this.removalReason = removalReason;
        }

        if (this.removalReason.shouldDestroy()) {
            this.stopRiding();
        }

        this.getPassengers().forEach(Entity::stopRiding);
        this.levelCallback.onRemove(removalReason);
    }

    protected void unsetRemoved() {
        this.removalReason = null;
    }

    @Override
    public void setLevelCallback(EntityInLevelCallback levelCallback) {
        this.levelCallback = levelCallback;
    }

    @Override
    public boolean shouldBeSaved() {
        if (this.removalReason != null && !this.removalReason.shouldSave()) {
            return false;
        } else {
            return this.isPassenger() ? false : !this.isVehicle() || !this.hasExactlyOnePlayerPassenger();
        }
    }

    @Override
    public boolean isAlwaysTicking() {
        return false;
    }

    public boolean mayInteract(Level level, BlockPos pos) {
        return true;
    }

    public Level level() {
        return this.level;
    }

    protected void setLevel(Level level) {
        this.level = level;
    }

    public DamageSources damageSources() {
        return this.level().damageSources();
    }

    public RegistryAccess registryAccess() {
        return this.level().registryAccess();
    }

    protected void lerpPositionAndRotationStep(int steps, double targetX, double targetY, double targetZ, double targetYRot, double targetXRot) {
        double d = 1.0 / steps;
        double e = Mth.lerp(d, this.getX(), targetX);
        double f = Mth.lerp(d, this.getY(), targetY);
        double g = Mth.lerp(d, this.getZ(), targetZ);
        float h = (float)Mth.rotLerp(d, this.getYRot(), targetYRot);
        float i = (float)Mth.lerp(d, this.getXRot(), targetXRot);
        this.setPos(e, f, g);
        this.setRot(h, i);
    }

    public RandomSource getRandom() {
        return this.random;
    }

    public Vec3 getKnownMovement() {
        return this.getControllingPassenger() instanceof Player player && this.isAlive() ? player.getKnownMovement() : this.getDeltaMovement();
    }

    @Nullable
    public ItemStack getWeaponItem() {
        return null;
    }

    @FunctionalInterface
    public interface MoveFunction {
        void accept(Entity var1, double var2, double var4, double var6);
    }

    public enum MovementEmission {
        NONE(false, false),
        SOUNDS(true, false),
        EVENTS(false, true),
        ALL(true, true);

        final boolean sounds;
        final boolean events;

        MovementEmission(final boolean sounds, final boolean events) {
            this.sounds = sounds;
            this.events = events;
        }

        public boolean emitsAnything() {
            return this.events || this.sounds;
        }

        public boolean emitsEvents() {
            return this.events;
        }

        public boolean emitsSounds() {
            return this.sounds;
        }
    }

    public enum RemovalReason {
        KILLED(true, false),
        DISCARDED(true, false),
        UNLOADED_TO_CHUNK(false, true),
        UNLOADED_WITH_PLAYER(false, false),
        CHANGED_DIMENSION(false, false);

        private final boolean destroy;
        private final boolean save;

        RemovalReason(final boolean destroy, final boolean save) {
            this.destroy = destroy;
            this.save = save;
        }

        public boolean shouldDestroy() {
            return this.destroy;
        }

        public boolean shouldSave() {
            return this.save;
        }
    }
}
