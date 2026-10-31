package net.minecraft.client.renderer.chunk;

import java.util.BitSet;
import java.util.Set;
import net.minecraft.core.Direction;

public class VisibilitySet {
    private static final int FACINGS = Direction.values().length;
    private final BitSet data = new BitSet(FACINGS * FACINGS);

    public void add(Set<Direction> faces) {
        for (Direction direction : faces) {
            for (Direction direction2 : faces) {
                this.set(direction, direction2, true);
            }
        }
    }

    public void set(Direction face, Direction otherFace, boolean visible) {
        this.data.set(face.ordinal() + otherFace.ordinal() * FACINGS, visible);
        this.data.set(otherFace.ordinal() + face.ordinal() * FACINGS, visible);
    }

    public void setAll(boolean visible) {
        this.data.set(0, this.data.size(), visible);
    }

    public boolean visibilityBetween(Direction face, Direction otherFace) {
        return this.data.get(face.ordinal() + otherFace.ordinal() * FACINGS);
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(' ');

        for (Direction direction : Direction.values()) {
            stringBuilder.append(' ').append(direction.toString().toUpperCase().charAt(0));
        }

        stringBuilder.append('\n');

        for (Direction direction : Direction.values()) {
            stringBuilder.append(direction.toString().toUpperCase().charAt(0));

            for (Direction direction2 : Direction.values()) {
                if (direction == direction2) {
                    stringBuilder.append("  ");
                } else {
                    boolean bl = this.visibilityBetween(direction, direction2);
                    stringBuilder.append(' ').append((char)(bl ? 'Y' : 'n'));
                }
            }

            stringBuilder.append('\n');
        }

        return stringBuilder.toString();
    }
}
