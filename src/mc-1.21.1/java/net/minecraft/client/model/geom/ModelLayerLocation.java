package net.minecraft.client.model.geom;

import net.minecraft.resources.ResourceLocation;

public final class ModelLayerLocation {
    private final ResourceLocation model;
    private final String layer;

    public ModelLayerLocation(ResourceLocation model, String layer) {
        this.model = model;
        this.layer = layer;
    }

    public ResourceLocation getModel() {
        return this.model;
    }

    public String getLayer() {
        return this.layer;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof ModelLayerLocation modelLayerLocation)
                ? false
                : this.model.equals(modelLayerLocation.model) && this.layer.equals(modelLayerLocation.layer);
        }
    }

    @Override
    public int hashCode() {
        int i = this.model.hashCode();
        return 31 * i + this.layer.hashCode();
    }

    @Override
    public String toString() {
        return this.model + "#" + this.layer;
    }
}
