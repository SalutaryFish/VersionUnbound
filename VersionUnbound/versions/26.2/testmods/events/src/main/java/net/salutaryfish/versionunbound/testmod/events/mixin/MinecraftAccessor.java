package net.salutaryfish.versionunbound.testmod.events.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The client's click handlers, for the empty-click checks in {@code ClientEvents}.
 */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
    @Invoker("startAttack")
    boolean events$startAttack();

    @Invoker("startUseItem")
    void events$startUseItem();
}
