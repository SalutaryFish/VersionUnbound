package net.minecraft.world.level.levelgen.structure.structures;

import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;

public class WoodlandMansionPieces {
    public static void generateMansion(
        StructureTemplateManager structureTemplateManager,
        BlockPos pos,
        Rotation rotation,
        List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
        RandomSource random
    ) {
        WoodlandMansionPieces.MansionGrid mansionGrid = new WoodlandMansionPieces.MansionGrid(random);
        WoodlandMansionPieces.MansionPiecePlacer mansionPiecePlacer = new WoodlandMansionPieces.MansionPiecePlacer(structureTemplateManager, random);
        mansionPiecePlacer.createMansion(pos, rotation, pieces, mansionGrid);
    }

    static class FirstFloorRoomCollection extends WoodlandMansionPieces.FloorRoomCollection {
        @Override
        public String get1x1(RandomSource randomSource) {
            return "1x1_a" + (randomSource.nextInt(5) + 1);
        }

        @Override
        public String get1x1Secret(RandomSource randomSource) {
            return "1x1_as" + (randomSource.nextInt(4) + 1);
        }

        @Override
        public String get1x2SideEntrance(RandomSource randomSource, boolean bl) {
            return "1x2_a" + (randomSource.nextInt(9) + 1);
        }

        @Override
        public String get1x2FrontEntrance(RandomSource randomSource, boolean bl) {
            return "1x2_b" + (randomSource.nextInt(5) + 1);
        }

        @Override
        public String get1x2Secret(RandomSource randomSource) {
            return "1x2_s" + (randomSource.nextInt(2) + 1);
        }

        @Override
        public String get2x2(RandomSource randomSource) {
            return "2x2_a" + (randomSource.nextInt(4) + 1);
        }

        @Override
        public String get2x2Secret(RandomSource randomSource) {
            return "2x2_s1";
        }
    }

    abstract static class FloorRoomCollection {
        public abstract String get1x1(RandomSource var1);

        public abstract String get1x1Secret(RandomSource var1);

        public abstract String get1x2SideEntrance(RandomSource var1, boolean var2);

        public abstract String get1x2FrontEntrance(RandomSource var1, boolean var2);

        public abstract String get1x2Secret(RandomSource var1);

        public abstract String get2x2(RandomSource var1);

        public abstract String get2x2Secret(RandomSource var1);
    }

    static class MansionGrid {
        private static final int DEFAULT_SIZE = 11;
        private static final int CLEAR = 0;
        private static final int CORRIDOR = 1;
        private static final int ROOM = 2;
        private static final int START_ROOM = 3;
        private static final int TEST_ROOM = 4;
        private static final int BLOCKED = 5;
        private static final int ROOM_1x1 = 65536;
        private static final int ROOM_1x2 = 131072;
        private static final int ROOM_2x2 = 262144;
        private static final int ROOM_ORIGIN_FLAG = 1048576;
        private static final int ROOM_DOOR_FLAG = 2097152;
        private static final int ROOM_STAIRS_FLAG = 4194304;
        private static final int ROOM_CORRIDOR_FLAG = 8388608;
        private static final int ROOM_TYPE_MASK = 983040;
        private static final int ROOM_ID_MASK = 65535;
        private final RandomSource random;
        final WoodlandMansionPieces.SimpleGrid baseGrid;
        final WoodlandMansionPieces.SimpleGrid thirdFloorGrid;
        final WoodlandMansionPieces.SimpleGrid[] floorRooms;
        final int entranceX;
        final int entranceY;

        public MansionGrid(RandomSource random) {
            this.random = random;
            int i = 11;
            this.entranceX = 7;
            this.entranceY = 4;
            this.baseGrid = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.baseGrid.set(this.entranceX, this.entranceY, this.entranceX + 1, this.entranceY + 1, 3);
            this.baseGrid.set(this.entranceX - 1, this.entranceY, this.entranceX - 1, this.entranceY + 1, 2);
            this.baseGrid.set(this.entranceX + 2, this.entranceY - 2, this.entranceX + 3, this.entranceY + 3, 5);
            this.baseGrid.set(this.entranceX + 1, this.entranceY - 2, this.entranceX + 1, this.entranceY - 1, 1);
            this.baseGrid.set(this.entranceX + 1, this.entranceY + 2, this.entranceX + 1, this.entranceY + 3, 1);
            this.baseGrid.set(this.entranceX - 1, this.entranceY - 1, 1);
            this.baseGrid.set(this.entranceX - 1, this.entranceY + 2, 1);
            this.baseGrid.set(0, 0, 11, 1, 5);
            this.baseGrid.set(0, 9, 11, 11, 5);
            this.recursiveCorridor(this.baseGrid, this.entranceX, this.entranceY - 2, Direction.WEST, 6);
            this.recursiveCorridor(this.baseGrid, this.entranceX, this.entranceY + 3, Direction.WEST, 6);
            this.recursiveCorridor(this.baseGrid, this.entranceX - 2, this.entranceY - 1, Direction.WEST, 3);
            this.recursiveCorridor(this.baseGrid, this.entranceX - 2, this.entranceY + 2, Direction.WEST, 3);

            while (this.cleanEdges(this.baseGrid)) {
            }

            this.floorRooms = new WoodlandMansionPieces.SimpleGrid[3];
            this.floorRooms[0] = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.floorRooms[1] = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.floorRooms[2] = new WoodlandMansionPieces.SimpleGrid(11, 11, 5);
            this.identifyRooms(this.baseGrid, this.floorRooms[0]);
            this.identifyRooms(this.baseGrid, this.floorRooms[1]);
            this.floorRooms[0].set(this.entranceX + 1, this.entranceY, this.entranceX + 1, this.entranceY + 1, 8388608);
            this.floorRooms[1].set(this.entranceX + 1, this.entranceY, this.entranceX + 1, this.entranceY + 1, 8388608);
            this.thirdFloorGrid = new WoodlandMansionPieces.SimpleGrid(this.baseGrid.width, this.baseGrid.height, 5);
            this.setupThirdFloor();
            this.identifyRooms(this.thirdFloorGrid, this.floorRooms[2]);
        }

        public static boolean isHouse(WoodlandMansionPieces.SimpleGrid layout, int x, int y) {
            int i = layout.get(x, y);
            return i == 1 || i == 2 || i == 3 || i == 4;
        }

        public boolean isRoomId(WoodlandMansionPieces.SimpleGrid layout, int x, int y, int floor, int roomId) {
            return (this.floorRooms[floor].get(x, y) & 65535) == roomId;
        }

        @Nullable
        public Direction get1x2RoomDirection(WoodlandMansionPieces.SimpleGrid layout, int x, int y, int floor, int roomId) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                if (this.isRoomId(layout, x + direction.getStepX(), y + direction.getStepZ(), floor, roomId)) {
                    return direction;
                }
            }

            return null;
        }

        private void recursiveCorridor(WoodlandMansionPieces.SimpleGrid layout, int x, int y, Direction direction, int length) {
            if (length > 0) {
                layout.set(x, y, 1);
                layout.setif(x + direction.getStepX(), y + direction.getStepZ(), 0, 1);

                for (int i = 0; i < 8; i++) {
                    Direction direction2 = Direction.from2DDataValue(this.random.nextInt(4));
                    if (direction2 != direction.getOpposite() && (direction2 != Direction.EAST || !this.random.nextBoolean())) {
                        int j = x + direction.getStepX();
                        int k = y + direction.getStepZ();
                        if (layout.get(j + direction2.getStepX(), k + direction2.getStepZ()) == 0
                            && layout.get(j + direction2.getStepX() * 2, k + direction2.getStepZ() * 2) == 0) {
                            this.recursiveCorridor(
                                layout,
                                x + direction.getStepX() + direction2.getStepX(),
                                y + direction.getStepZ() + direction2.getStepZ(),
                                direction2,
                                length - 1
                            );
                            break;
                        }
                    }
                }

                Direction direction3 = direction.getClockWise();
                Direction direction2 = direction.getCounterClockWise();
                layout.setif(x + direction3.getStepX(), y + direction3.getStepZ(), 0, 2);
                layout.setif(x + direction2.getStepX(), y + direction2.getStepZ(), 0, 2);
                layout.setif(x + direction.getStepX() + direction3.getStepX(), y + direction.getStepZ() + direction3.getStepZ(), 0, 2);
                layout.setif(x + direction.getStepX() + direction2.getStepX(), y + direction.getStepZ() + direction2.getStepZ(), 0, 2);
                layout.setif(x + direction.getStepX() * 2, y + direction.getStepZ() * 2, 0, 2);
                layout.setif(x + direction3.getStepX() * 2, y + direction3.getStepZ() * 2, 0, 2);
                layout.setif(x + direction2.getStepX() * 2, y + direction2.getStepZ() * 2, 0, 2);
            }
        }

        private boolean cleanEdges(WoodlandMansionPieces.SimpleGrid grid) {
            boolean bl = false;

            for (int i = 0; i < grid.height; i++) {
                for (int j = 0; j < grid.width; j++) {
                    if (grid.get(j, i) == 0) {
                        int k = 0;
                        k += isHouse(grid, j + 1, i) ? 1 : 0;
                        k += isHouse(grid, j - 1, i) ? 1 : 0;
                        k += isHouse(grid, j, i + 1) ? 1 : 0;
                        k += isHouse(grid, j, i - 1) ? 1 : 0;
                        if (k >= 3) {
                            grid.set(j, i, 2);
                            bl = true;
                        } else if (k == 2) {
                            int l = 0;
                            l += isHouse(grid, j + 1, i + 1) ? 1 : 0;
                            l += isHouse(grid, j - 1, i + 1) ? 1 : 0;
                            l += isHouse(grid, j + 1, i - 1) ? 1 : 0;
                            l += isHouse(grid, j - 1, i - 1) ? 1 : 0;
                            if (l <= 1) {
                                grid.set(j, i, 2);
                                bl = true;
                            }
                        }
                    }
                }
            }

            return bl;
        }

        private void setupThirdFloor() {
            List<Tuple<Integer, Integer>> list = Lists.newArrayList();
            WoodlandMansionPieces.SimpleGrid simpleGrid = this.floorRooms[1];

            for (int i = 0; i < this.thirdFloorGrid.height; i++) {
                for (int j = 0; j < this.thirdFloorGrid.width; j++) {
                    int k = simpleGrid.get(j, i);
                    int l = k & 983040;
                    if (l == 131072 && (k & 2097152) == 2097152) {
                        list.add(new Tuple<>(j, i));
                    }
                }
            }

            if (list.isEmpty()) {
                this.thirdFloorGrid.set(0, 0, this.thirdFloorGrid.width, this.thirdFloorGrid.height, 5);
            } else {
                Tuple<Integer, Integer> tuple = list.get(this.random.nextInt(list.size()));
                int j = simpleGrid.get(tuple.getA(), tuple.getB());
                simpleGrid.set(tuple.getA(), tuple.getB(), j | 4194304);
                Direction direction = this.get1x2RoomDirection(this.baseGrid, tuple.getA(), tuple.getB(), 1, j & 65535);
                int l = tuple.getA() + direction.getStepX();
                int m = tuple.getB() + direction.getStepZ();

                for (int n = 0; n < this.thirdFloorGrid.height; n++) {
                    for (int o = 0; o < this.thirdFloorGrid.width; o++) {
                        if (!isHouse(this.baseGrid, o, n)) {
                            this.thirdFloorGrid.set(o, n, 5);
                        } else if (o == tuple.getA() && n == tuple.getB()) {
                            this.thirdFloorGrid.set(o, n, 3);
                        } else if (o == l && n == m) {
                            this.thirdFloorGrid.set(o, n, 3);
                            this.floorRooms[2].set(o, n, 8388608);
                        }
                    }
                }

                List<Direction> list2 = Lists.newArrayList();

                for (Direction direction2 : Direction.Plane.HORIZONTAL) {
                    if (this.thirdFloorGrid.get(l + direction2.getStepX(), m + direction2.getStepZ()) == 0) {
                        list2.add(direction2);
                    }
                }

                if (list2.isEmpty()) {
                    this.thirdFloorGrid.set(0, 0, this.thirdFloorGrid.width, this.thirdFloorGrid.height, 5);
                    simpleGrid.set(tuple.getA(), tuple.getB(), j);
                } else {
                    Direction direction3 = list2.get(this.random.nextInt(list2.size()));
                    this.recursiveCorridor(this.thirdFloorGrid, l + direction3.getStepX(), m + direction3.getStepZ(), direction3, 4);

                    while (this.cleanEdges(this.thirdFloorGrid)) {
                    }
                }
            }
        }

        private void identifyRooms(WoodlandMansionPieces.SimpleGrid grid, WoodlandMansionPieces.SimpleGrid floorRooms) {
            ObjectArrayList<Tuple<Integer, Integer>> objectArrayList = new ObjectArrayList<>();

            for (int i = 0; i < grid.height; i++) {
                for (int j = 0; j < grid.width; j++) {
                    if (grid.get(j, i) == 2) {
                        objectArrayList.add(new Tuple<>(j, i));
                    }
                }
            }

            Util.shuffle(objectArrayList, this.random);
            int i = 10;

            for (Tuple<Integer, Integer> tuple : objectArrayList) {
                int k = tuple.getA();
                int l = tuple.getB();
                if (floorRooms.get(k, l) == 0) {
                    int m = k;
                    int n = k;
                    int o = l;
                    int p = l;
                    int q = 65536;
                    if (floorRooms.get(k + 1, l) == 0
                        && floorRooms.get(k, l + 1) == 0
                        && floorRooms.get(k + 1, l + 1) == 0
                        && grid.get(k + 1, l) == 2
                        && grid.get(k, l + 1) == 2
                        && grid.get(k + 1, l + 1) == 2) {
                        n++;
                        p++;
                        q = 262144;
                    } else if (floorRooms.get(k - 1, l) == 0
                        && floorRooms.get(k, l + 1) == 0
                        && floorRooms.get(k - 1, l + 1) == 0
                        && grid.get(k - 1, l) == 2
                        && grid.get(k, l + 1) == 2
                        && grid.get(k - 1, l + 1) == 2) {
                        m--;
                        p++;
                        q = 262144;
                    } else if (floorRooms.get(k - 1, l) == 0
                        && floorRooms.get(k, l - 1) == 0
                        && floorRooms.get(k - 1, l - 1) == 0
                        && grid.get(k - 1, l) == 2
                        && grid.get(k, l - 1) == 2
                        && grid.get(k - 1, l - 1) == 2) {
                        m--;
                        o--;
                        q = 262144;
                    } else if (floorRooms.get(k + 1, l) == 0 && grid.get(k + 1, l) == 2) {
                        n++;
                        q = 131072;
                    } else if (floorRooms.get(k, l + 1) == 0 && grid.get(k, l + 1) == 2) {
                        p++;
                        q = 131072;
                    } else if (floorRooms.get(k - 1, l) == 0 && grid.get(k - 1, l) == 2) {
                        m--;
                        q = 131072;
                    } else if (floorRooms.get(k, l - 1) == 0 && grid.get(k, l - 1) == 2) {
                        o--;
                        q = 131072;
                    }

                    int r = this.random.nextBoolean() ? m : n;
                    int s = this.random.nextBoolean() ? o : p;
                    int t = 2097152;
                    if (!grid.edgesTo(r, s, 1)) {
                        r = r == m ? n : m;
                        s = s == o ? p : o;
                        if (!grid.edgesTo(r, s, 1)) {
                            s = s == o ? p : o;
                            if (!grid.edgesTo(r, s, 1)) {
                                r = r == m ? n : m;
                                s = s == o ? p : o;
                                if (!grid.edgesTo(r, s, 1)) {
                                    t = 0;
                                    r = m;
                                    s = o;
                                }
                            }
                        }
                    }

                    for (int u = o; u <= p; u++) {
                        for (int v = m; v <= n; v++) {
                            if (v == r && u == s) {
                                floorRooms.set(v, u, 1048576 | t | q | i);
                            } else {
                                floorRooms.set(v, u, q | i);
                            }
                        }
                    }

                    i++;
                }
            }
        }
    }

    static class MansionPiecePlacer {
        private final StructureTemplateManager structureTemplateManager;
        private final RandomSource random;
        private int startX;
        private int startY;

        public MansionPiecePlacer(StructureTemplateManager structureTemplateManager, RandomSource random) {
            this.structureTemplateManager = structureTemplateManager;
            this.random = random;
        }

        public void createMansion(
            BlockPos pos, Rotation rotation, List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.MansionGrid grid
        ) {
            WoodlandMansionPieces.PlacementData placementData = new WoodlandMansionPieces.PlacementData();
            placementData.position = pos;
            placementData.rotation = rotation;
            placementData.wallType = "wall_flat";
            WoodlandMansionPieces.PlacementData placementData2 = new WoodlandMansionPieces.PlacementData();
            this.entrance(pieces, placementData);
            placementData2.position = placementData.position.above(8);
            placementData2.rotation = placementData.rotation;
            placementData2.wallType = "wall_window";
            if (!pieces.isEmpty()) {
            }

            WoodlandMansionPieces.SimpleGrid simpleGrid = grid.baseGrid;
            WoodlandMansionPieces.SimpleGrid simpleGrid2 = grid.thirdFloorGrid;
            this.startX = grid.entranceX + 1;
            this.startY = grid.entranceY + 1;
            int i = grid.entranceX + 1;
            int j = grid.entranceY;
            this.traverseOuterWalls(pieces, placementData, simpleGrid, Direction.SOUTH, this.startX, this.startY, i, j);
            this.traverseOuterWalls(pieces, placementData2, simpleGrid, Direction.SOUTH, this.startX, this.startY, i, j);
            WoodlandMansionPieces.PlacementData placementData3 = new WoodlandMansionPieces.PlacementData();
            placementData3.position = placementData.position.above(19);
            placementData3.rotation = placementData.rotation;
            placementData3.wallType = "wall_window";
            boolean bl = false;

            for (int k = 0; k < simpleGrid2.height && !bl; k++) {
                for (int l = simpleGrid2.width - 1; l >= 0 && !bl; l--) {
                    if (WoodlandMansionPieces.MansionGrid.isHouse(simpleGrid2, l, k)) {
                        placementData3.position = placementData3.position.relative(rotation.rotate(Direction.SOUTH), 8 + (k - this.startY) * 8);
                        placementData3.position = placementData3.position.relative(rotation.rotate(Direction.EAST), (l - this.startX) * 8);
                        this.traverseWallPiece(pieces, placementData3);
                        this.traverseOuterWalls(pieces, placementData3, simpleGrid2, Direction.SOUTH, l, k, l, k);
                        bl = true;
                    }
                }
            }

            this.createRoof(pieces, pos.above(16), rotation, simpleGrid, simpleGrid2);
            this.createRoof(pieces, pos.above(27), rotation, simpleGrid2, null);
            if (!pieces.isEmpty()) {
            }

            WoodlandMansionPieces.FloorRoomCollection[] floorRoomCollections = new WoodlandMansionPieces.FloorRoomCollection[]{
                new WoodlandMansionPieces.FirstFloorRoomCollection(),
                new WoodlandMansionPieces.SecondFloorRoomCollection(),
                new WoodlandMansionPieces.ThirdFloorRoomCollection()
            };

            for (int l = 0; l < 3; l++) {
                BlockPos blockPos = pos.above(8 * l + (l == 2 ? 3 : 0));
                WoodlandMansionPieces.SimpleGrid simpleGrid3 = grid.floorRooms[l];
                WoodlandMansionPieces.SimpleGrid simpleGrid4 = l == 2 ? simpleGrid2 : simpleGrid;
                String string = l == 0 ? "carpet_south_1" : "carpet_south_2";
                String string2 = l == 0 ? "carpet_west_1" : "carpet_west_2";

                for (int m = 0; m < simpleGrid4.height; m++) {
                    for (int n = 0; n < simpleGrid4.width; n++) {
                        if (simpleGrid4.get(n, m) == 1) {
                            BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.SOUTH), 8 + (m - this.startY) * 8);
                            blockPos2 = blockPos2.relative(rotation.rotate(Direction.EAST), (n - this.startX) * 8);
                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "corridor_floor", blockPos2, rotation));
                            if (simpleGrid4.get(n, m - 1) == 1 || (simpleGrid3.get(n, m - 1) & 8388608) == 8388608) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "carpet_north", blockPos2.relative(rotation.rotate(Direction.EAST), 1).above(), rotation
                                    )
                                );
                            }

                            if (simpleGrid4.get(n + 1, m) == 1 || (simpleGrid3.get(n + 1, m) & 8388608) == 8388608) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        "carpet_east",
                                        blockPos2.relative(rotation.rotate(Direction.SOUTH), 1).relative(rotation.rotate(Direction.EAST), 5).above(),
                                        rotation
                                    )
                                );
                            }

                            if (simpleGrid4.get(n, m + 1) == 1 || (simpleGrid3.get(n, m + 1) & 8388608) == 8388608) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        string,
                                        blockPos2.relative(rotation.rotate(Direction.SOUTH), 5).relative(rotation.rotate(Direction.WEST), 1),
                                        rotation
                                    )
                                );
                            }

                            if (simpleGrid4.get(n - 1, m) == 1 || (simpleGrid3.get(n - 1, m) & 8388608) == 8388608) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        string2,
                                        blockPos2.relative(rotation.rotate(Direction.WEST), 1).relative(rotation.rotate(Direction.NORTH), 1),
                                        rotation
                                    )
                                );
                            }
                        }
                    }
                }

                String string3 = l == 0 ? "indoors_wall_1" : "indoors_wall_2";
                String string4 = l == 0 ? "indoors_door_1" : "indoors_door_2";
                List<Direction> list = Lists.newArrayList();

                for (int o = 0; o < simpleGrid4.height; o++) {
                    for (int p = 0; p < simpleGrid4.width; p++) {
                        boolean bl2 = l == 2 && simpleGrid4.get(p, o) == 3;
                        if (simpleGrid4.get(p, o) == 2 || bl2) {
                            int q = simpleGrid3.get(p, o);
                            int r = q & 983040;
                            int s = q & 65535;
                            bl2 = bl2 && (q & 8388608) == 8388608;
                            list.clear();
                            if ((q & 2097152) == 2097152) {
                                for (Direction direction : Direction.Plane.HORIZONTAL) {
                                    if (simpleGrid4.get(p + direction.getStepX(), o + direction.getStepZ()) == 1) {
                                        list.add(direction);
                                    }
                                }
                            }

                            Direction direction2 = null;
                            if (!list.isEmpty()) {
                                direction2 = list.get(this.random.nextInt(list.size()));
                            } else if ((q & 1048576) == 1048576) {
                                direction2 = Direction.UP;
                            }

                            BlockPos blockPos3 = blockPos.relative(rotation.rotate(Direction.SOUTH), 8 + (o - this.startY) * 8);
                            blockPos3 = blockPos3.relative(rotation.rotate(Direction.EAST), -1 + (p - this.startX) * 8);
                            if (WoodlandMansionPieces.MansionGrid.isHouse(simpleGrid4, p - 1, o) && !grid.isRoomId(simpleGrid4, p - 1, o, l, s)) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, direction2 == Direction.WEST ? string4 : string3, blockPos3, rotation
                                    )
                                );
                            }

                            if (simpleGrid4.get(p + 1, o) == 1 && !bl2) {
                                BlockPos blockPos4 = blockPos3.relative(rotation.rotate(Direction.EAST), 8);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, direction2 == Direction.EAST ? string4 : string3, blockPos4, rotation
                                    )
                                );
                            }

                            if (WoodlandMansionPieces.MansionGrid.isHouse(simpleGrid4, p, o + 1) && !grid.isRoomId(simpleGrid4, p, o + 1, l, s)) {
                                BlockPos blockPos4 = blockPos3.relative(rotation.rotate(Direction.SOUTH), 7);
                                blockPos4 = blockPos4.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        direction2 == Direction.SOUTH ? string4 : string3,
                                        blockPos4,
                                        rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }

                            if (simpleGrid4.get(p, o - 1) == 1 && !bl2) {
                                BlockPos blockPos4 = blockPos3.relative(rotation.rotate(Direction.NORTH), 1);
                                blockPos4 = blockPos4.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager,
                                        direction2 == Direction.NORTH ? string4 : string3,
                                        blockPos4,
                                        rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }

                            if (r == 65536) {
                                this.addRoom1x1(pieces, blockPos3, rotation, direction2, floorRoomCollections[l]);
                            } else if (r == 131072 && direction2 != null) {
                                Direction direction3 = grid.get1x2RoomDirection(simpleGrid4, p, o, l, s);
                                boolean bl3 = (q & 4194304) == 4194304;
                                this.addRoom1x2(pieces, blockPos3, rotation, direction3, direction2, floorRoomCollections[l], bl3);
                            } else if (r == 262144 && direction2 != null && direction2 != Direction.UP) {
                                Direction direction3 = direction2.getClockWise();
                                if (!grid.isRoomId(simpleGrid4, p + direction3.getStepX(), o + direction3.getStepZ(), l, s)) {
                                    direction3 = direction3.getOpposite();
                                }

                                this.addRoom2x2(pieces, blockPos3, rotation, direction3, direction2, floorRoomCollections[l]);
                            } else if (r == 262144 && direction2 == Direction.UP) {
                                this.addRoom2x2Secret(pieces, blockPos3, rotation, floorRoomCollections[l]);
                            }
                        }
                    }
                }
            }
        }

        private void traverseOuterWalls(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            WoodlandMansionPieces.PlacementData data,
            WoodlandMansionPieces.SimpleGrid layout,
            Direction direction,
            int startX,
            int startY,
            int entranceX,
            int entranceY
        ) {
            int i = startX;
            int j = startY;
            Direction direction2 = direction;

            do {
                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, i + direction.getStepX(), j + direction.getStepZ())) {
                    this.traverseTurn(pieces, data);
                    direction = direction.getClockWise();
                    if (i != entranceX || j != entranceY || direction2 != direction) {
                        this.traverseWallPiece(pieces, data);
                    }
                } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, i + direction.getStepX(), j + direction.getStepZ())
                    && WoodlandMansionPieces.MansionGrid.isHouse(
                        layout,
                        i + direction.getStepX() + direction.getCounterClockWise().getStepX(),
                        j + direction.getStepZ() + direction.getCounterClockWise().getStepZ()
                    )) {
                    this.traverseInnerTurn(pieces, data);
                    i += direction.getStepX();
                    j += direction.getStepZ();
                    direction = direction.getCounterClockWise();
                } else {
                    i += direction.getStepX();
                    j += direction.getStepZ();
                    if (i != entranceX || j != entranceY || direction2 != direction) {
                        this.traverseWallPiece(pieces, data);
                    }
                }
            } while (i != entranceX || j != entranceY || direction2 != direction);
        }

        private void createRoof(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            WoodlandMansionPieces.SimpleGrid layout,
            @Nullable WoodlandMansionPieces.SimpleGrid nextFloorLayout
        ) {
            for (int i = 0; i < layout.height; i++) {
                for (int j = 0; j < layout.width; j++) {
                    BlockPos blockPos = pos;
                    blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 8 + (i - this.startY) * 8);
                    blockPos = blockPos.relative(rotation.rotate(Direction.EAST), (j - this.startX) * 8);
                    boolean bl = nextFloorLayout != null && WoodlandMansionPieces.MansionGrid.isHouse(nextFloorLayout, j, i);
                    if (WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i) && !bl) {
                        pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof", blockPos.above(3), rotation));
                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j + 1, i)) {
                            BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.EAST), 6);
                            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_front", blockPos2, rotation));
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j - 1, i)) {
                            BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.EAST), 0);
                            blockPos2 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 7);
                            pieces.add(
                                new WoodlandMansionPieces.WoodlandMansionPiece(
                                    this.structureTemplateManager, "roof_front", blockPos2, rotation.getRotated(Rotation.CLOCKWISE_180)
                                )
                            );
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i - 1)) {
                            BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.WEST), 1);
                            pieces.add(
                                new WoodlandMansionPieces.WoodlandMansionPiece(
                                    this.structureTemplateManager, "roof_front", blockPos2, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                )
                            );
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i + 1)) {
                            BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.EAST), 6);
                            blockPos2 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 6);
                            pieces.add(
                                new WoodlandMansionPieces.WoodlandMansionPiece(
                                    this.structureTemplateManager, "roof_front", blockPos2, rotation.getRotated(Rotation.CLOCKWISE_90)
                                )
                            );
                        }
                    }
                }
            }

            if (nextFloorLayout != null) {
                for (int i = 0; i < layout.height; i++) {
                    for (int j = 0; j < layout.width; j++) {
                        BlockPos blockPos = pos;
                        blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 8 + (i - this.startY) * 8);
                        blockPos = blockPos.relative(rotation.rotate(Direction.EAST), (j - this.startX) * 8);
                        boolean bl = WoodlandMansionPieces.MansionGrid.isHouse(nextFloorLayout, j, i);
                        if (WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i) && bl) {
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j + 1, i)) {
                                BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.EAST), 7);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall", blockPos2, rotation));
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j - 1, i)) {
                                BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.WEST), 1);
                                blockPos2 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 6);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "small_wall", blockPos2, rotation.getRotated(Rotation.CLOCKWISE_180)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i - 1)) {
                                BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.WEST), 0);
                                blockPos2 = blockPos2.relative(rotation.rotate(Direction.NORTH), 1);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "small_wall", blockPos2, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i + 1)) {
                                BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.EAST), 6);
                                blockPos2 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 7);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "small_wall", blockPos2, rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j + 1, i)) {
                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i - 1)) {
                                    BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.EAST), 7);
                                    blockPos2 = blockPos2.relative(rotation.rotate(Direction.NORTH), 2);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "small_wall_corner", blockPos2, rotation)
                                    );
                                }

                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i + 1)) {
                                    BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.EAST), 8);
                                    blockPos2 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 7);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(
                                            this.structureTemplateManager, "small_wall_corner", blockPos2, rotation.getRotated(Rotation.CLOCKWISE_90)
                                        )
                                    );
                                }
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j - 1, i)) {
                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i - 1)) {
                                    BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.WEST), 2);
                                    blockPos2 = blockPos2.relative(rotation.rotate(Direction.NORTH), 1);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(
                                            this.structureTemplateManager, "small_wall_corner", blockPos2, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                        )
                                    );
                                }

                                if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i + 1)) {
                                    BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.WEST), 1);
                                    blockPos2 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 8);
                                    pieces.add(
                                        new WoodlandMansionPieces.WoodlandMansionPiece(
                                            this.structureTemplateManager, "small_wall_corner", blockPos2, rotation.getRotated(Rotation.CLOCKWISE_180)
                                        )
                                    );
                                }
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < layout.height; i++) {
                for (int j = 0; j < layout.width; j++) {
                    BlockPos blockPos = pos;
                    blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 8 + (i - this.startY) * 8);
                    blockPos = blockPos.relative(rotation.rotate(Direction.EAST), (j - this.startX) * 8);
                    boolean bl = nextFloorLayout != null && WoodlandMansionPieces.MansionGrid.isHouse(nextFloorLayout, j, i);
                    if (WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i) && !bl) {
                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j + 1, i)) {
                            BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.EAST), 6);
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i + 1)) {
                                BlockPos blockPos3 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 6);
                                pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_corner", blockPos3, rotation));
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, j + 1, i + 1)) {
                                BlockPos blockPos3 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 5);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "roof_inner_corner", blockPos3, rotation)
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i - 1)) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_corner", blockPos2, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                    )
                                );
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, j + 1, i - 1)) {
                                BlockPos blockPos3 = blockPos.relative(rotation.rotate(Direction.EAST), 9);
                                blockPos3 = blockPos3.relative(rotation.rotate(Direction.NORTH), 2);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_inner_corner", blockPos3, rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            }
                        }

                        if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j - 1, i)) {
                            BlockPos blockPos2 = blockPos.relative(rotation.rotate(Direction.EAST), 0);
                            blockPos2 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 0);
                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i + 1)) {
                                BlockPos blockPos3 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 6);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_corner", blockPos3, rotation.getRotated(Rotation.CLOCKWISE_90)
                                    )
                                );
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, j - 1, i + 1)) {
                                BlockPos blockPos3 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 8);
                                blockPos3 = blockPos3.relative(rotation.rotate(Direction.WEST), 3);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_inner_corner", blockPos3, rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                                    )
                                );
                            }

                            if (!WoodlandMansionPieces.MansionGrid.isHouse(layout, j, i - 1)) {
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_corner", blockPos2, rotation.getRotated(Rotation.CLOCKWISE_180)
                                    )
                                );
                            } else if (WoodlandMansionPieces.MansionGrid.isHouse(layout, j - 1, i - 1)) {
                                BlockPos blockPos3 = blockPos2.relative(rotation.rotate(Direction.SOUTH), 1);
                                pieces.add(
                                    new WoodlandMansionPieces.WoodlandMansionPiece(
                                        this.structureTemplateManager, "roof_inner_corner", blockPos3, rotation.getRotated(Rotation.CLOCKWISE_180)
                                    )
                                );
                            }
                        }
                    }
                }
            }
        }

        private void entrance(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            Direction direction = data.rotation.rotate(Direction.WEST);
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "entrance", data.position.relative(direction, 9), data.rotation)
            );
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), 16);
        }

        private void traverseWallPiece(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(
                    this.structureTemplateManager, data.wallType, data.position.relative(data.rotation.rotate(Direction.EAST), 7), data.rotation
                )
            );
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), 8);
        }

        private void traverseTurn(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), -1);
            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, "wall_corner", data.position, data.rotation));
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), -7);
            data.position = data.position.relative(data.rotation.rotate(Direction.WEST), -6);
            data.rotation = data.rotation.getRotated(Rotation.CLOCKWISE_90);
        }

        private void traverseInnerTurn(List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, WoodlandMansionPieces.PlacementData data) {
            data.position = data.position.relative(data.rotation.rotate(Direction.SOUTH), 6);
            data.position = data.position.relative(data.rotation.rotate(Direction.EAST), 8);
            data.rotation = data.rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
        }

        private void addRoom1x1(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            Direction direction,
            WoodlandMansionPieces.FloorRoomCollection floorRooms
        ) {
            Rotation rotation2 = Rotation.NONE;
            String string = floorRooms.get1x1(this.random);
            if (direction != Direction.EAST) {
                if (direction == Direction.NORTH) {
                    rotation2 = rotation2.getRotated(Rotation.COUNTERCLOCKWISE_90);
                } else if (direction == Direction.WEST) {
                    rotation2 = rotation2.getRotated(Rotation.CLOCKWISE_180);
                } else if (direction == Direction.SOUTH) {
                    rotation2 = rotation2.getRotated(Rotation.CLOCKWISE_90);
                } else {
                    string = floorRooms.get1x1Secret(this.random);
                }
            }

            BlockPos blockPos = StructureTemplate.getZeroPositionWithTransform(new BlockPos(1, 0, 0), Mirror.NONE, rotation2, 7, 7);
            rotation2 = rotation2.getRotated(rotation);
            blockPos = blockPos.rotate(rotation);
            BlockPos blockPos2 = pos.offset(blockPos.getX(), 0, blockPos.getZ());
            pieces.add(new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, string, blockPos2, rotation2));
        }

        private void addRoom1x2(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            Direction frontDirection,
            Direction sideDirection,
            WoodlandMansionPieces.FloorRoomCollection floorRooms,
            boolean isStairs
        ) {
            if (sideDirection == Direction.EAST && frontDirection == Direction.SOUTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, floorRooms.get1x2SideEntrance(this.random, isStairs), blockPos, rotation
                    )
                );
            } else if (sideDirection == Direction.EAST && frontDirection == Direction.NORTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, floorRooms.get1x2SideEntrance(this.random, isStairs), blockPos, rotation, Mirror.LEFT_RIGHT
                    )
                );
            } else if (sideDirection == Direction.WEST && frontDirection == Direction.NORTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 7);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2SideEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_180)
                    )
                );
            } else if (sideDirection == Direction.WEST && frontDirection == Direction.SOUTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 7);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, floorRooms.get1x2SideEntrance(this.random, isStairs), blockPos, rotation, Mirror.FRONT_BACK
                    )
                );
            } else if (sideDirection == Direction.SOUTH && frontDirection == Direction.EAST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2SideEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_90),
                        Mirror.LEFT_RIGHT
                    )
                );
            } else if (sideDirection == Direction.SOUTH && frontDirection == Direction.WEST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 7);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2SideEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_90)
                    )
                );
            } else if (sideDirection == Direction.NORTH && frontDirection == Direction.WEST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 7);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2SideEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_90),
                        Mirror.FRONT_BACK
                    )
                );
            } else if (sideDirection == Direction.NORTH && frontDirection == Direction.EAST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2SideEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                    )
                );
            } else if (sideDirection == Direction.SOUTH && frontDirection == Direction.NORTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos = blockPos.relative(rotation.rotate(Direction.NORTH), 8);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, floorRooms.get1x2FrontEntrance(this.random, isStairs), blockPos, rotation
                    )
                );
            } else if (sideDirection == Direction.NORTH && frontDirection == Direction.SOUTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 7);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 14);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2FrontEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_180)
                    )
                );
            } else if (sideDirection == Direction.WEST && frontDirection == Direction.EAST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 15);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2FrontEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.CLOCKWISE_90)
                    )
                );
            } else if (sideDirection == Direction.EAST && frontDirection == Direction.WEST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.WEST), 7);
                blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), 6);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager,
                        floorRooms.get1x2FrontEntrance(this.random, isStairs),
                        blockPos,
                        rotation.getRotated(Rotation.COUNTERCLOCKWISE_90)
                    )
                );
            } else if (sideDirection == Direction.UP && frontDirection == Direction.EAST) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 15);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(
                        this.structureTemplateManager, floorRooms.get1x2Secret(this.random), blockPos, rotation.getRotated(Rotation.CLOCKWISE_90)
                    )
                );
            } else if (sideDirection == Direction.UP && frontDirection == Direction.SOUTH) {
                BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
                blockPos = blockPos.relative(rotation.rotate(Direction.NORTH), 0);
                pieces.add(
                    new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, floorRooms.get1x2Secret(this.random), blockPos, rotation)
                );
            }
        }

        private void addRoom2x2(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces,
            BlockPos pos,
            Rotation rotation,
            Direction frontDirection,
            Direction sideDirection,
            WoodlandMansionPieces.FloorRoomCollection floorRooms
        ) {
            int i = 0;
            int j = 0;
            Rotation rotation2 = rotation;
            Mirror mirror = Mirror.NONE;
            if (sideDirection == Direction.EAST && frontDirection == Direction.SOUTH) {
                i = -7;
            } else if (sideDirection == Direction.EAST && frontDirection == Direction.NORTH) {
                i = -7;
                j = 6;
                mirror = Mirror.LEFT_RIGHT;
            } else if (sideDirection == Direction.NORTH && frontDirection == Direction.EAST) {
                i = 1;
                j = 14;
                rotation2 = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
            } else if (sideDirection == Direction.NORTH && frontDirection == Direction.WEST) {
                i = 7;
                j = 14;
                rotation2 = rotation.getRotated(Rotation.COUNTERCLOCKWISE_90);
                mirror = Mirror.LEFT_RIGHT;
            } else if (sideDirection == Direction.SOUTH && frontDirection == Direction.WEST) {
                i = 7;
                j = -8;
                rotation2 = rotation.getRotated(Rotation.CLOCKWISE_90);
            } else if (sideDirection == Direction.SOUTH && frontDirection == Direction.EAST) {
                i = 1;
                j = -8;
                rotation2 = rotation.getRotated(Rotation.CLOCKWISE_90);
                mirror = Mirror.LEFT_RIGHT;
            } else if (sideDirection == Direction.WEST && frontDirection == Direction.NORTH) {
                i = 15;
                j = 6;
                rotation2 = rotation.getRotated(Rotation.CLOCKWISE_180);
            } else if (sideDirection == Direction.WEST && frontDirection == Direction.SOUTH) {
                i = 15;
                mirror = Mirror.FRONT_BACK;
            }

            BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), i);
            blockPos = blockPos.relative(rotation.rotate(Direction.SOUTH), j);
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(this.structureTemplateManager, floorRooms.get2x2(this.random), blockPos, rotation2, mirror)
            );
        }

        private void addRoom2x2Secret(
            List<WoodlandMansionPieces.WoodlandMansionPiece> pieces, BlockPos pos, Rotation rotation, WoodlandMansionPieces.FloorRoomCollection floorRooms
        ) {
            BlockPos blockPos = pos.relative(rotation.rotate(Direction.EAST), 1);
            pieces.add(
                new WoodlandMansionPieces.WoodlandMansionPiece(
                    this.structureTemplateManager, floorRooms.get2x2Secret(this.random), blockPos, rotation, Mirror.NONE
                )
            );
        }
    }

    static class PlacementData {
        public Rotation rotation;
        public BlockPos position;
        public String wallType;
    }

    static class SecondFloorRoomCollection extends WoodlandMansionPieces.FloorRoomCollection {
        @Override
        public String get1x1(RandomSource randomSource) {
            return "1x1_b" + (randomSource.nextInt(4) + 1);
        }

        @Override
        public String get1x1Secret(RandomSource randomSource) {
            return "1x1_as" + (randomSource.nextInt(4) + 1);
        }

        @Override
        public String get1x2SideEntrance(RandomSource randomSource, boolean bl) {
            return bl ? "1x2_c_stairs" : "1x2_c" + (randomSource.nextInt(4) + 1);
        }

        @Override
        public String get1x2FrontEntrance(RandomSource randomSource, boolean bl) {
            return bl ? "1x2_d_stairs" : "1x2_d" + (randomSource.nextInt(5) + 1);
        }

        @Override
        public String get1x2Secret(RandomSource randomSource) {
            return "1x2_se" + (randomSource.nextInt(1) + 1);
        }

        @Override
        public String get2x2(RandomSource randomSource) {
            return "2x2_b" + (randomSource.nextInt(5) + 1);
        }

        @Override
        public String get2x2Secret(RandomSource randomSource) {
            return "2x2_s1";
        }
    }

    static class SimpleGrid {
        private final int[][] grid;
        final int width;
        final int height;
        private final int valueIfOutside;

        public SimpleGrid(int width, int height, int valueIfOutside) {
            this.width = width;
            this.height = height;
            this.valueIfOutside = valueIfOutside;
            this.grid = new int[width][height];
        }

        public void set(int x, int y, int value) {
            if (x >= 0 && x < this.width && y >= 0 && y < this.height) {
                this.grid[x][y] = value;
            }
        }

        public void set(int minX, int minY, int maxX, int maxY, int value) {
            for (int i = minY; i <= maxY; i++) {
                for (int j = minX; j <= maxX; j++) {
                    this.set(j, i, value);
                }
            }
        }

        public int get(int x, int y) {
            return x >= 0 && x < this.width && y >= 0 && y < this.height ? this.grid[x][y] : this.valueIfOutside;
        }

        public void setif(int x, int y, int oldValue, int newValue) {
            if (this.get(x, y) == oldValue) {
                this.set(x, y, newValue);
            }
        }

        public boolean edgesTo(int x, int y, int expectedValue) {
            return this.get(x - 1, y) == expectedValue
                || this.get(x + 1, y) == expectedValue
                || this.get(x, y + 1) == expectedValue
                || this.get(x, y - 1) == expectedValue;
        }
    }

    static class ThirdFloorRoomCollection extends WoodlandMansionPieces.SecondFloorRoomCollection {
    }

    public static class WoodlandMansionPiece extends TemplateStructurePiece {
        public WoodlandMansionPiece(StructureTemplateManager structureTemplateManager, String templateName, BlockPos templatePosition, Rotation rotation) {
            this(structureTemplateManager, templateName, templatePosition, rotation, Mirror.NONE);
        }

        public WoodlandMansionPiece(
            StructureTemplateManager structureTemplateManager, String templateName, BlockPos templatePosition, Rotation rotation, Mirror mirror
        ) {
            super(
                StructurePieceType.WOODLAND_MANSION_PIECE,
                0,
                structureTemplateManager,
                makeLocation(templateName),
                templateName,
                makeSettings(mirror, rotation),
                templatePosition
            );
        }

        public WoodlandMansionPiece(StructureTemplateManager structureTemplateManager, CompoundTag tag) {
            super(
                StructurePieceType.WOODLAND_MANSION_PIECE,
                tag,
                structureTemplateManager,
                resourceLocation -> makeSettings(Mirror.valueOf(tag.getString("Mi")), Rotation.valueOf(tag.getString("Rot")))
            );
        }

        @Override
        protected ResourceLocation makeTemplateLocation() {
            return makeLocation(this.templateName);
        }

        private static ResourceLocation makeLocation(String name) {
            return ResourceLocation.withDefaultNamespace("woodland_mansion/" + name);
        }

        private static StructurePlaceSettings makeSettings(Mirror mirror, Rotation rotation) {
            return new StructurePlaceSettings()
                .setIgnoreEntities(true)
                .setRotation(rotation)
                .setMirror(mirror)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        @Override
        protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
            super.addAdditionalSaveData(context, tag);
            tag.putString("Rot", this.placeSettings.getRotation().name());
            tag.putString("Mi", this.placeSettings.getMirror().name());
        }

        @Override
        protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
            if (name.startsWith("Chest")) {
                Rotation rotation = this.placeSettings.getRotation();
                BlockState blockState = Blocks.CHEST.defaultBlockState();
                if ("ChestWest".equals(name)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.WEST));
                } else if ("ChestEast".equals(name)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.EAST));
                } else if ("ChestSouth".equals(name)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.SOUTH));
                } else if ("ChestNorth".equals(name)) {
                    blockState = blockState.setValue(ChestBlock.FACING, rotation.rotate(Direction.NORTH));
                }

                this.createChest(level, box, random, pos, BuiltInLootTables.WOODLAND_MANSION, blockState);
            } else {
                List<Mob> list = new ArrayList<>();
                switch (name) {
                    case "Mage":
                        list.add(EntityType.EVOKER.create(level.getLevel()));
                        break;
                    case "Warrior":
                        list.add(EntityType.VINDICATOR.create(level.getLevel()));
                        break;
                    case "Group of Allays":
                        int i = level.getRandom().nextInt(3) + 1;

                        for (int j = 0; j < i; j++) {
                            list.add(EntityType.ALLAY.create(level.getLevel()));
                        }
                        break;
                    default:
                        return;
                }

                for (Mob mob : list) {
                    if (mob != null) {
                        mob.setPersistenceRequired();
                        mob.moveTo(pos, 0.0F, 0.0F);
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.STRUCTURE, null);
                        level.addFreshEntityWithPassengers(mob);
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }
}
