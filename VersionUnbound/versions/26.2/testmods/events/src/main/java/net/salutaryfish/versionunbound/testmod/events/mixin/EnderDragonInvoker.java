package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Runs an ender dragon's death tick from a chosen point in its death, and for {@code GriefingScenario} its check of the
 * blocks it flies through.
 */
@Mixin(EnderDragon.class)
public interface EnderDragonInvoker {
    @Accessor("dragonDeathTime")
    void events$setDragonDeathTime(int ticks);

    @Invoker("tickDeath")
    void events$tickDeath();

    @Invoker("checkWalls")
    boolean events$checkWalls(ServerLevel level, AABB bb);
}
