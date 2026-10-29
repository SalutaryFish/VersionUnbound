package net.salutaryfish.versionunbound.neoforge.mixin.registries;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * NeoForge maps a point of interest type's block states in a registry callback, when the type is registered. Fabric
 * API's {@code PoiHelper} registers through vanilla's {@code PoiTypes.register}, which maps the states again right after
 * and would throw on finding them taken. Taken by the same type isn't a conflict, so those states are skipped.
 */
@Mixin(PoiTypes.class)
abstract class PoiTypesMixin {
    @Shadow
    @Final
    private static Map<BlockState, Holder<PoiType>> TYPE_BY_STATE;

    @ModifyVariable(method = "registerBlockStates", at = @At("HEAD"), argsOnly = true)
    private static Set<BlockState> versionunbound$skipStatesMappedByCallback(Set<BlockState> matchingStates, Holder<PoiType> type) {
        if (matchingStates.stream().noneMatch(state -> TYPE_BY_STATE.get(state) == type)) {
            return matchingStates;
        }
        return matchingStates.stream().filter(state -> TYPE_BY_STATE.get(state) != type).collect(Collectors.toSet());
    }
}
