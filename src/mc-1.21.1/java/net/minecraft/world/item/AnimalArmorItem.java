package net.minecraft.world.item;

import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

public class AnimalArmorItem extends ArmorItem {
    private final ResourceLocation textureLocation;
    @Nullable
    private final ResourceLocation overlayTextureLocation;
    private final AnimalArmorItem.BodyType bodyType;

    public AnimalArmorItem(Holder<ArmorMaterial> armorMaterial, AnimalArmorItem.BodyType bodyType, boolean hasOverlay, Item.Properties properties) {
        super(armorMaterial, ArmorItem.Type.BODY, properties);
        this.bodyType = bodyType;
        ResourceLocation resourceLocation = bodyType.textureLocator.apply(armorMaterial.unwrapKey().orElseThrow().location());
        this.textureLocation = resourceLocation.withSuffix(".png");
        if (hasOverlay) {
            this.overlayTextureLocation = resourceLocation.withSuffix("_overlay.png");
        } else {
            this.overlayTextureLocation = null;
        }
    }

    public ResourceLocation getTexture() {
        return this.textureLocation;
    }

    @Nullable
    public ResourceLocation getOverlayTexture() {
        return this.overlayTextureLocation;
    }

    public AnimalArmorItem.BodyType getBodyType() {
        return this.bodyType;
    }

    @Override
    public SoundEvent getBreakingSound() {
        return this.bodyType.breakingSound;
    }

    /**
     * Checks isDamagable and if it cannot be stacked
     */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }

    public enum BodyType {
        EQUESTRIAN(resourceLocation -> resourceLocation.withPath(string -> "textures/entity/horse/armor/horse_armor_" + string), SoundEvents.ITEM_BREAK),
        CANINE(resourceLocation -> resourceLocation.withPath("textures/entity/wolf/wolf_armor"), SoundEvents.WOLF_ARMOR_BREAK);

        final Function<ResourceLocation, ResourceLocation> textureLocator;
        final SoundEvent breakingSound;

        BodyType(final Function<ResourceLocation, ResourceLocation> textureLocator, final SoundEvent breakingSound) {
            this.textureLocator = textureLocator;
            this.breakingSound = breakingSound;
        }
    }
}
