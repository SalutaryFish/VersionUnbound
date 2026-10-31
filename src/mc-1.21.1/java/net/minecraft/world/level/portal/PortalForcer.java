package net.minecraft.world.level.portal;

import java.util.Comparator;
import java.util.Optional;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

public class PortalForcer {
    public static final int TICKET_RADIUS = 3;
    private static final int NETHER_PORTAL_RADIUS = 16;
    private static final int OVERWORLD_PORTAL_RADIUS = 128;
    private static final int FRAME_HEIGHT = 5;
    private static final int FRAME_WIDTH = 4;
    private static final int FRAME_BOX = 3;
    private static final int FRAME_HEIGHT_START = -1;
    private static final int FRAME_HEIGHT_END = 4;
    private static final int FRAME_WIDTH_START = -1;
    private static final int FRAME_WIDTH_END = 3;
    private static final int FRAME_BOX_START = -1;
    private static final int FRAME_BOX_END = 2;
    private static final int NOTHING_FOUND = -1;
    private final ServerLevel level;

    public PortalForcer(ServerLevel level) {
        this.level = level;
    }

    public Optional<BlockPos> findClosestPortalPosition(BlockPos exitPos, boolean isNether, WorldBorder worldBorder) {
        PoiManager poiManager = this.level.getPoiManager();
        int i = isNether ? 16 : 128;
        poiManager.ensureLoadedAndValid(this.level, exitPos, i);
        return poiManager.getInSquare(holder -> holder.is(PoiTypes.NETHER_PORTAL), exitPos, i, PoiManager.Occupancy.ANY)
            .map(PoiRecord::getPos)
            .filter(worldBorder::isWithinBounds)
            .filter(blockPos -> this.level.getBlockState(blockPos).hasProperty(BlockStateProperties.HORIZONTAL_AXIS))
            .min(Comparator.<BlockPos>comparingDouble(blockPos2 -> blockPos2.distSqr(exitPos)).thenComparingInt(Vec3i::getY));
    }

    public Optional<BlockUtil.FoundRectangle> createPortal(BlockPos pos, Direction.Axis axis) {
        Direction direction = Direction.get(Direction.AxisDirection.POSITIVE, axis);
        double d = -1.0;
        BlockPos blockPos = null;
        double e = -1.0;
        BlockPos blockPos2 = null;
        WorldBorder worldBorder = this.level.getWorldBorder();
        int i = Math.min(this.level.getMaxBuildHeight(), this.level.getMinBuildHeight() + this.level.getLogicalHeight()) - 1;
        int j = 1;
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (BlockPos.MutableBlockPos mutableBlockPos2 : BlockPos.spiralAround(pos, 16, Direction.EAST, Direction.SOUTH)) {
            int k = Math.min(i, this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, mutableBlockPos2.getX(), mutableBlockPos2.getZ()));
            if (worldBorder.isWithinBounds(mutableBlockPos2) && worldBorder.isWithinBounds(mutableBlockPos2.move(direction, 1))) {
                mutableBlockPos2.move(direction.getOpposite(), 1);

                for (int l = k; l >= this.level.getMinBuildHeight(); l--) {
                    mutableBlockPos2.setY(l);
                    if (this.canPortalReplaceBlock(mutableBlockPos2)) {
                        int m = l;

                        while (l > this.level.getMinBuildHeight() && this.canPortalReplaceBlock(mutableBlockPos2.move(Direction.DOWN))) {
                            l--;
                        }

                        if (l + 4 <= i) {
                            int n = m - l;
                            if (n <= 0 || n >= 3) {
                                mutableBlockPos2.setY(l);
                                if (this.canHostFrame(mutableBlockPos2, mutableBlockPos, direction, 0)) {
                                    double f = pos.distSqr(mutableBlockPos2);
                                    if (this.canHostFrame(mutableBlockPos2, mutableBlockPos, direction, -1)
                                        && this.canHostFrame(mutableBlockPos2, mutableBlockPos, direction, 1)
                                        && (d == -1.0 || d > f)) {
                                        d = f;
                                        blockPos = mutableBlockPos2.immutable();
                                    }

                                    if (d == -1.0 && (e == -1.0 || e > f)) {
                                        e = f;
                                        blockPos2 = mutableBlockPos2.immutable();
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (d == -1.0 && e != -1.0) {
            blockPos = blockPos2;
            d = e;
        }

        if (d == -1.0) {
            int o = Math.max(this.level.getMinBuildHeight() - -1, 70);
            int p = i - 9;
            if (p < o) {
                return Optional.empty();
            }

            blockPos = new BlockPos(pos.getX() - direction.getStepX() * 1, Mth.clamp(pos.getY(), o, p), pos.getZ() - direction.getStepZ() * 1).immutable();
            blockPos = worldBorder.clampToBounds(blockPos);
            Direction direction2 = direction.getClockWise();

            for (int l = -1; l < 2; l++) {
                for (int m = 0; m < 2; m++) {
                    for (int n = -1; n < 3; n++) {
                        BlockState blockState = n < 0 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();
                        mutableBlockPos.setWithOffset(
                            blockPos, m * direction.getStepX() + l * direction2.getStepX(), n, m * direction.getStepZ() + l * direction2.getStepZ()
                        );
                        this.level.setBlockAndUpdate(mutableBlockPos, blockState);
                    }
                }
            }
        }

        for (int o = -1; o < 3; o++) {
            for (int p = -1; p < 4; p++) {
                if (o == -1 || o == 2 || p == -1 || p == 3) {
                    mutableBlockPos.setWithOffset(blockPos, o * direction.getStepX(), p, o * direction.getStepZ());
                    this.level.setBlock(mutableBlockPos, Blocks.OBSIDIAN.defaultBlockState(), 3);
                }
            }
        }

        BlockState blockState2 = Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, axis);

        for (int p = 0; p < 2; p++) {
            for (int k = 0; k < 3; k++) {
                mutableBlockPos.setWithOffset(blockPos, p * direction.getStepX(), k, p * direction.getStepZ());
                this.level.setBlock(mutableBlockPos, blockState2, 18);
            }
        }

        return Optional.of(new BlockUtil.FoundRectangle(blockPos.immutable(), 2, 3));
    }

    private boolean canPortalReplaceBlock(BlockPos.MutableBlockPos pos) {
        BlockState blockState = this.level.getBlockState(pos);
        return blockState.canBeReplaced() && blockState.getFluidState().isEmpty();
    }

    private boolean canHostFrame(BlockPos originalPos, BlockPos.MutableBlockPos offsetPos, Direction direction, int offsetScale) {
        Direction direction2 = direction.getClockWise();

        for (int i = -1; i < 3; i++) {
            for (int j = -1; j < 4; j++) {
                offsetPos.setWithOffset(
                    originalPos,
                    direction.getStepX() * i + direction2.getStepX() * offsetScale,
                    j,
                    direction.getStepZ() * i + direction2.getStepZ() * offsetScale
                );
                if (j < 0 && !this.level.getBlockState(offsetPos).isSolid()) {
                    return false;
                }

                if (j >= 0 && !this.canPortalReplaceBlock(offsetPos)) {
                    return false;
                }
            }
        }

        return true;
    }
}
