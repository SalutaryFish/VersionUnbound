package net.minecraft.nbt;

import java.io.DataInput;
import java.io.IOException;

public interface TagType<T extends Tag> {
    T load(DataInput var1, NbtAccounter var2) throws IOException;

    StreamTagVisitor.ValueResult parse(DataInput var1, StreamTagVisitor var2, NbtAccounter var3) throws IOException;

    default void parseRoot(DataInput input, StreamTagVisitor visitor, NbtAccounter nbtAccounter) throws IOException {
        switch (visitor.visitRootEntry(this)) {
            case CONTINUE:
                this.parse(input, visitor, nbtAccounter);
            case HALT:
            default:
                break;
            case BREAK:
                this.skip(input, nbtAccounter);
        }
    }

    void skip(DataInput var1, int var2, NbtAccounter var3) throws IOException;

    void skip(DataInput var1, NbtAccounter var2) throws IOException;

    default boolean isValue() {
        return false;
    }

    String getName();

    String getPrettyName();

    static TagType<EndTag> createInvalid(int id) {
        return new TagType<EndTag>() {
            private IOException createException() {
                return new IOException("Invalid tag id: " + id);
            }

            public EndTag load(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
                throw this.createException();
            }

            @Override
            public StreamTagVisitor.ValueResult parse(DataInput dataInput, StreamTagVisitor streamTagVisitor, NbtAccounter nbtAccounter) throws IOException {
                throw this.createException();
            }

            @Override
            public void skip(DataInput dataInput, int i, NbtAccounter nbtAccounter) throws IOException {
                throw this.createException();
            }

            @Override
            public void skip(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
                throw this.createException();
            }

            @Override
            public String getName() {
                return "INVALID[" + id + "]";
            }

            @Override
            public String getPrettyName() {
                return "UNKNOWN_" + id;
            }
        };
    }

    interface StaticSize<T extends Tag> extends TagType<T> {
        @Override
        default void skip(DataInput dataInput, NbtAccounter nbtAccounter) throws IOException {
            dataInput.skipBytes(this.size());
        }

        @Override
        default void skip(DataInput dataInput, int i, NbtAccounter nbtAccounter) throws IOException {
            dataInput.skipBytes(this.size() * i);
        }

        int size();
    }

    interface VariableSize<T extends Tag> extends TagType<T> {
        @Override
        default void skip(DataInput dataInput, int i, NbtAccounter nbtAccounter) throws IOException {
            for (int j = 0; j < i; j++) {
                this.skip(dataInput, nbtAccounter);
            }
        }
    }
}
