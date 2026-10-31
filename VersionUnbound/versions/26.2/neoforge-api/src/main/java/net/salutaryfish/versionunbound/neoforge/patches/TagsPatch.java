package net.salutaryfish.versionunbound.neoforge.patches;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * The {@code create(Identifier)} overloads NeoForge's patches add to {@code BlockTags}, {@code ItemTags} and
 * {@code FluidTags} (vanilla's take a {@code String}). {@code AddedStaticMethods} adds them there as forwarders to
 * these; Layer A's own code, compiled against vanilla's classes, calls these directly.
 */
public final class TagsPatch {
    private TagsPatch() {}

    public static TagKey<Block> createBlockTag(Identifier name) {
        return TagKey.create(Registries.BLOCK, name);
    }

    public static TagKey<Item> createItemTag(Identifier name) {
        return TagKey.create(Registries.ITEM, name);
    }

    public static TagKey<Fluid> createFluidTag(Identifier name) {
        return TagKey.create(Registries.FLUID, name);
    }
}
