package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.google.common.base.Preconditions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Stack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.salutaryfish.versionunbound.neoforge.patches.EntityDropsCapture;
import net.salutaryfish.versionunbound.neoforge.patches.LivingEntityDamageContainers;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code LivingEntity}, for the events it fires: the damage pipeline (incoming damage, damage pre
 * and post, around a stack of {@link DamageContainer}s), death (cancellable), drops (the death loot is captured and
 * added to the level unless the event is canceled), knockback, equipment changes, and item use (start, tick, stop and
 * finish). {@code ServerPlayer.die} fires the death event itself
 * ({@code ServerPlayerMixin}), and {@code Player} has its own {@code actuallyHurt} ({@code PlayerMixin}).
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
abstract class LivingEntityMixin implements LivingEntityDamageContainers {
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
    public abstract int getUseItemRemainingTicks();

    @Shadow
    protected abstract void completeUsingItem();

    @Shadow
    protected abstract float getDamageAfterArmorAbsorb(DamageSource damageSource, float damage);

    @Shadow
    protected abstract float getDamageAfterMagicAbsorb(DamageSource damageSource, float damage);

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

    // Not yet: PlayerDestroyItemEvent when releasing uses up a player's item.
    @WrapWithCondition(method = "releaseUsingItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;releaseUsing(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;I)V"))
    private boolean versionunbound$useItemStop(ItemStack useItem, Level level, LivingEntity entity, int remaining) {
        return !EventHooks.onUseItemStop(entity, useItem, remaining);
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
}
