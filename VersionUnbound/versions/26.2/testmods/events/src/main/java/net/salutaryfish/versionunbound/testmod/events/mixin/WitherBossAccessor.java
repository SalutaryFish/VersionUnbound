package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.boss.wither.WitherBoss;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The countdown after which a hurt wither breaks the blocks around it, for {@code GriefingScenario}. */
@Mixin(WitherBoss.class)
public interface WitherBossAccessor {
    @Accessor("destroyBlocksTick")
    void events$setDestroyBlocksTick(int ticks);
}
