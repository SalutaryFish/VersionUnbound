package net.minecraft.world.entity.ai.behavior;

import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

public class MoveToSkySeeingSpot {
    public static OneShot<LivingEntity> create(float speedModifier) {
        return BehaviorBuilder.create(
            instance -> instance.group(instance.absent(MemoryModuleType.WALK_TARGET)).apply(instance, walkTarget -> (level, entity, gameTime) -> {
                if (level.canSeeSky(entity.blockPosition())) {
                    return false;
                }

                Optional<Vec3> optional = Optional.ofNullable(getOutdoorPosition(level, entity));
                optional.ifPresent(outdoorPos -> walkTarget.set(new WalkTarget(outdoorPos, speedModifier, 0)));
                return true;
            })
        );
    }

    @Nullable
    private static Vec3 getOutdoorPosition(ServerLevel level, LivingEntity entity) {
        RandomSource randomSource = entity.getRandom();
        BlockPos blockPos = entity.blockPosition();

        for (int i = 0; i < 10; i++) {
            BlockPos blockPos2 = blockPos.offset(randomSource.nextInt(20) - 10, randomSource.nextInt(6) - 3, randomSource.nextInt(20) - 10);
            if (hasNoBlocksAbove(level, entity, blockPos2)) {
                return Vec3.atBottomCenterOf(blockPos2);
            }
        }

        return null;
    }

    public static boolean hasNoBlocksAbove(ServerLevel level, LivingEntity entity, BlockPos pos) {
        return level.canSeeSky(pos) && level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos).getY() <= entity.getY();
    }
}
