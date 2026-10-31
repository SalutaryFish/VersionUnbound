package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's patch to {@link DyeColor}, injected into it by the class tweaker; {@link #getColor} is added to
 * {@code DyeColor} as a static method by {@code AddedStaticMethods}. NeoForge keeps the two tags in fields made in the
 * constructor; {@code TagKey.create} interns its keys, so making them on each call hands back the same keys.
 */
public interface DyeColorExtension {
    private DyeColor self() {
        return (DyeColor) this;
    }

    /**
     * Gets the tag key representing the set of items which provide this dye color.
     * @return A {@link net.minecraft.tags.TagKey<Item>} representing the set of items which provide this dye color.
     */
    default TagKey<Item> getTag() {
        return TagsPatch.createItemTag(Identifier.fromNamespaceAndPath("c", "dyes/" + self().getSerializedName()));
    }

    /**
     * Gets the tag key representing the set of items which are dyed with this color.
     * @return A {@link net.minecraft.tags.TagKey<Item>} representing the set of items which are dyed with this color.
     */
    default TagKey<Item> getDyedTag() {
        return TagsPatch.createItemTag(Identifier.fromNamespaceAndPath("c", "dyed/" + self().getSerializedName()));
    }

    static @Nullable DyeColor getColor(ItemStack stack) {
        DyeColor dyeColor = stack.get(DataComponents.DYE);
        if (dyeColor != null) {
            return dyeColor;
        }

        for (int x = 0; x < DyeColor.BLACK.getId(); x++) {
            DyeColor color = DyeColor.byId(x);
            if (stack.is(((DyeColorExtension) (Object) color).getTag()))
                return color;
        }

        return null;
    }
}
