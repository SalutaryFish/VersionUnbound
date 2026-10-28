package net.salutaryfish.versionunbound.neoforge.mixin.server;

import com.llamalad7.mixinextras.sugar.Local;
import joptsimple.OptionSet;
import net.minecraft.server.Main;
import net.neoforged.neoforge.server.loading.ServerModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge's patch to the dedicated server's {@code Main}, which loads mods. NeoForge does all of it where
 * {@code loadMods} runs, just before server.properties is read. Construction and registration move up to just after
 * {@code Bootstrap.validate()}, ahead of Fabric's mod initialization; see {@link ServerModLoader#begin(boolean)}.
 */
@Mixin(Main.class)
abstract class MainMixin {
    @Inject(method = "main", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/Bootstrap;validate()V", shift = At.Shift.AFTER))
    private static void versionunbound$beginModLoading(String[] args, CallbackInfo ci, @Local OptionSet options) {
        // As in NeoForge, --initSettings only writes server.properties and eula.txt, without loading mods.
        if (!options.has("initSettings")) ServerModLoader.begin(false);
    }

    @Inject(method = "main", at = @At(value = "NEW", target = "net/minecraft/server/dedicated/DedicatedServerSettings"))
    private static void versionunbound$loadMods(String[] args, CallbackInfo ci, @Local OptionSet options) {
        if (!options.has("initSettings")) ServerModLoader.load();
    }
}
