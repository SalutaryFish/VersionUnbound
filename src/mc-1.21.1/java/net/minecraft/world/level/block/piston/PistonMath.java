package net.minecraft.world.level.block.piston;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;

public class PistonMath {
    public static AABB getMovementArea(AABB bounds, Direction dir, double delta) {
        double d = delta * dir.getAxisDirection().getStep();
        double e = Math.min(d, 0.0);
        double f = Math.max(d, 0.0);
        switch (dir) {
            case WEST:
                return new AABB(bounds.minX + e, bounds.minY, bounds.minZ, bounds.minX + f, bounds.maxY, bounds.maxZ);
            case EAST:
                return new AABB(bounds.maxX + e, bounds.minY, bounds.minZ, bounds.maxX + f, bounds.maxY, bounds.maxZ);
            case DOWN:
                return new AABB(bounds.minX, bounds.minY + e, bounds.minZ, bounds.maxX, bounds.minY + f, bounds.maxZ);
            case UP:
            default:
                return new AABB(bounds.minX, bounds.maxY + e, bounds.minZ, bounds.maxX, bounds.maxY + f, bounds.maxZ);
            case NORTH:
                return new AABB(bounds.minX, bounds.minY, bounds.minZ + e, bounds.maxX, bounds.maxY, bounds.minZ + f);
            case SOUTH:
                return new AABB(bounds.minX, bounds.minY, bounds.maxZ + e, bounds.maxX, bounds.maxY, bounds.maxZ + f);
        }
    }
}
