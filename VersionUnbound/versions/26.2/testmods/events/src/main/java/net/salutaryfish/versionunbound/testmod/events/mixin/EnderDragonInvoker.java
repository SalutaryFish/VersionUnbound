package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Runs an ender dragon's death tick from a chosen point in its death. */
@Mixin(EnderDragon.class)
public interface EnderDragonInvoker {
    @Accessor("dragonDeathTime")
    void events$setDragonDeathTime(int ticks);

    @Invoker("tickDeath")
    void events$tickDeath();
}
