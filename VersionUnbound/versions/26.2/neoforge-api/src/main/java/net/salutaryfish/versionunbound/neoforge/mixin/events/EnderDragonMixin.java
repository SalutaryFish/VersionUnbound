package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code EnderDragon}, for {@code LivingExperienceDropEvent}: the dragon drops its experience while
 * it dies, over 200 ticks, by which time vanilla has forgotten the player who last hurt it, so NeoForge remembers that
 * player for as long as they're around. (The mixin extends {@code Mob} to reach {@code lastHurtByPlayer}.) And the
 * blocks the dragon flies through: {@code CommonHooks.canEntityDestroy} decides for each block whether the dragon
 * breaks it ({@code EntityMobGriefingEvent}, the block's {@code canEntityDestroy}, {@code LivingDestroyBlockEvent}).
 */
@Mixin(EnderDragon.class)
abstract class EnderDragonMixin extends Mob {
    @Unique
    private @Nullable EntityReference<Player> versionunbound$unlimitedLastHurtByPlayer = null;

    private EnderDragonMixin(EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void versionunbound$rememberPlayer(CallbackInfo ci) {
        // lastHurtByPlayer is cleared after 100 ticks, capture it indefinitely in unlimitedLastHurtByPlayer for LivingExperienceDropEvent
        if (this.lastHurtByPlayer != null) this.versionunbound$unlimitedLastHurtByPlayer = lastHurtByPlayer;
        if (this.versionunbound$unlimitedLastHurtByPlayer != null) {
            Player p = this.versionunbound$unlimitedLastHurtByPlayer.getEntity(this.level(), Player.class);
            if (p == null || p.isRemoved()) {
                this.versionunbound$unlimitedLastHurtByPlayer = null;
            }
        }
    }

    // In place of the mob griefing game rule check, before vanilla's check for dragon-immune blocks.
    @Definition(id = "get", method = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;")
    @Definition(id = "MOB_GRIEFING", field = "Lnet/minecraft/world/level/gamerules/GameRules;MOB_GRIEFING:Lnet/minecraft/world/level/gamerules/GameRule;")
    @Expression("?.get(MOB_GRIEFING)")
    @ModifyExpressionValue(method = "checkWalls", at = @At("MIXINEXTRAS:EXPRESSION"))
    private Object versionunbound$destroyBlock(Object mobGriefing, @Local(argsOnly = true) ServerLevel level, @Local(name = "blockPos") BlockPos blockPos) {
        return CommonHooks.canEntityDestroy(level, blockPos, this);
    }

    // Both awards: while dying, and at the end.
    @ModifyArg(method = "tickDeath", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"), index = 2)
    private int versionunbound$experienceDrop(int award) {
        return EventHooks.getExperienceDrop(this, EntityReference.get(this.versionunbound$unlimitedLastHurtByPlayer, this.level(), Player.class), award);
    }
}
