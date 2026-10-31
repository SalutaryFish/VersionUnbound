package net.minecraft.gametest.framework;

import com.mojang.logging.LogUtils;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class StructureUtils {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int DEFAULT_Y_SEARCH_RADIUS = 10;
    public static final String DEFAULT_TEST_STRUCTURES_DIR = "gameteststructures";
    public static String testStructuresDir = "gameteststructures";

    public static Rotation getRotationForRotationSteps(int rotationSteps) {
        switch (rotationSteps) {
            case 0:
                return Rotation.NONE;
            case 1:
                return Rotation.CLOCKWISE_90;
            case 2:
                return Rotation.CLOCKWISE_180;
            case 3:
                return Rotation.COUNTERCLOCKWISE_90;
            default:
                throw new IllegalArgumentException("rotationSteps must be a value from 0-3. Got value " + rotationSteps);
        }
    }

    public static int getRotationStepsForRotation(Rotation rotation) {
        switch (rotation) {
            case NONE:
                return 0;
            case CLOCKWISE_90:
                return 1;
            case CLOCKWISE_180:
                return 2;
            case COUNTERCLOCKWISE_90:
                return 3;
            default:
                throw new IllegalArgumentException("Unknown rotation value, don't know how many steps it represents: " + rotation);
        }
    }

    public static AABB getStructureBounds(StructureBlockEntity structureBlockEntity) {
        return AABB.of(getStructureBoundingBox(structureBlockEntity));
    }

    public static BoundingBox getStructureBoundingBox(StructureBlockEntity structureBlockEntity) {
        BlockPos blockPos = getStructureOrigin(structureBlockEntity);
        BlockPos blockPos2 = getTransformedFarCorner(blockPos, structureBlockEntity.getStructureSize(), structureBlockEntity.getRotation());
        return BoundingBox.fromCorners(blockPos, blockPos2);
    }

    public static BlockPos getStructureOrigin(StructureBlockEntity structureBlockEntity) {
        return structureBlockEntity.getBlockPos().offset(structureBlockEntity.getStructurePos());
    }

    public static void addCommandBlockAndButtonToStartTest(BlockPos structureBlockPos, BlockPos offset, Rotation rotation, ServerLevel serverLevel) {
        BlockPos blockPos = StructureTemplate.transform(structureBlockPos.offset(offset), Mirror.NONE, rotation, structureBlockPos);
        serverLevel.setBlockAndUpdate(blockPos, Blocks.COMMAND_BLOCK.defaultBlockState());
        CommandBlockEntity commandBlockEntity = (CommandBlockEntity)serverLevel.getBlockEntity(blockPos);
        commandBlockEntity.getCommandBlock().setCommand("test runclosest");
        BlockPos blockPos2 = StructureTemplate.transform(blockPos.offset(0, 0, -1), Mirror.NONE, rotation, blockPos);
        serverLevel.setBlockAndUpdate(blockPos2, Blocks.STONE_BUTTON.defaultBlockState().rotate(rotation));
    }

    public static void createNewEmptyStructureBlock(String structureName, BlockPos pos, Vec3i size, Rotation rotation, ServerLevel serverLevel) {
        BoundingBox boundingBox = getStructureBoundingBox(pos.above(), size, rotation);
        clearSpaceForStructure(boundingBox, serverLevel);
        serverLevel.setBlockAndUpdate(pos, Blocks.STRUCTURE_BLOCK.defaultBlockState());
        StructureBlockEntity structureBlockEntity = (StructureBlockEntity)serverLevel.getBlockEntity(pos);
        structureBlockEntity.setIgnoreEntities(false);
        structureBlockEntity.setStructureName(ResourceLocation.parse(structureName));
        structureBlockEntity.setStructureSize(size);
        structureBlockEntity.setMode(StructureMode.SAVE);
        structureBlockEntity.setShowBoundingBox(true);
    }

    public static StructureBlockEntity prepareTestStructure(GameTestInfo gameTestInfo, BlockPos pos, Rotation rotation, ServerLevel level) {
        Vec3i vec3i = level.getStructureManager()
            .get(ResourceLocation.parse(gameTestInfo.getStructureName()))
            .orElseThrow(() -> new IllegalStateException("Missing test structure: " + gameTestInfo.getStructureName()))
            .getSize();
        BoundingBox boundingBox = getStructureBoundingBox(pos, vec3i, rotation);
        BlockPos blockPos;
        if (rotation == Rotation.NONE) {
            blockPos = pos;
        } else if (rotation == Rotation.CLOCKWISE_90) {
            blockPos = pos.offset(vec3i.getZ() - 1, 0, 0);
        } else if (rotation == Rotation.CLOCKWISE_180) {
            blockPos = pos.offset(vec3i.getX() - 1, 0, vec3i.getZ() - 1);
        } else {
            if (rotation != Rotation.COUNTERCLOCKWISE_90) {
                throw new IllegalArgumentException("Invalid rotation: " + rotation);
            }

            blockPos = pos.offset(0, 0, vec3i.getX() - 1);
        }

        forceLoadChunks(boundingBox, level);
        clearSpaceForStructure(boundingBox, level);
        return createStructureBlock(gameTestInfo, blockPos.below(), rotation, level);
    }

    public static void encaseStructure(AABB bounds, ServerLevel level, boolean placeBarriers) {
        BlockPos blockPos = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ).offset(-1, 0, -1);
        BlockPos blockPos2 = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        BlockPos.betweenClosedStream(blockPos, blockPos2)
            .forEach(
                blockPos3 -> {
                    boolean bl2 = blockPos3.getX() == blockPos.getX()
                        || blockPos3.getX() == blockPos2.getX()
                        || blockPos3.getZ() == blockPos.getZ()
                        || blockPos3.getZ() == blockPos2.getZ();
                    boolean bl3 = blockPos3.getY() == blockPos2.getY();
                    if (bl2 || bl3 && placeBarriers) {
                        level.setBlockAndUpdate(blockPos3, Blocks.BARRIER.defaultBlockState());
                    }
                }
            );
    }

    public static void removeBarriers(AABB bounds, ServerLevel level) {
        BlockPos blockPos = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ).offset(-1, 0, -1);
        BlockPos blockPos2 = BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ);
        BlockPos.betweenClosedStream(blockPos, blockPos2)
            .forEach(
                blockPos3 -> {
                    boolean bl = blockPos3.getX() == blockPos.getX()
                        || blockPos3.getX() == blockPos2.getX()
                        || blockPos3.getZ() == blockPos.getZ()
                        || blockPos3.getZ() == blockPos2.getZ();
                    boolean bl2 = blockPos3.getY() == blockPos2.getY();
                    if (level.getBlockState(blockPos3).is(Blocks.BARRIER) && (bl || bl2)) {
                        level.setBlockAndUpdate(blockPos3, Blocks.AIR.defaultBlockState());
                    }
                }
            );
    }

    private static void forceLoadChunks(BoundingBox boundingBox, ServerLevel level) {
        boundingBox.intersectingChunks().forEach(chunkPos -> level.setChunkForced(chunkPos.x, chunkPos.z, true));
    }

    public static void clearSpaceForStructure(BoundingBox boundingBox, ServerLevel level) {
        int i = boundingBox.minY() - 1;
        BoundingBox boundingBox2 = new BoundingBox(
            boundingBox.minX() - 2, boundingBox.minY() - 3, boundingBox.minZ() - 3, boundingBox.maxX() + 3, boundingBox.maxY() + 20, boundingBox.maxZ() + 3
        );
        BlockPos.betweenClosedStream(boundingBox2).forEach(blockPos -> clearBlock(i, blockPos, level));
        level.getBlockTicks().clearArea(boundingBox2);
        level.clearBlockEvents(boundingBox2);
        AABB aABB = AABB.of(boundingBox2);
        List<Entity> list = level.getEntitiesOfClass(Entity.class, aABB, entity -> !(entity instanceof Player));
        list.forEach(Entity::discard);
    }

    public static BlockPos getTransformedFarCorner(BlockPos pos, Vec3i offset, Rotation rotation) {
        BlockPos blockPos = pos.offset(offset).offset(-1, -1, -1);
        return StructureTemplate.transform(blockPos, Mirror.NONE, rotation, pos);
    }

    public static BoundingBox getStructureBoundingBox(BlockPos pos, Vec3i offset, Rotation rotation) {
        BlockPos blockPos = getTransformedFarCorner(pos, offset, rotation);
        BoundingBox boundingBox = BoundingBox.fromCorners(pos, blockPos);
        int i = Math.min(boundingBox.minX(), boundingBox.maxX());
        int j = Math.min(boundingBox.minZ(), boundingBox.maxZ());
        return boundingBox.move(pos.getX() - i, 0, pos.getZ() - j);
    }

    public static Optional<BlockPos> findStructureBlockContainingPos(BlockPos pos, int radius, ServerLevel serverLevel) {
        return findStructureBlocks(pos, radius, serverLevel).filter(blockPos2 -> doesStructureContain(blockPos2, pos, serverLevel)).findFirst();
    }

    public static Optional<BlockPos> findNearestStructureBlock(BlockPos pos, int radius, ServerLevel level) {
        Comparator<BlockPos> comparator = Comparator.comparingInt(blockPos2 -> blockPos2.distManhattan(pos));
        return findStructureBlocks(pos, radius, level).min(comparator);
    }

    public static Stream<BlockPos> findStructureByTestFunction(BlockPos pos, int radius, ServerLevel level, String testName) {
        return findStructureBlocks(pos, radius, level)
            .map(blockPos -> (StructureBlockEntity)level.getBlockEntity(blockPos))
            .filter(Objects::nonNull)
            .filter(structureBlockEntity -> Objects.equals(structureBlockEntity.getStructureName(), testName))
            .map(BlockEntity::getBlockPos)
            .map(BlockPos::immutable);
    }

    public static Stream<BlockPos> findStructureBlocks(BlockPos pos, int radius, ServerLevel level) {
        BoundingBox boundingBox = getBoundingBoxAtGround(pos, radius, level);
        return BlockPos.betweenClosedStream(boundingBox).filter(blockPos -> level.getBlockState(blockPos).is(Blocks.STRUCTURE_BLOCK)).map(BlockPos::immutable);
    }

    private static StructureBlockEntity createStructureBlock(GameTestInfo gameTestInfo, BlockPos pos, Rotation rotation, ServerLevel level) {
        level.setBlockAndUpdate(pos, Blocks.STRUCTURE_BLOCK.defaultBlockState());
        StructureBlockEntity structureBlockEntity = (StructureBlockEntity)level.getBlockEntity(pos);
        structureBlockEntity.setMode(StructureMode.LOAD);
        structureBlockEntity.setRotation(rotation);
        structureBlockEntity.setIgnoreEntities(false);
        structureBlockEntity.setStructureName(ResourceLocation.parse(gameTestInfo.getStructureName()));
        structureBlockEntity.setMetaData(gameTestInfo.getTestName());
        if (!structureBlockEntity.loadStructureInfo(level)) {
            throw new RuntimeException(
                "Failed to load structure info for test: " + gameTestInfo.getTestName() + ". Structure name: " + gameTestInfo.getStructureName()
            );
        } else {
            return structureBlockEntity;
        }
    }

    private static BoundingBox getBoundingBoxAtGround(BlockPos pos, int radius, ServerLevel level) {
        BlockPos blockPos = BlockPos.containing(pos.getX(), level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, pos).getY(), pos.getZ());
        return new BoundingBox(blockPos).inflatedBy(radius, 10, radius);
    }

    public static Stream<BlockPos> lookedAtStructureBlockPos(BlockPos pos, Entity entity, ServerLevel level) {
        int i = 200;
        Vec3 vec3 = entity.getEyePosition();
        Vec3 vec32 = vec3.add(entity.getLookAngle().scale(200.0));
        return findStructureBlocks(pos, 200, level)
            .map(blockPos -> level.getBlockEntity(blockPos, BlockEntityType.STRUCTURE_BLOCK))
            .flatMap(Optional::stream)
            .filter(structureBlockEntity -> getStructureBounds(structureBlockEntity).clip(vec3, vec32).isPresent())
            .map(BlockEntity::getBlockPos)
            .sorted(Comparator.comparing(pos::distSqr))
            .limit(1L);
    }

    private static void clearBlock(int structureBlockY, BlockPos pos, ServerLevel serverLevel) {
        BlockState blockState;
        if (pos.getY() < structureBlockY) {
            blockState = Blocks.STONE.defaultBlockState();
        } else {
            blockState = Blocks.AIR.defaultBlockState();
        }

        BlockInput blockInput = new BlockInput(blockState, Collections.emptySet(), null);
        blockInput.place(serverLevel, pos, 2);
        serverLevel.blockUpdated(pos, blockState.getBlock());
    }

    private static boolean doesStructureContain(BlockPos structureBlockPos, BlockPos posToTest, ServerLevel serverLevel) {
        StructureBlockEntity structureBlockEntity = (StructureBlockEntity)serverLevel.getBlockEntity(structureBlockPos);
        return getStructureBoundingBox(structureBlockEntity).isInside(posToTest);
    }
}
