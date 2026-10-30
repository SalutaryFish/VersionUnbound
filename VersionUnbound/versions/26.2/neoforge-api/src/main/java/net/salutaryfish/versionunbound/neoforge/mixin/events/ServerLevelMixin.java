package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.redstone.Orientation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code ServerLevel}, for the events it fires: entity ticks (cancellable), level saves, and players
 * joining the level (cancellable; other entities join in {@code PersistentEntitySectionManagerMixin}),
 * {@code onAddedToLevel} once a player or a new entity is added, {@code BlockEvent.NeighborNotifyEvent} for neighbor
 * updates (cancellable only when one side is left out), and {@code EntityMobGriefingEvent} for the explosion's source
 * when a mob's explosion decides whether it breaks blocks.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    // Neo: Permit cancellation of Entity#tick via EntityTickEvent.Pre
    @WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void versionunbound$entityTick(Entity entity, Operation<Void> original) {
        if (!EventHooks.fireEntityTickPre(entity).isCanceled()) {
            original.call(entity);
            EventHooks.fireEntityTickPost(entity);
        }
    }

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void versionunbound$playerJoinLevel(ServerPlayer player, CallbackInfo ci) {
        if (NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(player, (ServerLevel) (Object) this)).isCanceled()) ci.cancel();
    }

    // The event has fired above. NeoForge calls onAddedToLevel whether or not the add succeeded.
    @WrapOperation(method = "addPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z"))
    private boolean versionunbound$addPlayerWithoutEvent(PersistentEntitySectionManager<Entity> entityManager, EntityAccess player, Operation<Boolean> original) {
        boolean added = entityManager.addNewEntityWithoutEvent((Entity) player);
        ((Entity) player).onAddedToLevel();
        return added;
    }

    @WrapOperation(method = "addEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addNewEntity(Lnet/minecraft/world/level/entity/EntityAccess;)Z"))
    private boolean versionunbound$entityAddedToLevel(PersistentEntitySectionManager<Entity> entityManager, EntityAccess entity, Operation<Boolean> original) {
        if (original.call(entityManager, entity)) {
            ((Entity) entity).onAddedToLevel();
            return true;
        } else {
            return false;
        }
    }

    // At the end of vanilla's "if (!noSave)" block, once the level data, chunks and entities are saved.
    @Inject(method = "save", at = @At("TAIL"))
    private void versionunbound$levelSave(@Nullable ProgressListener progressListener, boolean flush, boolean noSave, CallbackInfo ci) {
        if (!noSave) {
            NeoForge.EVENT_BUS.post(new LevelEvent.Save((ServerLevel) (Object) this));
        }
    }

    // VersionUnbound: NeoForge fires the event in the two-argument updateNeighborsAt, which calls this overload. Vanilla
    // added the overload after 1.21.1, and its levers, buttons, redstone torches, pistons, trapped chests, lecterns,
    // lightning rods and tripwire hooks call it themselves, so on NeoForge 26.2 their updates fire no event on the
    // server, though the event is documented to fire for every physics update and NeoForge 1.21.1 fired it for them.
    // Firing it here covers both, once per call. NeoForge ignores whether the event was canceled, here as well.
    @Inject(
            method = "updateNeighborsAt(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;)V",
            at = @At("HEAD"))
    private void versionunbound$neighborNotify(BlockPos pos, Block sourceBlock, @Nullable Orientation orientation, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), EnumSet.allOf(Direction.class), false).isCanceled();
    }

    // The mob griefing game rule check for ExplosionInteraction.MOB asks the event about the explosion's source (null:
    // the game rule).
    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "explode", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$explosionGriefing(Object mobGriefing, @Local(argsOnly = true) @Nullable Entity source) {
        return EventHooks.canEntityGrief((ServerLevel) (Object) this, source);
    }

    @Inject(method = "updateNeighborsAtExceptFromFacing", at = @At("HEAD"), cancellable = true)
    private void versionunbound$neighborNotifyExceptFromFacing(BlockPos pos, Block blockObject, Direction skipDirection, @Nullable Orientation orientation, CallbackInfo ci) {
        ServerLevel level = (ServerLevel) (Object) this;
        EnumSet<Direction> directions = EnumSet.allOf(Direction.class);
        directions.remove(skipDirection);
        if (EventHooks.onNeighborNotify(level, pos, level.getBlockState(pos), directions, false).isCanceled())
            ci.cancel();
    }
}
