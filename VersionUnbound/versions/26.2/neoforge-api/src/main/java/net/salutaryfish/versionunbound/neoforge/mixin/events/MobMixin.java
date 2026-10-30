package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.salutaryfish.versionunbound.neoforge.patches.MobExtension;
import org.jspecify.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code Mob}, for {@code LivingChangeTargetEvent} when a mob's target is set (cancellable; the event
 * can replace the target), and the spawn type and spawn cancellation of {@link MobExtension}: {@code finalizeSpawn}
 * records its spawn reason, which is saved and loaded. Brain-driven mobs set their target in {@code StartAttacking}
 * ({@code StartAttackingMixin}). {@code FinalizeSpawnEvent} fires where vanilla calls {@code finalizeSpawn}
 * ({@code CoremodMixinPlugin}). And {@code EntityMobGriefingEvent} before a mob picks up items.
 */
@Mixin(Mob.class)
abstract class MobMixin implements MobExtension.SpawnState {
    @Unique
    private @Nullable EntitySpawnReason versionunbound$spawnType;
    @Unique
    private boolean versionunbound$spawnCancelled = false;

    @Override
    public @Nullable EntitySpawnReason versionunbound$getSpawnType() {
        return this.versionunbound$spawnType;
    }

    @Override
    public boolean versionunbound$isSpawnCancelled() {
        return this.versionunbound$spawnCancelled;
    }

    @Override
    public void versionunbound$setSpawnCancelled(boolean cancel) {
        this.versionunbound$spawnCancelled = cancel;
    }

    // After vanilla's asValidTarget, before the field is set.
    @WrapOperation(method = "setTarget", at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;target:Lnet/minecraft/world/entity/LivingEntity;", opcode = Opcodes.PUTFIELD))
    private void versionunbound$changeTarget(Mob mob, LivingEntity target, Operation<Void> original) {
        LivingChangeTargetEvent changeTargetEvent = CommonHooks.onLivingChangeTarget(mob, target, LivingChangeTargetEvent.LivingTargetType.MOB_TARGET);
        if (!changeTargetEvent.isCanceled()) {
            original.call(mob, changeTargetEvent.getNewAboutToBeSetTarget());
        }
    }

    // The mob griefing game rule check before picking up items, last in its condition.
    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "aiStep", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$pickUpGriefing(Object mobGriefing) {
        Mob mob = (Mob) (Object) this;
        return EventHooks.canEntityGrief((ServerLevel) mob.level(), mob);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void versionunbound$saveSpawnType(ValueOutput output, CallbackInfo ci) {
        if (this.versionunbound$spawnType != null) {
            output.putString("neoforge:spawn_type", this.versionunbound$spawnType.name());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void versionunbound$loadSpawnType(ValueInput input, CallbackInfo ci) {
        input.getString("neoforge:spawn_type")
                .ifPresent(spawnType -> {
                    try {
                        this.versionunbound$spawnType = EntitySpawnReason.valueOf(spawnType);
                    } catch (Exception ignored) {
                    }
                });
    }

    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void versionunbound$recordSpawnType(ServerLevelAccessor level, DifficultyInstance difficulty, EntitySpawnReason spawnReason, @Nullable SpawnGroupData groupData, CallbackInfoReturnable<SpawnGroupData> cir) {
        this.versionunbound$spawnType = spawnReason;
    }
}
