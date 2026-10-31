package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.google.common.base.Preconditions;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.EventHooks;
import net.salutaryfish.versionunbound.neoforge.patches.LivingEntityDamageContainers;
import net.salutaryfish.versionunbound.neoforge.patches.PlayerExtension;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code Player}, for the events it fires: player ticks (wrapping {@code Player.tick()}, so inside
 * the entity tick events), entity interaction and attack (both cancellable, on both sides), and the damage pipeline in
 * the player's own {@code actuallyHurt} (see {@code LivingEntityMixin}), and {@code ItemTossEvent} for items the player
 * drops ({@code CommonHooks.onPlayerTossEvent} drops them through the drop capture), and {@code PlayerDestroyItemEvent}
 * when an entity interaction uses up the held item or an attack breaks the weapon, {@code PlayerWakeUpEvent} when
 * the player leaves a bed (before anything else; {@code ServerPlayer}'s override tells the clients first), and
 * {@code PlayerEvent.BreakSpeed} on the player's destroy speed, with NeoForge's position-sensitive overloads of
 * {@code getDestroySpeed} and {@code hasCorrectToolForDrops} ({@link PlayerExtension}), and
 * {@code PlayerFlyableFallEvent} when a player who may fly lands.
 */
@Mixin(Player.class)
abstract class PlayerMixin implements PlayerExtension {
    // The position getDestroySpeed(BlockState, BlockPos) hands vanilla's getDestroySpeed, for its BreakSpeed event.
    @Unique
    private @Nullable BlockPos versionunbound$destroySpeedPos;

    // Neo: NeoForge's overload, which vanilla's (deprecated there) calls with no position. Here vanilla's keeps its body
    // (other mods' mixins target it) and this one hands it the position.
    @Override
    public float getDestroySpeed(BlockState state, @Nullable BlockPos pos) {
        BlockPos previous = this.versionunbound$destroySpeedPos;
        this.versionunbound$destroySpeedPos = pos;
        try {
            return ((Player) (Object) this).getDestroySpeed(state);
        } finally {
            this.versionunbound$destroySpeedPos = previous;
        }
    }

    @Override
    public boolean hasCorrectToolForDrops(BlockState state, Level level, BlockPos pos) {
        return EventHooks.doPlayerHarvestCheck((Player) (Object) this, state, level, pos);
    }

    @ModifyReturnValue(method = "getDestroySpeed(Lnet/minecraft/world/level/block/state/BlockState;)F", at = @At("RETURN"))
    private float versionunbound$breakSpeed(float speed, @Local(argsOnly = true) BlockState state) {
        return EventHooks.getBreakSpeed((Player) (Object) this, state, speed, this.versionunbound$destroySpeedPos);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void versionunbound$playerTickPre(CallbackInfo ci) {
        EventHooks.firePlayerTickPre((Player) (Object) this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void versionunbound$playerTickPost(CallbackInfo ci) {
        EventHooks.firePlayerTickPost((Player) (Object) this);
    }

    // After the spectator check, before the entity is interacted with.
    @Inject(method = "interactOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getItemInHand(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;", ordinal = 0), cancellable = true)
    private void versionunbound$interactEntity(Entity entity, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir) {
        InteractionResult cancelResult = CommonHooks.onInteractEntity((Player) (Object) this, entity, hand, location);
        if (cancelResult != null) cir.setReturnValue(cancelResult);
    }

    // NeoForge fires it just before "return interact", after the creative count fix. That fix runs only with infinite
    // materials, which is when the event doesn't fire, so firing at the check gives the same result and doesn't depend
    // on the method's return ordinals.
    @Inject(method = "interactOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasInfiniteMaterials()Z", ordinal = 0))
    private void versionunbound$destroyItemOnInteract(Entity entity, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir,
            @Local(ordinal = 0) ItemStack itemStack, @Local(ordinal = 1) ItemStack itemStackClone) {
        Player self = (Player) (Object) this;
        if (!self.getAbilities().instabuild && itemStack.isEmpty()) {
            EventHooks.onPlayerDestroyItem(self, itemStackClone, hand);
        }
    }

    // The item used on a living entity is used up, before it's taken out of the hand.
    @Inject(method = "interactOn", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setItemInHand(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;)V"))
    private void versionunbound$destroyItemOnInteractLiving(Entity entity, InteractionHand hand, Vec3 location, CallbackInfoReturnable<InteractionResult> cir,
            @Local(ordinal = 1) ItemStack itemStackClone) {
        EventHooks.onPlayerDestroyItem((Player) (Object) this, itemStackClone, hand);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void versionunbound$attackEntity(Entity entity, CallbackInfo ci) {
        if (!CommonHooks.onPlayerAttackTarget((Player) (Object) this, entity)) ci.cancel();
    }

    // NeoForge copies the weapon after resolving the ender dragon part, which doesn't touch the stack.
    @Inject(method = "itemAttackInteraction", at = @At("HEAD"))
    private void versionunbound$copyAttackingItem(Entity entity, ItemStack attackingItemStack, DamageSource damageSource, boolean applyToTarget, CallbackInfo ci,
            @Share("attackingItemCopy") LocalRef<ItemStack> copy) {
        copy.set(attackingItemStack.copy());
    }

    // The weapon broke in the attack, before it's taken out of the hand.
    @Inject(method = "itemAttackInteraction", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private void versionunbound$destroyItemOnAttack(Entity entity, ItemStack attackingItemStack, DamageSource damageSource, boolean applyToTarget, CallbackInfo ci,
            @Share("attackingItemCopy") LocalRef<ItemStack> copy) {
        Player self = (Player) (Object) this;
        EventHooks.onPlayerDestroyItem(self, copy.get(), attackingItemStack == self.getMainHandItem() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
    }

    @WrapOperation(method = "drop(Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/item/ItemEntity;", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;"))
    private ItemEntity versionunbound$toss(Player player, ItemStack itemStack, boolean randomly, boolean thrownFromHand, Operation<ItemEntity> original) {
        return CommonHooks.onPlayerTossEvent(player, itemStack, randomly, thrownFromHand);
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's damage pipeline, the player's copy of {@code LivingEntity.actuallyHurt}'s. LivingEntity's
     *         protected members are reached through {@link LivingEntityDamageContainers}.
     */
    @Overwrite
    protected void actuallyHurt(ServerLevel level, DamageSource source, float dmg) {
        Player self = (Player) (Object) this;
        if (!self.isInvulnerableTo(level, source)) {
            LivingEntityDamageContainers containers = (LivingEntityDamageContainers) this;
            DamageContainer container = containers.versionunbound$currentDamageContainer(source, dmg);
            // Neo (ImplNote): Keep this in-sync with the equivalent patches in LivingEntity#actuallyHurt.
            // Neo: Primary damage pipeline hooks. Compute the armor and enchantment reductions against the "original" damage, and then fire LivingDamageEvent.Pre
            container.setReduction(DamageContainer.Reduction.ARMOR, container.getNewDamage() - containers.versionunbound$getDamageAfterArmorAbsorb(source, container.getNewDamage()));
            containers.versionunbound$getDamageAfterMagicAbsorb(source, container.getNewDamage());

            // LivingDamageEvent.Pre is then able to mutate the container to change the reductions and change the "real" damage that will be inflicted.
            float damage = CommonHooks.onLivingDamagePre(self, container);
            // Throw an exception if someone kills the entity during Pre. Vanilla is going to kill any entity which isDeadOrDying() when it bubbles back up to hurtServer, so it dying early is invalid.
            Preconditions.checkArgument(!containers.versionunbound$isDead(), "It is invalid to have killed an entity during LivingDamageEvent.Pre. Use LivingIncomingDamageEvent or LivingDamageEvent.Post instead.");

            // Store a copy of the "true" inflicted damage so we can propagate it back to hurtServer.
            container.captureInflictedDamage();

            // Next we attribute damage to absorption hearts. We record the amount blocked by absorption in the container for later calculations.
            // Mods reduction functions may adjust the split of damage between absorption and health, so we need to first apply the reductions and then compute the amounts that go to each pool.
            container.setReduction(DamageContainer.Reduction.ABSORPTION, Math.min(self.getAbsorptionAmount(), damage));
            float absorbed = Math.min(damage, container.getReduction(DamageContainer.Reduction.ABSORPTION));
            self.setAbsorptionAmount(Math.max(0, self.getAbsorptionAmount() - absorbed));
            dmg = container.getNewDamage();
            float absorbedDamage = absorbed;
            if (absorbedDamage > 0.0F && absorbedDamage < 3.4028235E37F) {
                self.awardStat(Stats.DAMAGE_ABSORBED, Math.round(absorbedDamage * 10.0F));
            }

            // Last thing here, inflict damage to the entity's health, based on how much damage is remaining after absorption has been processes.
            if (dmg != 0.0F) {
                self.causeFoodExhaustion(source.getFoodExhaustion());
                self.getCombatTracker().recordDamage(source, dmg);
                self.setHealth(self.getHealth() - dmg);
                if (dmg < 3.4028235E37F) {
                    self.awardStat(Stats.DAMAGE_TAKEN, Math.round(dmg * 10.0F));
                }

                self.gameEvent(GameEvent.ENTITY_DAMAGE);
            }

            // Neo: Now we go back to hurtServer, where we'll update various metadata fields, process the side effects of damage (i.e. killing the entity), and firing LivingDamageEvent.Post
        }
    }

    // Neo: PlayerFlyableFallEvent when a player who may fly lands (and takes no fall damage). NeoForge asks the player
    // extension's mayFly(), which also counts the creative flight attribute (Phase 2 step 5); vanilla's mayfly until then.
    // VersionUnbound: NeoForge 26.2 passes damageModifier as both the distance and the multiplier (it adapted 1.21.1's
    // call, which passed the fall distance as both, when fallDistance became a double); the event's distance is the
    // fall distance and its multiplier the damage multiplier, so each gets its own (HANDOFF.md §1, the documented
    // behaviour rule).
    @ModifyExpressionValue(method = "causeFallDamage", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/player/Abilities;mayfly:Z"))
    private boolean versionunbound$flyableFall(boolean mayfly, @Local(argsOnly = true) double fallDistance, @Local(argsOnly = true) float damageModifier) {
        if (mayfly) EventHooks.onPlayerFall((Player) (Object) this, (float) fallDistance, damageModifier);
        return mayfly;
    }

    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void versionunbound$wakeUp(boolean forcefulWakeUp, boolean updateLevelList, CallbackInfo ci) {
        EventHooks.onPlayerWakeup((Player) (Object) this, forcefulWakeUp, updateLevelList);
    }
}
