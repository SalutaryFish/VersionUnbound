package net.minecraft.world.entity.ai.behavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;

public class GoToClosestVillage {
    public static BehaviorControl<Villager> create(float speedModifier, int closeEnoughDist) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.WALK_TARGET)).apply(instance, walkTarget -> (level, villager, gameTime) -> {
                if (level.isVillage(villager.blockPosition())) {
                    return false;
                }

                PoiManager poiManager = level.getPoiManager();
                int j = poiManager.sectionsToVillage(SectionPos.of(villager.blockPosition()));
                Vec3 vec3 = null;

                for (int k = 0; k < 5; k++) {
                    Vec3 vec32 = LandRandomPos.getPos(villager, 15, 7, pos -> -poiManager.sectionsToVillage(SectionPos.of(pos)));
                    if (vec32 != null) {
                        int l = poiManager.sectionsToVillage(SectionPos.of(BlockPos.containing(vec32)));
                        if (l < j) {
                            vec3 = vec32;
                            break;
                        }

                        if (l == j) {
                            vec3 = vec32;
                        }
                    }
                }

                if (vec3 != null) {
                    walkTarget.set(new WalkTarget(vec3, speedModifier, closeEnoughDist));
                }

                return true;
            })
        );
    }
}
