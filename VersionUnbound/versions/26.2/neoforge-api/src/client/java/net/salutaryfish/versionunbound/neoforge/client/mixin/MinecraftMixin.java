package net.salutaryfish.versionunbound.neoforge.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig;
import net.neoforged.neoforge.client.loading.ClientModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to {@code Minecraft}'s constructor: mod setup runs once the window, options, resource and texture
 * managers and renderers exist, before the first resource reload.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;registerPanoramaTextures(Lnet/minecraft/client/renderer/texture/TextureManager;)V", shift = At.Shift.AFTER))
    private void versionunbound$finishModLoading(GameConfig gameConfig, CallbackInfo ci) {
        ClientModLoader.finish();
    }
}
