package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.google.common.base.Preconditions;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalBooleanRef;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.salutaryfish.versionunbound.neoforge.patches.EntityDropsCapture;
import net.salutaryfish.versionunbound.neoforge.patches.LivingEntityDamageContainers;
import net.salutaryfish.versionunbound.neoforge.patches.LivingEntityRandomTeleport;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code LivingEntity}, for the events it fires: the damage pipeline (incoming damage, damage pre
 * and post, around a stack of {@link DamageContainer}s), death (cancellable), drops (the death loot is captured and
 * added to the level unless the event is canceled; {@code drop} adds to the capture too, which covers a player's
 * inventory and tossed items), knockback, equipment changes, item use (start, tick, stop and finish, and
 * {@code PlayerDestroyItemEvent} when releasing uses up a player's item), healing, falling and jumping (the mobs that
 * jump their own way fire it in their own mixins). {@code ServerPlayer.die} fires the death event itself
 * ({@code ServerPlayerMixin}), and {@code Player} has its own {@code actuallyHurt} ({@code PlayerMixin}). Also
 * {@code EntityTeleportEvent} in {@code randomTeleport} (for a consumed item, or an enderman), with NeoForge's overload
 * that takes the item; the air supply ({@code LivingBreatheEvent} and {@code LivingDrownEvent}, in
 * {@code CommonHooks.onLivingBreathe}) and {@code LivingExperienceDropEvent}. And mob effects: {@code MobEffectEvent}
 * ({@code Applicable} in place of {@code canBeAffected} when adding, also forced; {@code Added}; {@code Remove} for one
 * effect or all, cancelable for each; {@code Expired}, cancelable) and {@code EffectParticleModificationEvent}.
 *
 * <p>{@code hurtServer} keeps vanilla's code with NeoForge's additions injected; one difference is that vanilla sets
 * {@code lastHurt} and the invulnerability ticks before {@code actuallyHurt} in the normal case (NeoForge after), so
 * they already have their new values during {@code LivingDamageEvent.Pre}; the ticks end up as the container says.
 * {@code actuallyHurt} is replaced by NeoForge's version. Not yet: {@code onDamageTaken} (the living entity extension,
 * Phase 2 step 5), the shield event ({@code LivingShieldBlockEvent} in {@code applyItemBlocking}, which needs
 * NeoForge's six-argument {@code BlocksAttacks.hurtBlockingItem}), and the armor durability event
 * ({@code ArmorHurtEvent} in {@code doHurtEquipment}).
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin implements LivingEntityDamageContainers, LivingEntityRandomTeleport {
    // Neo: one container per hurtServer call in progress. Added under NeoForge's name, for mods that read it.
    // (invulnerableTime is Entity's, which a mixin can't shadow from here; it's public.)
    protected Stack<DamageContainer> damageContainers = new Stack<>();

    @Shadow
    protected int lastHurtByPlayerMemoryTime;

    @Shadow
    protected float lastHurt;

    @Shadow
    protected boolean dead;

    @Shadow
    protected int useItemRemaining;

    @Shadow
    protected ItemStack useItem;

    @Shadow
    public abstract int getUseItemRemainingTicks();

    @Shadow
    protected abstract void completeUsingItem();

    @Shadow
    protected abstract float getDamageAfterArmorAbsorb(DamageSource damageSource, float damage);

    @Shadow
    protected abstract float getDamageAfterMagicAbsorb(DamageSource damageSource, float damage);

    @Shadow
    protected abstract int decreaseAirSupply(int currentSupply);

    @Shadow
    protected abstract int increaseAirSupply(int currentSupply);

    @Override
    public Stack<DamageContainer> versionunbound$damageContainers() {
        return this.damageContainers;
    }

    @Override
    public boolean versionunbound$isDead() {
        return this.dead;
    }

    @Override
    public float versionunbound$getDamageAfterArmorAbsorb(DamageSource source, float damage) {
        return this.getDamageAfterArmorAbsorb(source, damage);
    }

    @Override
    public float versionunbound$getDamageAfterMagicAbsorb(DamageSource source, float damage) {
        return this.getDamageAfterMagicAbsorb(source, damage);
    }

    // Each call pops the containers it pushed, whichever way it ends: NeoForge pops at its own returns, and other mods
    // (Fabric API's ALLOW_DAMAGE among them) may return early from the same method.
    @WrapMethod(method = "hurtServer")
    private boolean versionunbound$damageContainerScope(ServerLevel level, DamageSource source, float damage, Operation<Boolean> original) {
        int depth = this.damageContainers.size();
        try {
            return original.call(level, source, damage);
        } finally {
            while (this.damageContainers.size() > depth) {
                this.damageContainers.pop();
            }
        }
    }

    // After the invulnerability, death and fire resistance checks.
    @Inject(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"), cancellable = true)
    private void versionunbound$incomingDamage(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        this.damageContainers.push(new DamageContainer(source, damage));
        if (CommonHooks.onEntityIncomingDamage((LivingEntity) (Object) this, this.damageContainers.peek())) {
            this.damageContainers.pop();
            cir.setReturnValue(false);
        }
    }

    // Neo: enforce damage container as source of truth for damage amount
    @ModifyVariable(method = "hurtServer", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;noActionTime:I", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), argsOnly = true)
    private float versionunbound$damageFromContainer(float damage) {
        return this.damageContainers.peek().getNewDamage();
    }

    // Update container with vanilla changes (blocking, freezing, helmets), before the invulnerability check.
    @Inject(method = "hurtServer", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;invulnerableTime:I", opcode = Opcodes.GETFIELD, ordinal = 0))
    private void versionunbound$updateContainer(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        this.damageContainers.peek().setNewDamage(damage);
    }

    // If we are going to do damage despite invulnerability (due to the damage being higher than prior), we need to setup
    // the container differently.
    @Inject(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V", ordinal = 0))
    private void versionunbound$damageDespiteInvulnerability(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        DamageContainer container = this.damageContainers.peek();
        container.setShouldCauseSideEffects(false);
        container.setPostAttackInvulnerabilityTicks(((LivingEntity) (Object) this).invulnerableTime);
        container.setReduction(DamageContainer.Reduction.INVULNERABILITY, this.lastHurt);
    }

    // The invulnerability ticks come from the container, in both branches (after lastHurt in the first, after
    // actuallyHurt in the second).
    @Inject(method = "hurtServer", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;lastHurt:F", opcode = Opcodes.PUTFIELD, ordinal = 0, shift = At.Shift.AFTER))
    private void versionunbound$invulnerabilityTicksAfterInvulnerableDamage(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        ((LivingEntity) (Object) this).invulnerableTime = this.damageContainers.peek().getPostAttackInvulnerabilityTicks();
    }

    @Inject(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;actuallyHurt(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)V", ordinal = 1, shift = At.Shift.AFTER))
    private void versionunbound$invulnerabilityTicksAfterDamage(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        ((LivingEntity) (Object) this).invulnerableTime = this.damageContainers.peek().getPostAttackInvulnerabilityTicks();
    }

    // Neo: vanilla's tookFullDamage (the second boolean local) becomes "performSideEffects", as the container says.
    @ModifyVariable(method = "hurtServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;resolveMobResponsibleForDamage(Lnet/minecraft/world/damagesource/DamageSource;)V"), ordinal = 1)
    private boolean versionunbound$performSideEffects(boolean tookFullDamage) {
        return this.damageContainers.peek().shouldCauseSideEffects();
    }

    // Neo: Fire our post-damage event hooks. As usual, fire the extension method first, then the event version.
    @Inject(method = "hurtServer", at = @At("TAIL"))
    private void versionunbound$damagePost(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        // VersionUnbound: this.onDamageTaken(container) comes with the living entity extension (Phase 2 step 5).
        CommonHooks.onLivingDamagePost((LivingEntity) (Object) this, this.damageContainers.peek());
        this.damageContainers.pop();
    }

    // Inside the resistance block, when vanilla counts the resisted damage.
    @ModifyVariable(method = "getDamageAfterMagicAbsorb", at = @At("STORE"), ordinal = 3)
    private float versionunbound$mobEffectsReduction(float damageResisted) {
        if (damageResisted > 0.0F && damageResisted < 3.4028235E37F && !this.damageContainers.isEmpty()) {
            this.damageContainers.peek().setReduction(DamageContainer.Reduction.MOB_EFFECTS, damageResisted);
        }
        return damageResisted;
    }

    @ModifyExpressionValue(method = "getDamageAfterMagicAbsorb", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/CombatRules;getDamageAfterMagicAbsorb(FF)F"))
    private float versionunbound$enchantmentsReduction(float damage) {
        if (!this.damageContainers.isEmpty()) {
            this.damageContainers.peek().setReduction(DamageContainer.Reduction.ENCHANTMENTS, this.damageContainers.peek().getNewDamage() - damage);
        }
        return damage;
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's damage pipeline: armor and enchantment reductions are recorded against the container's damage,
     *         {@code LivingDamageEvent.Pre} decides the damage, and absorption is taken from what remains.
     */
    @Overwrite
    protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.isInvulnerableTo(level, source)) {
            DamageContainer container = this.versionunbound$currentDamageContainer(source, dmg);
            // Neo (ImplNote): Keep this in-sync with the equivalent patches in Player#actuallyHurt.
            // Neo: Primary damage pipeline hooks. Compute the armor and enchantment reductions against the "original" damage, and then fire LivingDamageEvent.Pre
            container.setReduction(DamageContainer.Reduction.ARMOR, container.getNewDamage() - this.getDamageAfterArmorAbsorb(source, container.getNewDamage()));
            this.getDamageAfterMagicAbsorb(source, container.getNewDamage());

            // LivingDamageEvent.Pre is then able to mutate the container to change the reductions and change the "real" damage that will be inflicted.
            float damage = CommonHooks.onLivingDamagePre(self, container);
            // Throw an exception if someone kills the entity during Pre. Vanilla is going to kill any entity which isDeadOrDying() when it bubbles back up to hurtServer, so it dying early is invalid.
            Preconditions.checkArgument(!this.dead, "It is invalid to have killed an entity during LivingDamageEvent.Pre. Use LivingIncomingDamageEvent or LivingDamageEvent.Post instead.");

            // Store a copy of the "true" inflicted damage so we can propagate it back to hurtServer.
            container.captureInflictedDamage();

            // Next we attribute damage to absorption hearts. We record the amount blocked by absorption in the container for later calculations.
            // Mods reduction functions may adjust the split of damage between absorption and health, so we need to first apply the reductions and then compute the amounts that go to each pool.
            container.setReduction(DamageContainer.Reduction.ABSORPTION, Math.min(self.getAbsorptionAmount(), damage));
            float absorbedDamage = Math.min(damage, container.getReduction(DamageContainer.Reduction.ABSORPTION));
            self.setAbsorptionAmount(Math.max(0, self.getAbsorptionAmount() - absorbedDamage));
            dmg = container.getNewDamage();

            if (absorbedDamage > 0.0F && absorbedDamage < 3.4028235E37F && source.getEntity() instanceof ServerPlayer serverPlayer) {
                serverPlayer.awardStat(Stats.DAMAGE_DEALT_ABSORBED, Math.round(absorbedDamage * 10.0F));
            }

            // Last thing here, inflict damage to the entity's health, based on how much damage is remaining after absorption has been processes.
            if (dmg != 0.0F) {
                self.getCombatTracker().recordDamage(source, dmg);
                self.setHealth(self.getHealth() - dmg);
                self.gameEvent(GameEvent.ENTITY_DAMAGE);
            }
            // Neo: Now we go back to hurtServer, where we'll update various metadata fields, process the side effects of damage (i.e. killing the entity), and firing LivingDamageEvent.Post
        }
    }

    // NeoForge's code at the head of knockback: the event can cancel it or change its strength and direction.
    @WrapMethod(method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V")
    private void versionunbound$knockBack(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect, Operation<Void> original) {
        LivingKnockBackEvent event = CommonHooks.onLivingKnockBack((LivingEntity) (Object) this, (float) power, xd, zd);
        if (event.isCanceled()) return;
        original.call((double) event.getStrength(), event.getRatioX(), event.getRatioZ(), source, damage, comesFromEffect);
    }

    @WrapOperation(method = "collectEquipmentChanges", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;equipmentHasChanged(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z"))
    private boolean versionunbound$equipmentChange(LivingEntity entity, ItemStack previous, ItemStack current, Operation<Boolean> original, @Local EquipmentSlot slot) {
        boolean changed = original.call(entity, previous, current);
        if (changed) NeoForge.EVENT_BUS.post(new LivingEquipmentChangeEvent(entity, slot, previous, current));
        return changed;
    }

    // Before the item in use is set.
    @Inject(method = "startUsingItem", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;useItem:Lnet/minecraft/world/item/ItemStack;", opcode = Opcodes.PUTFIELD), cancellable = true)
    private void versionunbound$useItemStart(InteractionHand hand, CallbackInfo ci, @Local ItemStack itemStack, @Share("useDuration") LocalIntRef duration) {
        duration.set(EventHooks.onItemUseStart((LivingEntity) (Object) this, itemStack, hand, itemStack.getUseDuration((LivingEntity) (Object) this)));
        if (duration.get() < 0) ci.cancel(); // Neo: Early return for negative values, as that indicates event cancellation.
    }

    @ModifyExpressionValue(method = "startUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseDuration(Lnet/minecraft/world/entity/LivingEntity;)I"))
    private int versionunbound$useDuration(int vanillaDuration, @Share("useDuration") LocalIntRef duration) {
        return duration.get();
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason {@code LivingEntityUseItemEvent.Tick} sets the remaining use time each tick, so the item only ticks while
     *         time remains, and it finishes once none does.
     */
    @Overwrite
    protected void updateUsingItem(ItemStack useItem) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!useItem.isEmpty())
            this.useItemRemaining = EventHooks.onItemUseTick(self, useItem, this.getUseItemRemainingTicks());
        if (this.getUseItemRemainingTicks() > 0)
            useItem.onUseTick(self.level(), self, this.getUseItemRemainingTicks());
        if (--this.useItemRemaining <= 0 && !self.level().isClientSide() && !useItem.useOnRelease()) {
            this.completeUsingItem();
        }
    }

    @WrapOperation(method = "completeUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;finishUsingItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack versionunbound$useItemFinish(ItemStack useItem, Level level, LivingEntity entity, Operation<ItemStack> original) {
        ItemStack copy = useItem.copy();
        return EventHooks.onItemUseFinish(entity, copy, this.getUseItemRemainingTicks(), original.call(useItem, level, entity));
    }

    // A canceled stop keeps the item from being released; a player's item used up by the release fires
    // PlayerDestroyItemEvent (NeoForge checks the useItem field afterwards, not the stack it released).
    @WrapOperation(method = "releaseUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;releaseUsing(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V"))
    private void versionunbound$useItemStop(ItemStack useItem, Level level, LivingEntity entity, int remaining, Operation<Void> original) {
        if (!EventHooks.onUseItemStop(entity, useItem, remaining)) {
            ItemStack copy = entity instanceof Player ? useItem.copy() : null;
            original.call(useItem, level, entity, remaining);
            if (copy != null && this.useItem.isEmpty()) EventHooks.onPlayerDestroyItem((Player) entity, copy, entity.getUsedItemHand());
        }
    }

    // NeoForge's code at the head of heal: the event can change the amount, and nothing heals if it's canceled or
    // the amount isn't positive.
    @Inject(method = "heal", at = @At("HEAD"), cancellable = true)
    private void versionunbound$livingHeal(float amount, CallbackInfo ci, @Local(argsOnly = true) LocalFloatRef heal) {
        heal.set(EventHooks.onLivingHeal((LivingEntity) (Object) this, heal.get()));
        if (heal.get() <= 0) ci.cancel();
    }

    // After the fall distance is adjusted for the current impulse, before the super call: a canceled fall causes no
    // damage, and the event's distance and multiplier replace vanilla's for the rest of the method. (A super call's
    // receiver is typed as the calling class.)
    @WrapOperation(method = "causeFallDamage", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;causeFallDamage(DFLnet/minecraft/world/damagesource/DamageSource;)Z"))
    private boolean versionunbound$livingFall(LivingEntity entity, double effectiveFallDistance, float damageModifier, DamageSource source, Operation<Boolean> original,
            @Local(ordinal = 1) LocalDoubleRef distance, @Local(argsOnly = true) LocalFloatRef modifier, @Cancellable CallbackInfoReturnable<Boolean> cir) {
        LivingFallEvent event = CommonHooks.onLivingFall(entity, effectiveFallDistance, damageModifier);
        if (event.isCanceled()) {
            cir.setReturnValue(false);
            return false;
        }
        distance.set(event.getDistance());
        modifier.set(event.getDamageMultiplier());
        return original.call(entity, event.getDistance(), event.getDamageMultiplier(), source);
    }

    @Inject(method = "jumpFromGround", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/LivingEntity;needsSync:Z", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void versionunbound$livingJump(CallbackInfo ci) {
        CommonHooks.onLivingJump((LivingEntity) (Object) this);
    }

    // Neo: Fire the LivingDeathEvent after validating that the entity isn't already dead, but before trying to kill it.
    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/damagesource/DamageSource;getEntity()Lnet/minecraft/world/entity/Entity;", ordinal = 0), cancellable = true)
    private void versionunbound$livingDeath(DamageSource source, CallbackInfo ci) {
        if (CommonHooks.onLivingDeath((LivingEntity) (Object) this, source)) ci.cancel();
    }

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void versionunbound$captureDeathLoot(ServerLevel level, DamageSource source, CallbackInfo ci) {
        ((EntityDropsCapture) this).captureDrops(new ArrayList<>());
    }

    @Inject(method = "dropAllDeathLoot", at = @At("TAIL"))
    private void versionunbound$livingDrops(ServerLevel level, DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        Collection<ItemEntity> drops = ((EntityDropsCapture) this).captureDrops(null);
        if (!CommonHooks.onLivingDrops(self, source, drops, lastHurtByPlayerMemoryTime > 0))
            drops.forEach(e -> self.level().addFreshEntity(e));
    }

    @WrapOperation(method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean versionunbound$captureDrop(Level level, Entity entity, Operation<Boolean> original) {
        Collection<ItemEntity> drops = ((EntityDropsCapture) this).captureDrops();
        if (drops != null) {
            drops.add((ItemEntity) entity);
            return true;
        }
        return original.call(level, entity);
    }

    // Set by NeoForge's randomTeleport overload for the vanilla method it calls, which takes it at its head.
    @Unique
    private @Nullable ItemStack versionunbound$teleportStack;

    // Neo: Add overload with the consumed item causing the random teleport
    // VersionUnbound: vanilla's method keeps its body (other mods' mixins target it); this overload hands it the stack.
    @Override
    public boolean randomTeleport(double xx, double yy, double zz, boolean showParticles, ItemStack consumedStack) {
        this.versionunbound$teleportStack = consumedStack;
        return ((LivingEntity) (Object) this).randomTeleport(xx, yy, zz, showParticles);
    }

    @Inject(method = "randomTeleport(DDDZ)Z", at = @At("HEAD"))
    private void versionunbound$takeConsumedStack(double xx, double yy, double zz, boolean showParticles, CallbackInfoReturnable<Boolean> cir,
            @Share("consumedStack") LocalRef<ItemStack> consumedStack) {
        consumedStack.set(this.versionunbound$teleportStack == null ? ItemStack.EMPTY : this.versionunbound$teleportStack);
        this.versionunbound$teleportStack = null;
    }

    // Neo: Fire event for either item consumption or EnderMan teleport, to get the actual final position being teleported to
    @WrapOperation(method = "randomTeleport(DDDZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;teleportTo(DDD)V", ordinal = 0))
    private void versionunbound$entityTeleport(LivingEntity self, double xx, double y, double zz, Operation<Void> original,
            @Share("consumedStack") LocalRef<ItemStack> consumedStack, @Share("teleported") LocalBooleanRef teleported) {
        EntityTeleportEvent event = null;
        if (!consumedStack.get().isEmpty()) {
            event = EventHooks.onItemConsumptionTeleport(self, consumedStack.get(), xx, y, zz);
        } else if (self instanceof EnderMan) {
            event = EventHooks.onEnderTeleport(self, xx, y, zz);
        }
        if (event != null) {
            xx = event.getTargetX();
            y = event.getTargetY();
            zz = event.getTargetZ();
        }
        if (event == null || !event.isCanceled()) {
            teleported.set(true);
            original.call(self, xx, y, zz);
        }
    }

    // Vanilla checks the destination right after teleporting there; NeoForge skips the check when the event canceled it.
    @WrapOperation(method = "randomTeleport(DDDZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;noCollision(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean versionunbound$checkIfTeleported(Level level, Entity entity, Operation<Boolean> original, @Share("teleported") LocalBooleanRef teleported) {
        return teleported.get() && original.call(level, entity);
    }

    // Neo: Teleport back to original only if we teleported away and need to revert
    @WrapWithCondition(method = "randomTeleport(DDDZ)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;teleportTo(DDD)V", ordinal = 1))
    private boolean versionunbound$revertIfTeleported(LivingEntity self, double xo, double yo, double zo, @Share("teleported") LocalBooleanRef teleported) {
        return teleported.get();
    }

    // The air supply: CommonHooks.onLivingBreathe replaces vanilla's block (`if eyes in water {...} else if air is below
    // the maximum {...}`), which NeoForge's patch skips with `if (false)`; here both conditions are made false.
    @Inject(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private void versionunbound$breathe(CallbackInfo ci, @Local ServerLevel level) {
        LivingEntity self = (LivingEntity) (Object) this;
        int airSupply = self.getAirSupply();
        CommonHooks.onLivingBreathe(self, level, airSupply - this.decreaseAirSupply(airSupply), this.increaseAirSupply(airSupply) - airSupply);
    }

    @ModifyExpressionValue(method = "baseTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isEyeInFluid(Lnet/minecraft/tags/TagKey;)Z"))
    private boolean versionunbound$skipVanillaAirInWater(boolean eyeInWater) {
        return false;
    }

    @Definition(id = "getAirSupply", method = "Lnet/minecraft/world/entity/LivingEntity;getAirSupply()I")
    @Definition(id = "getMaxAirSupply", method = "Lnet/minecraft/world/entity/LivingEntity;getMaxAirSupply()I")
    @Expression("this.getAirSupply() < this.getMaxAirSupply()")
    @ModifyExpressionValue(method = "baseTick", at = @At("MIXINEXTRAS:EXPRESSION"))
    private boolean versionunbound$skipVanillaAirRefill(boolean belowMaximum) {
        return false;
    }

    @ModifyArg(method = "dropExperience", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"), index = 2)
    private int versionunbound$experienceDrop(int reward) {
        LivingEntity self = (LivingEntity) (Object) this;
        return EventHooks.getExperienceDrop(self, self.getLastHurtByPlayer(), reward);
    }

    // An expired effect whose MobEffectEvent.Expired is canceled stays, and isn't reported as removed.
    @WrapWithCondition(method = "tickEffects", at = @At(value = "INVOKE", target = "Ljava/util/Iterator;remove()V"))
    private boolean versionunbound$effectExpired(Iterator<Holder<MobEffect>> iterator, @Local(name = "effect") MobEffectInstance effect,
            @Share("expiryCanceled") LocalBooleanRef canceled) {
        canceled.set(NeoForge.EVENT_BUS.post(new MobEffectEvent.Expired((LivingEntity) (Object) this, effect)).isCanceled());
        return !canceled.get();
    }

    @WrapWithCondition(method = "tickEffects", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;onEffectsRemoved(Ljava/util/Collection;)V"))
    private boolean versionunbound$expiredEffectRemoved(LivingEntity self, Collection<MobEffectInstance> effects, @Share("expiryCanceled") LocalBooleanRef canceled) {
        return !canceled.get();
    }

    // EffectParticleModificationEvent decides each effect's visibility and particles: vanilla's filter and map, which ask
    // the effect, become NeoForge's, which ask the event.
    @WrapOperation(method = "updateSynchronizedMobEffectParticles", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;filter(Ljava/util/function/Predicate;)Ljava/util/stream/Stream;"))
    private Stream<EffectParticleModificationEvent> versionunbound$effectParticleEvents(Stream<MobEffectInstance> effects, Predicate<MobEffectInstance> isVisible,
            Operation<Stream<MobEffectInstance>> original) {
        return effects.map(effect -> NeoForge.EVENT_BUS.post(new EffectParticleModificationEvent((LivingEntity) (Object) this, effect)))
                .filter(EffectParticleModificationEvent::isVisible);
    }

    @WrapOperation(method = "updateSynchronizedMobEffectParticles", at = @At(value = "INVOKE", target = "Ljava/util/stream/Stream;map(Ljava/util/function/Function;)Ljava/util/stream/Stream;"))
    private Stream<ParticleOptions> versionunbound$effectParticleOptions(Stream<EffectParticleModificationEvent> events, Function<MobEffectInstance, ParticleOptions> getParticleOptions,
            Operation<Stream<ParticleOptions>> original) {
        return events.map(EffectParticleModificationEvent::getParticleOptions);
    }

    // Each effect's MobEffectEvent.Remove can keep it. The copy vanilla reports as removed holds the others, and the kept
    // ones go back once vanilla has cleared the map. (NeoForge removes only the others instead. Fabric API wraps the same
    // clear() to keep the effects its own event refuses to remove; this way both kinds stay.)
    @WrapOperation(method = "removeAllEffects", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Maps;newHashMap(Ljava/util/Map;)Ljava/util/HashMap;"))
    private HashMap<Holder<MobEffect>, MobEffectInstance> versionunbound$effectsRemoved(Map<Holder<MobEffect>, MobEffectInstance> activeEffects,
            Operation<HashMap<Holder<MobEffect>, MobEffectInstance>> original, @Share("keptEffects") LocalRef<Map<Holder<MobEffect>, MobEffectInstance>> kept) {
        HashMap<Holder<MobEffect>, MobEffectInstance> map = new HashMap<>(activeEffects.size());
        kept.set(new HashMap<>());
        for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : activeEffects.entrySet()) {
            if (!EventHooks.onEffectRemoved((LivingEntity) (Object) this, entry.getValue())) {
                map.put(entry.getKey(), entry.getValue());
            } else {
                kept.get().put(entry.getKey(), entry.getValue());
            }
        }
        return map;
    }

    @Inject(method = "removeAllEffects", at = @At(value = "INVOKE", target = "Ljava/util/Map;clear()V", shift = At.Shift.AFTER))
    private void versionunbound$keepEffects(CallbackInfoReturnable<Boolean> cir, @Share("keptEffects") LocalRef<Map<Holder<MobEffect>, MobEffectInstance>> kept) {
        ((LivingEntity) (Object) this).getActiveEffectsMap().putAll(kept.get());
    }

    // MobEffectEvent.Applicable decides in place of canBeAffected, which the event asks when it has no result of its own.
    @WrapOperation(method = {"addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", "forceAddEffect"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;canBeAffected(Lnet/minecraft/world/effect/MobEffectInstance;)Z"))
    private boolean versionunbound$effectApplicable(LivingEntity self, MobEffectInstance newEffect, Operation<Boolean> original, @Local(argsOnly = true) @Nullable Entity source) {
        return CommonHooks.canMobEffectBeApplied(self, newEffect, source);
    }

    // Once the effect the entity already has is known, before it's added or updated (whether or not the update changes it).
    @ModifyVariable(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z", at = @At("STORE"), name = "effect")
    private @Nullable MobEffectInstance versionunbound$effectAdded(@Nullable MobEffectInstance effect, MobEffectInstance newEffect, @Nullable Entity source) {
        NeoForge.EVENT_BUS.post(new MobEffectEvent.Added((LivingEntity) (Object) this, effect, newEffect, source));
        return effect;
    }

    @Inject(method = "removeEffect", at = @At("HEAD"), cancellable = true)
    private void versionunbound$effectRemove(Holder<MobEffect> effect, CallbackInfoReturnable<Boolean> cir) {
        if (EventHooks.onEffectRemoved((LivingEntity) (Object) this, effect)) cir.setReturnValue(false);
    }
}
