package net.minecraft.world.entity.animal;

import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Shearable;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SuspiciousEffectHolder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public class MushroomCow extends Cow implements Shearable, VariantHolder<MushroomCow.MushroomType> {
    private static final EntityDataAccessor<String> DATA_TYPE = SynchedEntityData.defineId(MushroomCow.class, EntityDataSerializers.STRING);
    private static final int MUTATE_CHANCE = 1024;
    private static final String TAG_STEW_EFFECTS = "stew_effects";
    @Nullable
    private SuspiciousStewEffects stewEffects;
    /**
     * Stores the UUID of the most recent lightning bolt to strike
     */
    @Nullable
    private UUID lastLightningBoltUUID;

    public MushroomCow(EntityType<? extends MushroomCow> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level) {
        return level.getBlockState(pos.below()).is(Blocks.MYCELIUM) ? 10.0F : level.getPathfindingCostFromLightLevels(pos);
    }

    public static boolean checkMushroomSpawnRules(
        EntityType<MushroomCow> mushroomCow, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource randomSource
    ) {
        return level.getBlockState(pos.below()).is(BlockTags.MOOSHROOMS_SPAWNABLE_ON) && isBrightEnoughToSpawn(level, pos);
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
        UUID uUID = lightning.getUUID();
        if (!uUID.equals(this.lastLightningBoltUUID)) {
            this.setVariant(this.getVariant() == MushroomCow.MushroomType.RED ? MushroomCow.MushroomType.BROWN : MushroomCow.MushroomType.RED);
            this.lastLightningBoltUUID = uUID;
            this.playSound(SoundEvents.MOOSHROOM_CONVERT, 2.0F, 1.0F);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TYPE, MushroomCow.MushroomType.RED.type);
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (itemStack.is(Items.BOWL) && !this.isBaby()) {
            boolean bl = false;
            ItemStack itemStack2;
            if (this.stewEffects != null) {
                bl = true;
                itemStack2 = new ItemStack(Items.SUSPICIOUS_STEW);
                itemStack2.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, this.stewEffects);
                this.stewEffects = null;
            } else {
                itemStack2 = new ItemStack(Items.MUSHROOM_STEW);
            }

            ItemStack itemStack3 = ItemUtils.createFilledResult(itemStack, player, itemStack2, false);
            player.setItemInHand(hand, itemStack3);
            SoundEvent soundEvent;
            if (bl) {
                soundEvent = SoundEvents.MOOSHROOM_MILK_SUSPICIOUSLY;
            } else {
                soundEvent = SoundEvents.MOOSHROOM_MILK;
            }

            this.playSound(soundEvent, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        } else if (itemStack.is(Items.SHEARS) && this.readyForShearing()) {
            this.shear(SoundSource.PLAYERS);
            this.gameEvent(GameEvent.SHEAR, player);
            if (!this.level().isClientSide) {
                itemStack.hurtAndBreak(1, player, getSlotForHand(hand));
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        } else if (this.getVariant() == MushroomCow.MushroomType.BROWN && itemStack.is(ItemTags.SMALL_FLOWERS)) {
            if (this.stewEffects != null) {
                for (int i = 0; i < 2; i++) {
                    this.level()
                        .addParticle(
                            ParticleTypes.SMOKE,
                            this.getX() + this.random.nextDouble() / 2.0,
                            this.getY(0.5),
                            this.getZ() + this.random.nextDouble() / 2.0,
                            0.0,
                            this.random.nextDouble() / 5.0,
                            0.0
                        );
                }
            } else {
                Optional<SuspiciousStewEffects> optional = this.getEffectsFromItemStack(itemStack);
                if (optional.isEmpty()) {
                    return InteractionResult.PASS;
                }

                itemStack.consume(1, player);

                for (int j = 0; j < 4; j++) {
                    this.level()
                        .addParticle(
                            ParticleTypes.EFFECT,
                            this.getX() + this.random.nextDouble() / 2.0,
                            this.getY(0.5),
                            this.getZ() + this.random.nextDouble() / 2.0,
                            0.0,
                            this.random.nextDouble() / 5.0,
                            0.0
                        );
                }

                this.stewEffects = optional.get();
                this.playSound(SoundEvents.MOOSHROOM_EAT, 2.0F, 1.0F);
            }

            return InteractionResult.sidedSuccess(this.level().isClientSide);
        } else {
            return super.mobInteract(player, hand);
        }
    }

    @Override
    public void shear(SoundSource category) {
        this.level().playSound(null, this, SoundEvents.MOOSHROOM_SHEAR, category, 1.0F, 1.0F);
        if (!this.level().isClientSide()) {
            Cow cow = EntityType.COW.create(this.level());
            if (cow != null) {
                ((ServerLevel)this.level()).sendParticles(ParticleTypes.EXPLOSION, this.getX(), this.getY(0.5), this.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                this.discard();
                cow.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot());
                cow.setHealth(this.getHealth());
                cow.yBodyRot = this.yBodyRot;
                if (this.hasCustomName()) {
                    cow.setCustomName(this.getCustomName());
                    cow.setCustomNameVisible(this.isCustomNameVisible());
                }

                if (this.isPersistenceRequired()) {
                    cow.setPersistenceRequired();
                }

                cow.setInvulnerable(this.isInvulnerable());
                this.level().addFreshEntity(cow);

                for (int i = 0; i < 5; i++) {
                    this.level()
                        .addFreshEntity(
                            new ItemEntity(this.level(), this.getX(), this.getY(1.0), this.getZ(), new ItemStack(this.getVariant().blockState.getBlock()))
                        );
                }
            }
        }
    }

    @Override
    public boolean readyForShearing() {
        return this.isAlive() && !this.isBaby();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("Type", this.getVariant().getSerializedName());
        if (this.stewEffects != null) {
            SuspiciousStewEffects.CODEC.encodeStart(NbtOps.INSTANCE, this.stewEffects).ifSuccess(tag -> compound.put("stew_effects", tag));
        }
    }

    /**
     * (abstract) Protected helper method to read subclass entity data from NBT.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setVariant(MushroomCow.MushroomType.byType(compound.getString("Type")));
        if (compound.contains("stew_effects", 9)) {
            SuspiciousStewEffects.CODEC
                .parse(NbtOps.INSTANCE, compound.get("stew_effects"))
                .ifSuccess(suspiciousStewEffects -> this.stewEffects = suspiciousStewEffects);
        }
    }

    private Optional<SuspiciousStewEffects> getEffectsFromItemStack(ItemStack stack) {
        SuspiciousEffectHolder suspiciousEffectHolder = SuspiciousEffectHolder.tryGet(stack.getItem());
        return suspiciousEffectHolder != null ? Optional.of(suspiciousEffectHolder.getSuspiciousEffects()) : Optional.empty();
    }

    public void setVariant(MushroomCow.MushroomType variant) {
        this.entityData.set(DATA_TYPE, variant.type);
    }

    public MushroomCow.MushroomType getVariant() {
        return MushroomCow.MushroomType.byType(this.entityData.get(DATA_TYPE));
    }

    @Nullable
    public MushroomCow getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        MushroomCow mushroomCow = EntityType.MOOSHROOM.create(level);
        if (mushroomCow != null) {
            mushroomCow.setVariant(this.getOffspringType((MushroomCow)otherParent));
        }

        return mushroomCow;
    }

    private MushroomCow.MushroomType getOffspringType(MushroomCow mate) {
        MushroomCow.MushroomType mushroomType = this.getVariant();
        MushroomCow.MushroomType mushroomType2 = mate.getVariant();
        MushroomCow.MushroomType mushroomType3;
        if (mushroomType == mushroomType2 && this.random.nextInt(1024) == 0) {
            mushroomType3 = mushroomType == MushroomCow.MushroomType.BROWN ? MushroomCow.MushroomType.RED : MushroomCow.MushroomType.BROWN;
        } else {
            mushroomType3 = this.random.nextBoolean() ? mushroomType : mushroomType2;
        }

        return mushroomType3;
    }

    public enum MushroomType implements StringRepresentable {
        RED("red", Blocks.RED_MUSHROOM.defaultBlockState()),
        BROWN("brown", Blocks.BROWN_MUSHROOM.defaultBlockState());

        public static final StringRepresentable.EnumCodec<MushroomCow.MushroomType> CODEC = StringRepresentable.fromEnum(MushroomCow.MushroomType::values);
        final String type;
        final BlockState blockState;

        MushroomType(final String type, final BlockState blockState) {
            this.type = type;
            this.blockState = blockState;
        }

        public BlockState getBlockState() {
            return this.blockState;
        }

        @Override
        public String getSerializedName() {
            return this.type;
        }

        static MushroomCow.MushroomType byType(String name) {
            return CODEC.byName(name, RED);
        }
    }
}
