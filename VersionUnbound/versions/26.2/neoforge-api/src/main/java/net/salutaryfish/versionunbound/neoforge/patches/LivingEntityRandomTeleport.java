package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The {@code randomTeleport} overload NeoForge's patch adds to {@link LivingEntity}, with the consumed item that causes
 * the teleport (chorus fruit's {@code TeleportRandomlyConsumeEffect} passes it, so the teleport fires
 * {@code EntityTeleportEvent.ItemConsumption}). {@code LivingEntityMixin} adds it as a public method, for mods; our code
 * reaches it through this interface, which the mixin implements.
 */
public interface LivingEntityRandomTeleport {
    boolean randomTeleport(double xx, double yy, double zz, boolean showParticles, ItemStack consumedStack);
}
