package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.salutaryfish.versionunbound.neoforge.patches.PersistentEntitySectionManagerExtension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's patch to {@code PersistentEntitySectionManager}: {@code EntityJoinLevelEvent} (cancellable) for every
 * entity a server level adds, new or loaded, except through {@code addNewEntityWithoutEvent}, and
 * {@code onAddedToLevel} after the entities of a loaded or generated chunk are added (NeoForge calls it whether or not
 * the add succeeded; {@code ServerLevelMixin} calls it for new entities). Not yet: the entering-section hook, which
 * comes with the entity extension (Phase 2 step 5).
 */
@Mixin(PersistentEntitySectionManager.class)
abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> implements PersistentEntitySectionManagerExtension.WithoutEvent<T> {
    // Set for the one addEntity call that addNewEntityWithoutEvent makes.
    @Unique
    private boolean versionunbound$skipJoinEvent;

    @Shadow
    public abstract boolean addNewEntity(T entity);

    @Override
    public boolean versionunbound$addNewEntityWithoutEvent(T entity) {
        this.versionunbound$skipJoinEvent = true;
        try {
            return this.addNewEntity(entity);
        } finally {
            this.versionunbound$skipJoinEvent = false;
        }
    }

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void versionunbound$entityJoinLevel(T entity, boolean loaded, CallbackInfoReturnable<Boolean> cir) {
        if (this.versionunbound$skipJoinEvent) {
            this.versionunbound$skipJoinEvent = false;
            return;
        }
        if (entity instanceof Entity actualEntity && NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(actualEntity, actualEntity.level(), loaded)).isCanceled())
            cir.setReturnValue(false);
    }

    @WrapOperation(method = {"lambda$addLegacyChunkEntities$0", "lambda$addWorldGenChunkEntities$0", "lambda$processPendingLoads$0"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/entity/PersistentEntitySectionManager;addEntity(Lnet/minecraft/world/level/entity/EntityAccess;Z)Z"))
    private boolean versionunbound$entityAddedToLevel(PersistentEntitySectionManager<T> manager, T e, boolean loaded, Operation<Boolean> original) {
        boolean added = original.call(manager, e, loaded);
        if (e instanceof Entity entity) entity.onAddedToLevel();
        return added;
    }
}
