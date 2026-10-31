package net.minecraft.world.level;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class Explosion {
    private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new ExplosionDamageCalculator();
    private static final int MAX_DROPS_PER_COMBINED_STACK = 16;
    private final boolean fire;
    private final Explosion.BlockInteraction blockInteraction;
    private final RandomSource random = RandomSource.create();
    private final Level level;
    private final double x;
    private final double y;
    private final double z;
    @Nullable
    private final Entity source;
    private final float radius;
    private final DamageSource damageSource;
    private final ExplosionDamageCalculator damageCalculator;
    private final ParticleOptions smallExplosionParticles;
    private final ParticleOptions largeExplosionParticles;
    private final Holder<SoundEvent> explosionSound;
    private final ObjectArrayList<BlockPos> toBlow = new ObjectArrayList<>();
    private final Map<Player, Vec3> hitPlayers = Maps.newHashMap();

    public static DamageSource getDefaultDamageSource(Level level, @Nullable Entity source) {
        return level.damageSources().explosion(source, getIndirectSourceEntityInternal(source));
    }

    public Explosion(
        Level level,
        @Nullable Entity source,
        double x,
        double y,
        double z,
        float radius,
        List<BlockPos> toBlow,
        Explosion.BlockInteraction blockInteraction,
        ParticleOptions smallExplosionParticles,
        ParticleOptions largeExplosionParticles,
        Holder<SoundEvent> explosionSound
    ) {
        this(
            level,
            source,
            getDefaultDamageSource(level, source),
            null,
            x,
            y,
            z,
            radius,
            false,
            blockInteraction,
            smallExplosionParticles,
            largeExplosionParticles,
            explosionSound
        );
        this.toBlow.addAll(toBlow);
    }

    public Explosion(
        Level level,
        @Nullable Entity source,
        double x,
        double y,
        double z,
        float radius,
        boolean fire,
        Explosion.BlockInteraction blockInteraction,
        List<BlockPos> positions
    ) {
        this(level, source, x, y, z, radius, fire, blockInteraction);
        this.toBlow.addAll(positions);
    }

    public Explosion(
        Level level, @Nullable Entity source, double x, double y, double z, float radius, boolean fire, Explosion.BlockInteraction blockInteraction
    ) {
        this(
            level,
            source,
            getDefaultDamageSource(level, source),
            null,
            x,
            y,
            z,
            radius,
            fire,
            blockInteraction,
            ParticleTypes.EXPLOSION,
            ParticleTypes.EXPLOSION_EMITTER,
            SoundEvents.GENERIC_EXPLODE
        );
    }

    public Explosion(
        Level level,
        @Nullable Entity source,
        @Nullable DamageSource damageSource,
        @Nullable ExplosionDamageCalculator damageCalculator,
        double x,
        double y,
        double z,
        float radius,
        boolean fire,
        Explosion.BlockInteraction blockInteraction,
        ParticleOptions smallExplosionParticles,
        ParticleOptions largeExplosionParticles,
        Holder<SoundEvent> explosionSound
    ) {
        this.level = level;
        this.source = source;
        this.radius = radius;
        this.x = x;
        this.y = y;
        this.z = z;
        this.fire = fire;
        this.blockInteraction = blockInteraction;
        this.damageSource = damageSource == null ? level.damageSources().explosion(this) : damageSource;
        this.damageCalculator = damageCalculator == null ? this.makeDamageCalculator(source) : damageCalculator;
        this.smallExplosionParticles = smallExplosionParticles;
        this.largeExplosionParticles = largeExplosionParticles;
        this.explosionSound = explosionSound;
    }

    private ExplosionDamageCalculator makeDamageCalculator(@Nullable Entity entity) {
        return entity == null ? EXPLOSION_DAMAGE_CALCULATOR : new EntityBasedExplosionDamageCalculator(entity);
    }

    public static float getSeenPercent(Vec3 explosionVector, Entity entity) {
        AABB aABB = entity.getBoundingBox();
        double d = 1.0 / ((aABB.maxX - aABB.minX) * 2.0 + 1.0);
        double e = 1.0 / ((aABB.maxY - aABB.minY) * 2.0 + 1.0);
        double f = 1.0 / ((aABB.maxZ - aABB.minZ) * 2.0 + 1.0);
        double g = (1.0 - Math.floor(1.0 / d) * d) / 2.0;
        double h = (1.0 - Math.floor(1.0 / f) * f) / 2.0;
        if (!(d < 0.0) && !(e < 0.0) && !(f < 0.0)) {
            int i = 0;
            int j = 0;

            for (double k = 0.0; k <= 1.0; k += d) {
                for (double l = 0.0; l <= 1.0; l += e) {
                    for (double m = 0.0; m <= 1.0; m += f) {
                        double n = Mth.lerp(k, aABB.minX, aABB.maxX);
                        double o = Mth.lerp(l, aABB.minY, aABB.maxY);
                        double p = Mth.lerp(m, aABB.minZ, aABB.maxZ);
                        Vec3 vec3 = new Vec3(n + g, o, p + h);
                        if (entity.level().clip(new ClipContext(vec3, explosionVector, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity)).getType()
                            == HitResult.Type.MISS) {
                            i++;
                        }

                        j++;
                    }
                }
            }

            return (float)i / j;
        } else {
            return 0.0F;
        }
    }

    public float radius() {
        return this.radius;
    }

    public Vec3 center() {
        return new Vec3(this.x, this.y, this.z);
    }

    public void explode() {
        this.level.gameEvent(this.source, GameEvent.EXPLODE, new Vec3(this.x, this.y, this.z));
        Set<BlockPos> set = Sets.newHashSet();
        int i = 16;

        for (int j = 0; j < 16; j++) {
            for (int k = 0; k < 16; k++) {
                for (int l = 0; l < 16; l++) {
                    if (j == 0 || j == 15 || k == 0 || k == 15 || l == 0 || l == 15) {
                        double d = j / 15.0F * 2.0F - 1.0F;
                        double e = k / 15.0F * 2.0F - 1.0F;
                        double f = l / 15.0F * 2.0F - 1.0F;
                        double g = Math.sqrt(d * d + e * e + f * f);
                        d /= g;
                        e /= g;
                        f /= g;
                        float h = this.radius * (0.7F + this.level.random.nextFloat() * 0.6F);
                        double m = this.x;
                        double n = this.y;
                        double o = this.z;
                        float p = 0.3F;

                        while (h > 0.0F) {
                            BlockPos blockPos = BlockPos.containing(m, n, o);
                            BlockState blockState = this.level.getBlockState(blockPos);
                            FluidState fluidState = this.level.getFluidState(blockPos);
                            if (!this.level.isInWorldBounds(blockPos)) {
                                break;
                            }

                            Optional<Float> optional = this.damageCalculator.getBlockExplosionResistance(this, this.level, blockPos, blockState, fluidState);
                            if (optional.isPresent()) {
                                h -= (optional.get() + 0.3F) * 0.3F;
                            }

                            if (h > 0.0F && this.damageCalculator.shouldBlockExplode(this, this.level, blockPos, blockState, h)) {
                                set.add(blockPos);
                            }

                            m += d * 0.3F;
                            n += e * 0.3F;
                            o += f * 0.3F;
                            h -= 0.22500001F;
                        }
                    }
                }
            }
        }

        this.toBlow.addAll(set);
        float q = this.radius * 2.0F;
        int k = Mth.floor(this.x - q - 1.0);
        int l = Mth.floor(this.x + q + 1.0);
        int r = Mth.floor(this.y - q - 1.0);
        int s = Mth.floor(this.y + q + 1.0);
        int t = Mth.floor(this.z - q - 1.0);
        int u = Mth.floor(this.z + q + 1.0);
        List<Entity> list = this.level.getEntities(this.source, new AABB(k, r, t, l, s, u));
        Vec3 vec3 = new Vec3(this.x, this.y, this.z);

        for (Entity entity : list) {
            if (!entity.ignoreExplosion(this)) {
                double v = Math.sqrt(entity.distanceToSqr(vec3)) / q;
                if (v <= 1.0) {
                    double w = entity.getX() - this.x;
                    double x = (entity instanceof PrimedTnt ? entity.getY() : entity.getEyeY()) - this.y;
                    double y = entity.getZ() - this.z;
                    double z = Math.sqrt(w * w + x * x + y * y);
                    if (z != 0.0) {
                        w /= z;
                        x /= z;
                        y /= z;
                        if (this.damageCalculator.shouldDamageEntity(this, entity)) {
                            entity.hurt(this.damageSource, this.damageCalculator.getEntityDamageAmount(this, entity));
                        }

                        double aa = (1.0 - v) * getSeenPercent(vec3, entity) * this.damageCalculator.getKnockbackMultiplier(entity);
                        double ab;
                        if (entity instanceof LivingEntity livingEntity) {
                            ab = aa * (1.0 - livingEntity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
                        } else {
                            ab = aa;
                        }

                        w *= ab;
                        x *= ab;
                        y *= ab;
                        Vec3 vec32 = new Vec3(w, x, y);
                        entity.setDeltaMovement(entity.getDeltaMovement().add(vec32));
                        if (entity instanceof Player player && !player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying)) {
                            this.hitPlayers.put(player, vec32);
                        }

                        entity.onExplosionHit(this.source);
                    }
                }
            }
        }
    }

    /**
     * Does the second part of the explosion (sound, particles, drop spawn)
     */
    public void finalizeExplosion(boolean spawnParticles) {
        if (this.level.isClientSide) {
            this.level
                .playLocalSound(
                    this.x,
                    this.y,
                    this.z,
                    this.explosionSound.value(),
                    SoundSource.BLOCKS,
                    4.0F,
                    (1.0F + (this.level.random.nextFloat() - this.level.random.nextFloat()) * 0.2F) * 0.7F,
                    false
                );
        }

        boolean bl = this.interactsWithBlocks();
        if (spawnParticles) {
            ParticleOptions particleOptions;
            if (!(this.radius < 2.0F) && bl) {
                particleOptions = this.largeExplosionParticles;
            } else {
                particleOptions = this.smallExplosionParticles;
            }

            this.level.addParticle(particleOptions, this.x, this.y, this.z, 1.0, 0.0, 0.0);
        }

        if (bl) {
            this.level.getProfiler().push("explosion_blocks");
            List<Pair<ItemStack, BlockPos>> list = new ArrayList<>();
            Util.shuffle(this.toBlow, this.level.random);

            for (BlockPos blockPos : this.toBlow) {
                this.level
                    .getBlockState(blockPos)
                    .onExplosionHit(this.level, blockPos, this, (itemStack, blockPosx) -> addOrAppendStack(list, itemStack, blockPosx));
            }

            for (Pair<ItemStack, BlockPos> pair : list) {
                Block.popResource(this.level, pair.getSecond(), pair.getFirst());
            }

            this.level.getProfiler().pop();
        }

        if (this.fire) {
            for (BlockPos blockPos2 : this.toBlow) {
                if (this.random.nextInt(3) == 0
                    && this.level.getBlockState(blockPos2).isAir()
                    && this.level.getBlockState(blockPos2.below()).isSolidRender(this.level, blockPos2.below())) {
                    this.level.setBlockAndUpdate(blockPos2, BaseFireBlock.getState(this.level, blockPos2));
                }
            }
        }
    }

    private static void addOrAppendStack(List<Pair<ItemStack, BlockPos>> drops, ItemStack stack, BlockPos pos) {
        for (int i = 0; i < drops.size(); i++) {
            Pair<ItemStack, BlockPos> pair = drops.get(i);
            ItemStack itemStack = pair.getFirst();
            if (ItemEntity.areMergable(itemStack, stack)) {
                drops.set(i, Pair.of(ItemEntity.merge(itemStack, stack, 16), pair.getSecond()));
                if (stack.isEmpty()) {
                    return;
                }
            }
        }

        drops.add(Pair.of(stack, pos));
    }

    public boolean interactsWithBlocks() {
        return this.blockInteraction != Explosion.BlockInteraction.KEEP;
    }

    public Map<Player, Vec3> getHitPlayers() {
        return this.hitPlayers;
    }

    @Nullable
    private static LivingEntity getIndirectSourceEntityInternal(@Nullable Entity source) {
        if (source == null) {
            return null;
        } else if (source instanceof PrimedTnt primedTnt) {
            return primedTnt.getOwner();
        } else if (source instanceof LivingEntity livingEntity) {
            return livingEntity;
        } else {
            return source instanceof Projectile projectile && projectile.getOwner() instanceof LivingEntity livingEntity2 ? livingEntity2 : null;
        }
    }

    @Nullable
    public LivingEntity getIndirectSourceEntity() {
        return getIndirectSourceEntityInternal(this.source);
    }

    @Nullable
    public Entity getDirectSourceEntity() {
        return this.source;
    }

    public void clearToBlow() {
        this.toBlow.clear();
    }

    public List<BlockPos> getToBlow() {
        return this.toBlow;
    }

    public Explosion.BlockInteraction getBlockInteraction() {
        return this.blockInteraction;
    }

    public ParticleOptions getSmallExplosionParticles() {
        return this.smallExplosionParticles;
    }

    public ParticleOptions getLargeExplosionParticles() {
        return this.largeExplosionParticles;
    }

    public Holder<SoundEvent> getExplosionSound() {
        return this.explosionSound;
    }

    public boolean canTriggerBlocks() {
        if (this.blockInteraction == Explosion.BlockInteraction.TRIGGER_BLOCK && !this.level.isClientSide()) {
            return this.source != null && this.source.getType() == EntityType.BREEZE_WIND_CHARGE
                ? this.level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                : true;
        } else {
            return false;
        }
    }

    public enum BlockInteraction {
        KEEP,
        DESTROY,
        DESTROY_WITH_DECAY,
        TRIGGER_BLOCK;
    }
}
