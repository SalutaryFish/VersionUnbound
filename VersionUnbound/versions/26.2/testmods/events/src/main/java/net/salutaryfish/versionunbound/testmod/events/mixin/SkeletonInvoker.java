package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.world.entity.monster.skeleton.Skeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** A frozen skeleton's conversion, run at once, and its conversion timer, for {@code ConversionScenario}. */
@Mixin(Skeleton.class)
public interface SkeletonInvoker {
    @Invoker("doFreezeConversion")
    void events$doFreezeConversion();

    @Accessor("conversionTime")
    int events$conversionTime();
}
