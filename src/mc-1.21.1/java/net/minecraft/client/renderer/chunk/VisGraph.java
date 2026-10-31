package net.minecraft.client.renderer.chunk;

import it.unimi.dsi.fastutil.ints.IntArrayFIFOQueue;
import it.unimi.dsi.fastutil.ints.IntPriorityQueue;
import java.util.BitSet;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class VisGraph {
    private static final int SIZE_IN_BITS = 4;
    private static final int LEN = 16;
    private static final int MASK = 15;
    private static final int SIZE = 4096;
    private static final int X_SHIFT = 0;
    private static final int Z_SHIFT = 4;
    private static final int Y_SHIFT = 8;
    private static final int DX = (int)Math.pow(16.0, 0.0);
    private static final int DZ = (int)Math.pow(16.0, 1.0);
    private static final int DY = (int)Math.pow(16.0, 2.0);
    private static final int INVALID_INDEX = -1;
    private static final Direction[] DIRECTIONS = Direction.values();
    private final BitSet bitSet = new BitSet(4096);
    private static final int[] INDEX_OF_EDGES = Util.make(new int[1352], indices -> {
        int i = 0;
        int j = 15;
        int k = 0;

        for (int l = 0; l < 16; l++) {
            for (int m = 0; m < 16; m++) {
                for (int n = 0; n < 16; n++) {
                    if (l == 0 || l == 15 || m == 0 || m == 15 || n == 0 || n == 15) {
                        indices[k++] = getIndex(l, m, n);
                    }
                }
            }
        }
    });
    private int empty = 4096;

    public void setOpaque(BlockPos pos) {
        this.bitSet.set(getIndex(pos), true);
        this.empty--;
    }

    private static int getIndex(BlockPos pos) {
        return getIndex(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
    }

    private static int getIndex(int x, int y, int z) {
        return x << 0 | y << 8 | z << 4;
    }

    public VisibilitySet resolve() {
        VisibilitySet visibilitySet = new VisibilitySet();
        if (4096 - this.empty < 256) {
            visibilitySet.setAll(true);
        } else if (this.empty == 0) {
            visibilitySet.setAll(false);
        } else {
            for (int i : INDEX_OF_EDGES) {
                if (!this.bitSet.get(i)) {
                    visibilitySet.add(this.floodFill(i));
                }
            }
        }

        return visibilitySet;
    }

    private Set<Direction> floodFill(int index) {
        Set<Direction> set = EnumSet.noneOf(Direction.class);
        IntPriorityQueue intPriorityQueue = new IntArrayFIFOQueue();
        intPriorityQueue.enqueue(index);
        this.bitSet.set(index, true);

        while (!intPriorityQueue.isEmpty()) {
            int i = intPriorityQueue.dequeueInt();
            this.addEdges(i, set);

            for (Direction direction : DIRECTIONS) {
                int j = this.getNeighborIndexAtFace(i, direction);
                if (j >= 0 && !this.bitSet.get(j)) {
                    this.bitSet.set(j, true);
                    intPriorityQueue.enqueue(j);
                }
            }
        }

        return set;
    }

    private void addEdges(int index, Set<Direction> faces) {
        int i = index >> 0 & 15;
        if (i == 0) {
            faces.add(Direction.WEST);
        } else if (i == 15) {
            faces.add(Direction.EAST);
        }

        int j = index >> 8 & 15;
        if (j == 0) {
            faces.add(Direction.DOWN);
        } else if (j == 15) {
            faces.add(Direction.UP);
        }

        int k = index >> 4 & 15;
        if (k == 0) {
            faces.add(Direction.NORTH);
        } else if (k == 15) {
            faces.add(Direction.SOUTH);
        }
    }

    private int getNeighborIndexAtFace(int index, Direction face) {
        switch (face) {
            case DOWN:
                if ((index >> 8 & 15) == 0) {
                    return -1;
                }

                return index - DY;
            case UP:
                if ((index >> 8 & 15) == 15) {
                    return -1;
                }

                return index + DY;
            case NORTH:
                if ((index >> 4 & 15) == 0) {
                    return -1;
                }

                return index - DZ;
            case SOUTH:
                if ((index >> 4 & 15) == 15) {
                    return -1;
                }

                return index + DZ;
            case WEST:
                if ((index >> 0 & 15) == 0) {
                    return -1;
                }

                return index - DX;
            case EAST:
                if ((index >> 0 & 15) == 15) {
                    return -1;
                }

                return index + DX;
            default:
                return -1;
        }
    }
}
