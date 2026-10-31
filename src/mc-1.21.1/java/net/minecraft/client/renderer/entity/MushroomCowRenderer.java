package net.minecraft.client.renderer.entity;

import com.google.common.collect.Maps;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.model.CowModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.MushroomCowMushroomLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.MushroomCow;

public class MushroomCowRenderer extends MobRenderer<MushroomCow, CowModel<MushroomCow>> {
    private static final Map<MushroomCow.MushroomType, ResourceLocation> TEXTURES = Util.make(Maps.newHashMap(), map -> {
        map.put(MushroomCow.MushroomType.BROWN, ResourceLocation.withDefaultNamespace("textures/entity/cow/brown_mooshroom.png"));
        map.put(MushroomCow.MushroomType.RED, ResourceLocation.withDefaultNamespace("textures/entity/cow/red_mooshroom.png"));
    });

    public MushroomCowRenderer(EntityRendererProvider.Context context) {
        super(context, new CowModel<>(context.bakeLayer(ModelLayers.MOOSHROOM)), 0.7F);
        this.addLayer(new MushroomCowMushroomLayer<>(this, context.getBlockRenderDispatcher()));
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(MushroomCow entity) {
        return TEXTURES.get(entity.getVariant());
    }
}
