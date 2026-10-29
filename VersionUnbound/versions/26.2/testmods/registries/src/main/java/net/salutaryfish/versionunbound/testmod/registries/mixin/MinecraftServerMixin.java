package net.salutaryfish.versionunbound.testmod.registries.mixin;

import net.minecraft.server.MinecraftServer;
import net.salutaryfish.versionunbound.testmod.registries.RegistriesMod;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Datapack registries exist once the server has loaded its datapacks, just before it loads the world.
 */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Inject(method = "loadLevel", at = @At("HEAD"))
    private void registries$checkDataPackRegistries(CallbackInfo ci) {
        RegistriesMod.checkWorld((MinecraftServer) (Object) this);
    }
}
