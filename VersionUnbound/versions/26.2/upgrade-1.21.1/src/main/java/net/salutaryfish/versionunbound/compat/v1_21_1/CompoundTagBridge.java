package net.salutaryfish.versionunbound.compat.v1_21_1;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** 1.21.1's {@code CompoundTag} members. */
public final class CompoundTagBridge {
    /** 1.21.1's {@code Tag.TAG_ANY_NUMERIC}: {@code contains} takes it for any numeric tag. */
    private static final int ANY_NUMERIC = 99;

    private CompoundTagBridge() {}

    /** {@code contains(String, int tagType)}: whether the key holds a tag of that type (1.21.1's body). */
    public static boolean contains(CompoundTag tag, String key, int tagType) {
        Tag value = tag.get(key);
        int type = value == null ? 0 : value.getId();
        if (type == tagType) return true;
        return tagType == ANY_NUMERIC && type >= Tag.TAG_BYTE && type <= Tag.TAG_DOUBLE;
    }

    /** {@code getString(String)}: the string, or {@code ""} when the key holds none (26.2 returns an {@code Optional}). */
    public static String getString(CompoundTag tag, String key) {
        return tag.getStringOr(key, "");
    }
}
