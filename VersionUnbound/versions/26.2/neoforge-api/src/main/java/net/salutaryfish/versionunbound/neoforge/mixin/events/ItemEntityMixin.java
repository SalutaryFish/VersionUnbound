package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.UUID;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ItemEntity}, for the events it fires: the item's lifespan (a saved field, extended by
 * {@code ItemExpireEvent} when the item would despawn) and the pickup events around {@code playerTouch}, which is
 * replaced by NeoForge's version. Also NeoForge's {@code getTarget}. Not yet (the item and block extensions, Phase 2
 * step 5): {@code onEntityItemUpdate}, the item's own {@code getEntityLifespan} (vanilla's 6000 ticks meanwhile), fluid
 * type movement, the block state's {@code getFriction} and the damage source passed to {@code onDestroyed}.
 */
@Mixin(ItemEntity.class)
abstract class ItemEntityMixin {
    @Shadow
    @Final
    private static int LIFETIME;
    @Shadow
    private int age;
    @Shadow
    private int pickupDelay;
    @Shadow
    private @Nullable UUID target;

    /**
     * The maximum age of this EntityItem. The item is expired once this is reached.
     */
    public int lifespan = LIFETIME;

    // Vanilla's despawn check, age >= 6000 (LIFETIME).
    @ModifyConstant(method = "tick", constant = @Constant(intValue = 6000))
    private int versionunbound$lifespan(int lifetime) {
        return this.lifespan;
    }

    // Only reached on the server once the age has reached the lifespan.
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/item/ItemEntity;discard()V", ordinal = 1))
    private void versionunbound$itemExpire(ItemEntity self, Operation<Void> original) {
        // Clamping to MAX_VALUE -1 as age is a Short and going above that would produce an infinite lifespan implicitly (accidentally)
        this.lifespan = Mth.clamp(lifespan + EventHooks.onItemExpire(self), 0, Short.MAX_VALUE - 1);
        if (this.age >= lifespan) {
            original.call(self);
        }
    }

    // The end of the branch that ticks a non-empty item (the empty branch discards and returns early).
    @Inject(method = "tick", at = @At("TAIL"))
    private void versionunbound$discardIfEmpty(CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.getItem().isEmpty() && !self.isRemoved()) {
            self.discard();
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void versionunbound$saveLifespan(ValueOutput output, CallbackInfo ci) {
        output.putInt("Lifespan", this.lifespan);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void versionunbound$readLifespan(ValueInput input, CallbackInfo ci) {
        this.lifespan = input.getIntOr("Lifespan", LIFETIME);
    }

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's version: the pickup events, the pre event's result overriding vanilla's conditions, and the
     *         count of what was actually picked up.
     */
    @Overwrite
    public void playerTouch(Player player) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (!self.level().isClientSide()) {
            ItemStack itemStack = self.getItem();
            Item item = itemStack.getItem();
            int orgCount = itemStack.getCount();

            // Neo: Fire item pickup pre/post and adjust handling logic to adhere to the event result.
            TriState result = EventHooks.fireItemPickupPre(self, player).canPickup();
            if (result.isFalse()) {
                return;
            }

            // Make a copy of the original stack for use in ItemEntityPickupEvent.Post
            ItemStack originalCopy = itemStack.copy();
            // Subvert the vanilla conditions (pickup delay and target check) if the result is true.
            if ((result.isTrue() || this.pickupDelay == 0 && (this.target == null || this.target.equals(player.getUUID()))) && player.getInventory().add(itemStack)) {
                // Fire ItemEntityPickupEvent.Post
                EventHooks.fireItemPickupPost(self, player, originalCopy);
                // Update `orgCount` to reflect the actual pickup amount. Vanilla is wrong here and always reports the whole stack.
                orgCount = originalCopy.getCount() - itemStack.getCount();

                player.take(self, orgCount);
                if (itemStack.isEmpty()) {
                    self.discard();
                    itemStack.setCount(orgCount);
                }

                player.awardStat(Stats.ITEM_PICKED_UP.get(item), orgCount);
                player.onItemPickup(self);
            }
        }
    }

    /**
     * Neo: Add getter for ItemEntity's {@link ItemEntity#target target}.
     * @return The {@link ItemEntity#target target} that can pick up this item entity (if {@code null}, anyone can pick it up)
     */
    public @Nullable UUID getTarget() {
        return this.target;
    }
}
