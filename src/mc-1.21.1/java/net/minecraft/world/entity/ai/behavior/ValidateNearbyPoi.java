package net.minecraft.world.entity.ai.behavior;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

public class ValidateNearbyPoi {
    private static final int MAX_DISTANCE = 16;

    public static BehaviorControl<LivingEntity> create(Predicate<Holder<PoiType>> poiValidator, MemoryModuleType<GlobalPos> poiPosMemory) {
        return BehaviorBuilder.create(instance -> instance.group(instance.present(poiPosMemory)).apply(instance, poiPos -> (level, entity, gameTime) -> {
            GlobalPos globalPos = instance.get(poiPos);
            BlockPos blockPos = globalPos.pos();
            if (level.dimension() == globalPos.dimension() && blockPos.closerToCenterThan(entity.position(), 16.0)) {
                ServerLevel serverLevel = level.getServer().getLevel(globalPos.dimension());
                if (serverLevel == null || !serverLevel.getPoiManager().exists(blockPos, poiValidator)) {
                    poiPos.erase();
                } else if (bedIsOccupied(serverLevel, blockPos, entity)) {
                    poiPos.erase();
                    level.getPoiManager().release(blockPos);
                    DebugPackets.sendPoiTicketCountPacket(level, blockPos);
                }

                return true;
            } else {
                return false;
            }
        }));
    }

    private static boolean bedIsOccupied(ServerLevel level, BlockPos pos, LivingEntity entity) {
        BlockState blockState = level.getBlockState(pos);
        return blockState.is(BlockTags.BEDS) && blockState.getValue(BedBlock.OCCUPIED) && !entity.isSleeping();
    }
}
