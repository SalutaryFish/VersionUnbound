package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import it.unimi.dsi.fastutil.doubles.DoubleDoubleImmutablePair;
import it.unimi.dsi.fastutil.objects.Reference2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.effects.EnchantmentLocationBasedEffect;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;

/**
 * @return null or the {@linkplain LivingEntity} it was ignited by
 */
public abstract class LivingEntity extends Entity implements Attackable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_ACTIVE_EFFECTS = "active_effects";
    private static final ResourceLocation SPEED_MODIFIER_POWDER_SNOW_ID = ResourceLocation.withDefaultNamespace("powder_snow");
    private static final ResourceLocation SPRINTING_MODIFIER_ID = ResourceLocation.withDefaultNamespace("sprinting");
    private static final AttributeModifier SPEED_MODIFIER_SPRINTING = new AttributeModifier(
        SPRINTING_MODIFIER_ID, 0.3F, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
    );
    public static final int HAND_SLOTS = 2;
    public static final int ARMOR_SLOTS = 4;
    public static final int EQUIPMENT_SLOT_OFFSET = 98;
    public static final int ARMOR_SLOT_OFFSET = 100;
    public static final int BODY_ARMOR_OFFSET = 105;
    public static final int SWING_DURATION = 6;
    public static final int PLAYER_HURT_EXPERIENCE_TIME = 100;
    private static final int DAMAGE_SOURCE_TIMEOUT = 40;
    public static final double MIN_MOVEMENT_DISTANCE = 0.003;
    public static final double DEFAULT_BASE_GRAVITY = 0.08;
    public static final int DEATH_DURATION = 20;
    private static final int TICKS_PER_ELYTRA_FREE_FALL_EVENT = 10;
    private static final int FREE_FALL_EVENTS_PER_ELYTRA_BREAK = 2;
    public static final int USE_ITEM_INTERVAL = 4;
    public static final float BASE_JUMP_POWER = 0.42F;
    private static final double MAX_LINE_OF_SIGHT_TEST_RANGE = 128.0;
    protected static final int LIVING_ENTITY_FLAG_IS_USING = 1;
    protected static final int LIVING_ENTITY_FLAG_OFF_HAND = 2;
    protected static final int LIVING_ENTITY_FLAG_SPIN_ATTACK = 4;
    protected static final EntityDataAccessor<Byte> DATA_LIVING_ENTITY_FLAGS = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_HEALTH_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<List<ParticleOptions>> DATA_EFFECT_PARTICLES = SynchedEntityData.defineId(
        LivingEntity.class, EntityDataSerializers.PARTICLES
    );
    private static final EntityDataAccessor<Boolean> DATA_EFFECT_AMBIENCE_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ARROW_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STINGER_COUNT_ID = SynchedEntityData.defineId(LivingEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<BlockPos>> SLEEPING_POS_ID = SynchedEntityData.defineId(
        LivingEntity.class, EntityDataSerializers.OPTIONAL_BLOCK_POS
    );
    private static final int PARTICLE_FREQUENCY_WHEN_INVISIBLE = 15;
    protected static final EntityDimensions SLEEPING_DIMENSIONS = EntityDimensions.fixed(0.2F, 0.2F).withEyeHeight(0.2F);
    public static final float EXTRA_RENDER_CULLING_SIZE_WITH_BIG_HAT = 0.5F;
    public static final float DEFAULT_BABY_SCALE = 0.5F;
    private static final float ITEM_USE_EFFECT_START_FRACTION = 0.21875F;
    public static final String ATTRIBUTES_FIELD = "attributes";
    private final AttributeMap attributes;
    private final CombatTracker combatTracker = new CombatTracker(this);
    private final Map<Holder<MobEffect>, MobEffectInstance> activeEffects = Maps.newHashMap();
    private final NonNullList<ItemStack> lastHandItemStacks = NonNullList.withSize(2, ItemStack.EMPTY);
    private final NonNullList<ItemStack> lastArmorItemStacks = NonNullList.withSize(4, ItemStack.EMPTY);
    private ItemStack lastBodyItemStack = ItemStack.EMPTY;
    public boolean swinging;
    private boolean discardFriction = false;
    public InteractionHand swingingArm;
    public int swingTime;
    public int removeArrowTime;
    public int removeStingerTime;
    public int hurtTime;
    public int hurtDuration;
    public int deathTime;
    public float oAttackAnim;
    public float attackAnim;
    protected int attackStrengthTicker;
    public final WalkAnimationState walkAnimation = new WalkAnimationState();
    public final int invulnerableDuration = 20;
    public final float timeOffs;
    public final float rotA;
    public float yBodyRot;
    public float yBodyRotO;
    public float yHeadRot;
    public float yHeadRotO;
    @Nullable
    protected Player lastHurtByPlayer;
    protected int lastHurtByPlayerTime;
    protected boolean dead;
    protected int noActionTime;
    protected float oRun;
    protected float run;
    protected float animStep;
    protected float animStepO;
    protected float rotOffs;
    protected int deathScore;
    /**
     * Damage taken in the last hit. Mobs are resistant to damage less than this for a short time after taking damage.
     */
    protected float lastHurt;
    protected boolean jumping;
    public float xxa;
    public float yya;
    public float zza;
    protected int lerpSteps;
    protected double lerpX;
    protected double lerpY;
    protected double lerpZ;
    protected double lerpYRot;
    protected double lerpXRot;
    protected double lerpYHeadRot;
    protected int lerpHeadSteps;
    private boolean effectsDirty = true;
    @Nullable
    private LivingEntity lastHurtByMob;
    private int lastHurtByMobTimestamp;
    @Nullable
    private LivingEntity lastHurtMob;
    /**
     * Holds the value of ticksExisted when setLastAttacker was last called.
     */
    private int lastHurtMobTimestamp;
    private float speed;
    private int noJumpDelay;
    private float absorptionAmount;
    protected ItemStack useItem = ItemStack.EMPTY;
    protected int useItemRemaining;
    protected int fallFlyTicks;
    private BlockPos lastPos;
    private Optional<BlockPos> lastClimbablePos = Optional.empty();
    @Nullable
    private DamageSource lastDamageSource;
    private long lastDamageStamp;
    protected int autoSpinAttackTicks;
    protected float autoSpinAttackDmg;
    @Nullable
    protected ItemStack autoSpinAttackItemStack;
    private float swimAmount;
    private float swimAmountO;
    protected Brain<?> brain;
    private boolean skipDropExperience;
    private final Reference2ObjectMap<Enchantment, Set<EnchantmentLocationBasedEffect>> activeLocationDependentEnchantments = new Reference2ObjectArrayMap<>();
    protected float appliedScale = 1.0F;

    protected LivingEntity(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
        this.attributes = new AttributeMap(DefaultAttributes.getSupplier(entityType));
        this.setHealth(this.getMaxHealth());
        this.blocksBuilding = true;
        this.rotA = (float)((Math.random() + 1.0) * 0.01F);
        this.reapplyPosition();
        this.timeOffs = (float)Math.random() * 12398.0F;
        this.setYRot((float)(Math.random() * (float) (Math.PI * 2)));
        this.yHeadRot = this.getYRot();
        NbtOps nbtOps = NbtOps.INSTANCE;
        this.brain = this.makeBrain(new Dynamic<>(nbtOps, nbtOps.createMap(ImmutableMap.of(nbtOps.createString("memories"), nbtOps.emptyMap()))));
    }

    public Brain<?> getBrain() {
        return this.brain;
    }

    protected Brain.Provider<?> brainProvider() {
        return Brain.provider(ImmutableList.of(), ImmutableList.of());
    }

    protected Brain<?> makeBrain(Dynamic<?> dynamic) {
        return this.brainProvider().makeBrain(dynamic);
    }

    @Override
    public void kill() {
        this.hurt(this.damageSources().genericKill(), Float.MAX_VALUE);
    }

    public boolean canAttackType(EntityType<?> entityType) {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_LIVING_ENTITY_FLAGS, (byte)0);
        builder.define(DATA_EFFECT_PARTICLES, List.of());
        builder.define(DATA_EFFECT_AMBIENCE_ID, false);
        builder.define(DATA_ARROW_COUNT_ID, 0);
        builder.define(DATA_STINGER_COUNT_ID, 0);
        builder.define(DATA_HEALTH_ID, 1.0F);
        builder.define(SLEEPING_POS_ID, Optional.empty());
    }

    public static AttributeSupplier.Builder createLivingAttributes() {
        return AttributeSupplier.builder()
            .add(Attributes.MAX_HEALTH)
            .add(Attributes.KNOCKBACK_RESISTANCE)
            .add(Attributes.MOVEMENT_SPEED)
            .add(Attributes.ARMOR)
            .add(Attributes.ARMOR_TOUGHNESS)
            .add(Attributes.MAX_ABSORPTION)
            .add(Attributes.STEP_HEIGHT)
            .add(Attributes.SCALE)
            .add(Attributes.GRAVITY)
            .add(Attributes.SAFE_FALL_DISTANCE)
            .add(Attributes.FALL_DAMAGE_MULTIPLIER)
            .add(Attributes.JUMP_STRENGTH)
            .add(Attributes.OXYGEN_BONUS)
            .add(Attributes.BURNING_TIME)
            .add(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
            .add(Attributes.WATER_MOVEMENT_EFFICIENCY)
            .add(Attributes.MOVEMENT_EFFICIENCY)
            .add(Attributes.ATTACK_KNOCKBACK);
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {
        if (!this.isInWater()) {
            this.updateInWaterStateAndDoWaterCurrentPushing();
        }

        if (this.level() instanceof ServerLevel serverLevel && onGround && this.fallDistance > 0.0F) {
            this.onChangedBlock(serverLevel, pos);
            double d = this.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
            if (this.fallDistance > d && !state.isAir()) {
                double e = this.getX();
                double f = this.getY();
                double g = this.getZ();
                BlockPos blockPos = this.blockPosition();
                if (pos.getX() != blockPos.getX() || pos.getZ() != blockPos.getZ()) {
                    double h = e - pos.getX() - 0.5;
                    double i = g - pos.getZ() - 0.5;
                    double j = Math.max(Math.abs(h), Math.abs(i));
                    e = pos.getX() + 0.5 + h / j * 0.5;
                    g = pos.getZ() + 0.5 + i / j * 0.5;
                }

                float k = Mth.ceil(this.fallDistance - d);
                double l = Math.min(0.2F + k / 15.0F, 2.5);
                int m = (int)(150.0 * l);
                ((ServerLevel)this.level()).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), e, f, g, m, 0.0, 0.0, 0.0, 0.15F);
            }
        }

        super.checkFallDamage(y, onGround, state, pos);
        if (onGround) {
            this.lastClimbablePos = Optional.empty();
        }
    }

    public final boolean canBreatheUnderwater() {
        return this.getType().is(EntityTypeTags.CAN_BREATHE_UNDER_WATER);
    }

    public float getSwimAmount(float partialTicks) {
        return Mth.lerp(partialTicks, this.swimAmountO, this.swimAmount);
    }

    public boolean hasLandedInLiquid() {
        return this.getDeltaMovement().y() < 1.0E-5F && this.isInLiquid();
    }

    @Override
    public void baseTick() {
        this.oAttackAnim = this.attackAnim;
        if (this.firstTick) {
            this.getSleepingPos().ifPresent(this::setPosToBed);
        }

        if (this.level() instanceof ServerLevel serverLevel) {
            EnchantmentHelper.tickEffects(serverLevel, this);
        }

        super.baseTick();
        this.level().getProfiler().push("livingEntityBaseTick");
        if (this.fireImmune() || this.level().isClientSide) {
            this.clearFire();
        }

        if (this.isAlive()) {
            boolean bl = this instanceof Player;
            if (!this.level().isClientSide) {
                if (this.isInWall()) {
                    this.hurt(this.damageSources().inWall(), 1.0F);
                } else if (bl && !this.level().getWorldBorder().isWithinBounds(this.getBoundingBox())) {
                    double d = this.level().getWorldBorder().getDistanceToBorder(this) + this.level().getWorldBorder().getDamageSafeZone();
                    if (d < 0.0) {
                        double e = this.level().getWorldBorder().getDamagePerBlock();
                        if (e > 0.0) {
                            this.hurt(this.damageSources().outOfBorder(), Math.max(1, Mth.floor(-d * e)));
                        }
                    }
                }
            }

            if (this.isEyeInFluid(FluidTags.WATER)
                && !this.level().getBlockState(BlockPos.containing(this.getX(), this.getEyeY(), this.getZ())).is(Blocks.BUBBLE_COLUMN)) {
                boolean bl2 = !this.canBreatheUnderwater() && !MobEffectUtil.hasWaterBreathing(this) && (!bl || !((Player)this).getAbilities().invulnerable);
                if (bl2) {
                    this.setAirSupply(this.decreaseAirSupply(this.getAirSupply()));
                    if (this.getAirSupply() == -20) {
                        this.setAirSupply(0);
                        Vec3 vec3 = this.getDeltaMovement();

                        for (int i = 0; i < 8; i++) {
                            double f = this.random.nextDouble() - this.random.nextDouble();
                            double g = this.random.nextDouble() - this.random.nextDouble();
                            double h = this.random.nextDouble() - this.random.nextDouble();
                            this.level().addParticle(ParticleTypes.BUBBLE, this.getX() + f, this.getY() + g, this.getZ() + h, vec3.x, vec3.y, vec3.z);
                        }

                        this.hurt(this.damageSources().drown(), 2.0F);
                    }
                }

                if (!this.level().isClientSide && this.isPassenger() && this.getVehicle() != null && this.getVehicle().dismountsUnderwater()) {
                    this.stopRiding();
                }
            } else if (this.getAirSupply() < this.getMaxAirSupply()) {
                this.setAirSupply(this.increaseAirSupply(this.getAirSupply()));
            }

            if (this.level() instanceof ServerLevel serverLevel2) {
                BlockPos blockPos = this.blockPosition();
                if (!Objects.equal(this.lastPos, blockPos)) {
                    this.lastPos = blockPos;
                    this.onChangedBlock(serverLevel2, blockPos);
                }
            }
        }

        if (this.isAlive() && (this.isInWaterRainOrBubble() || this.isInPowderSnow)) {
            this.extinguishFire();
        }

        if (this.hurtTime > 0) {
            this.hurtTime--;
        }

        if (this.invulnerableTime > 0 && !(this instanceof ServerPlayer)) {
            this.invulnerableTime--;
        }

        if (this.isDeadOrDying() && this.level().shouldTickDeath(this)) {
            this.tickDeath();
        }

        if (this.lastHurtByPlayerTime > 0) {
            this.lastHurtByPlayerTime--;
        } else {
            this.lastHurtByPlayer = null;
        }

        if (this.lastHurtMob != null && !this.lastHurtMob.isAlive()) {
            this.lastHurtMob = null;
        }

        if (this.lastHurtByMob != null) {
            if (!this.lastHurtByMob.isAlive()) {
                this.setLastHurtByMob(null);
            } else if (this.tickCount - this.lastHurtByMobTimestamp > 100) {
                this.setLastHurtByMob(null);
            }
        }

        this.tickEffects();
        this.animStepO = this.animStep;
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        this.level().getProfiler().pop();
    }

    @Override
    protected float getBlockSpeedFactor() {
        return Mth.lerp((float)this.getAttributeValue(Attributes.MOVEMENT_EFFICIENCY), super.getBlockSpeedFactor(), 1.0F);
    }

    protected void removeFrost() {
        AttributeInstance attributeInstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attributeInstance != null) {
            if (attributeInstance.getModifier(SPEED_MODIFIER_POWDER_SNOW_ID) != null) {
                attributeInstance.removeModifier(SPEED_MODIFIER_POWDER_SNOW_ID);
            }
        }
    }

    protected void tryAddFrost() {
        if (!this.getBlockStateOnLegacy().isAir()) {
            int i = this.getTicksFrozen();
            if (i > 0) {
                AttributeInstance attributeInstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attributeInstance == null) {
                    return;
                }

                float f = -0.05F * this.getPercentFrozen();
                attributeInstance.addTransientModifier(new AttributeModifier(SPEED_MODIFIER_POWDER_SNOW_ID, f, AttributeModifier.Operation.ADD_VALUE));
            }
        }
    }

    protected void onChangedBlock(ServerLevel level, BlockPos pos) {
        EnchantmentHelper.runLocationChangedEffects(level, this);
    }

    public boolean isBaby() {
        return false;
    }

    public float getAgeScale() {
        return this.isBaby() ? 0.5F : 1.0F;
    }

    public float getScale() {
        AttributeMap attributeMap = this.getAttributes();
        return attributeMap == null ? 1.0F : this.sanitizeScale((float)attributeMap.getValue(Attributes.SCALE));
    }

    protected float sanitizeScale(float scale) {
        return scale;
    }

    protected boolean isAffectedByFluids() {
        return true;
    }

    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime >= 20 && !this.level().isClientSide() && !this.isRemoved()) {
            this.level().broadcastEntityEvent(this, (byte)60);
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    public boolean shouldDropExperience() {
        return !this.isBaby();
    }

    protected boolean shouldDropLoot() {
        return !this.isBaby();
    }

    /**
     * Decrements the entity's air supply when underwater
     */
    protected int decreaseAirSupply(int currentAir) {
        AttributeInstance attributeInstance = this.getAttribute(Attributes.OXYGEN_BONUS);
        double d;
        if (attributeInstance != null) {
            d = attributeInstance.getValue();
        } else {
            d = 0.0;
        }

        return d > 0.0 && this.random.nextDouble() >= 1.0 / (d + 1.0) ? currentAir : currentAir - 1;
    }

    protected int increaseAirSupply(int currentAir) {
        return Math.min(currentAir + 4, this.getMaxAirSupply());
    }

    public final int getExperienceReward(ServerLevel level, @Nullable Entity killer) {
        return EnchantmentHelper.processMobExperience(level, killer, this, this.getBaseExperienceReward());
    }

    protected int getBaseExperienceReward() {
        return 0;
    }

    protected boolean isAlwaysExperienceDropper() {
        return false;
    }

    @Nullable
    public LivingEntity getLastHurtByMob() {
        return this.lastHurtByMob;
    }

    @Override
    public LivingEntity getLastAttacker() {
        return this.getLastHurtByMob();
    }

    public int getLastHurtByMobTimestamp() {
        return this.lastHurtByMobTimestamp;
    }

    public void setLastHurtByPlayer(@Nullable Player player) {
        this.lastHurtByPlayer = player;
        this.lastHurtByPlayerTime = this.tickCount;
    }

    /**
     * Hint to AI tasks that we were attacked by the passed EntityLivingBase and should retaliate. Is not guaranteed to change our actual active target (for example if we are currently busy attacking someone else)
     */
    public void setLastHurtByMob(@Nullable LivingEntity livingEntity) {
        this.lastHurtByMob = livingEntity;
        this.lastHurtByMobTimestamp = this.tickCount;
    }

    @Nullable
    public LivingEntity getLastHurtMob() {
        return this.lastHurtMob;
    }

    public int getLastHurtMobTimestamp() {
        return this.lastHurtMobTimestamp;
    }

    public void setLastHurtMob(Entity entity) {
        if (entity instanceof LivingEntity) {
            this.lastHurtMob = (LivingEntity)entity;
        } else {
            this.lastHurtMob = null;
        }

        this.lastHurtMobTimestamp = this.tickCount;
    }

    public int getNoActionTime() {
        return this.noActionTime;
    }

    public void setNoActionTime(int idleTime) {
        this.noActionTime = idleTime;
    }

    public boolean shouldDiscardFriction() {
        return this.discardFriction;
    }

    public void setDiscardFriction(boolean discardFriction) {
        this.discardFriction = discardFriction;
    }

    protected boolean doesEmitEquipEvent(EquipmentSlot slot) {
        return true;
    }

    public void onEquipItem(EquipmentSlot slot, ItemStack oldItem, ItemStack newItem) {
        boolean bl = newItem.isEmpty() && oldItem.isEmpty();
        if (!bl && !ItemStack.isSameItemSameComponents(oldItem, newItem) && !this.firstTick) {
            Equipable equipable = Equipable.get(newItem);
            if (!this.level().isClientSide() && !this.isSpectator()) {
                if (!this.isSilent() && equipable != null && equipable.getEquipmentSlot() == slot) {
                    this.level()
                        .playSeededSound(
                            null, this.getX(), this.getY(), this.getZ(), equipable.getEquipSound(), this.getSoundSource(), 1.0F, 1.0F, this.random.nextLong()
                        );
                }

                if (this.doesEmitEquipEvent(slot)) {
                    this.gameEvent(equipable != null ? GameEvent.EQUIP : GameEvent.UNEQUIP);
                }
            }
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            this.triggerOnDeathMobEffects(reason);
        }

        super.remove(reason);
        this.brain.clearMemories();
    }

    protected void triggerOnDeathMobEffects(Entity.RemovalReason removalReason) {
        for (MobEffectInstance mobEffectInstance : this.getActiveEffects()) {
            mobEffectInstance.onMobRemoved(this, removalReason);
        }

        this.activeEffects.clear();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        compound.putFloat("Health", this.getHealth());
        compound.putShort("HurtTime", (short)this.hurtTime);
        compound.putInt("HurtByTimestamp", this.lastHurtByMobTimestamp);
        compound.putShort("DeathTime", (short)this.deathTime);
        compound.putFloat("AbsorptionAmount", this.getAbsorptionAmount());
        compound.put("attributes", this.getAttributes().save());
        if (!this.activeEffects.isEmpty()) {
            ListTag listTag = new ListTag();

            for (MobEffectInstance mobEffectInstance : this.activeEffects.values()) {
                listTag.add(mobEffectInstance.save());
            }

            compound.put("active_effects", listTag);
        }

        compound.putBoolean("FallFlying", this.isFallFlying());
        this.getSleepingPos().ifPresent(pos -> {
            compound.putInt("SleepingX", pos.getX());
            compound.putInt("SleepingY", pos.getY());
            compound.putInt("SleepingZ", pos.getZ());
        });
        DataResult<Tag> dataResult = this.brain.serializeStart(NbtOps.INSTANCE);
        dataResult.resultOrPartial(LOGGER::error).ifPresent(tag -> compound.put("Brain", tag));
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        this.internalSetAbsorptionAmount(compound.getFloat("AbsorptionAmount"));
        if (compound.contains("attributes", 9) && this.level() != null && !this.level().isClientSide) {
            this.getAttributes().load(compound.getList("attributes", 10));
        }

        if (compound.contains("active_effects", 9)) {
            ListTag listTag = compound.getList("active_effects", 10);

            for (int i = 0; i < listTag.size(); i++) {
                CompoundTag compoundTag = listTag.getCompound(i);
                MobEffectInstance mobEffectInstance = MobEffectInstance.load(compoundTag);
                if (mobEffectInstance != null) {
                    this.activeEffects.put(mobEffectInstance.getEffect(), mobEffectInstance);
                }
            }
        }

        if (compound.contains("Health", 99)) {
            this.setHealth(compound.getFloat("Health"));
        }

        this.hurtTime = compound.getShort("HurtTime");
        this.deathTime = compound.getShort("DeathTime");
        this.lastHurtByMobTimestamp = compound.getInt("HurtByTimestamp");
        if (compound.contains("Team", 8)) {
            String string = compound.getString("Team");
            Scoreboard scoreboard = this.level().getScoreboard();
            PlayerTeam playerTeam = scoreboard.getPlayerTeam(string);
            boolean bl = playerTeam != null && scoreboard.addPlayerToTeam(this.getStringUUID(), playerTeam);
            if (!bl) {
                LOGGER.warn("Unable to add mob to team \"{}\" (that team probably doesn't exist)", string);
            }
        }

        if (compound.getBoolean("FallFlying")) {
            this.setSharedFlag(7, true);
        }

        if (compound.contains("SleepingX", 99) && compound.contains("SleepingY", 99) && compound.contains("SleepingZ", 99)) {
            BlockPos blockPos = new BlockPos(compound.getInt("SleepingX"), compound.getInt("SleepingY"), compound.getInt("SleepingZ"));
            this.setSleepingPos(blockPos);
            this.entityData.set(DATA_POSE, Pose.SLEEPING);
            if (!this.firstTick) {
                this.setPosToBed(blockPos);
            }
        }

        if (compound.contains("Brain", 10)) {
            this.brain = this.makeBrain(new Dynamic<>(NbtOps.INSTANCE, compound.get("Brain")));
        }
    }

    protected void tickEffects() {
        Iterator<Holder<MobEffect>> iterator = this.activeEffects.keySet().iterator();

        try {
            while (iterator.hasNext()) {
                Holder<MobEffect> holder = iterator.next();
                MobEffectInstance mobEffectInstance = this.activeEffects.get(holder);
                if (!mobEffectInstance.tick(this, () -> this.onEffectUpdated(mobEffectInstance, true, null))) {
                    if (!this.level().isClientSide) {
                        iterator.remove();
                        this.onEffectRemoved(mobEffectInstance);
                    }
                } else if (mobEffectInstance.getDuration() % 600 == 0) {
                    this.onEffectUpdated(mobEffectInstance, false, null);
                }
            }
        } catch (ConcurrentModificationException var6) {
        }

        if (this.effectsDirty) {
            if (!this.level().isClientSide) {
                this.updateInvisibilityStatus();
                this.updateGlowingStatus();
            }

            this.effectsDirty = false;
        }

        List<ParticleOptions> list = this.entityData.get(DATA_EFFECT_PARTICLES);
        if (!list.isEmpty()) {
            boolean bl = this.entityData.get(DATA_EFFECT_AMBIENCE_ID);
            int i = this.isInvisible() ? 15 : 4;
            int j = bl ? 5 : 1;
            if (this.random.nextInt(i * j) == 0) {
                this.level().addParticle(Util.getRandom(list, this.random), this.getRandomX(0.5), this.getRandomY(), this.getRandomZ(0.5), 1.0, 1.0, 1.0);
            }
        }
    }

    protected void updateInvisibilityStatus() {
        if (this.activeEffects.isEmpty()) {
            this.removeEffectParticles();
            this.setInvisible(false);
        } else {
            this.setInvisible(this.hasEffect(MobEffects.INVISIBILITY));
            this.updateSynchronizedMobEffectParticles();
        }
    }

    private void updateSynchronizedMobEffectParticles() {
        List<ParticleOptions> list = this.activeEffects
            .values()
            .stream()
            .filter(MobEffectInstance::isVisible)
            .map(MobEffectInstance::getParticleOptions)
            .toList();
        this.entityData.set(DATA_EFFECT_PARTICLES, list);
        this.entityData.set(DATA_EFFECT_AMBIENCE_ID, areAllEffectsAmbient(this.activeEffects.values()));
    }

    private void updateGlowingStatus() {
        boolean bl = this.isCurrentlyGlowing();
        if (this.getSharedFlag(6) != bl) {
            this.setSharedFlag(6, bl);
        }
    }

    public double getVisibilityPercent(@Nullable Entity lookingEntity) {
        double d = 1.0;
        if (this.isDiscrete()) {
            d *= 0.8;
        }

        if (this.isInvisible()) {
            float f = this.getArmorCoverPercentage();
            if (f < 0.1F) {
                f = 0.1F;
            }

            d *= 0.7 * f;
        }

        if (lookingEntity != null) {
            ItemStack itemStack = this.getItemBySlot(EquipmentSlot.HEAD);
            EntityType<?> entityType = lookingEntity.getType();
            if (entityType == EntityType.SKELETON && itemStack.is(Items.SKELETON_SKULL)
                || entityType == EntityType.ZOMBIE && itemStack.is(Items.ZOMBIE_HEAD)
                || entityType == EntityType.PIGLIN && itemStack.is(Items.PIGLIN_HEAD)
                || entityType == EntityType.PIGLIN_BRUTE && itemStack.is(Items.PIGLIN_HEAD)
                || entityType == EntityType.CREEPER && itemStack.is(Items.CREEPER_HEAD)) {
                d *= 0.5;
            }
        }

        return d;
    }

    public boolean canAttack(LivingEntity target) {
        return target instanceof Player && this.level().getDifficulty() == Difficulty.PEACEFUL ? false : target.canBeSeenAsEnemy();
    }

    public boolean canAttack(LivingEntity livingentity, TargetingConditions condition) {
        return condition.test(this, livingentity);
    }

    public boolean canBeSeenAsEnemy() {
        return !this.isInvulnerable() && this.canBeSeenByAnyone();
    }

    public boolean canBeSeenByAnyone() {
        return !this.isSpectator() && this.isAlive();
    }

    /**
     * Returns {@code true} if all the potion effects in the specified collection are ambient.
     */
    public static boolean areAllEffectsAmbient(Collection<MobEffectInstance> potionEffects) {
        for (MobEffectInstance mobEffectInstance : potionEffects) {
            if (mobEffectInstance.isVisible() && !mobEffectInstance.isAmbient()) {
                return false;
            }
        }

        return true;
    }

    protected void removeEffectParticles() {
        this.entityData.set(DATA_EFFECT_PARTICLES, List.of());
    }

    public boolean removeAllEffects() {
        if (this.level().isClientSide) {
            return false;
        }

        Iterator<MobEffectInstance> iterator = this.activeEffects.values().iterator();

        boolean bl;
        for (bl = false; iterator.hasNext(); bl = true) {
            this.onEffectRemoved(iterator.next());
            iterator.remove();
        }

        return bl;
    }

    public Collection<MobEffectInstance> getActiveEffects() {
        return this.activeEffects.values();
    }

    public Map<Holder<MobEffect>, MobEffectInstance> getActiveEffectsMap() {
        return this.activeEffects;
    }

    public boolean hasEffect(Holder<MobEffect> effect) {
        return this.activeEffects.containsKey(effect);
    }

    @Nullable
    public MobEffectInstance getEffect(Holder<MobEffect> effect) {
        return this.activeEffects.get(effect);
    }

    public final boolean addEffect(MobEffectInstance effectInstance) {
        return this.addEffect(effectInstance, null);
    }

    public boolean addEffect(MobEffectInstance effectInstance, @Nullable Entity entity) {
        if (!this.canBeAffected(effectInstance)) {
            return false;
        }

        MobEffectInstance mobEffectInstance = this.activeEffects.get(effectInstance.getEffect());
        boolean bl = false;
        if (mobEffectInstance == null) {
            this.activeEffects.put(effectInstance.getEffect(), effectInstance);
            this.onEffectAdded(effectInstance, entity);
            bl = true;
            effectInstance.onEffectAdded(this);
        } else if (mobEffectInstance.update(effectInstance)) {
            this.onEffectUpdated(mobEffectInstance, true, entity);
            bl = true;
        }

        effectInstance.onEffectStarted(this);
        return bl;
    }

    public boolean canBeAffected(MobEffectInstance effectInstance) {
        if (this.getType().is(EntityTypeTags.IMMUNE_TO_INFESTED)) {
            return !effectInstance.is(MobEffects.INFESTED);
        } else if (this.getType().is(EntityTypeTags.IMMUNE_TO_OOZING)) {
            return !effectInstance.is(MobEffects.OOZING);
        } else {
            return !this.getType().is(EntityTypeTags.IGNORES_POISON_AND_REGEN)
                ? true
                : !effectInstance.is(MobEffects.REGENERATION) && !effectInstance.is(MobEffects.POISON);
        }
    }

    public void forceAddEffect(MobEffectInstance instance, @Nullable Entity entity) {
        if (this.canBeAffected(instance)) {
            MobEffectInstance mobEffectInstance = this.activeEffects.put(instance.getEffect(), instance);
            if (mobEffectInstance == null) {
                this.onEffectAdded(instance, entity);
            } else {
                instance.copyBlendState(mobEffectInstance);
                this.onEffectUpdated(instance, true, entity);
            }
        }
    }

    public boolean isInvertedHealAndHarm() {
        return this.getType().is(EntityTypeTags.INVERTED_HEALING_AND_HARM);
    }

    @Nullable
    public MobEffectInstance removeEffectNoUpdate(Holder<MobEffect> effect) {
        return this.activeEffects.remove(effect);
    }

    public boolean removeEffect(Holder<MobEffect> effect) {
        MobEffectInstance mobEffectInstance = this.removeEffectNoUpdate(effect);
        if (mobEffectInstance != null) {
            this.onEffectRemoved(mobEffectInstance);
            return true;
        } else {
            return false;
        }
    }

    protected void onEffectAdded(MobEffectInstance effectInstance, @Nullable Entity entity) {
        this.effectsDirty = true;
        if (!this.level().isClientSide) {
            effectInstance.getEffect().value().addAttributeModifiers(this.getAttributes(), effectInstance.getAmplifier());
            this.sendEffectToPassengers(effectInstance);
        }
    }

    public void sendEffectToPassengers(MobEffectInstance effectInstance) {
        for (Entity entity : this.getPassengers()) {
            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effectInstance, false));
            }
        }
    }

    protected void onEffectUpdated(MobEffectInstance effectInstance, boolean forced, @Nullable Entity entity) {
        this.effectsDirty = true;
        if (forced && !this.level().isClientSide) {
            MobEffect mobEffect = effectInstance.getEffect().value();
            mobEffect.removeAttributeModifiers(this.getAttributes());
            mobEffect.addAttributeModifiers(this.getAttributes(), effectInstance.getAmplifier());
            this.refreshDirtyAttributes();
        }

        if (!this.level().isClientSide) {
            this.sendEffectToPassengers(effectInstance);
        }
    }

    protected void onEffectRemoved(MobEffectInstance effectInstance) {
        this.effectsDirty = true;
        if (!this.level().isClientSide) {
            effectInstance.getEffect().value().removeAttributeModifiers(this.getAttributes());
            this.refreshDirtyAttributes();

            for (Entity entity : this.getPassengers()) {
                if (entity instanceof ServerPlayer serverPlayer) {
                    serverPlayer.connection.send(new ClientboundRemoveMobEffectPacket(this.getId(), effectInstance.getEffect()));
                }
            }
        }
    }

    private void refreshDirtyAttributes() {
        Set<AttributeInstance> set = this.getAttributes().getAttributesToUpdate();

        for (AttributeInstance attributeInstance : set) {
            this.onAttributeUpdated(attributeInstance.getAttribute());
        }

        set.clear();
    }

    private void onAttributeUpdated(Holder<Attribute> attribute) {
        if (attribute.is(Attributes.MAX_HEALTH)) {
            float f = this.getMaxHealth();
            if (this.getHealth() > f) {
                this.setHealth(f);
            }
        } else if (attribute.is(Attributes.MAX_ABSORPTION)) {
            float f = this.getMaxAbsorption();
            if (this.getAbsorptionAmount() > f) {
                this.setAbsorptionAmount(f);
            }
        }
    }

    /**
     * Heal living entity (param: amount of half-hearts)
     */
    public void heal(float healAmount) {
        float f = this.getHealth();
        if (f > 0.0F) {
            this.setHealth(f + healAmount);
        }
    }

    public float getHealth() {
        return this.entityData.get(DATA_HEALTH_ID);
    }

    public void setHealth(float health) {
        this.entityData.set(DATA_HEALTH_ID, Mth.clamp(health, 0.0F, this.getMaxHealth()));
    }

    public boolean isDeadOrDying() {
        return this.getHealth() <= 0.0F;
    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isInvulnerableTo(source)) {
            return false;
        }

        if (this.level().isClientSide) {
            return false;
        }

        if (this.isDeadOrDying()) {
            return false;
        }

        if (source.is(DamageTypeTags.IS_FIRE) && this.hasEffect(MobEffects.FIRE_RESISTANCE)) {
            return false;
        }

        if (this.isSleeping() && !this.level().isClientSide) {
            this.stopSleeping();
        }

        this.noActionTime = 0;
        float f = amount;
        boolean bl = false;
        float g = 0.0F;
        if (amount > 0.0F && this.isDamageSourceBlocked(source)) {
            this.hurtCurrentlyUsedShield(amount);
            g = amount;
            amount = 0.0F;
            if (!source.is(DamageTypeTags.IS_PROJECTILE) && source.getDirectEntity() instanceof LivingEntity livingEntity) {
                this.blockUsingShield(livingEntity);
            }

            bl = true;
        }

        if (source.is(DamageTypeTags.IS_FREEZING) && this.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES)) {
            amount *= 5.0F;
        }

        if (source.is(DamageTypeTags.DAMAGES_HELMET) && !this.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            this.hurtHelmet(source, amount);
            amount *= 0.75F;
        }

        this.walkAnimation.setSpeed(1.5F);
        boolean bl2 = true;
        if (this.invulnerableTime > 10.0F && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) {
            if (amount <= this.lastHurt) {
                return false;
            }

            this.actuallyHurt(source, amount - this.lastHurt);
            this.lastHurt = amount;
            bl2 = false;
        } else {
            this.lastHurt = amount;
            this.invulnerableTime = 20;
            this.actuallyHurt(source, amount);
            this.hurtDuration = 10;
            this.hurtTime = this.hurtDuration;
        }

        Entity entity2 = source.getEntity();
        if (entity2 != null) {
            if (entity2 instanceof LivingEntity livingEntity2
                && !source.is(DamageTypeTags.NO_ANGER)
                && (!source.is(DamageTypes.WIND_CHARGE) || !this.getType().is(EntityTypeTags.NO_ANGER_FROM_WIND_CHARGE))) {
                this.setLastHurtByMob(livingEntity2);
            }

            if (entity2 instanceof Player player) {
                this.lastHurtByPlayerTime = 100;
                this.lastHurtByPlayer = player;
            } else if (entity2 instanceof Wolf wolf && wolf.isTame()) {
                this.lastHurtByPlayerTime = 100;
                if (wolf.getOwner() instanceof Player player2) {
                    this.lastHurtByPlayer = player2;
                } else {
                    this.lastHurtByPlayer = null;
                }
            }
        }

        if (bl2) {
            if (bl) {
                this.level().broadcastEntityEvent(this, (byte)29);
            } else {
                this.level().broadcastDamageEvent(this, source);
            }

            if (!source.is(DamageTypeTags.NO_IMPACT) && (!bl || amount > 0.0F)) {
                this.markHurt();
            }

            if (!source.is(DamageTypeTags.NO_KNOCKBACK)) {
                double d = 0.0;
                double e = 0.0;
                if (source.getDirectEntity() instanceof Projectile projectile) {
                    DoubleDoubleImmutablePair doubleDoubleImmutablePair = projectile.calculateHorizontalHurtKnockbackDirection(this, source);
                    d = -doubleDoubleImmutablePair.leftDouble();
                    e = -doubleDoubleImmutablePair.rightDouble();
                } else if (source.getSourcePosition() != null) {
                    d = source.getSourcePosition().x() - this.getX();
                    e = source.getSourcePosition().z() - this.getZ();
                }

                this.knockback(0.4F, d, e);
                if (!bl) {
                    this.indicateDamage(d, e);
                }
            }
        }

        if (this.isDeadOrDying()) {
            if (!this.checkTotemDeathProtection(source)) {
                if (bl2) {
                    this.makeSound(this.getDeathSound());
                }

                this.die(source);
            }
        } else if (bl2) {
            this.playHurtSound(source);
        }

        boolean bl3 = !bl || amount > 0.0F;
        if (bl3) {
            this.lastDamageSource = source;
            this.lastDamageStamp = this.level().getGameTime();

            for (MobEffectInstance mobEffectInstance : this.getActiveEffects()) {
                mobEffectInstance.onMobHurt(this, source, amount);
            }
        }

        if (this instanceof ServerPlayer) {
            CriteriaTriggers.ENTITY_HURT_PLAYER.trigger((ServerPlayer)this, source, f, amount, bl);
            if (g > 0.0F && g < 3.4028235E37F) {
                ((ServerPlayer)this).awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, Math.round(g * 10.0F));
            }
        }

        if (entity2 instanceof ServerPlayer) {
            CriteriaTriggers.PLAYER_HURT_ENTITY.trigger((ServerPlayer)entity2, this, source, f, amount, bl);
        }

        return bl3;
    }

    protected void blockUsingShield(LivingEntity attacker) {
        attacker.blockedByShield(this);
    }

    protected void blockedByShield(LivingEntity defender) {
        defender.knockback(0.5, defender.getX() - this.getX(), defender.getZ() - this.getZ());
    }

    private boolean checkTotemDeathProtection(DamageSource damageSource) {
        if (damageSource.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }

        ItemStack itemStack = null;

        for (InteractionHand interactionHand : InteractionHand.values()) {
            ItemStack itemStack2 = this.getItemInHand(interactionHand);
            if (itemStack2.is(Items.TOTEM_OF_UNDYING)) {
                itemStack = itemStack2.copy();
                itemStack2.shrink(1);
                break;
            }
        }

        if (itemStack != null) {
            if (this instanceof ServerPlayer serverPlayer) {
                serverPlayer.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING));
                CriteriaTriggers.USED_TOTEM.trigger(serverPlayer, itemStack);
                this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
            }

            this.setHealth(1.0F);
            this.removeAllEffects();
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
            this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
            this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
            this.level().broadcastEntityEvent(this, (byte)35);
        }

        return itemStack != null;
    }

    @Nullable
    public DamageSource getLastDamageSource() {
        if (this.level().getGameTime() - this.lastDamageStamp > 40L) {
            this.lastDamageSource = null;
        }

        return this.lastDamageSource;
    }

    protected void playHurtSound(DamageSource source) {
        this.makeSound(this.getHurtSound(source));
    }

    public void makeSound(@Nullable SoundEvent sound) {
        if (sound != null) {
            this.playSound(sound, this.getSoundVolume(), this.getVoicePitch());
        }
    }

    /**
     * Determines whether the entity can block the damage source based on the damage source's location, whether the damage source is blockable, and whether the entity is blocking.
     */
    public boolean isDamageSourceBlocked(DamageSource damageSource) {
        Entity entity = damageSource.getDirectEntity();
        boolean bl = false;
        if (entity instanceof AbstractArrow abstractArrow && abstractArrow.getPierceLevel() > 0) {
            bl = true;
        }

        if (!damageSource.is(DamageTypeTags.BYPASSES_SHIELD) && this.isBlocking() && !bl) {
            Vec3 vec3 = damageSource.getSourcePosition();
            if (vec3 != null) {
                Vec3 vec32 = this.calculateViewVector(0.0F, this.getYHeadRot());
                Vec3 vec33 = vec3.vectorTo(this.position());
                vec33 = new Vec3(vec33.x, 0.0, vec33.z).normalize();
                return vec33.dot(vec32) < 0.0;
            }
        }

        return false;
    }

    /**
     * Renders broken item particles using the given ItemStack
     */
    private void breakItem(ItemStack stack) {
        if (!stack.isEmpty()) {
            if (!this.isSilent()) {
                this.level()
                    .playLocalSound(
                        this.getX(),
                        this.getY(),
                        this.getZ(),
                        stack.getBreakingSound(),
                        this.getSoundSource(),
                        0.8F,
                        0.8F + this.level().random.nextFloat() * 0.4F,
                        false
                    );
            }

            this.spawnItemParticles(stack, 5);
        }
    }

    /**
     * Called when the mob's health reaches 0.
     */
    public void die(DamageSource damageSource) {
        if (!this.isRemoved() && !this.dead) {
            Entity entity = damageSource.getEntity();
            LivingEntity livingEntity = this.getKillCredit();
            if (this.deathScore >= 0 && livingEntity != null) {
                livingEntity.awardKillScore(this, this.deathScore, damageSource);
            }

            if (this.isSleeping()) {
                this.stopSleeping();
            }

            if (!this.level().isClientSide && this.hasCustomName()) {
                LOGGER.info("Named entity {} died: {}", this, this.getCombatTracker().getDeathMessage().getString());
            }

            this.dead = true;
            this.getCombatTracker().recheckStatus();
            if (this.level() instanceof ServerLevel serverLevel) {
                if (entity == null || entity.killedEntity(serverLevel, this)) {
                    this.gameEvent(GameEvent.ENTITY_DIE);
                    this.dropAllDeathLoot(serverLevel, damageSource);
                    this.createWitherRose(livingEntity);
                }

                this.level().broadcastEntityEvent(this, (byte)3);
            }

            this.setPose(Pose.DYING);
        }
    }

    protected void createWitherRose(@Nullable LivingEntity entitySource) {
        if (!this.level().isClientSide) {
            boolean bl = false;
            if (entitySource instanceof WitherBoss) {
                if (this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                    BlockPos blockPos = this.blockPosition();
                    BlockState blockState = Blocks.WITHER_ROSE.defaultBlockState();
                    if (this.level().getBlockState(blockPos).isAir() && blockState.canSurvive(this.level(), blockPos)) {
                        this.level().setBlock(blockPos, blockState, 3);
                        bl = true;
                    }
                }

                if (!bl) {
                    ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), new ItemStack(Items.WITHER_ROSE));
                    this.level().addFreshEntity(itemEntity);
                }
            }
        }
    }

    protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
        boolean bl = this.lastHurtByPlayerTime > 0;
        if (this.shouldDropLoot() && level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) {
            this.dropFromLootTable(damageSource, bl);
            this.dropCustomDeathLoot(level, damageSource, bl);
        }

        this.dropEquipment();
        this.dropExperience(damageSource.getEntity());
    }

    protected void dropEquipment() {
    }

    protected void dropExperience(@Nullable Entity entity) {
        if (this.level() instanceof ServerLevel serverLevel
            && !this.wasExperienceConsumed()
            && (
                this.isAlwaysExperienceDropper()
                    || this.lastHurtByPlayerTime > 0 && this.shouldDropExperience() && this.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
            )) {
            ExperienceOrb.award(serverLevel, this.position(), this.getExperienceReward(serverLevel, entity));
        }
    }

    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
    }

    public ResourceKey<LootTable> getLootTable() {
        return this.getType().getDefaultLootTable();
    }

    public long getLootTableSeed() {
        return 0L;
    }

    protected float getKnockback(Entity attacker, DamageSource damageSource) {
        float f = (float)this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        return this.level() instanceof ServerLevel serverLevel
            ? EnchantmentHelper.modifyKnockback(serverLevel, this.getWeaponItem(), attacker, damageSource, f)
            : f;
    }

    protected void dropFromLootTable(DamageSource damageSource, boolean hitByPlayer) {
        ResourceKey<LootTable> resourceKey = this.getLootTable();
        LootTable lootTable = this.level().getServer().reloadableRegistries().getLootTable(resourceKey);
        LootParams.Builder builder = new LootParams.Builder((ServerLevel)this.level())
            .withParameter(LootContextParams.THIS_ENTITY, this)
            .withParameter(LootContextParams.ORIGIN, this.position())
            .withParameter(LootContextParams.DAMAGE_SOURCE, damageSource)
            .withOptionalParameter(LootContextParams.ATTACKING_ENTITY, damageSource.getEntity())
            .withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, damageSource.getDirectEntity());
        if (hitByPlayer && this.lastHurtByPlayer != null) {
            builder = builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, this.lastHurtByPlayer).withLuck(this.lastHurtByPlayer.getLuck());
        }

        LootParams lootParams = builder.create(LootContextParamSets.ENTITY);
        lootTable.getRandomItems(lootParams, this.getLootTableSeed(), this::spawnAtLocation);
    }

    public void knockback(double strength, double x, double z) {
        strength *= 1.0 - this.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        if (!(strength <= 0.0)) {
            this.hasImpulse = true;
            Vec3 vec3 = this.getDeltaMovement();

            while (x * x + z * z < 1.0E-5F) {
                x = (Math.random() - Math.random()) * 0.01;
                z = (Math.random() - Math.random()) * 0.01;
            }

            Vec3 vec32 = new Vec3(x, 0.0, z).normalize().scale(strength);
            this.setDeltaMovement(vec3.x / 2.0 - vec32.x, this.onGround() ? Math.min(0.4, vec3.y / 2.0 + strength) : vec3.y, vec3.z / 2.0 - vec32.z);
        }
    }

    public void indicateDamage(double xDistance, double zDistance) {
    }

    @Nullable
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.GENERIC_HURT;
    }

    @Nullable
    protected SoundEvent getDeathSound() {
        return SoundEvents.GENERIC_DEATH;
    }

    private SoundEvent getFallDamageSound(int height) {
        return height > 4 ? this.getFallSounds().big() : this.getFallSounds().small();
    }

    public void skipDropExperience() {
        this.skipDropExperience = true;
    }

    public boolean wasExperienceConsumed() {
        return this.skipDropExperience;
    }

    public float getHurtDir() {
        return 0.0F;
    }

    protected AABB getHitbox() {
        AABB aABB = this.getBoundingBox();
        Entity entity = this.getVehicle();
        if (entity != null) {
            Vec3 vec3 = entity.getPassengerRidingPosition(this);
            return aABB.setMinY(Math.max(vec3.y, aABB.minY));
        } else {
            return aABB;
        }
    }

    public Map<Enchantment, Set<EnchantmentLocationBasedEffect>> activeLocationDependentEnchantments() {
        return this.activeLocationDependentEnchantments;
    }

    public LivingEntity.Fallsounds getFallSounds() {
        return new LivingEntity.Fallsounds(SoundEvents.GENERIC_SMALL_FALL, SoundEvents.GENERIC_BIG_FALL);
    }

    protected SoundEvent getDrinkingSound(ItemStack stack) {
        return stack.getDrinkingSound();
    }

    public SoundEvent getEatingSound(ItemStack stack) {
        return stack.getEatingSound();
    }

    public Optional<BlockPos> getLastClimbablePos() {
        return this.lastClimbablePos;
    }

    public boolean onClimbable() {
        if (this.isSpectator()) {
            return false;
        } else {
            BlockPos blockPos = this.blockPosition();
            BlockState blockState = this.getInBlockState();
            if (blockState.is(BlockTags.CLIMBABLE)) {
                this.lastClimbablePos = Optional.of(blockPos);
                return true;
            } else if (blockState.getBlock() instanceof TrapDoorBlock && this.trapdoorUsableAsLadder(blockPos, blockState)) {
                this.lastClimbablePos = Optional.of(blockPos);
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean trapdoorUsableAsLadder(BlockPos pos, BlockState state) {
        if (!state.getValue(TrapDoorBlock.OPEN)) {
            return false;
        }

        BlockState blockState = this.level().getBlockState(pos.below());
        return blockState.is(Blocks.LADDER) && blockState.getValue(LadderBlock.FACING) == state.getValue(TrapDoorBlock.FACING);
    }

    @Override
    public boolean isAlive() {
        return !this.isRemoved() && this.getHealth() > 0.0F;
    }

    @Override
    public int getMaxFallDistance() {
        return this.getComfortableFallDistance(0.0F);
    }

    protected final int getComfortableFallDistance(float health) {
        return Mth.floor(health + 3.0F);
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        boolean bl = super.causeFallDamage(fallDistance, multiplier, source);
        int i = this.calculateFallDamage(fallDistance, multiplier);
        if (i > 0) {
            this.playSound(this.getFallDamageSound(i), 1.0F, 1.0F);
            this.playBlockFallSound();
            this.hurt(source, i);
            return true;
        } else {
            return bl;
        }
    }

    protected int calculateFallDamage(float fallDistance, float damageMultiplier) {
        if (this.getType().is(EntityTypeTags.FALL_DAMAGE_IMMUNE)) {
            return 0;
        }

        float f = (float)this.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        float g = fallDistance - f;
        return Mth.ceil(g * damageMultiplier * this.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER));
    }

    protected void playBlockFallSound() {
        if (!this.isSilent()) {
            int i = Mth.floor(this.getX());
            int j = Mth.floor(this.getY() - 0.2F);
            int k = Mth.floor(this.getZ());
            BlockState blockState = this.level().getBlockState(new BlockPos(i, j, k));
            if (!blockState.isAir()) {
                SoundType soundType = blockState.getSoundType();
                this.playSound(soundType.getFallSound(), soundType.getVolume() * 0.5F, soundType.getPitch() * 0.75F);
            }
        }
    }

    @Override
    public void animateHurt(float yaw) {
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
    }

    public int getArmorValue() {
        return Mth.floor(this.getAttributeValue(Attributes.ARMOR));
    }

    protected void hurtArmor(DamageSource damageSource, float damageAmount) {
    }

    protected void hurtHelmet(DamageSource damageSource, float damageAmount) {
    }

    protected void hurtCurrentlyUsedShield(float damageAmount) {
    }

    protected void doHurtEquipment(DamageSource damageSource, float damageAmount, EquipmentSlot... slots) {
        if (!(damageAmount <= 0.0F)) {
            int i = (int)Math.max(1.0F, damageAmount / 4.0F);

            for (EquipmentSlot equipmentSlot : slots) {
                ItemStack itemStack = this.getItemBySlot(equipmentSlot);
                if (itemStack.getItem() instanceof ArmorItem && itemStack.canBeHurtBy(damageSource)) {
                    itemStack.hurtAndBreak(i, this, equipmentSlot);
                }
            }
        }
    }

    /**
     * Reduces damage, depending on armor
     */
    protected float getDamageAfterArmorAbsorb(DamageSource damageSource, float damageAmount) {
        if (!damageSource.is(DamageTypeTags.BYPASSES_ARMOR)) {
            this.hurtArmor(damageSource, damageAmount);
            damageAmount = CombatRules.getDamageAfterAbsorb(
                this, damageAmount, damageSource, this.getArmorValue(), (float)this.getAttributeValue(Attributes.ARMOR_TOUGHNESS)
            );
        }

        return damageAmount;
    }

    /**
     * Reduces damage, depending on potions
     */
    protected float getDamageAfterMagicAbsorb(DamageSource damageSource, float damageAmount) {
        if (damageSource.is(DamageTypeTags.BYPASSES_EFFECTS)) {
            return damageAmount;
        }

        if (this.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !damageSource.is(DamageTypeTags.BYPASSES_RESISTANCE)) {
            int i = (this.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1) * 5;
            int j = 25 - i;
            float f = damageAmount * j;
            float g = damageAmount;
            damageAmount = Math.max(f / 25.0F, 0.0F);
            float h = g - damageAmount;
            if (h > 0.0F && h < 3.4028235E37F) {
                if (this instanceof ServerPlayer) {
                    ((ServerPlayer)this).awardStat(Stats.DAMAGE_RESISTED, Math.round(h * 10.0F));
                } else if (damageSource.getEntity() instanceof ServerPlayer) {
                    ((ServerPlayer)damageSource.getEntity()).awardStat(Stats.DAMAGE_DEALT_RESISTED, Math.round(h * 10.0F));
                }
            }
        }

        if (damageAmount <= 0.0F) {
            return 0.0F;
        }

        if (damageSource.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)) {
            return damageAmount;
        }

        float k;
        if (this.level() instanceof ServerLevel serverLevel) {
            k = EnchantmentHelper.getDamageProtection(serverLevel, this, damageSource);
        } else {
            k = 0.0F;
        }

        if (k > 0.0F) {
            damageAmount = CombatRules.getDamageAfterMagicAbsorb(damageAmount, k);
        }

        return damageAmount;
    }

    /**
     * Deals damage to the entity. This will take the armor of the entity into consideration before damaging the health bar.
     */
    protected void actuallyHurt(DamageSource damageSource, float damageAmount) {
        if (!this.isInvulnerableTo(damageSource)) {
            damageAmount = this.getDamageAfterArmorAbsorb(damageSource, damageAmount);
            damageAmount = this.getDamageAfterMagicAbsorb(damageSource, damageAmount);
            float f = damageAmount;
            damageAmount = Math.max(damageAmount - this.getAbsorptionAmount(), 0.0F);
            this.setAbsorptionAmount(this.getAbsorptionAmount() - (f - damageAmount));
            float g = f - damageAmount;
            if (g > 0.0F && g < 3.4028235E37F && damageSource.getEntity() instanceof ServerPlayer serverPlayer) {
                serverPlayer.awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(g * 10.0F));
            }

            if (damageAmount != 0.0F) {
                this.getCombatTracker().recordDamage(damageSource, damageAmount);
                this.setHealth(this.getHealth() - damageAmount);
                this.setAbsorptionAmount(this.getAbsorptionAmount() - damageAmount);
                this.gameEvent(GameEvent.ENTITY_DAMAGE);
            }
        }
    }

    public CombatTracker getCombatTracker() {
        return this.combatTracker;
    }

    @Nullable
    public LivingEntity getKillCredit() {
        if (this.lastHurtByPlayer != null) {
            return this.lastHurtByPlayer;
        } else {
            return this.lastHurtByMob != null ? this.lastHurtByMob : null;
        }
    }

    public final float getMaxHealth() {
        return (float)this.getAttributeValue(Attributes.MAX_HEALTH);
    }

    public final float getMaxAbsorption() {
        return (float)this.getAttributeValue(Attributes.MAX_ABSORPTION);
    }

    public final int getArrowCount() {
        return this.entityData.get(DATA_ARROW_COUNT_ID);
    }

    /**
     * Sets the amount of arrows stuck in the entity. Used for rendering those.
     */
    public final void setArrowCount(int count) {
        this.entityData.set(DATA_ARROW_COUNT_ID, count);
    }

    public final int getStingerCount() {
        return this.entityData.get(DATA_STINGER_COUNT_ID);
    }

    public final void setStingerCount(int stingerCount) {
        this.entityData.set(DATA_STINGER_COUNT_ID, stingerCount);
    }

    private int getCurrentSwingDuration() {
        if (MobEffectUtil.hasDigSpeed(this)) {
            return 6 - (1 + MobEffectUtil.getDigSpeedAmplification(this));
        } else {
            return this.hasEffect(MobEffects.DIG_SLOWDOWN) ? 6 + (1 + this.getEffect(MobEffects.DIG_SLOWDOWN).getAmplifier()) * 2 : 6;
        }
    }

    public void swing(InteractionHand hand) {
        this.swing(hand, false);
    }

    public void swing(InteractionHand hand, boolean updateSelf) {
        if (!this.swinging || this.swingTime >= this.getCurrentSwingDuration() / 2 || this.swingTime < 0) {
            this.swingTime = -1;
            this.swinging = true;
            this.swingingArm = hand;
            if (this.level() instanceof ServerLevel) {
                ClientboundAnimatePacket clientboundAnimatePacket = new ClientboundAnimatePacket(this, hand == InteractionHand.MAIN_HAND ? 0 : 3);
                ServerChunkCache serverChunkCache = ((ServerLevel)this.level()).getChunkSource();
                if (updateSelf) {
                    serverChunkCache.broadcastAndSend(this, clientboundAnimatePacket);
                } else {
                    serverChunkCache.broadcast(this, clientboundAnimatePacket);
                }
            }
        }
    }

    @Override
    public void handleDamageEvent(DamageSource damageSource) {
        this.walkAnimation.setSpeed(1.5F);
        this.invulnerableTime = 20;
        this.hurtDuration = 10;
        this.hurtTime = this.hurtDuration;
        SoundEvent soundEvent = this.getHurtSound(damageSource);
        if (soundEvent != null) {
            this.playSound(soundEvent, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
        }

        this.hurt(this.damageSources().generic(), 0.0F);
        this.lastDamageSource = damageSource;
        this.lastDamageStamp = this.level().getGameTime();
    }

    /**
     * Handler for {@link World#setEntityState}
     */
    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case 3:
                SoundEvent soundEvent = this.getDeathSound();
                if (soundEvent != null) {
                    this.playSound(soundEvent, this.getSoundVolume(), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
                }

                if (!(this instanceof Player)) {
                    this.setHealth(0.0F);
                    this.die(this.damageSources().generic());
                }
                break;
            case 29:
                this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 0.8F + this.level().random.nextFloat() * 0.4F);
                break;
            case 30:
                this.playSound(SoundEvents.SHIELD_BREAK, 0.8F, 0.8F + this.level().random.nextFloat() * 0.4F);
                break;
            case 46:
                int i = 128;

                for (int j = 0; j < 128; j++) {
                    double d = j / 127.0;
                    float f = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float g = (this.random.nextFloat() - 0.5F) * 0.2F;
                    float h = (this.random.nextFloat() - 0.5F) * 0.2F;
                    double e = Mth.lerp(d, this.xo, this.getX()) + (this.random.nextDouble() - 0.5) * this.getBbWidth() * 2.0;
                    double k = Mth.lerp(d, this.yo, this.getY()) + this.random.nextDouble() * this.getBbHeight();
                    double l = Mth.lerp(d, this.zo, this.getZ()) + (this.random.nextDouble() - 0.5) * this.getBbWidth() * 2.0;
                    this.level().addParticle(ParticleTypes.PORTAL, e, k, l, f, g, h);
                }
                break;
            case 47:
                this.breakItem(this.getItemBySlot(EquipmentSlot.MAINHAND));
                break;
            case 48:
                this.breakItem(this.getItemBySlot(EquipmentSlot.OFFHAND));
                break;
            case 49:
                this.breakItem(this.getItemBySlot(EquipmentSlot.HEAD));
                break;
            case 50:
                this.breakItem(this.getItemBySlot(EquipmentSlot.CHEST));
                break;
            case 51:
                this.breakItem(this.getItemBySlot(EquipmentSlot.LEGS));
                break;
            case 52:
                this.breakItem(this.getItemBySlot(EquipmentSlot.FEET));
                break;
            case 54:
                HoneyBlock.showJumpParticles(this);
                break;
            case 55:
                this.swapHandItems();
                break;
            case 60:
                this.makePoofParticles();
                break;
            case 65:
                this.breakItem(this.getItemBySlot(EquipmentSlot.BODY));
                break;
            default:
                super.handleEntityEvent(id);
        }
    }

    private void makePoofParticles() {
        for (int i = 0; i < 20; i++) {
            double d = this.random.nextGaussian() * 0.02;
            double e = this.random.nextGaussian() * 0.02;
            double f = this.random.nextGaussian() * 0.02;
            this.level().addParticle(ParticleTypes.POOF, this.getRandomX(1.0), this.getRandomY(), this.getRandomZ(1.0), d, e, f);
        }
    }

    private void swapHandItems() {
        ItemStack itemStack = this.getItemBySlot(EquipmentSlot.OFFHAND);
        this.setItemSlot(EquipmentSlot.OFFHAND, this.getItemBySlot(EquipmentSlot.MAINHAND));
        this.setItemSlot(EquipmentSlot.MAINHAND, itemStack);
    }

    @Override
    protected void onBelowWorld() {
        this.hurt(this.damageSources().fellOutOfWorld(), 4.0F);
    }

    protected void updateSwingTime() {
        int i = this.getCurrentSwingDuration();
        if (this.swinging) {
            this.swingTime++;
            if (this.swingTime >= i) {
                this.swingTime = 0;
                this.swinging = false;
            }
        } else {
            this.swingTime = 0;
        }

        this.attackAnim = (float)this.swingTime / i;
    }

    @Nullable
    public AttributeInstance getAttribute(Holder<Attribute> attribute) {
        return this.getAttributes().getInstance(attribute);
    }

    public double getAttributeValue(Holder<Attribute> attribute) {
        return this.getAttributes().getValue(attribute);
    }

    public double getAttributeBaseValue(Holder<Attribute> attribute) {
        return this.getAttributes().getBaseValue(attribute);
    }

    public AttributeMap getAttributes() {
        return this.attributes;
    }

    public ItemStack getMainHandItem() {
        return this.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    public ItemStack getOffhandItem() {
        return this.getItemBySlot(EquipmentSlot.OFFHAND);
    }

    @Nonnull
    @Override
    public ItemStack getWeaponItem() {
        return this.getMainHandItem();
    }

    public boolean isHolding(Item item) {
        return this.isHolding(itemStack -> itemStack.is(item));
    }

    public boolean isHolding(Predicate<ItemStack> predicate) {
        return predicate.test(this.getMainHandItem()) || predicate.test(this.getOffhandItem());
    }

    public ItemStack getItemInHand(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            return this.getItemBySlot(EquipmentSlot.MAINHAND);
        } else if (hand == InteractionHand.OFF_HAND) {
            return this.getItemBySlot(EquipmentSlot.OFFHAND);
        } else {
            throw new IllegalArgumentException("Invalid hand " + hand);
        }
    }

    public void setItemInHand(InteractionHand hand, ItemStack stack) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.setItemSlot(EquipmentSlot.MAINHAND, stack);
        } else {
            if (hand != InteractionHand.OFF_HAND) {
                throw new IllegalArgumentException("Invalid hand " + hand);
            }

            this.setItemSlot(EquipmentSlot.OFFHAND, stack);
        }
    }

    public boolean hasItemInSlot(EquipmentSlot slot) {
        return !this.getItemBySlot(slot).isEmpty();
    }

    public boolean canUseSlot(EquipmentSlot slot) {
        return false;
    }

    public abstract Iterable<ItemStack> getArmorSlots();

    public abstract ItemStack getItemBySlot(EquipmentSlot var1);

    public abstract void setItemSlot(EquipmentSlot var1, ItemStack var2);

    public Iterable<ItemStack> getHandSlots() {
        return List.of();
    }

    public Iterable<ItemStack> getArmorAndBodyArmorSlots() {
        return this.getArmorSlots();
    }

    public Iterable<ItemStack> getAllSlots() {
        return Iterables.concat(this.getHandSlots(), this.getArmorAndBodyArmorSlots());
    }

    protected void verifyEquippedItem(ItemStack stack) {
        stack.getItem().verifyComponentsAfterLoad(stack);
    }

    public float getArmorCoverPercentage() {
        Iterable<ItemStack> iterable = this.getArmorSlots();
        int i = 0;
        int j = 0;

        for (ItemStack itemStack : iterable) {
            if (!itemStack.isEmpty()) {
                j++;
            }

            i++;
        }

        return i > 0 ? (float)j / i : 0.0F;
    }

    /**
     * Set sprinting switch for Entity.
     */
    @Override
    public void setSprinting(boolean sprinting) {
        super.setSprinting(sprinting);
        AttributeInstance attributeInstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
        attributeInstance.removeModifier(SPEED_MODIFIER_SPRINTING.id());
        if (sprinting) {
            attributeInstance.addTransientModifier(SPEED_MODIFIER_SPRINTING);
        }
    }

    protected float getSoundVolume() {
        return 1.0F;
    }

    public float getVoicePitch() {
        return this.isBaby()
            ? (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.5F
            : (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F;
    }

    protected boolean isImmobile() {
        return this.isDeadOrDying();
    }

    /**
     * Applies a velocity to the entities, to push them away from each other.
     */
    @Override
    public void push(Entity entity) {
        if (!this.isSleeping()) {
            super.push(entity);
        }
    }

    private void dismountVehicle(Entity vehicle) {
        Vec3 vec3;
        if (this.isRemoved()) {
            vec3 = this.position();
        } else if (!vehicle.isRemoved() && !this.level().getBlockState(vehicle.blockPosition()).is(BlockTags.PORTALS)) {
            vec3 = vehicle.getDismountLocationForPassenger(this);
        } else {
            double d = Math.max(this.getY(), vehicle.getY());
            vec3 = new Vec3(this.getX(), d, this.getZ());
        }

        this.dismountTo(vec3.x, vec3.y, vec3.z);
    }

    @Override
    public boolean shouldShowName() {
        return this.isCustomNameVisible();
    }

    protected float getJumpPower() {
        return this.getJumpPower(1.0F);
    }

    protected float getJumpPower(float multiplier) {
        return (float)this.getAttributeValue(Attributes.JUMP_STRENGTH) * multiplier * this.getBlockJumpFactor() + this.getJumpBoostPower();
    }

    public float getJumpBoostPower() {
        return this.hasEffect(MobEffects.JUMP) ? 0.1F * (this.getEffect(MobEffects.JUMP).getAmplifier() + 1.0F) : 0.0F;
    }

    @VisibleForTesting
    public void jumpFromGround() {
        float f = this.getJumpPower();
        if (!(f <= 1.0E-5F)) {
            Vec3 vec3 = this.getDeltaMovement();
            this.setDeltaMovement(vec3.x, f, vec3.z);
            if (this.isSprinting()) {
                float g = this.getYRot() * (float) (Math.PI / 180.0);
                this.addDeltaMovement(new Vec3(-Mth.sin(g) * 0.2, 0.0, Mth.cos(g) * 0.2));
            }

            this.hasImpulse = true;
        }
    }

    protected void goDownInWater() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.04F, 0.0));
    }

    protected void jumpInLiquid(TagKey<Fluid> fluidTag) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.04F, 0.0));
    }

    protected float getWaterSlowDown() {
        return 0.8F;
    }

    public boolean canStandOnFluid(FluidState fluidState) {
        return false;
    }

    @Override
    protected double getDefaultGravity() {
        return this.getAttributeValue(Attributes.GRAVITY);
    }

    public void travel(Vec3 travelVector) {
        if (this.isControlledByLocalInstance()) {
            double d = this.getGravity();
            boolean bl = this.getDeltaMovement().y <= 0.0;
            if (bl && this.hasEffect(MobEffects.SLOW_FALLING)) {
                d = Math.min(d, 0.01);
            }

            FluidState fluidState = this.level().getFluidState(this.blockPosition());
            if (this.isInWater() && this.isAffectedByFluids() && !this.canStandOnFluid(fluidState)) {
                double e = this.getY();
                float f = this.isSprinting() ? 0.9F : this.getWaterSlowDown();
                float g = 0.02F;
                float h = (float)this.getAttributeValue(Attributes.WATER_MOVEMENT_EFFICIENCY);
                if (!this.onGround()) {
                    h *= 0.5F;
                }

                if (h > 0.0F) {
                    f += (0.54600006F - f) * h;
                    g += (this.getSpeed() - g) * h;
                }

                if (this.hasEffect(MobEffects.DOLPHINS_GRACE)) {
                    f = 0.96F;
                }

                this.moveRelative(g, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                Vec3 vec3 = this.getDeltaMovement();
                if (this.horizontalCollision && this.onClimbable()) {
                    vec3 = new Vec3(vec3.x, 0.2, vec3.z);
                }

                this.setDeltaMovement(vec3.multiply(f, 0.8F, f));
                Vec3 vec32 = this.getFluidFallingAdjustedMovement(d, bl, this.getDeltaMovement());
                this.setDeltaMovement(vec32);
                if (this.horizontalCollision && this.isFree(vec32.x, vec32.y + 0.6F - this.getY() + e, vec32.z)) {
                    this.setDeltaMovement(vec32.x, 0.3F, vec32.z);
                }
            } else if (this.isInLava() && this.isAffectedByFluids() && !this.canStandOnFluid(fluidState)) {
                double e = this.getY();
                this.moveRelative(0.02F, travelVector);
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.getFluidHeight(FluidTags.LAVA) <= this.getFluidJumpThreshold()) {
                    this.setDeltaMovement(this.getDeltaMovement().multiply(0.5, 0.8F, 0.5));
                    Vec3 vec33 = this.getFluidFallingAdjustedMovement(d, bl, this.getDeltaMovement());
                    this.setDeltaMovement(vec33);
                } else {
                    this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
                }

                if (d != 0.0) {
                    this.setDeltaMovement(this.getDeltaMovement().add(0.0, -d / 4.0, 0.0));
                }

                Vec3 vec33 = this.getDeltaMovement();
                if (this.horizontalCollision && this.isFree(vec33.x, vec33.y + 0.6F - this.getY() + e, vec33.z)) {
                    this.setDeltaMovement(vec33.x, 0.3F, vec33.z);
                }
            } else if (this.isFallFlying()) {
                this.checkSlowFallDistance();
                Vec3 vec34 = this.getDeltaMovement();
                Vec3 vec35 = this.getLookAngle();
                float f = this.getXRot() * (float) (Math.PI / 180.0);
                double i = Math.sqrt(vec35.x * vec35.x + vec35.z * vec35.z);
                double j = vec34.horizontalDistance();
                double k = vec35.length();
                double l = Math.cos(f);
                l = l * l * Math.min(1.0, k / 0.4);
                vec34 = this.getDeltaMovement().add(0.0, d * (-1.0 + l * 0.75), 0.0);
                if (vec34.y < 0.0 && i > 0.0) {
                    double m = vec34.y * -0.1 * l;
                    vec34 = vec34.add(vec35.x * m / i, m, vec35.z * m / i);
                }

                if (f < 0.0F && i > 0.0) {
                    double m = j * -Mth.sin(f) * 0.04;
                    vec34 = vec34.add(-vec35.x * m / i, m * 3.2, -vec35.z * m / i);
                }

                if (i > 0.0) {
                    vec34 = vec34.add((vec35.x / i * j - vec34.x) * 0.1, 0.0, (vec35.z / i * j - vec34.z) * 0.1);
                }

                this.setDeltaMovement(vec34.multiply(0.99F, 0.98F, 0.99F));
                this.move(MoverType.SELF, this.getDeltaMovement());
                if (this.horizontalCollision && !this.level().isClientSide) {
                    double m = this.getDeltaMovement().horizontalDistance();
                    double n = j - m;
                    float o = (float)(n * 10.0 - 3.0);
                    if (o > 0.0F) {
                        this.playSound(this.getFallDamageSound((int)o), 1.0F, 1.0F);
                        this.hurt(this.damageSources().flyIntoWall(), o);
                    }
                }

                if (this.onGround() && !this.level().isClientSide) {
                    this.setSharedFlag(7, false);
                }
            } else {
                BlockPos blockPos = this.getBlockPosBelowThatAffectsMyMovement();
                float p = this.level().getBlockState(blockPos).getBlock().getFriction();
                float f = this.onGround() ? p * 0.91F : 0.91F;
                Vec3 vec36 = this.handleRelativeFrictionAndCalculateMovement(travelVector, p);
                double q = vec36.y;
                if (this.hasEffect(MobEffects.LEVITATION)) {
                    q += (0.05 * (this.getEffect(MobEffects.LEVITATION).getAmplifier() + 1) - vec36.y) * 0.2;
                } else if (!this.level().isClientSide || this.level().hasChunkAt(blockPos)) {
                    q -= d;
                } else if (this.getY() > this.level().getMinBuildHeight()) {
                    q = -0.1;
                } else {
                    q = 0.0;
                }

                if (this.shouldDiscardFriction()) {
                    this.setDeltaMovement(vec36.x, q, vec36.z);
                } else {
                    this.setDeltaMovement(vec36.x * f, this instanceof FlyingAnimal ? q * f : q * 0.98F, vec36.z * f);
                }
            }
        }

        this.calculateEntityAnimation(this instanceof FlyingAnimal);
    }

    private void travelRidden(Player player, Vec3 travelVector) {
        Vec3 vec3 = this.getRiddenInput(player, travelVector);
        this.tickRidden(player, vec3);
        if (this.isControlledByLocalInstance()) {
            this.setSpeed(this.getRiddenSpeed(player));
            this.travel(vec3);
        } else {
            this.calculateEntityAnimation(false);
            this.setDeltaMovement(Vec3.ZERO);
            this.tryCheckInsideBlocks();
        }
    }

    protected void tickRidden(Player player, Vec3 travelVector) {
    }

    protected Vec3 getRiddenInput(Player player, Vec3 travelVector) {
        return travelVector;
    }

    protected float getRiddenSpeed(Player player) {
        return this.getSpeed();
    }

    public void calculateEntityAnimation(boolean includeHeight) {
        float f = (float)Mth.length(this.getX() - this.xo, includeHeight ? this.getY() - this.yo : 0.0, this.getZ() - this.zo);
        this.updateWalkAnimation(f);
    }

    protected void updateWalkAnimation(float partialTick) {
        float f = Math.min(partialTick * 4.0F, 1.0F);
        this.walkAnimation.update(f, 0.4F);
    }

    public Vec3 handleRelativeFrictionAndCalculateMovement(Vec3 deltaMovement, float friction) {
        this.moveRelative(this.getFrictionInfluencedSpeed(friction), deltaMovement);
        this.setDeltaMovement(this.handleOnClimbable(this.getDeltaMovement()));
        this.move(MoverType.SELF, this.getDeltaMovement());
        Vec3 vec3 = this.getDeltaMovement();
        if ((this.horizontalCollision || this.jumping)
            && (this.onClimbable() || this.getInBlockState().is(Blocks.POWDER_SNOW) && PowderSnowBlock.canEntityWalkOnPowderSnow(this))) {
            vec3 = new Vec3(vec3.x, 0.2, vec3.z);
        }

        return vec3;
    }

    public Vec3 getFluidFallingAdjustedMovement(double gravity, boolean isFalling, Vec3 deltaMovement) {
        if (gravity != 0.0 && !this.isSprinting()) {
            double d;
            if (isFalling && Math.abs(deltaMovement.y - 0.005) >= 0.003 && Math.abs(deltaMovement.y - gravity / 16.0) < 0.003) {
                d = -0.003;
            } else {
                d = deltaMovement.y - gravity / 16.0;
            }

            return new Vec3(deltaMovement.x, d, deltaMovement.z);
        } else {
            return deltaMovement;
        }
    }

    private Vec3 handleOnClimbable(Vec3 deltaMovement) {
        if (this.onClimbable()) {
            this.resetFallDistance();
            float f = 0.15F;
            double d = Mth.clamp(deltaMovement.x, -0.15F, 0.15F);
            double e = Mth.clamp(deltaMovement.z, -0.15F, 0.15F);
            double g = Math.max(deltaMovement.y, -0.15F);
            if (g < 0.0 && !this.getInBlockState().is(Blocks.SCAFFOLDING) && this.isSuppressingSlidingDownLadder() && this instanceof Player) {
                g = 0.0;
            }

            deltaMovement = new Vec3(d, g, e);
        }

        return deltaMovement;
    }

    private float getFrictionInfluencedSpeed(float friction) {
        return this.onGround() ? this.getSpeed() * (0.21600002F / (friction * friction * friction)) : this.getFlyingSpeed();
    }

    protected float getFlyingSpeed() {
        return this.getControllingPassenger() instanceof Player ? this.getSpeed() * 0.1F : 0.02F;
    }

    public float getSpeed() {
        return this.speed;
    }

    /**
     * Sets the movespeed used for the new AI system.
     */
    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public boolean doHurtTarget(Entity target) {
        this.setLastHurtMob(target);
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        this.updatingUsingItem();
        this.updateSwimAmount();
        if (!this.level().isClientSide) {
            int i = this.getArrowCount();
            if (i > 0) {
                if (this.removeArrowTime <= 0) {
                    this.removeArrowTime = 20 * (30 - i);
                }

                this.removeArrowTime--;
                if (this.removeArrowTime <= 0) {
                    this.setArrowCount(i - 1);
                }
            }

            int j = this.getStingerCount();
            if (j > 0) {
                if (this.removeStingerTime <= 0) {
                    this.removeStingerTime = 20 * (30 - j);
                }

                this.removeStingerTime--;
                if (this.removeStingerTime <= 0) {
                    this.setStingerCount(j - 1);
                }
            }

            this.detectEquipmentUpdates();
            if (this.tickCount % 20 == 0) {
                this.getCombatTracker().recheckStatus();
            }

            if (this.isSleeping() && !this.checkBedExists()) {
                this.stopSleeping();
            }
        }

        if (!this.isRemoved()) {
            this.aiStep();
        }

        double d = this.getX() - this.xo;
        double e = this.getZ() - this.zo;
        float f = (float)(d * d + e * e);
        float g = this.yBodyRot;
        float h = 0.0F;
        this.oRun = this.run;
        float k = 0.0F;
        if (f > 0.0025000002F) {
            k = 1.0F;
            h = (float)Math.sqrt(f) * 3.0F;
            float l = (float)Mth.atan2(e, d) * (180.0F / (float)Math.PI) - 90.0F;
            float m = Mth.abs(Mth.wrapDegrees(this.getYRot()) - l);
            if (95.0F < m && m < 265.0F) {
                g = l - 180.0F;
            } else {
                g = l;
            }
        }

        if (this.attackAnim > 0.0F) {
            g = this.getYRot();
        }

        if (!this.onGround()) {
            k = 0.0F;
        }

        this.run = this.run + (k - this.run) * 0.3F;
        this.level().getProfiler().push("headTurn");
        h = this.tickHeadTurn(g, h);
        this.level().getProfiler().pop();
        this.level().getProfiler().push("rangeChecks");

        while (this.getYRot() - this.yRotO < -180.0F) {
            this.yRotO -= 360.0F;
        }

        while (this.getYRot() - this.yRotO >= 180.0F) {
            this.yRotO += 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO < -180.0F) {
            this.yBodyRotO -= 360.0F;
        }

        while (this.yBodyRot - this.yBodyRotO >= 180.0F) {
            this.yBodyRotO += 360.0F;
        }

        while (this.getXRot() - this.xRotO < -180.0F) {
            this.xRotO -= 360.0F;
        }

        while (this.getXRot() - this.xRotO >= 180.0F) {
            this.xRotO += 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO < -180.0F) {
            this.yHeadRotO -= 360.0F;
        }

        while (this.yHeadRot - this.yHeadRotO >= 180.0F) {
            this.yHeadRotO += 360.0F;
        }

        this.level().getProfiler().pop();
        this.animStep += h;
        if (this.isFallFlying()) {
            this.fallFlyTicks++;
        } else {
            this.fallFlyTicks = 0;
        }

        if (this.isSleeping()) {
            this.setXRot(0.0F);
        }

        this.refreshDirtyAttributes();
        float l = this.getScale();
        if (l != this.appliedScale) {
            this.appliedScale = l;
            this.refreshDimensions();
        }
    }

    private void detectEquipmentUpdates() {
        Map<EquipmentSlot, ItemStack> map = this.collectEquipmentChanges();
        if (map != null) {
            this.handleHandSwap(map);
            if (!map.isEmpty()) {
                this.handleEquipmentChanges(map);
            }
        }
    }

    @Nullable
    private Map<EquipmentSlot, ItemStack> collectEquipmentChanges() {
        Map<EquipmentSlot, ItemStack> map = null;

        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            ItemStack itemStack = switch (equipmentSlot.getType()) {
                case HAND -> this.getLastHandItem(equipmentSlot);
                case HUMANOID_ARMOR -> this.getLastArmorItem(equipmentSlot);
                case ANIMAL_ARMOR -> this.lastBodyItemStack;
            };
            ItemStack itemStack2 = this.getItemBySlot(equipmentSlot);
            if (this.equipmentHasChanged(itemStack, itemStack2)) {
                if (map == null) {
                    map = Maps.newEnumMap(EquipmentSlot.class);
                }

                map.put(equipmentSlot, itemStack2);
                AttributeMap attributeMap = this.getAttributes();
                if (!itemStack.isEmpty()) {
                    itemStack.forEachModifier(equipmentSlot, (holder, attributeModifier) -> {
                        AttributeInstance attributeInstance = attributeMap.getInstance(holder);
                        if (attributeInstance != null) {
                            attributeInstance.removeModifier(attributeModifier);
                        }

                        EnchantmentHelper.stopLocationBasedEffects(itemStack, this, equipmentSlot);
                    });
                }
            }
        }

        if (map != null) {
            for (Entry<EquipmentSlot, ItemStack> entry : map.entrySet()) {
                EquipmentSlot equipmentSlot2 = entry.getKey();
                ItemStack itemStack3 = entry.getValue();
                if (!itemStack3.isEmpty()) {
                    itemStack3.forEachModifier(equipmentSlot2, (holder, attributeModifier) -> {
                        AttributeInstance attributeInstance = this.attributes.getInstance(holder);
                        if (attributeInstance != null) {
                            attributeInstance.removeModifier(attributeModifier.id());
                            attributeInstance.addTransientModifier(attributeModifier);
                        }

                        if (this.level() instanceof ServerLevel serverLevel) {
                            EnchantmentHelper.runLocationChangedEffects(serverLevel, itemStack3, this, equipmentSlot2);
                        }
                    });
                }
            }
        }

        return map;
    }

    public boolean equipmentHasChanged(ItemStack oldItem, ItemStack newItem) {
        return !ItemStack.matches(newItem, oldItem);
    }

    private void handleHandSwap(Map<EquipmentSlot, ItemStack> hands) {
        ItemStack itemStack = hands.get(EquipmentSlot.MAINHAND);
        ItemStack itemStack2 = hands.get(EquipmentSlot.OFFHAND);
        if (itemStack != null
            && itemStack2 != null
            && ItemStack.matches(itemStack, this.getLastHandItem(EquipmentSlot.OFFHAND))
            && ItemStack.matches(itemStack2, this.getLastHandItem(EquipmentSlot.MAINHAND))) {
            ((ServerLevel)this.level()).getChunkSource().broadcast(this, new ClientboundEntityEventPacket(this, (byte)55));
            hands.remove(EquipmentSlot.MAINHAND);
            hands.remove(EquipmentSlot.OFFHAND);
            this.setLastHandItem(EquipmentSlot.MAINHAND, itemStack.copy());
            this.setLastHandItem(EquipmentSlot.OFFHAND, itemStack2.copy());
        }
    }

    private void handleEquipmentChanges(Map<EquipmentSlot, ItemStack> equipments) {
        List<Pair<EquipmentSlot, ItemStack>> list = Lists.newArrayListWithCapacity(equipments.size());
        equipments.forEach((equipmentSlot, itemStack) -> {
            ItemStack itemStack2 = itemStack.copy();
            list.add(Pair.of(equipmentSlot, itemStack2));
            switch (equipmentSlot.getType()) {
                case HAND:
                    this.setLastHandItem(equipmentSlot, itemStack2);
                    break;
                case HUMANOID_ARMOR:
                    this.setLastArmorItem(equipmentSlot, itemStack2);
                    break;
                case ANIMAL_ARMOR:
                    this.lastBodyItemStack = itemStack2;
            }
        });
        ((ServerLevel)this.level()).getChunkSource().broadcast(this, new ClientboundSetEquipmentPacket(this.getId(), list));
    }

    private ItemStack getLastArmorItem(EquipmentSlot slot) {
        return this.lastArmorItemStacks.get(slot.getIndex());
    }

    private void setLastArmorItem(EquipmentSlot slot, ItemStack stack) {
        this.lastArmorItemStacks.set(slot.getIndex(), stack);
    }

    private ItemStack getLastHandItem(EquipmentSlot slot) {
        return this.lastHandItemStacks.get(slot.getIndex());
    }

    private void setLastHandItem(EquipmentSlot slot, ItemStack stack) {
        this.lastHandItemStacks.set(slot.getIndex(), stack);
    }

    protected float tickHeadTurn(float yRot, float animStep) {
        float f = Mth.wrapDegrees(yRot - this.yBodyRot);
        this.yBodyRot += f * 0.3F;
        float g = Mth.wrapDegrees(this.getYRot() - this.yBodyRot);
        float h = this.getMaxHeadRotationRelativeToBody();
        if (Math.abs(g) > h) {
            this.yBodyRot = this.yBodyRot + (g - Mth.sign(g) * h);
        }

        boolean bl = g < -90.0F || g >= 90.0F;
        if (bl) {
            animStep *= -1.0F;
        }

        return animStep;
    }

    protected float getMaxHeadRotationRelativeToBody() {
        return 50.0F;
    }

    public void aiStep() {
        if (this.noJumpDelay > 0) {
            this.noJumpDelay--;
        }

        if (this.isControlledByLocalInstance()) {
            this.lerpSteps = 0;
            this.syncPacketPositionCodec(this.getX(), this.getY(), this.getZ());
        }

        if (this.lerpSteps > 0) {
            this.lerpPositionAndRotationStep(this.lerpSteps, this.lerpX, this.lerpY, this.lerpZ, this.lerpYRot, this.lerpXRot);
            this.lerpSteps--;
        } else if (!this.isEffectiveAi()) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
        }

        if (this.lerpHeadSteps > 0) {
            this.lerpHeadRotationStep(this.lerpHeadSteps, this.lerpYHeadRot);
            this.lerpHeadSteps--;
        }

        Vec3 vec3 = this.getDeltaMovement();
        double d = vec3.x;
        double e = vec3.y;
        double f = vec3.z;
        if (Math.abs(vec3.x) < 0.003) {
            d = 0.0;
        }

        if (Math.abs(vec3.y) < 0.003) {
            e = 0.0;
        }

        if (Math.abs(vec3.z) < 0.003) {
            f = 0.0;
        }

        this.setDeltaMovement(d, e, f);
        this.level().getProfiler().push("ai");
        if (this.isImmobile()) {
            this.jumping = false;
            this.xxa = 0.0F;
            this.zza = 0.0F;
        } else if (this.isEffectiveAi()) {
            this.level().getProfiler().push("newAi");
            this.serverAiStep();
            this.level().getProfiler().pop();
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("jump");
        if (this.jumping && this.isAffectedByFluids()) {
            double g;
            if (this.isInLava()) {
                g = this.getFluidHeight(FluidTags.LAVA);
            } else {
                g = this.getFluidHeight(FluidTags.WATER);
            }

            boolean bl = this.isInWater() && g > 0.0;
            double h = this.getFluidJumpThreshold();
            if (!bl || this.onGround() && !(g > h)) {
                if (!this.isInLava() || this.onGround() && !(g > h)) {
                    if ((this.onGround() || bl && g <= h) && this.noJumpDelay == 0) {
                        this.jumpFromGround();
                        this.noJumpDelay = 10;
                    }
                } else {
                    this.jumpInLiquid(FluidTags.LAVA);
                }
            } else {
                this.jumpInLiquid(FluidTags.WATER);
            }
        } else {
            this.noJumpDelay = 0;
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("travel");
        this.xxa *= 0.98F;
        this.zza *= 0.98F;
        this.updateFallFlying();
        AABB aABB = this.getBoundingBox();
        Vec3 vec32 = new Vec3(this.xxa, this.yya, this.zza);
        if (this.hasEffect(MobEffects.SLOW_FALLING) || this.hasEffect(MobEffects.LEVITATION)) {
            this.resetFallDistance();
        }

        if (this.getControllingPassenger() instanceof Player player && this.isAlive()) {
            this.travelRidden(player, vec32);
        } else {
            this.travel(vec32);
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("freezing");
        if (!this.level().isClientSide && !this.isDeadOrDying()) {
            int i = this.getTicksFrozen();
            if (this.isInPowderSnow && this.canFreeze()) {
                this.setTicksFrozen(Math.min(this.getTicksRequiredToFreeze(), i + 1));
            } else {
                this.setTicksFrozen(Math.max(0, i - 2));
            }
        }

        this.removeFrost();
        this.tryAddFrost();
        if (!this.level().isClientSide && this.tickCount % 40 == 0 && this.isFullyFrozen() && this.canFreeze()) {
            this.hurt(this.damageSources().freeze(), 1.0F);
        }

        this.level().getProfiler().pop();
        this.level().getProfiler().push("push");
        if (this.autoSpinAttackTicks > 0) {
            this.autoSpinAttackTicks--;
            this.checkAutoSpinAttack(aABB, this.getBoundingBox());
        }

        this.pushEntities();
        this.level().getProfiler().pop();
        if (!this.level().isClientSide && this.isSensitiveToWater() && this.isInWaterRainOrBubble()) {
            this.hurt(this.damageSources().drown(), 1.0F);
        }
    }

    public boolean isSensitiveToWater() {
        return false;
    }

    private void updateFallFlying() {
        boolean bl = this.getSharedFlag(7);
        if (bl && !this.onGround() && !this.isPassenger() && !this.hasEffect(MobEffects.LEVITATION)) {
            ItemStack itemStack = this.getItemBySlot(EquipmentSlot.CHEST);
            if (itemStack.is(Items.ELYTRA) && ElytraItem.isFlyEnabled(itemStack)) {
                bl = true;
                int i = this.fallFlyTicks + 1;
                if (!this.level().isClientSide && i % 10 == 0) {
                    int j = i / 10;
                    if (j % 2 == 0) {
                        itemStack.hurtAndBreak(1, this, EquipmentSlot.CHEST);
                    }

                    this.gameEvent(GameEvent.ELYTRA_GLIDE);
                }
            } else {
                bl = false;
            }
        } else {
            bl = false;
        }

        if (!this.level().isClientSide) {
            this.setSharedFlag(7, bl);
        }
    }

    protected void serverAiStep() {
    }

    protected void pushEntities() {
        if (this.level().isClientSide()) {
            this.level().getEntities(EntityTypeTest.forClass(Player.class), this.getBoundingBox(), EntitySelector.pushableBy(this)).forEach(this::doPush);
        } else {
            List<Entity> list = this.level().getEntities(this, this.getBoundingBox(), EntitySelector.pushableBy(this));
            if (!list.isEmpty()) {
                int i = this.level().getGameRules().getInt(GameRules.RULE_MAX_ENTITY_CRAMMING);
                if (i > 0 && list.size() > i - 1 && this.random.nextInt(4) == 0) {
                    int j = 0;

                    for (Entity entity : list) {
                        if (!entity.isPassenger()) {
                            j++;
                        }
                    }

                    if (j > i - 1) {
                        this.hurt(this.damageSources().cramming(), 6.0F);
                    }
                }

                for (Entity entity2 : list) {
                    this.doPush(entity2);
                }
            }
        }
    }

    protected void checkAutoSpinAttack(AABB boundingBoxBeforeSpin, AABB boundingBoxAfterSpin) {
        AABB aABB = boundingBoxBeforeSpin.minmax(boundingBoxAfterSpin);
        List<Entity> list = this.level().getEntities(this, aABB);
        if (!list.isEmpty()) {
            for (Entity entity : list) {
                if (entity instanceof LivingEntity) {
                    this.doAutoAttackOnTouch((LivingEntity)entity);
                    this.autoSpinAttackTicks = 0;
                    this.setDeltaMovement(this.getDeltaMovement().scale(-0.2));
                    break;
                }
            }
        } else if (this.horizontalCollision) {
            this.autoSpinAttackTicks = 0;
        }

        if (!this.level().isClientSide && this.autoSpinAttackTicks <= 0) {
            this.setLivingEntityFlag(4, false);
            this.autoSpinAttackDmg = 0.0F;
            this.autoSpinAttackItemStack = null;
        }
    }

    protected void doPush(Entity entity) {
        entity.push(this);
    }

    protected void doAutoAttackOnTouch(LivingEntity target) {
    }

    public boolean isAutoSpinAttack() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 4) != 0;
    }

    @Override
    public void stopRiding() {
        Entity entity = this.getVehicle();
        super.stopRiding();
        if (entity != null && entity != this.getVehicle() && !this.level().isClientSide) {
            this.dismountVehicle(entity);
        }
    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.oRun = this.run;
        this.run = 0.0F;
        this.resetFallDistance();
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        this.lerpX = x;
        this.lerpY = y;
        this.lerpZ = z;
        this.lerpYRot = yRot;
        this.lerpXRot = xRot;
        this.lerpSteps = steps;
    }

    @Override
    public double lerpTargetX() {
        return this.lerpSteps > 0 ? this.lerpX : this.getX();
    }

    @Override
    public double lerpTargetY() {
        return this.lerpSteps > 0 ? this.lerpY : this.getY();
    }

    @Override
    public double lerpTargetZ() {
        return this.lerpSteps > 0 ? this.lerpZ : this.getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return this.lerpSteps > 0 ? (float)this.lerpXRot : this.getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return this.lerpSteps > 0 ? (float)this.lerpYRot : this.getYRot();
    }

    @Override
    public void lerpHeadTo(float yaw, int pitch) {
        this.lerpYHeadRot = yaw;
        this.lerpHeadSteps = pitch;
    }

    public void setJumping(boolean jumping) {
        this.jumping = jumping;
    }

    public void onItemPickup(ItemEntity itemEntity) {
        Entity entity = itemEntity.getOwner();
        if (entity instanceof ServerPlayer) {
            CriteriaTriggers.THROWN_ITEM_PICKED_UP_BY_ENTITY.trigger((ServerPlayer)entity, itemEntity.getItem(), this);
        }
    }

    /**
     * Called when the entity picks up an item.
     */
    public void take(Entity entity, int amount) {
        if (!entity.isRemoved()
            && !this.level().isClientSide
            && (entity instanceof ItemEntity || entity instanceof AbstractArrow || entity instanceof ExperienceOrb)) {
            ((ServerLevel)this.level()).getChunkSource().broadcast(entity, new ClientboundTakeItemEntityPacket(entity.getId(), this.getId(), amount));
        }
    }

    public boolean hasLineOfSight(Entity entity) {
        if (entity.level() != this.level()) {
            return false;
        }

        Vec3 vec3 = new Vec3(this.getX(), this.getEyeY(), this.getZ());
        Vec3 vec32 = new Vec3(entity.getX(), entity.getEyeY(), entity.getZ());
        return vec32.distanceTo(vec3) > 128.0
            ? false
            : this.level().clip(new ClipContext(vec3, vec32, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    /**
     * Gets the current yaw of the entity
     */
    @Override
    public float getViewYRot(float partialTicks) {
        return partialTicks == 1.0F ? this.yHeadRot : Mth.lerp(partialTicks, this.yHeadRotO, this.yHeadRot);
    }

    /**
     * Gets the progression of the swing animation, ranges from 0.0 to 1.0.
     */
    public float getAttackAnim(float partialTick) {
        float f = this.attackAnim - this.oAttackAnim;
        if (f < 0.0F) {
            f++;
        }

        return this.oAttackAnim + f * partialTick;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isPushable() {
        return this.isAlive() && !this.isSpectator() && !this.onClimbable();
    }

    @Override
    public float getYHeadRot() {
        return this.yHeadRot;
    }

    /**
     * Sets the head's yaw rotation of the entity.
     */
    @Override
    public void setYHeadRot(float rotation) {
        this.yHeadRot = rotation;
    }

    /**
     * Set the render yaw offset
     */
    @Override
    public void setYBodyRot(float offset) {
        this.yBodyRot = offset;
    }

    @Override
    public Vec3 getRelativePortalPosition(Direction.Axis axis, BlockUtil.FoundRectangle portal) {
        return resetForwardDirectionOfRelativePortalPosition(super.getRelativePortalPosition(axis, portal));
    }

    public static Vec3 resetForwardDirectionOfRelativePortalPosition(Vec3 relativePortalPosition) {
        return new Vec3(relativePortalPosition.x, relativePortalPosition.y, 0.0);
    }

    public float getAbsorptionAmount() {
        return this.absorptionAmount;
    }

    public final void setAbsorptionAmount(float absorptionAmount) {
        this.internalSetAbsorptionAmount(Mth.clamp(absorptionAmount, 0.0F, this.getMaxAbsorption()));
    }

    protected void internalSetAbsorptionAmount(float absorptionAmount) {
        this.absorptionAmount = absorptionAmount;
    }

    public void onEnterCombat() {
    }

    public void onLeaveCombat() {
    }

    protected void updateEffectVisibility() {
        this.effectsDirty = true;
    }

    public abstract HumanoidArm getMainArm();

    public boolean isUsingItem() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
    }

    public InteractionHand getUsedItemHand() {
        return (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
    }

    private void updatingUsingItem() {
        if (this.isUsingItem()) {
            if (ItemStack.isSameItem(this.getItemInHand(this.getUsedItemHand()), this.useItem)) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                this.updateUsingItem(this.useItem);
            } else {
                this.stopUsingItem();
            }
        }
    }

    protected void updateUsingItem(ItemStack usingItem) {
        usingItem.onUseTick(this.level(), this, this.getUseItemRemainingTicks());
        if (this.shouldTriggerItemUseEffects()) {
            this.triggerItemUseEffects(usingItem, 5);
        }

        if (--this.useItemRemaining == 0 && !this.level().isClientSide && !usingItem.useOnRelease()) {
            this.completeUsingItem();
        }
    }

    private boolean shouldTriggerItemUseEffects() {
        int i = this.useItem.getUseDuration(this) - this.getUseItemRemainingTicks();
        int j = (int)(this.useItem.getUseDuration(this) * 0.21875F);
        boolean bl = i > j;
        return bl && this.getUseItemRemainingTicks() % 4 == 0;
    }

    private void updateSwimAmount() {
        this.swimAmountO = this.swimAmount;
        if (this.isVisuallySwimming()) {
            this.swimAmount = Math.min(1.0F, this.swimAmount + 0.09F);
        } else {
            this.swimAmount = Math.max(0.0F, this.swimAmount - 0.09F);
        }
    }

    protected void setLivingEntityFlag(int key, boolean value) {
        int i = this.entityData.get(DATA_LIVING_ENTITY_FLAGS);
        if (value) {
            i |= key;
        } else {
            i &= ~key;
        }

        this.entityData.set(DATA_LIVING_ENTITY_FLAGS, (byte)i);
    }

    public void startUsingItem(InteractionHand hand) {
        ItemStack itemStack = this.getItemInHand(hand);
        if (!itemStack.isEmpty() && !this.isUsingItem()) {
            this.useItem = itemStack;
            this.useItemRemaining = itemStack.getUseDuration(this);
            if (!this.level().isClientSide) {
                this.setLivingEntityFlag(1, true);
                this.setLivingEntityFlag(2, hand == InteractionHand.OFF_HAND);
                this.gameEvent(GameEvent.ITEM_INTERACT_START);
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (SLEEPING_POS_ID.equals(key)) {
            if (this.level().isClientSide) {
                this.getSleepingPos().ifPresent(this::setPosToBed);
            }
        } else if (DATA_LIVING_ENTITY_FLAGS.equals(key) && this.level().isClientSide) {
            if (this.isUsingItem() && this.useItem.isEmpty()) {
                this.useItem = this.getItemInHand(this.getUsedItemHand());
                if (!this.useItem.isEmpty()) {
                    this.useItemRemaining = this.useItem.getUseDuration(this);
                }
            } else if (!this.isUsingItem() && !this.useItem.isEmpty()) {
                this.useItem = ItemStack.EMPTY;
                this.useItemRemaining = 0;
            }
        }
    }

    @Override
    public void lookAt(EntityAnchorArgument.Anchor anchor, Vec3 target) {
        super.lookAt(anchor, target);
        this.yHeadRotO = this.yHeadRot;
        this.yBodyRot = this.yHeadRot;
        this.yBodyRotO = this.yBodyRot;
    }

    @Override
    public float getPreciseBodyRotation(float partialTick) {
        return Mth.lerp(partialTick, this.yBodyRotO, this.yBodyRot);
    }

    protected void triggerItemUseEffects(ItemStack stack, int amount) {
        if (!stack.isEmpty() && this.isUsingItem()) {
            if (stack.getUseAnimation() == UseAnim.DRINK) {
                this.playSound(this.getDrinkingSound(stack), 0.5F, this.level().random.nextFloat() * 0.1F + 0.9F);
            }

            if (stack.getUseAnimation() == UseAnim.EAT) {
                this.spawnItemParticles(stack, amount);
                this.playSound(
                    this.getEatingSound(stack), 0.5F + 0.5F * this.random.nextInt(2), (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F
                );
            }
        }
    }

    private void spawnItemParticles(ItemStack stack, int amount) {
        for (int i = 0; i < amount; i++) {
            Vec3 vec3 = new Vec3((this.random.nextFloat() - 0.5) * 0.1, Math.random() * 0.1 + 0.1, 0.0);
            vec3 = vec3.xRot(-this.getXRot() * (float) (Math.PI / 180.0));
            vec3 = vec3.yRot(-this.getYRot() * (float) (Math.PI / 180.0));
            double d = -this.random.nextFloat() * 0.6 - 0.3;
            Vec3 vec32 = new Vec3((this.random.nextFloat() - 0.5) * 0.3, d, 0.6);
            vec32 = vec32.xRot(-this.getXRot() * (float) (Math.PI / 180.0));
            vec32 = vec32.yRot(-this.getYRot() * (float) (Math.PI / 180.0));
            vec32 = vec32.add(this.getX(), this.getEyeY(), this.getZ());
            this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, stack), vec32.x, vec32.y, vec32.z, vec3.x, vec3.y + 0.05, vec3.z);
        }
    }

    protected void completeUsingItem() {
        if (!this.level().isClientSide || this.isUsingItem()) {
            InteractionHand interactionHand = this.getUsedItemHand();
            if (!this.useItem.equals(this.getItemInHand(interactionHand))) {
                this.releaseUsingItem();
            } else {
                if (!this.useItem.isEmpty() && this.isUsingItem()) {
                    this.triggerItemUseEffects(this.useItem, 16);
                    ItemStack itemStack = this.useItem.finishUsingItem(this.level(), this);
                    if (itemStack != this.useItem) {
                        this.setItemInHand(interactionHand, itemStack);
                    }

                    this.stopUsingItem();
                }
            }
        }
    }

    public ItemStack getUseItem() {
        return this.useItem;
    }

    public int getUseItemRemainingTicks() {
        return this.useItemRemaining;
    }

    public int getTicksUsingItem() {
        return this.isUsingItem() ? this.useItem.getUseDuration(this) - this.getUseItemRemainingTicks() : 0;
    }

    public void releaseUsingItem() {
        if (!this.useItem.isEmpty()) {
            this.useItem.releaseUsing(this.level(), this, this.getUseItemRemainingTicks());
            if (this.useItem.useOnRelease()) {
                this.updatingUsingItem();
            }
        }

        this.stopUsingItem();
    }

    public void stopUsingItem() {
        if (!this.level().isClientSide) {
            boolean bl = this.isUsingItem();
            this.setLivingEntityFlag(1, false);
            if (bl) {
                this.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
            }
        }

        this.useItem = ItemStack.EMPTY;
        this.useItemRemaining = 0;
    }

    public boolean isBlocking() {
        if (this.isUsingItem() && !this.useItem.isEmpty()) {
            Item item = this.useItem.getItem();
            return item.getUseAnimation(this.useItem) != UseAnim.BLOCK ? false : item.getUseDuration(this.useItem, this) - this.useItemRemaining >= 5;
        } else {
            return false;
        }
    }

    public boolean isSuppressingSlidingDownLadder() {
        return this.isShiftKeyDown();
    }

    public boolean isFallFlying() {
        return this.getSharedFlag(7);
    }

    @Override
    public boolean isVisuallySwimming() {
        return super.isVisuallySwimming() || !this.isFallFlying() && this.hasPose(Pose.FALL_FLYING);
    }

    public int getFallFlyingTicks() {
        return this.fallFlyTicks;
    }

    public boolean randomTeleport(double x, double y, double z, boolean broadcastTeleport) {
        double d = this.getX();
        double e = this.getY();
        double f = this.getZ();
        double g = y;
        boolean bl = false;
        BlockPos blockPos = BlockPos.containing(x, g, z);
        Level level = this.level();
        if (level.hasChunkAt(blockPos)) {
            boolean bl2 = false;

            while (!bl2 && blockPos.getY() > level.getMinBuildHeight()) {
                BlockPos blockPos2 = blockPos.below();
                BlockState blockState = level.getBlockState(blockPos2);
                if (blockState.blocksMotion()) {
                    bl2 = true;
                } else {
                    g--;
                    blockPos = blockPos2;
                }
            }

            if (bl2) {
                this.teleportTo(x, g, z);
                if (level.noCollision(this) && !level.containsAnyLiquid(this.getBoundingBox())) {
                    bl = true;
                }
            }
        }

        if (!bl) {
            this.teleportTo(d, e, f);
            return false;
        }

        if (broadcastTeleport) {
            level.broadcastEntityEvent(this, (byte)46);
        }

        if (this instanceof PathfinderMob pathfinderMob) {
            pathfinderMob.getNavigation().stop();
        }

        return true;
    }

    public boolean isAffectedByPotions() {
        return !this.isDeadOrDying();
    }

    public boolean attackable() {
        return true;
    }

    /**
     * Called when a record starts or stops playing. Used to make parrots start or stop partying.
     */
    public void setRecordPlayingNearby(BlockPos jukebox, boolean partyParrot) {
    }

    public boolean canTakeItem(ItemStack stack) {
        return false;
    }

    @Override
    public final EntityDimensions getDimensions(Pose pose) {
        return pose == Pose.SLEEPING ? SLEEPING_DIMENSIONS : this.getDefaultDimensions(pose).scale(this.getScale());
    }

    protected EntityDimensions getDefaultDimensions(Pose pose) {
        return this.getType().getDimensions().scale(this.getAgeScale());
    }

    public ImmutableList<Pose> getDismountPoses() {
        return ImmutableList.of(Pose.STANDING);
    }

    public AABB getLocalBoundsForPose(Pose pose) {
        EntityDimensions entityDimensions = this.getDimensions(pose);
        return new AABB(
            -entityDimensions.width() / 2.0F,
            0.0,
            -entityDimensions.width() / 2.0F,
            entityDimensions.width() / 2.0F,
            entityDimensions.height(),
            entityDimensions.width() / 2.0F
        );
    }

    protected boolean wouldNotSuffocateAtTargetPose(Pose pose) {
        AABB aABB = this.getDimensions(pose).makeBoundingBox(this.position());
        return this.level().noBlockCollision(this, aABB);
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return super.canUsePortal(allowPassengers) && !this.isSleeping();
    }

    public Optional<BlockPos> getSleepingPos() {
        return this.entityData.get(SLEEPING_POS_ID);
    }

    public void setSleepingPos(BlockPos pos) {
        this.entityData.set(SLEEPING_POS_ID, Optional.of(pos));
    }

    public void clearSleepingPos() {
        this.entityData.set(SLEEPING_POS_ID, Optional.empty());
    }

    public boolean isSleeping() {
        return this.getSleepingPos().isPresent();
    }

    public void startSleeping(BlockPos pos) {
        if (this.isPassenger()) {
            this.stopRiding();
        }

        BlockState blockState = this.level().getBlockState(pos);
        if (blockState.getBlock() instanceof BedBlock) {
            this.level().setBlock(pos, blockState.setValue(BedBlock.OCCUPIED, true), 3);
        }

        this.setPose(Pose.SLEEPING);
        this.setPosToBed(pos);
        this.setSleepingPos(pos);
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;
    }

    /**
     * Sets entity position to a supplied BlockPos plus a little offset
     */
    private void setPosToBed(BlockPos pos) {
        this.setPos(pos.getX() + 0.5, pos.getY() + 0.6875, pos.getZ() + 0.5);
    }

    private boolean checkBedExists() {
        return this.getSleepingPos().map(pos -> this.level().getBlockState(pos).getBlock() instanceof BedBlock).orElse(false);
    }

    public void stopSleeping() {
        this.getSleepingPos().filter(this.level()::hasChunkAt).ifPresent(pos -> {
            BlockState blockState = this.level().getBlockState(pos);
            if (blockState.getBlock() instanceof BedBlock) {
                Direction direction = blockState.getValue(BedBlock.FACING);
                this.level().setBlock(pos, blockState.setValue(BedBlock.OCCUPIED, false), 3);
                Vec3 vec3x = BedBlock.findStandUpPosition(this.getType(), this.level(), pos, direction, this.getYRot()).orElseGet(() -> {
                    BlockPos blockPos2 = pos.above();
                    return new Vec3(blockPos2.getX() + 0.5, blockPos2.getY() + 0.1, blockPos2.getZ() + 0.5);
                });
                Vec3 vec32 = Vec3.atBottomCenterOf(pos).subtract(vec3x).normalize();
                float f = (float)Mth.wrapDegrees(Mth.atan2(vec32.z, vec32.x) * 180.0F / (float)Math.PI - 90.0);
                this.setPos(vec3x.x, vec3x.y, vec3x.z);
                this.setYRot(f);
                this.setXRot(0.0F);
            }
        });
        Vec3 vec3 = this.position();
        this.setPose(Pose.STANDING);
        this.setPos(vec3.x, vec3.y, vec3.z);
        this.clearSleepingPos();
    }

    @Nullable
    public Direction getBedOrientation() {
        BlockPos blockPos = this.getSleepingPos().orElse(null);
        return blockPos != null ? BedBlock.getBedOrientation(this.level(), blockPos) : null;
    }

    @Override
    public boolean isInWall() {
        return !this.isSleeping() && super.isInWall();
    }

    /**
     * Gets an item stack available to this entity to be loaded into the provided weapon, or an empty item stack if no such item stack is available.
     */
    public ItemStack getProjectile(ItemStack weaponStack) {
        return ItemStack.EMPTY;
    }

    public final ItemStack eat(Level level, ItemStack food) {
        FoodProperties foodProperties = food.get(DataComponents.FOOD);
        return foodProperties != null ? this.eat(level, food, foodProperties) : food;
    }

    public ItemStack eat(Level level, ItemStack food, FoodProperties foodProperties) {
        level.playSound(
            null,
            this.getX(),
            this.getY(),
            this.getZ(),
            this.getEatingSound(food),
            SoundSource.NEUTRAL,
            1.0F,
            1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F
        );
        this.addEatEffect(foodProperties);
        food.consume(1, this);
        this.gameEvent(GameEvent.EAT);
        return food;
    }

    private void addEatEffect(FoodProperties foodProperties) {
        if (!this.level().isClientSide()) {
            for (FoodProperties.PossibleEffect possibleEffect : foodProperties.effects()) {
                if (this.random.nextFloat() < possibleEffect.probability()) {
                    this.addEffect(possibleEffect.effect());
                }
            }
        }
    }

    private static byte entityEventForEquipmentBreak(EquipmentSlot slot) {
        return switch (slot) {
            case MAINHAND -> 47;
            case OFFHAND -> 48;
            case HEAD -> 49;
            case CHEST -> 50;
            case FEET -> 52;
            case LEGS -> 51;
            case BODY -> 65;
        };
    }

    public void onEquippedItemBroken(Item item, EquipmentSlot slot) {
        this.level().broadcastEntityEvent(this, entityEventForEquipmentBreak(slot));
    }

    public static EquipmentSlot getSlotForHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        if (this.getItemBySlot(EquipmentSlot.HEAD).is(Items.DRAGON_HEAD)) {
            float f = 0.5F;
            return this.getBoundingBox().inflate(0.5, 0.5, 0.5);
        } else {
            return super.getBoundingBoxForCulling();
        }
    }

    public EquipmentSlot getEquipmentSlotForItem(ItemStack stack) {
        Equipable equipable = Equipable.get(stack);
        if (equipable != null) {
            EquipmentSlot equipmentSlot = equipable.getEquipmentSlot();
            if (this.canUseSlot(equipmentSlot)) {
                return equipmentSlot;
            }
        }

        return EquipmentSlot.MAINHAND;
    }

    private static SlotAccess createEquipmentSlotAccess(LivingEntity entity, EquipmentSlot slot) {
        return slot != EquipmentSlot.HEAD && slot != EquipmentSlot.MAINHAND && slot != EquipmentSlot.OFFHAND
            ? SlotAccess.forEquipmentSlot(entity, slot, stack -> stack.isEmpty() || entity.getEquipmentSlotForItem(stack) == slot)
            : SlotAccess.forEquipmentSlot(entity, slot);
    }

    @Nullable
    private static EquipmentSlot getEquipmentSlot(int index) {
        if (index == 100 + EquipmentSlot.HEAD.getIndex()) {
            return EquipmentSlot.HEAD;
        } else if (index == 100 + EquipmentSlot.CHEST.getIndex()) {
            return EquipmentSlot.CHEST;
        } else if (index == 100 + EquipmentSlot.LEGS.getIndex()) {
            return EquipmentSlot.LEGS;
        } else if (index == 100 + EquipmentSlot.FEET.getIndex()) {
            return EquipmentSlot.FEET;
        } else if (index == 98) {
            return EquipmentSlot.MAINHAND;
        } else if (index == 99) {
            return EquipmentSlot.OFFHAND;
        } else {
            return index == 105 ? EquipmentSlot.BODY : null;
        }
    }

    @Override
    public SlotAccess getSlot(int slot) {
        EquipmentSlot equipmentSlot = getEquipmentSlot(slot);
        return equipmentSlot != null ? createEquipmentSlotAccess(this, equipmentSlot) : super.getSlot(slot);
    }

    @Override
    public boolean canFreeze() {
        if (this.isSpectator()) {
            return false;
        }

        boolean bl = !this.getItemBySlot(EquipmentSlot.HEAD).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
            && !this.getItemBySlot(EquipmentSlot.CHEST).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
            && !this.getItemBySlot(EquipmentSlot.LEGS).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
            && !this.getItemBySlot(EquipmentSlot.FEET).is(ItemTags.FREEZE_IMMUNE_WEARABLES)
            && !this.getItemBySlot(EquipmentSlot.BODY).is(ItemTags.FREEZE_IMMUNE_WEARABLES);
        return bl && super.canFreeze();
    }

    @Override
    public boolean isCurrentlyGlowing() {
        return !this.level().isClientSide() && this.hasEffect(MobEffects.GLOWING) || super.isCurrentlyGlowing();
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return this.yBodyRot;
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        double d = packet.getX();
        double e = packet.getY();
        double f = packet.getZ();
        float g = packet.getYRot();
        float h = packet.getXRot();
        this.syncPacketPositionCodec(d, e, f);
        this.yBodyRot = packet.getYHeadRot();
        this.yHeadRot = packet.getYHeadRot();
        this.yBodyRotO = this.yBodyRot;
        this.yHeadRotO = this.yHeadRot;
        this.setId(packet.getId());
        this.setUUID(packet.getUUID());
        this.absMoveTo(d, e, f, g, h);
        this.setDeltaMovement(packet.getXa(), packet.getYa(), packet.getZa());
    }

    public boolean canDisableShield() {
        return this.getWeaponItem().getItem() instanceof AxeItem;
    }

    @Override
    public float maxUpStep() {
        float f = (float)this.getAttributeValue(Attributes.STEP_HEIGHT);
        return this.getControllingPassenger() instanceof Player ? Math.max(f, 1.0F) : f;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity entity) {
        return this.position().add(this.getPassengerAttachmentPoint(entity, this.getDimensions(this.getPose()), this.getScale() * this.getAgeScale()));
    }

    protected void lerpHeadRotationStep(int lerpHeadSteps, double lerpYHeadRot) {
        this.yHeadRot = (float)Mth.rotLerp(1.0 / lerpHeadSteps, this.yHeadRot, lerpYHeadRot);
    }

    @Override
    public void igniteForTicks(int ticks) {
        super.igniteForTicks(Mth.ceil(ticks * this.getAttributeValue(Attributes.BURNING_TIME)));
    }

    public boolean hasInfiniteMaterials() {
        return false;
    }

    /**
     * Returns whether this Entity is invulnerable to the given DamageSource.
     */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return super.isInvulnerableTo(source)
            || this.level() instanceof ServerLevel serverLevel && EnchantmentHelper.isImmuneToDamage(serverLevel, this, source);
    }

    public record Fallsounds(SoundEvent small, SoundEvent big) {
    }
}
