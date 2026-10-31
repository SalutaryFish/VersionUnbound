package net.minecraft.world.entity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.providers.VanillaEnchantmentProviders;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;

public abstract class Mob extends LivingEntity implements EquipmentUser, Leashable, Targeting {
    private static final EntityDataAccessor<Byte> DATA_MOB_FLAGS_ID = SynchedEntityData.defineId(Mob.class, EntityDataSerializers.BYTE);
    private static final int MOB_FLAG_NO_AI = 1;
    private static final int MOB_FLAG_LEFTHANDED = 2;
    private static final int MOB_FLAG_AGGRESSIVE = 4;
    protected static final int PICKUP_REACH = 1;
    private static final Vec3i ITEM_PICKUP_REACH = new Vec3i(1, 0, 1);
    public static final float MAX_WEARING_ARMOR_CHANCE = 0.15F;
    public static final float MAX_PICKUP_LOOT_CHANCE = 0.55F;
    public static final float MAX_ENCHANTED_ARMOR_CHANCE = 0.5F;
    public static final float MAX_ENCHANTED_WEAPON_CHANCE = 0.25F;
    public static final float DEFAULT_EQUIPMENT_DROP_CHANCE = 0.085F;
    public static final float PRESERVE_ITEM_DROP_CHANCE_THRESHOLD = 1.0F;
    public static final int PRESERVE_ITEM_DROP_CHANCE = 2;
    public static final int UPDATE_GOAL_SELECTOR_EVERY_N_TICKS = 2;
    private static final double DEFAULT_ATTACK_REACH = Math.sqrt(2.04F) - 0.6F;
    protected static final ResourceLocation RANDOM_SPAWN_BONUS_ID = ResourceLocation.withDefaultNamespace("random_spawn_bonus");
    public int ambientSoundTime;
    protected int xpReward;
    protected LookControl lookControl;
    protected MoveControl moveControl;
    protected JumpControl jumpControl;
    private final BodyRotationControl bodyRotationControl;
    protected PathNavigation navigation;
    protected final GoalSelector goalSelector;
    protected final GoalSelector targetSelector;
    @Nullable
    private LivingEntity target;
    private final Sensing sensing;
    private final NonNullList<ItemStack> handItems = NonNullList.withSize(2, ItemStack.EMPTY);
    protected final float[] handDropChances = new float[2];
    private final NonNullList<ItemStack> armorItems = NonNullList.withSize(4, ItemStack.EMPTY);
    protected final float[] armorDropChances = new float[4];
    private ItemStack bodyArmorItem = ItemStack.EMPTY;
    protected float bodyArmorDropChance;
    private boolean canPickUpLoot;
    private boolean persistenceRequired;
    private final Map<PathType, Float> pathfindingMalus = Maps.newEnumMap(PathType.class);
    @Nullable
    private ResourceKey<LootTable> lootTable;
    private long lootTableSeed;
    @Nullable
    private Leashable.LeashData leashData;
    private BlockPos restrictCenter = BlockPos.ZERO;
    private float restrictRadius = -1.0F;

    protected Mob(EntityType<? extends Mob> entityType, Level level) {
        super(entityType, level);
        this.goalSelector = new GoalSelector(level.getProfilerSupplier());
        this.targetSelector = new GoalSelector(level.getProfilerSupplier());
        this.lookControl = new LookControl(this);
        this.moveControl = new MoveControl(this);
        this.jumpControl = new JumpControl(this);
        this.bodyRotationControl = this.createBodyControl();
        this.navigation = this.createNavigation(level);
        this.sensing = new Sensing(this);
        Arrays.fill(this.armorDropChances, 0.085F);
        Arrays.fill(this.handDropChances, 0.085F);
        this.bodyArmorDropChance = 0.085F;
        if (level != null && !level.isClientSide) {
            this.registerGoals();
        }
    }

    protected void registerGoals() {
    }

    public static AttributeSupplier.Builder createMobAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.FOLLOW_RANGE, 16.0);
    }

    protected PathNavigation createNavigation(Level level) {
        return new GroundPathNavigation(this, level);
    }

    protected boolean shouldPassengersInheritMalus() {
        return false;
    }

    public float getPathfindingMalus(PathType pathType) {
        Mob mob2;
        if (this.getControlledVehicle() instanceof Mob mob && mob.shouldPassengersInheritMalus()) {
            mob2 = mob;
        } else {
            mob2 = this;
        }

        Float float_ = mob2.pathfindingMalus.get(pathType);
        return float_ == null ? pathType.getMalus() : float_;
    }

    public void setPathfindingMalus(PathType pathType, float malus) {
        this.pathfindingMalus.put(pathType, malus);
    }

    public void onPathfindingStart() {
    }

    public void onPathfindingDone() {
    }

    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this);
    }

    public LookControl getLookControl() {
        return this.lookControl;
    }

    public MoveControl getMoveControl() {
        return this.getControlledVehicle() instanceof Mob mob ? mob.getMoveControl() : this.moveControl;
    }

    public JumpControl getJumpControl() {
        return this.jumpControl;
    }

    public PathNavigation getNavigation() {
        return this.getControlledVehicle() instanceof Mob mob ? mob.getNavigation() : this.navigation;
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        Entity entity = this.getFirstPassenger();
        return !this.isNoAi() && entity instanceof Mob mob && entity.canControlVehicle() ? mob : null;
    }

    public Sensing getSensing() {
        return this.sensing;
    }

    @Nullable
    @Override
    public LivingEntity getTarget() {
        return this.target;
    }

    @Nullable
    protected final LivingEntity getTargetFromBrain() {
        return this.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    /**
     * Sets the active target the Goal system uses for tracking
     */
    public void setTarget(@Nullable LivingEntity target) {
        this.target = target;
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return type != EntityType.GHAST;
    }

    public boolean canFireProjectileWeapon(ProjectileWeaponItem projectileWeapon) {
        return false;
    }

    public void ate() {
        this.gameEvent(GameEvent.EAT);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MOB_FLAGS_ID, (byte)0);
    }

    public int getAmbientSoundInterval() {
        return 80;
    }

    public void playAmbientSound() {
        this.makeSound(this.getAmbientSound());
    }

    @Override
    public void baseTick() {
        super.baseTick();
        this.level().getProfiler().push("mobBaseTick");
        if (this.isAlive() && this.random.nextInt(1000) < this.ambientSoundTime++) {
            this.resetAmbientSoundTime();
            this.playAmbientSound();
        }

        this.level().getProfiler().pop();
    }

    @Override
    protected void playHurtSound(DamageSource source) {
        this.resetAmbientSoundTime();
        super.playHurtSound(source);
    }

    private void resetAmbientSoundTime() {
        this.ambientSoundTime = -this.getAmbientSoundInterval();
    }

    @Override
    protected int getBaseExperienceReward() {
        if (this.xpReward > 0) {
            int i = this.xpReward;

            for (int j = 0; j < this.armorItems.size(); j++) {
                if (!this.armorItems.get(j).isEmpty() && this.armorDropChances[j] <= 1.0F) {
                    i += 1 + this.random.nextInt(3);
                }
            }

            for (int j = 0; j < this.handItems.size(); j++) {
                if (!this.handItems.get(j).isEmpty() && this.handDropChances[j] <= 1.0F) {
                    i += 1 + this.random.nextInt(3);
                }
            }

            if (!this.bodyArmorItem.isEmpty() && this.bodyArmorDropChance <= 1.0F) {
                i += 1 + this.random.nextInt(3);
            }

            return i;
        } else {
            return this.xpReward;
        }
    }

    public void spawnAnim() {
        if (this.level().isClientSide) {
            for (int i = 0; i < 20; i++) {
                double d = this.random.nextGaussian() * 0.02;
                double e = this.random.nextGaussian() * 0.02;
                double f = this.random.nextGaussian() * 0.02;
                double g = 10.0;
                this.level().addParticle(ParticleTypes.POOF, this.getX(1.0) - d * 10.0, this.getRandomY() - e * 10.0, this.getRandomZ(1.0) - f * 10.0, d, e, f);
            }
        } else {
            this.level().broadcastEntityEvent(this, (byte)20);
        }
    }

    /**
     * Handler for {@link World#setEntityState}
     */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 20) {
            this.spawnAnim();
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount % 5 == 0) {
            this.updateControlFlags();
        }
    }

    protected void updateControlFlags() {
        boolean bl = !(this.getControllingPassenger() instanceof Mob);
        boolean bl2 = !(this.getVehicle() instanceof Boat);
        this.goalSelector.setControlFlag(Goal.Flag.MOVE, bl);
        this.goalSelector.setControlFlag(Goal.Flag.JUMP, bl && bl2);
        this.goalSelector.setControlFlag(Goal.Flag.LOOK, bl);
    }

    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        this.bodyRotationControl.clientTick();
        return animStep;
    }

    @Nullable
    protected SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("CanPickUpLoot", this.canPickUpLoot());
        compound.putBoolean("PersistenceRequired", this.persistenceRequired);
        ListTag listTag = new ListTag();

        for (ItemStack itemStack : this.armorItems) {
            if (!itemStack.isEmpty()) {
                listTag.add(itemStack.save(this.registryAccess()));
            } else {
                listTag.add(new CompoundTag());
            }
        }

        compound.put("ArmorItems", listTag);
        ListTag listTag2 = new ListTag();

        for (float f : this.armorDropChances) {
            listTag2.add(FloatTag.valueOf(f));
        }

        compound.put("ArmorDropChances", listTag2);
        ListTag listTag3 = new ListTag();

        for (ItemStack itemStack2 : this.handItems) {
            if (!itemStack2.isEmpty()) {
                listTag3.add(itemStack2.save(this.registryAccess()));
            } else {
                listTag3.add(new CompoundTag());
            }
        }

        compound.put("HandItems", listTag3);
        ListTag listTag4 = new ListTag();

        for (float g : this.handDropChances) {
            listTag4.add(FloatTag.valueOf(g));
        }

        compound.put("HandDropChances", listTag4);
        if (!this.bodyArmorItem.isEmpty()) {
            compound.put("body_armor_item", this.bodyArmorItem.save(this.registryAccess()));
            compound.putFloat("body_armor_drop_chance", this.bodyArmorDropChance);
        }

        this.writeLeashData(compound, this.leashData);
        compound.putBoolean("LeftHanded", this.isLeftHanded());
        if (this.lootTable != null) {
            compound.putString("DeathLootTable", this.lootTable.location().toString());
            if (this.lootTableSeed != 0L) {
                compound.putLong("DeathLootTableSeed", this.lootTableSeed);
            }
        }

        if (this.isNoAi()) {
            compound.putBoolean("NoAI", this.isNoAi());
        }
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("CanPickUpLoot", 1)) {
            this.setCanPickUpLoot(compound.getBoolean("CanPickUpLoot"));
        }

        this.persistenceRequired = compound.getBoolean("PersistenceRequired");
        if (compound.contains("ArmorItems", 9)) {
            ListTag listTag = compound.getList("ArmorItems", 10);

            for (int i = 0; i < this.armorItems.size(); i++) {
                CompoundTag compoundTag = listTag.getCompound(i);
                this.armorItems.set(i, ItemStack.parseOptional(this.registryAccess(), compoundTag));
            }
        }

        if (compound.contains("ArmorDropChances", 9)) {
            ListTag listTag = compound.getList("ArmorDropChances", 5);

            for (int i = 0; i < listTag.size(); i++) {
                this.armorDropChances[i] = listTag.getFloat(i);
            }
        }

        if (compound.contains("HandItems", 9)) {
            ListTag listTag = compound.getList("HandItems", 10);

            for (int i = 0; i < this.handItems.size(); i++) {
                CompoundTag compoundTag = listTag.getCompound(i);
                this.handItems.set(i, ItemStack.parseOptional(this.registryAccess(), compoundTag));
            }
        }

        if (compound.contains("HandDropChances", 9)) {
            ListTag listTag = compound.getList("HandDropChances", 5);

            for (int i = 0; i < listTag.size(); i++) {
                this.handDropChances[i] = listTag.getFloat(i);
            }
        }

        if (compound.contains("body_armor_item", 10)) {
            this.bodyArmorItem = ItemStack.parse(this.registryAccess(), compound.getCompound("body_armor_item")).orElse(ItemStack.EMPTY);
            this.bodyArmorDropChance = compound.getFloat("body_armor_drop_chance");
        } else {
            this.bodyArmorItem = ItemStack.EMPTY;
        }

        this.leashData = this.readLeashData(compound);
        this.setLeftHanded(compound.getBoolean("LeftHanded"));
        if (compound.contains("DeathLootTable", 8)) {
            this.lootTable = ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.parse(compound.getString("DeathLootTable")));
            this.lootTableSeed = compound.getLong("DeathLootTableSeed");
        }

        this.setNoAi(compound.getBoolean("NoAI"));
    }

    @Override
    protected void dropFromLootTable(DamageSource damageSource, boolean attackedRecently) {
        super.dropFromLootTable(damageSource, attackedRecently);
        this.lootTable = null;
    }

    @Override
    public final ResourceKey<LootTable> getLootTable() {
        return this.lootTable == null ? this.getDefaultLootTable() : this.lootTable;
    }

    protected ResourceKey<LootTable> getDefaultLootTable() {
        return super.getLootTable();
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    public void setZza(float amount) {
        this.zza = amount;
    }

    public void setYya(float amount) {
        this.yya = amount;
    }

    public void setXxa(float amount) {
        this.xxa = amount;
    }

    /**
     * Sets the movespeed used for the new AI system.
     */
    @Override
    public void setSpeed(float speed) {
        super.setSpeed(speed);
        this.setZza(speed);
    }

    public void stopInPlace() {
        this.getNavigation().stop();
        this.setXxa(0.0F);
        this.setYya(0.0F);
        this.setSpeed(0.0F);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        this.level().getProfiler().push("looting");
        if (!this.level().isClientSide
            && this.canPickUpLoot()
            && this.isAlive()
            && !this.dead
            && this.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            Vec3i vec3i = this.getPickupReach();

            for (ItemEntity itemEntity : this.level()
                .getEntitiesOfClass(ItemEntity.class, this.getBoundingBox().inflate(vec3i.getX(), vec3i.getY(), vec3i.getZ()))) {
                if (!itemEntity.isRemoved() && !itemEntity.getItem().isEmpty() && !itemEntity.hasPickUpDelay() && this.wantsToPickUp(itemEntity.getItem())) {
                    this.pickUpItem(itemEntity);
                }
            }
        }

        this.level().getProfiler().pop();
    }

    protected Vec3i getPickupReach() {
        return ITEM_PICKUP_REACH;
    }

    /**
     * Tests if this entity should pick up a weapon or an armor piece. Entity drops current weapon or armor if the new one is better.
     */
    protected void pickUpItem(ItemEntity itemEntity) {
        ItemStack itemStack = itemEntity.getItem();
        ItemStack itemStack2 = this.equipItemIfPossible(itemStack.copy());
        if (!itemStack2.isEmpty()) {
            this.onItemPickup(itemEntity);
            this.take(itemEntity, itemStack2.getCount());
            itemStack.shrink(itemStack2.getCount());
            if (itemStack.isEmpty()) {
                itemEntity.discard();
            }
        }
    }

    public ItemStack equipItemIfPossible(ItemStack stack) {
        EquipmentSlot equipmentSlot = this.getEquipmentSlotForItem(stack);
        ItemStack itemStack = this.getItemBySlot(equipmentSlot);
        boolean bl = this.canReplaceCurrentItem(stack, itemStack);
        if (equipmentSlot.isArmor() && !bl) {
            equipmentSlot = EquipmentSlot.MAINHAND;
            itemStack = this.getItemBySlot(equipmentSlot);
            bl = itemStack.isEmpty();
        }

        if (bl && this.canHoldItem(stack)) {
            double d = this.getEquipmentDropChance(equipmentSlot);
            if (!itemStack.isEmpty() && Math.max(this.random.nextFloat() - 0.1F, 0.0F) < d) {
                this.spawnAtLocation(itemStack);
            }

            ItemStack itemStack2 = equipmentSlot.limit(stack);
            this.setItemSlotAndDropWhenKilled(equipmentSlot, itemStack2);
            return itemStack2;
        } else {
            return ItemStack.EMPTY;
        }
    }

    protected void setItemSlotAndDropWhenKilled(EquipmentSlot slot, ItemStack stack) {
        this.setItemSlot(slot, stack);
        this.setGuaranteedDrop(slot);
        this.persistenceRequired = true;
    }

    public void setGuaranteedDrop(EquipmentSlot slot) {
        switch (slot.getType()) {
            case HAND:
                this.handDropChances[slot.getIndex()] = 2.0F;
                break;
            case HUMANOID_ARMOR:
                this.armorDropChances[slot.getIndex()] = 2.0F;
                break;
            case ANIMAL_ARMOR:
                this.bodyArmorDropChance = 2.0F;
        }
    }

    protected boolean canReplaceCurrentItem(ItemStack candidate, ItemStack existing) {
        if (existing.isEmpty()) {
            return true;
        }

        if (candidate.getItem() instanceof SwordItem) {
            if (!(existing.getItem() instanceof SwordItem)) {
                return true;
            }

            double d = this.getApproximateAttackDamageWithItem(candidate);
            double e = this.getApproximateAttackDamageWithItem(existing);
            return d != e ? d > e : this.canReplaceEqualItem(candidate, existing);
        } else {
            if (candidate.getItem() instanceof BowItem && existing.getItem() instanceof BowItem) {
                return this.canReplaceEqualItem(candidate, existing);
            }

            if (candidate.getItem() instanceof CrossbowItem && existing.getItem() instanceof CrossbowItem) {
                return this.canReplaceEqualItem(candidate, existing);
            }

            if (candidate.getItem() instanceof ArmorItem armorItem) {
                if (EnchantmentHelper.has(existing, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
                    return false;
                } else if (!(existing.getItem() instanceof ArmorItem)) {
                    return true;
                } else {
                    ArmorItem armorItem2 = (ArmorItem)existing.getItem();
                    if (armorItem.getDefense() != armorItem2.getDefense()) {
                        return armorItem.getDefense() > armorItem2.getDefense();
                    } else {
                        return armorItem.getToughness() != armorItem2.getToughness()
                            ? armorItem.getToughness() > armorItem2.getToughness()
                            : this.canReplaceEqualItem(candidate, existing);
                    }
                }
            } else {
                if (candidate.getItem() instanceof DiggerItem) {
                    if (existing.getItem() instanceof BlockItem) {
                        return true;
                    }

                    if (existing.getItem() instanceof DiggerItem) {
                        double d = this.getApproximateAttackDamageWithItem(candidate);
                        double e = this.getApproximateAttackDamageWithItem(existing);
                        if (d != e) {
                            return d > e;
                        }

                        return this.canReplaceEqualItem(candidate, existing);
                    }
                }

                return false;
            }
        }
    }

    private double getApproximateAttackDamageWithItem(ItemStack itemStack) {
        ItemAttributeModifiers itemAttributeModifiers = itemStack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return itemAttributeModifiers.compute(this.getAttributeBaseValue(Attributes.ATTACK_DAMAGE), EquipmentSlot.MAINHAND);
    }

    public boolean canReplaceEqualItem(ItemStack candidate, ItemStack existing) {
        return candidate.getDamageValue() < existing.getDamageValue() ? true : hasAnyComponentExceptDamage(candidate) && !hasAnyComponentExceptDamage(existing);
    }

    private static boolean hasAnyComponentExceptDamage(ItemStack stack) {
        DataComponentMap dataComponentMap = stack.getComponents();
        int i = dataComponentMap.size();
        return i > 1 || i == 1 && !dataComponentMap.has(DataComponents.DAMAGE);
    }

    public boolean canHoldItem(ItemStack stack) {
        return true;
    }

    public boolean wantsToPickUp(ItemStack stack) {
        return this.canHoldItem(stack);
    }

    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return true;
    }

    public boolean requiresCustomPersistence() {
        return this.isPassenger();
    }

    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void checkDespawn() {
        if (this.level().getDifficulty() == Difficulty.PEACEFUL && this.shouldDespawnInPeaceful()) {
            this.discard();
        } else if (!this.isPersistenceRequired() && !this.requiresCustomPersistence()) {
            Entity entity = this.level().getNearestPlayer(this, -1.0);
            if (entity != null) {
                double d = entity.distanceToSqr(this);
                int i = this.getType().getCategory().getDespawnDistance();
                int j = i * i;
                if (d > j && this.removeWhenFarAway(d)) {
                    this.discard();
                }

                int k = this.getType().getCategory().getNoDespawnDistance();
                int l = k * k;
                if (this.noActionTime > 600 && this.random.nextInt(800) == 0 && d > l && this.removeWhenFarAway(d)) {
                    this.discard();
                } else if (d < l) {
                    this.noActionTime = 0;
                }
            }
        } else {
            this.noActionTime = 0;
        }
    }

    @Override
    protected final void serverAiStep() {
        this.noActionTime++;
        ProfilerFiller profilerFiller = this.level().getProfiler();
        profilerFiller.push("sensing");
        this.sensing.tick();
        profilerFiller.pop();
        int i = this.tickCount + this.getId();
        if (i % 2 != 0 && this.tickCount > 1) {
            profilerFiller.push("targetSelector");
            this.targetSelector.tickRunningGoals(false);
            profilerFiller.pop();
            profilerFiller.push("goalSelector");
            this.goalSelector.tickRunningGoals(false);
            profilerFiller.pop();
        } else {
            profilerFiller.push("targetSelector");
            this.targetSelector.tick();
            profilerFiller.pop();
            profilerFiller.push("goalSelector");
            this.goalSelector.tick();
            profilerFiller.pop();
        }

        profilerFiller.push("navigation");
        this.navigation.tick();
        profilerFiller.pop();
        profilerFiller.push("mob tick");
        this.customServerAiStep();
        profilerFiller.pop();
        profilerFiller.push("controls");
        profilerFiller.push("move");
        this.moveControl.tick();
        profilerFiller.popPush("look");
        this.lookControl.tick();
        profilerFiller.popPush("jump");
        this.jumpControl.tick();
        profilerFiller.pop();
        profilerFiller.pop();
        this.sendDebugPackets();
    }

    protected void sendDebugPackets() {
        DebugPackets.sendGoalSelector(this.level(), this, this.goalSelector);
    }

    protected void customServerAiStep() {
    }

    public int getMaxHeadXRot() {
        return 40;
    }

    public int getMaxHeadYRot() {
        return 75;
    }

    protected void clampHeadRotationToBody() {
        float f = this.getMaxHeadYRot();
        float g = this.getYHeadRot();
        float h = Mth.wrapDegrees(this.yBodyRot - g);
        float i = Mth.clamp(Mth.wrapDegrees(this.yBodyRot - g), -f, f);
        float j = g + h - i;
        this.setYHeadRot(j);
    }

    public int getHeadRotSpeed() {
        return 10;
    }

    /**
     * Changes the X and Y rotation so that this entity is facing the given entity.
     */
    public void lookAt(Entity entity, float maxYRotIncrease, float maxXRotIncrease) {
        double d = entity.getX() - this.getX();
        double e = entity.getZ() - this.getZ();
        double f;
        if (entity instanceof LivingEntity livingEntity) {
            f = livingEntity.getEyeY() - this.getEyeY();
        } else {
            f = (entity.getBoundingBox().minY + entity.getBoundingBox().maxY) / 2.0 - this.getEyeY();
        }

        double g = Math.sqrt(d * d + e * e);
        float h = (float)(Mth.atan2(e, d) * 180.0F / (float)Math.PI) - 90.0F;
        float i = (float)(-(Mth.atan2(f, g) * 180.0F / (float)Math.PI));
        this.setXRot(this.rotlerp(this.getXRot(), i, maxXRotIncrease));
        this.setYRot(this.rotlerp(this.getYRot(), h, maxYRotIncrease));
    }

    /**
     * Arguments: current rotation, intended rotation, max increment.
     */
    private float rotlerp(float angle, float targetAngle, float maxIncrease) {
        float f = Mth.wrapDegrees(targetAngle - angle);
        if (f > maxIncrease) {
            f = maxIncrease;
        }

        if (f < -maxIncrease) {
            f = -maxIncrease;
        }

        return angle + f;
    }

    public static boolean checkMobSpawnRules(EntityType<? extends Mob> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        BlockPos blockPos = pos.below();
        return spawnType == MobSpawnType.SPAWNER || level.getBlockState(blockPos).isValidSpawn(level, blockPos, type);
    }

    public boolean checkSpawnRules(LevelAccessor level, MobSpawnType reason) {
        return true;
    }

    public boolean checkSpawnObstruction(LevelReader level) {
        return !level.containsAnyLiquid(this.getBoundingBox()) && level.isUnobstructed(this);
    }

    public int getMaxSpawnClusterSize() {
        return 4;
    }

    public boolean isMaxGroupSizeReached(int size) {
        return false;
    }

    @Override
    public int getMaxFallDistance() {
        if (this.getTarget() == null) {
            return this.getComfortableFallDistance(0.0F);
        }

        int i = (int)(this.getHealth() - this.getMaxHealth() * 0.33F);
        i -= (3 - this.level().getDifficulty().getId()) * 4;
        if (i < 0) {
            i = 0;
        }

        return this.getComfortableFallDistance(i);
    }

    @Override
    public Iterable<ItemStack> getHandSlots() {
        return this.handItems;
    }

    @Override
    public Iterable<ItemStack> getArmorSlots() {
        return this.armorItems;
    }

    public ItemStack getBodyArmorItem() {
        return this.bodyArmorItem;
    }

    @Override
    public boolean canUseSlot(EquipmentSlot slot) {
        return slot != EquipmentSlot.BODY;
    }

    public boolean isWearingBodyArmor() {
        return !this.getItemBySlot(EquipmentSlot.BODY).isEmpty();
    }

    public boolean isBodyArmorItem(ItemStack stack) {
        return false;
    }

    public void setBodyArmorItem(ItemStack stack) {
        this.setItemSlotAndDropWhenKilled(EquipmentSlot.BODY, stack);
    }

    @Override
    public Iterable<ItemStack> getArmorAndBodyArmorSlots() {
        return this.bodyArmorItem.isEmpty() ? this.armorItems : Iterables.concat(this.armorItems, List.of(this.bodyArmorItem));
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> (ItemStack)this.handItems.get(slot.getIndex());
            case HUMANOID_ARMOR -> (ItemStack)this.armorItems.get(slot.getIndex());
            case ANIMAL_ARMOR -> this.bodyArmorItem;
        };
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
        this.verifyEquippedItem(stack);
        switch (slot.getType()) {
            case HAND:
                this.onEquipItem(slot, this.handItems.set(slot.getIndex(), stack), stack);
                break;
            case HUMANOID_ARMOR:
                this.onEquipItem(slot, this.armorItems.set(slot.getIndex(), stack), stack);
                break;
            case ANIMAL_ARMOR:
                ItemStack itemStack = this.bodyArmorItem;
                this.bodyArmorItem = stack;
                this.onEquipItem(slot, itemStack, stack);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, damageSource, recentlyHit);

        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            ItemStack itemStack = this.getItemBySlot(equipmentSlot);
            float f = this.getEquipmentDropChance(equipmentSlot);
            if (f != 0.0F) {
                boolean bl = f > 1.0F;
                if (damageSource.getEntity() instanceof LivingEntity livingEntity && this.level() instanceof ServerLevel serverLevel) {
                    f = EnchantmentHelper.processEquipmentDropChance(serverLevel, livingEntity, damageSource, f);
                }

                if (!itemStack.isEmpty()
                    && !EnchantmentHelper.has(itemStack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)
                    && (recentlyHit || bl)
                    && this.random.nextFloat() < f) {
                    if (!bl && itemStack.isDamageableItem()) {
                        itemStack.setDamageValue(
                            itemStack.getMaxDamage() - this.random.nextInt(1 + this.random.nextInt(Math.max(itemStack.getMaxDamage() - 3, 1)))
                        );
                    }

                    this.spawnAtLocation(itemStack);
                    this.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                }
            }
        }
    }

    protected float getEquipmentDropChance(EquipmentSlot slot) {
        return switch (slot.getType()) {
            case HAND -> this.handDropChances[slot.getIndex()];
            case HUMANOID_ARMOR -> this.armorDropChances[slot.getIndex()];
            case ANIMAL_ARMOR -> this.bodyArmorDropChance;
        };
    }

    public void dropPreservedEquipment() {
        this.dropPreservedEquipment(itemStack -> true);
    }

    public Set<EquipmentSlot> dropPreservedEquipment(Predicate<ItemStack> predicate) {
        Set<EquipmentSlot> set = new HashSet<>();

        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            ItemStack itemStack = this.getItemBySlot(equipmentSlot);
            if (!itemStack.isEmpty()) {
                if (!predicate.test(itemStack)) {
                    set.add(equipmentSlot);
                } else {
                    double d = this.getEquipmentDropChance(equipmentSlot);
                    if (d > 1.0) {
                        this.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                        this.spawnAtLocation(itemStack);
                    }
                }
            }
        }

        return set;
    }

    private LootParams createEquipmentParams(ServerLevel level) {
        return new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, this.position())
            .withParameter(LootContextParams.THIS_ENTITY, this)
            .create(LootContextParamSets.EQUIPMENT);
    }

    public void equip(EquipmentTable equipmentTable) {
        this.equip(equipmentTable.lootTable(), equipmentTable.slotDropChances());
    }

    public void equip(ResourceKey<LootTable> equipmentLootTable, Map<EquipmentSlot, Float> slotDropChances) {
        if (this.level() instanceof ServerLevel serverLevel) {
            this.equip(equipmentLootTable, this.createEquipmentParams(serverLevel), slotDropChances);
        }
    }

    protected void populateDefaultEquipmentSlots(RandomSource random, DifficultyInstance difficulty) {
        if (random.nextFloat() < 0.15F * difficulty.getSpecialMultiplier()) {
            int i = random.nextInt(2);
            float f = this.level().getDifficulty() == Difficulty.HARD ? 0.1F : 0.25F;
            if (random.nextFloat() < 0.095F) {
                i++;
            }

            if (random.nextFloat() < 0.095F) {
                i++;
            }

            if (random.nextFloat() < 0.095F) {
                i++;
            }

            boolean bl = true;

            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                if (equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                    ItemStack itemStack = this.getItemBySlot(equipmentSlot);
                    if (!bl && random.nextFloat() < f) {
                        break;
                    }

                    bl = false;
                    if (itemStack.isEmpty()) {
                        Item item = getEquipmentForSlot(equipmentSlot, i);
                        if (item != null) {
                            this.setItemSlot(equipmentSlot, new ItemStack(item));
                        }
                    }
                }
            }
        }
    }

    @Nullable
    public static Item getEquipmentForSlot(EquipmentSlot slot, int chance) {
        switch (slot) {
            case HEAD:
                if (chance == 0) {
                    return Items.LEATHER_HELMET;
                } else if (chance == 1) {
                    return Items.GOLDEN_HELMET;
                } else if (chance == 2) {
                    return Items.CHAINMAIL_HELMET;
                } else if (chance == 3) {
                    return Items.IRON_HELMET;
                } else if (chance == 4) {
                    return Items.DIAMOND_HELMET;
                }
            case CHEST:
                if (chance == 0) {
                    return Items.LEATHER_CHESTPLATE;
                } else if (chance == 1) {
                    return Items.GOLDEN_CHESTPLATE;
                } else if (chance == 2) {
                    return Items.CHAINMAIL_CHESTPLATE;
                } else if (chance == 3) {
                    return Items.IRON_CHESTPLATE;
                } else if (chance == 4) {
                    return Items.DIAMOND_CHESTPLATE;
                }
            case LEGS:
                if (chance == 0) {
                    return Items.LEATHER_LEGGINGS;
                } else if (chance == 1) {
                    return Items.GOLDEN_LEGGINGS;
                } else if (chance == 2) {
                    return Items.CHAINMAIL_LEGGINGS;
                } else if (chance == 3) {
                    return Items.IRON_LEGGINGS;
                } else if (chance == 4) {
                    return Items.DIAMOND_LEGGINGS;
                }
            case FEET:
                if (chance == 0) {
                    return Items.LEATHER_BOOTS;
                } else if (chance == 1) {
                    return Items.GOLDEN_BOOTS;
                } else if (chance == 2) {
                    return Items.CHAINMAIL_BOOTS;
                } else if (chance == 3) {
                    return Items.IRON_BOOTS;
                } else if (chance == 4) {
                    return Items.DIAMOND_BOOTS;
                }
            default:
                return null;
        }
    }

    protected void populateDefaultEquipmentEnchantments(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty) {
        this.enchantSpawnedWeapon(level, random, difficulty);

        for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
            if (equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                this.enchantSpawnedArmor(level, random, equipmentSlot, difficulty);
            }
        }
    }

    protected void enchantSpawnedWeapon(ServerLevelAccessor level, RandomSource random, DifficultyInstance difficulty) {
        this.enchantSpawnedEquipment(level, EquipmentSlot.MAINHAND, random, 0.25F, difficulty);
    }

    protected void enchantSpawnedArmor(ServerLevelAccessor level, RandomSource random, EquipmentSlot slot, DifficultyInstance difficulty) {
        this.enchantSpawnedEquipment(level, slot, random, 0.5F, difficulty);
    }

    private void enchantSpawnedEquipment(ServerLevelAccessor level, EquipmentSlot slot, RandomSource random, float enchantChance, DifficultyInstance difficulty) {
        ItemStack itemStack = this.getItemBySlot(slot);
        if (!itemStack.isEmpty() && random.nextFloat() < enchantChance * difficulty.getSpecialMultiplier()) {
            EnchantmentHelper.enchantItemFromProvider(itemStack, level.registryAccess(), VanillaEnchantmentProviders.MOB_SPAWN_EQUIPMENT, difficulty, random);
            this.setItemSlot(slot, itemStack);
        }
    }

    @Nullable
    public SpawnGroupData finalizeSpawn(
        ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, @Nullable SpawnGroupData spawnGroupData
    ) {
        RandomSource randomSource = level.getRandom();
        AttributeInstance attributeInstance = Objects.requireNonNull(this.getAttribute(Attributes.FOLLOW_RANGE));
        if (!attributeInstance.hasModifier(RANDOM_SPAWN_BONUS_ID)) {
            attributeInstance.addPermanentModifier(
                new AttributeModifier(RANDOM_SPAWN_BONUS_ID, randomSource.triangle(0.0, 0.11485000000000001), AttributeModifier.Operation.ADD_MULTIPLIED_BASE)
            );
        }

        this.setLeftHanded(randomSource.nextFloat() < 0.05F);
        return spawnGroupData;
    }

    public void setPersistenceRequired() {
        this.persistenceRequired = true;
    }

    @Override
    public void setDropChance(EquipmentSlot slot, float chance) {
        switch (slot.getType()) {
            case HAND:
                this.handDropChances[slot.getIndex()] = chance;
                break;
            case HUMANOID_ARMOR:
                this.armorDropChances[slot.getIndex()] = chance;
                break;
            case ANIMAL_ARMOR:
                this.bodyArmorDropChance = chance;
        }
    }

    public boolean canPickUpLoot() {
        return this.canPickUpLoot;
    }

    public void setCanPickUpLoot(boolean canPickUpLoot) {
        this.canPickUpLoot = canPickUpLoot;
    }

    @Override
    public boolean canTakeItem(ItemStack itemstack) {
        EquipmentSlot equipmentSlot = this.getEquipmentSlotForItem(itemstack);
        return this.getItemBySlot(equipmentSlot).isEmpty() && this.canPickUpLoot();
    }

    public boolean isPersistenceRequired() {
        return this.persistenceRequired;
    }

    @Override
    public final InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.isAlive()) {
            return InteractionResult.PASS;
        } else {
            InteractionResult interactionResult = this.checkAndHandleImportantInteractions(player, hand);
            if (interactionResult.consumesAction()) {
                this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                return interactionResult;
            } else {
                InteractionResult interactionResult2 = super.interact(player, hand);
                if (interactionResult2 != InteractionResult.PASS) {
                    return interactionResult2;
                } else {
                    interactionResult = this.mobInteract(player, hand);
                    if (interactionResult.consumesAction()) {
                        this.gameEvent(GameEvent.ENTITY_INTERACT, player);
                        return interactionResult;
                    } else {
                        return InteractionResult.PASS;
                    }
                }
            }
        }
    }

    private InteractionResult checkAndHandleImportantInteractions(Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.is(Items.NAME_TAG)) {
            InteractionResult interactionResult = itemStack.interactLivingEntity(player, this, hand);
            if (interactionResult.consumesAction()) {
                return interactionResult;
            }
        }

        if (itemStack.getItem() instanceof SpawnEggItem) {
            if (this.level() instanceof ServerLevel) {
                SpawnEggItem spawnEggItem = (SpawnEggItem)itemStack.getItem();
                Optional<Mob> optional = spawnEggItem.spawnOffspringFromSpawnEgg(
                    player, this, (EntityType<? extends Mob>)this.getType(), (ServerLevel)this.level(), this.position(), itemStack
                );
                optional.ifPresent(mob -> this.onOffspringSpawnedFromEgg(player, mob));
                return optional.isPresent() ? InteractionResult.SUCCESS : InteractionResult.PASS;
            } else {
                return InteractionResult.CONSUME;
            }
        } else {
            return InteractionResult.PASS;
        }
    }

    protected void onOffspringSpawnedFromEgg(Player player, Mob child) {
    }

    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    public boolean isWithinRestriction() {
        return this.isWithinRestriction(this.blockPosition());
    }

    public boolean isWithinRestriction(BlockPos pos) {
        return this.restrictRadius == -1.0F ? true : this.restrictCenter.distSqr(pos) < this.restrictRadius * this.restrictRadius;
    }

    public void restrictTo(BlockPos pos, int distance) {
        this.restrictCenter = pos;
        this.restrictRadius = distance;
    }

    public BlockPos getRestrictCenter() {
        return this.restrictCenter;
    }

    public float getRestrictRadius() {
        return this.restrictRadius;
    }

    public void clearRestriction() {
        this.restrictRadius = -1.0F;
    }

    public boolean hasRestriction() {
        return this.restrictRadius != -1.0F;
    }

    @Nullable
    public <T extends Mob> T convertTo(EntityType<T> entityType, boolean transferInventory) {
        if (this.isRemoved()) {
            return null;
        }

        T mob = (T)entityType.create(this.level());
        if (mob == null) {
            return null;
        }

        mob.copyPosition(this);
        mob.setBaby(this.isBaby());
        mob.setNoAi(this.isNoAi());
        if (this.hasCustomName()) {
            mob.setCustomName(this.getCustomName());
            mob.setCustomNameVisible(this.isCustomNameVisible());
        }

        if (this.isPersistenceRequired()) {
            mob.setPersistenceRequired();
        }

        mob.setInvulnerable(this.isInvulnerable());
        if (transferInventory) {
            mob.setCanPickUpLoot(this.canPickUpLoot());

            for (EquipmentSlot equipmentSlot : EquipmentSlot.values()) {
                ItemStack itemStack = this.getItemBySlot(equipmentSlot);
                if (!itemStack.isEmpty()) {
                    mob.setItemSlot(equipmentSlot, itemStack.copyAndClear());
                    mob.setDropChance(equipmentSlot, this.getEquipmentDropChance(equipmentSlot));
                }
            }
        }

        this.level().addFreshEntity(mob);
        if (this.isPassenger()) {
            Entity entity = this.getVehicle();
            this.stopRiding();
            mob.startRiding(entity, true);
        }

        this.discard();
        return mob;
    }

    @Nullable
    @Override
    public Leashable.LeashData getLeashData() {
        return this.leashData;
    }

    @Override
    public void setLeashData(@Nullable Leashable.LeashData leashData) {
        this.leashData = leashData;
    }

    /**
     * Removes the leash from this entity
     */
    @Override
    public void dropLeash(boolean broadcastPacket, boolean dropLeash) {
        Leashable.super.dropLeash(broadcastPacket, dropLeash);
        if (this.getLeashData() == null) {
            this.clearRestriction();
        }
    }

    @Override
    public void leashTooFarBehaviour() {
        Leashable.super.leashTooFarBehaviour();
        this.goalSelector.disableControlFlag(Goal.Flag.MOVE);
    }

    @Override
    public boolean canBeLeashed() {
        return !(this instanceof Enemy);
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        boolean bl = super.startRiding(entity, force);
        if (bl && this.isLeashed()) {
            this.dropLeash(true, true);
        }

        return bl;
    }

    @Override
    public boolean isEffectiveAi() {
        return super.isEffectiveAi() && !this.isNoAi();
    }

    /**
     * Set whether this Entity's AI is disabled
     */
    public void setNoAi(boolean noAi) {
        byte b = this.entityData.get(DATA_MOB_FLAGS_ID);
        this.entityData.set(DATA_MOB_FLAGS_ID, noAi ? (byte)(b | 1) : (byte)(b & -2));
    }

    public void setLeftHanded(boolean leftHanded) {
        byte b = this.entityData.get(DATA_MOB_FLAGS_ID);
        this.entityData.set(DATA_MOB_FLAGS_ID, leftHanded ? (byte)(b | 2) : (byte)(b & -3));
    }

    public void setAggressive(boolean aggressive) {
        byte b = this.entityData.get(DATA_MOB_FLAGS_ID);
        this.entityData.set(DATA_MOB_FLAGS_ID, aggressive ? (byte)(b | 4) : (byte)(b & -5));
    }

    public boolean isNoAi() {
        return (this.entityData.get(DATA_MOB_FLAGS_ID) & 1) != 0;
    }

    public boolean isLeftHanded() {
        return (this.entityData.get(DATA_MOB_FLAGS_ID) & 2) != 0;
    }

    public boolean isAggressive() {
        return (this.entityData.get(DATA_MOB_FLAGS_ID) & 4) != 0;
    }

    /**
     * Set whether this mob is a child.
     */
    public void setBaby(boolean baby) {
    }

    @Override
    public HumanoidArm getMainArm() {
        return this.isLeftHanded() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    }

    public boolean isWithinMeleeAttackRange(LivingEntity entity) {
        return this.getAttackBoundingBox().intersects(entity.getHitbox());
    }

    protected AABB getAttackBoundingBox() {
        Entity entity = this.getVehicle();
        AABB aABB3;
        if (entity != null) {
            AABB aABB = entity.getBoundingBox();
            AABB aABB2 = this.getBoundingBox();
            aABB3 = new AABB(
                Math.min(aABB2.minX, aABB.minX),
                aABB2.minY,
                Math.min(aABB2.minZ, aABB.minZ),
                Math.max(aABB2.maxX, aABB.maxX),
                aABB2.maxY,
                Math.max(aABB2.maxZ, aABB.maxZ)
            );
        } else {
            aABB3 = this.getBoundingBox();
        }

        return aABB3.inflate(DEFAULT_ATTACK_REACH, 0.0, DEFAULT_ATTACK_REACH);
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        float f = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource damageSource = this.damageSources().mobAttack(this);
        if (this.level() instanceof ServerLevel serverLevel) {
            f = EnchantmentHelper.modifyDamage(serverLevel, this.getWeaponItem(), entity, damageSource, f);
        }

        boolean bl = entity.hurt(damageSource, f);
        if (bl) {
            float g = this.getKnockback(entity, damageSource);
            if (g > 0.0F && entity instanceof LivingEntity livingEntity) {
                livingEntity.knockback(g * 0.5F, Mth.sin(this.getYRot() * (float) (Math.PI / 180.0)), -Mth.cos(this.getYRot() * (float) (Math.PI / 180.0)));
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            }

            if (this.level() instanceof ServerLevel serverLevel2) {
                EnchantmentHelper.doPostAttackEffects(serverLevel2, entity, damageSource);
            }

            this.setLastHurtMob(entity);
            this.playAttackSound();
        }

        return bl;
    }

    protected void playAttackSound() {
    }

    protected boolean isSunBurnTick() {
        if (this.level().isDay() && !this.level().isClientSide) {
            float f = this.getLightLevelDependentMagicValue();
            BlockPos blockPos = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
            boolean bl = this.isInWaterRainOrBubble() || this.isInPowderSnow || this.wasInPowderSnow;
            if (f > 0.5F && this.random.nextFloat() * 30.0F < (f - 0.4F) * 2.0F && !bl && this.level().canSeeSky(blockPos)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> fluidTag) {
        if (this.getNavigation().canFloat()) {
            super.jumpInLiquid(fluidTag);
        } else {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, 0.3, 0.0));
        }
    }

    @VisibleForTesting
    public void removeFreeWill() {
        this.removeAllGoals(goal -> true);
        this.getBrain().removeAllBehaviors();
    }

    public void removeAllGoals(Predicate<Goal> filter) {
        this.goalSelector.removeAllGoals(filter);
    }

    @Override
    protected void removeAfterChangingDimensions() {
        super.removeAfterChangingDimensions();
        this.getAllSlots().forEach(itemStack -> {
            if (!itemStack.isEmpty()) {
                itemStack.setCount(0);
            }
        });
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        SpawnEggItem spawnEggItem = SpawnEggItem.byId(this.getType());
        return spawnEggItem == null ? null : new ItemStack(spawnEggItem);
    }
}
