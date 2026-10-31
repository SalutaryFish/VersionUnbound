package net.minecraft.stats;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.RecipeBookType;

public final class RecipeBookSettings {
    private static final Map<RecipeBookType, Pair<String, String>> TAG_FIELDS = ImmutableMap.of(
        RecipeBookType.CRAFTING,
        Pair.of("isGuiOpen", "isFilteringCraftable"),
        RecipeBookType.FURNACE,
        Pair.of("isFurnaceGuiOpen", "isFurnaceFilteringCraftable"),
        RecipeBookType.BLAST_FURNACE,
        Pair.of("isBlastingFurnaceGuiOpen", "isBlastingFurnaceFilteringCraftable"),
        RecipeBookType.SMOKER,
        Pair.of("isSmokerGuiOpen", "isSmokerFilteringCraftable")
    );
    private final Map<RecipeBookType, RecipeBookSettings.TypeSettings> states;

    private RecipeBookSettings(Map<RecipeBookType, RecipeBookSettings.TypeSettings> states) {
        this.states = states;
    }

    public RecipeBookSettings() {
        this(Util.make(Maps.newEnumMap(RecipeBookType.class), states -> {
            for (RecipeBookType recipeBookType : RecipeBookType.values()) {
                states.put(recipeBookType, new RecipeBookSettings.TypeSettings(false, false));
            }
        }));
    }

    public boolean isOpen(RecipeBookType bookType) {
        return this.states.get(bookType).open;
    }

    public void setOpen(RecipeBookType bookType, boolean open) {
        this.states.get(bookType).open = open;
    }

    public boolean isFiltering(RecipeBookType bookType) {
        return this.states.get(bookType).filtering;
    }

    public void setFiltering(RecipeBookType bookType, boolean filtering) {
        this.states.get(bookType).filtering = filtering;
    }

    public static RecipeBookSettings read(FriendlyByteBuf buffer) {
        Map<RecipeBookType, RecipeBookSettings.TypeSettings> map = Maps.newEnumMap(RecipeBookType.class);

        for (RecipeBookType recipeBookType : RecipeBookType.values()) {
            boolean bl = buffer.readBoolean();
            boolean bl2 = buffer.readBoolean();
            map.put(recipeBookType, new RecipeBookSettings.TypeSettings(bl, bl2));
        }

        return new RecipeBookSettings(map);
    }

    public void write(FriendlyByteBuf buffer) {
        for (RecipeBookType recipeBookType : RecipeBookType.values()) {
            RecipeBookSettings.TypeSettings typeSettings = this.states.get(recipeBookType);
            if (typeSettings == null) {
                buffer.writeBoolean(false);
                buffer.writeBoolean(false);
            } else {
                buffer.writeBoolean(typeSettings.open);
                buffer.writeBoolean(typeSettings.filtering);
            }
        }
    }

    public static RecipeBookSettings read(CompoundTag tag) {
        Map<RecipeBookType, RecipeBookSettings.TypeSettings> map = Maps.newEnumMap(RecipeBookType.class);
        TAG_FIELDS.forEach((bookType, fieldKeys) -> {
            boolean bl = tag.getBoolean(fieldKeys.getFirst());
            boolean bl2 = tag.getBoolean(fieldKeys.getSecond());
            map.put(bookType, new RecipeBookSettings.TypeSettings(bl, bl2));
        });
        return new RecipeBookSettings(map);
    }

    public void write(CompoundTag tag) {
        TAG_FIELDS.forEach((bookType, fieldKeys) -> {
            RecipeBookSettings.TypeSettings typeSettings = this.states.get(bookType);
            tag.putBoolean(fieldKeys.getFirst(), typeSettings.open);
            tag.putBoolean(fieldKeys.getSecond(), typeSettings.filtering);
        });
    }

    public RecipeBookSettings copy() {
        Map<RecipeBookType, RecipeBookSettings.TypeSettings> map = Maps.newEnumMap(RecipeBookType.class);

        for (RecipeBookType recipeBookType : RecipeBookType.values()) {
            RecipeBookSettings.TypeSettings typeSettings = this.states.get(recipeBookType);
            map.put(recipeBookType, typeSettings.copy());
        }

        return new RecipeBookSettings(map);
    }

    public void replaceFrom(RecipeBookSettings other) {
        this.states.clear();

        for (RecipeBookType recipeBookType : RecipeBookType.values()) {
            RecipeBookSettings.TypeSettings typeSettings = other.states.get(recipeBookType);
            this.states.put(recipeBookType, typeSettings.copy());
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RecipeBookSettings && this.states.equals(((RecipeBookSettings)other).states);
    }

    @Override
    public int hashCode() {
        return this.states.hashCode();
    }

    static final class TypeSettings {
        boolean open;
        boolean filtering;

        public TypeSettings(boolean open, boolean filtering) {
            this.open = open;
            this.filtering = filtering;
        }

        public RecipeBookSettings.TypeSettings copy() {
            return new RecipeBookSettings.TypeSettings(this.open, this.filtering);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            } else {
                return !(other instanceof RecipeBookSettings.TypeSettings typeSettings)
                    ? false
                    : this.open == typeSettings.open && this.filtering == typeSettings.filtering;
            }
        }

        @Override
        public int hashCode() {
            int i = this.open ? 1 : 0;
            return 31 * i + (this.filtering ? 1 : 0);
        }

        @Override
        public String toString() {
            return "[open=" + this.open + ", filtering=" + this.filtering + "]";
        }
    }
}
