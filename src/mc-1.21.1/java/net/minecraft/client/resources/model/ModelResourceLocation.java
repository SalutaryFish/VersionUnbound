package net.minecraft.client.resources.model;

import java.util.Locale;
import net.minecraft.resources.ResourceLocation;

public record ModelResourceLocation(ResourceLocation id, String variant) {
    public static final String INVENTORY_VARIANT = "inventory";

    public ModelResourceLocation {
        variant = lowercaseVariant(variant);
    }

    public static ModelResourceLocation vanilla(String path, String variant) {
        return new ModelResourceLocation(ResourceLocation.withDefaultNamespace(path), variant);
    }

    public static ModelResourceLocation inventory(ResourceLocation id) {
        return new ModelResourceLocation(id, "inventory");
    }

    private static String lowercaseVariant(String variant) {
        return variant.toLowerCase(Locale.ROOT);
    }

    public String getVariant() {
        return this.variant;
    }

    @Override
    public String toString() {
        return this.id + "#" + this.variant;
    }
}
