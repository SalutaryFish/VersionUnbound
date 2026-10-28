package net.salutaryfish.versionunbound.neoforge.client.mixin;

import net.minecraft.client.main.Main;
import net.neoforged.neoforge.client.loading.ClientModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to the client's {@code Main}: mods are constructed and registered right after vanilla's bootstrap,
 * before the game window exists and before Fabric initializes its mods (in {@code Minecraft}'s constructor).
 */
@Mixin(Main.class)
abstract class MainMixin {
    @Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/Bootstrap;validate()V", shift = At.Shift.AFTER))
    private static void versionunbound$beginModLoading(String[] args, CallbackInfo ci) {
        ClientModLoader.begin();
    }
}
