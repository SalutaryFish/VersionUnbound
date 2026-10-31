package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class WorldCoordinates implements Coordinates {
    private final WorldCoordinate x;
    private final WorldCoordinate y;
    private final WorldCoordinate z;

    public WorldCoordinates(WorldCoordinate x, WorldCoordinate y, WorldCoordinate z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public Vec3 getPosition(CommandSourceStack source) {
        Vec3 vec3 = source.getPosition();
        return new Vec3(this.x.get(vec3.x), this.y.get(vec3.y), this.z.get(vec3.z));
    }

    @Override
    public Vec2 getRotation(CommandSourceStack source) {
        Vec2 vec2 = source.getRotation();
        return new Vec2((float)this.x.get(vec2.x), (float)this.y.get(vec2.y));
    }

    @Override
    public boolean isXRelative() {
        return this.x.isRelative();
    }

    @Override
    public boolean isYRelative() {
        return this.y.isRelative();
    }

    @Override
    public boolean isZRelative() {
        return this.z.isRelative();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof WorldCoordinates worldCoordinates)) {
            return false;
        } else if (!this.x.equals(worldCoordinates.x)) {
            return false;
        } else {
            return !this.y.equals(worldCoordinates.y) ? false : this.z.equals(worldCoordinates.z);
        }
    }

    public static WorldCoordinates parseInt(StringReader reader) throws CommandSyntaxException {
        int i = reader.getCursor();
        WorldCoordinate worldCoordinate = WorldCoordinate.parseInt(reader);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate worldCoordinate2 = WorldCoordinate.parseInt(reader);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate3 = WorldCoordinate.parseInt(reader);
                return new WorldCoordinates(worldCoordinate, worldCoordinate2, worldCoordinate3);
            } else {
                reader.setCursor(i);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        } else {
            reader.setCursor(i);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
    }

    public static WorldCoordinates parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        int i = reader.getCursor();
        WorldCoordinate worldCoordinate = WorldCoordinate.parseDouble(reader, centerCorrect);
        if (reader.canRead() && reader.peek() == ' ') {
            reader.skip();
            WorldCoordinate worldCoordinate2 = WorldCoordinate.parseDouble(reader, false);
            if (reader.canRead() && reader.peek() == ' ') {
                reader.skip();
                WorldCoordinate worldCoordinate3 = WorldCoordinate.parseDouble(reader, centerCorrect);
                return new WorldCoordinates(worldCoordinate, worldCoordinate2, worldCoordinate3);
            } else {
                reader.setCursor(i);
                throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
            }
        } else {
            reader.setCursor(i);
            throw Vec3Argument.ERROR_NOT_COMPLETE.createWithContext(reader);
        }
    }

    public static WorldCoordinates absolute(double x, double y, double z) {
        return new WorldCoordinates(new WorldCoordinate(false, x), new WorldCoordinate(false, y), new WorldCoordinate(false, z));
    }

    public static WorldCoordinates absolute(Vec2 vector) {
        return new WorldCoordinates(new WorldCoordinate(false, vector.x), new WorldCoordinate(false, vector.y), new WorldCoordinate(true, 0.0));
    }

    public static WorldCoordinates current() {
        return new WorldCoordinates(new WorldCoordinate(true, 0.0), new WorldCoordinate(true, 0.0), new WorldCoordinate(true, 0.0));
    }

    @Override
    public int hashCode() {
        int i = this.x.hashCode();
        i = 31 * i + this.y.hashCode();
        return 31 * i + this.z.hashCode();
    }
}
