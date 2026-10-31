package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Ordering;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

public abstract class EffectRenderingInventoryScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    private static final ResourceLocation EFFECT_BACKGROUND_LARGE_SPRITE = ResourceLocation.withDefaultNamespace("container/inventory/effect_background_large");
    private static final ResourceLocation EFFECT_BACKGROUND_SMALL_SPRITE = ResourceLocation.withDefaultNamespace("container/inventory/effect_background_small");

    public EffectRenderingInventoryScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * Renders the graphical user interface (GUI) element.
     *
     * @param guiGraphics the GuiGraphics object used for rendering.
     * @param mouseX      the x-coordinate of the mouse cursor.
     * @param mouseY      the y-coordinate of the mouse cursor.
     * @param partialTick the partial tick time.
     */
    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderEffects(guiGraphics, mouseX, mouseY);
    }

    public boolean canSeeEffects() {
        int i = this.leftPos + this.imageWidth + 2;
        int j = this.width - i;
        return j >= 32;
    }

    private void renderEffects(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int i = this.leftPos + this.imageWidth + 2;
        int j = this.width - i;
        Collection<MobEffectInstance> collection = this.minecraft.player.getActiveEffects();
        if (!collection.isEmpty() && j >= 32) {
            boolean bl = j >= 120;
            int k = 33;
            if (collection.size() > 5) {
                k = 132 / (collection.size() - 1);
            }

            Iterable<MobEffectInstance> iterable = Ordering.natural().sortedCopy(collection);
            this.renderBackgrounds(guiGraphics, i, k, iterable, bl);
            this.renderIcons(guiGraphics, i, k, iterable, bl);
            if (bl) {
                this.renderLabels(guiGraphics, i, k, iterable);
            } else if (mouseX >= i && mouseX <= i + 33) {
                int l = this.topPos;
                MobEffectInstance mobEffectInstance = null;

                for (MobEffectInstance mobEffectInstance2 : iterable) {
                    if (mouseY >= l && mouseY <= l + k) {
                        mobEffectInstance = mobEffectInstance2;
                    }

                    l += k;
                }

                if (mobEffectInstance != null) {
                    List<Component> list = List.of(
                        this.getEffectName(mobEffectInstance),
                        MobEffectUtil.formatDuration(mobEffectInstance, 1.0F, this.minecraft.level.tickRateManager().tickrate())
                    );
                    guiGraphics.renderTooltip(this.font, list, Optional.empty(), mouseX, mouseY);
                }
            }
        }
    }

    private void renderBackgrounds(GuiGraphics guiGraphics, int renderX, int yOffset, Iterable<MobEffectInstance> effects, boolean isSmall) {
        int i = this.topPos;

        for (MobEffectInstance mobEffectInstance : effects) {
            if (isSmall) {
                guiGraphics.blitSprite(EFFECT_BACKGROUND_LARGE_SPRITE, renderX, i, 120, 32);
            } else {
                guiGraphics.blitSprite(EFFECT_BACKGROUND_SMALL_SPRITE, renderX, i, 32, 32);
            }

            i += yOffset;
        }
    }

    private void renderIcons(GuiGraphics guiGraphics, int renderX, int yOffset, Iterable<MobEffectInstance> effects, boolean isSmall) {
        MobEffectTextureManager mobEffectTextureManager = this.minecraft.getMobEffectTextures();
        int i = this.topPos;

        for (MobEffectInstance mobEffectInstance : effects) {
            Holder<MobEffect> holder = mobEffectInstance.getEffect();
            TextureAtlasSprite textureAtlasSprite = mobEffectTextureManager.get(holder);
            guiGraphics.blit(renderX + (isSmall ? 6 : 7), i + 7, 0, 18, 18, textureAtlasSprite);
            i += yOffset;
        }
    }

    private void renderLabels(GuiGraphics guiGraphics, int renderX, int yOffset, Iterable<MobEffectInstance> effects) {
        int i = this.topPos;

        for (MobEffectInstance mobEffectInstance : effects) {
            Component component = this.getEffectName(mobEffectInstance);
            guiGraphics.drawString(this.font, component, renderX + 10 + 18, i + 6, 16777215);
            Component component2 = MobEffectUtil.formatDuration(mobEffectInstance, 1.0F, this.minecraft.level.tickRateManager().tickrate());
            guiGraphics.drawString(this.font, component2, renderX + 10 + 18, i + 6 + 10, 8355711);
            i += yOffset;
        }
    }

    private Component getEffectName(MobEffectInstance effect) {
        MutableComponent mutableComponent = effect.getEffect().value().getDisplayName().copy();
        if (effect.getAmplifier() >= 1 && effect.getAmplifier() <= 9) {
            mutableComponent.append(CommonComponents.SPACE).append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
        }

        return mutableComponent;
    }
}
