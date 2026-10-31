package net.minecraft.commands.arguments.coordinates;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.network.chat.Component;

public class WorldCoordinate {
    private static final char PREFIX_RELATIVE = '~';
    public static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.double"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_INT = new SimpleCommandExceptionType(Component.translatable("argument.pos.missing.int"));
    private final boolean relative;
    private final double value;

    public WorldCoordinate(boolean relative, double value) {
        this.relative = relative;
        this.value = value;
    }

    public double get(double coord) {
        return this.relative ? this.value + coord : this.value;
    }

    public static WorldCoordinate parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        }

        if (!reader.canRead()) {
            throw ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        }

        boolean bl = isRelative(reader);
        int i = reader.getCursor();
        double d = reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
        String string = reader.getString().substring(i, reader.getCursor());
        if (bl && string.isEmpty()) {
            return new WorldCoordinate(true, 0.0);
        }

        if (!string.contains(".") && !bl && centerCorrect) {
            d += 0.5;
        }

        return new WorldCoordinate(bl, d);
    }

    public static WorldCoordinate parseInt(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') {
            throw Vec3Argument.ERROR_MIXED_TYPE.createWithContext(reader);
        }

        if (!reader.canRead()) {
            throw ERROR_EXPECTED_INT.createWithContext(reader);
        }

        boolean bl = isRelative(reader);
        double d;
        if (reader.canRead() && reader.peek() != ' ') {
            d = bl ? reader.readDouble() : reader.readInt();
        } else {
            d = 0.0;
        }

        return new WorldCoordinate(bl, d);
    }

    public static boolean isRelative(StringReader reader) {
        boolean bl;
        if (reader.peek() == '~') {
            bl = true;
            reader.skip();
        } else {
            bl = false;
        }

        return bl;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof WorldCoordinate worldCoordinate)) {
            return false;
        } else {
            return this.relative != worldCoordinate.relative ? false : Double.compare(worldCoordinate.value, this.value) == 0;
        }
    }

    @Override
    public int hashCode() {
        int i = this.relative ? 1 : 0;
        long l = Double.doubleToLongBits(this.value);
        return 31 * i + (int)(l ^ l >>> 32);
    }

    public boolean isRelative() {
        return this.relative;
    }
}
