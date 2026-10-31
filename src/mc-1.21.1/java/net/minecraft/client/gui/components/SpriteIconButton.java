package net.minecraft.client.gui.components;

import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public abstract class SpriteIconButton extends Button {
    protected final ResourceLocation sprite;
    protected final int spriteWidth;
    protected final int spriteHeight;

    SpriteIconButton(
        int width,
        int height,
        Component message,
        int spriteWidth,
        int spriteHeight,
        ResourceLocation sprite,
        Button.OnPress onPress,
        @Nullable Button.CreateNarration createNarration
    ) {
        super(0, 0, width, height, message, onPress, createNarration == null ? DEFAULT_NARRATION : createNarration);
        this.spriteWidth = spriteWidth;
        this.spriteHeight = spriteHeight;
        this.sprite = sprite;
    }

    public static SpriteIconButton.Builder builder(Component message, Button.OnPress onPress, boolean iconOnly) {
        return new SpriteIconButton.Builder(message, onPress, iconOnly);
    }

    public static class Builder {
        private final Component message;
        private final Button.OnPress onPress;
        private final boolean iconOnly;
        private int width = 150;
        private int height = 20;
        @Nullable
        private ResourceLocation sprite;
        private int spriteWidth;
        private int spriteHeight;
        @Nullable
        Button.CreateNarration narration;

        public Builder(Component message, Button.OnPress onPress, boolean iconOnly) {
            this.message = message;
            this.onPress = onPress;
            this.iconOnly = iconOnly;
        }

        public SpriteIconButton.Builder width(int width) {
            this.width = width;
            return this;
        }

        public SpriteIconButton.Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public SpriteIconButton.Builder sprite(ResourceLocation sprite, int spriteWidth, int spriteHeight) {
            this.sprite = sprite;
            this.spriteWidth = spriteWidth;
            this.spriteHeight = spriteHeight;
            return this;
        }

        public SpriteIconButton.Builder narration(Button.CreateNarration narration) {
            this.narration = narration;
            return this;
        }

        public SpriteIconButton build() {
            if (this.sprite == null) {
                throw new IllegalStateException("Sprite not set");
            } else {
                return this.iconOnly
                    ? new SpriteIconButton.CenteredIcon(
                        this.width, this.height, this.message, this.spriteWidth, this.spriteHeight, this.sprite, this.onPress, this.narration
                    )
                    : new SpriteIconButton.TextAndIcon(
                        this.width, this.height, this.message, this.spriteWidth, this.spriteHeight, this.sprite, this.onPress, this.narration
                    );
            }
        }
    }

    public static class CenteredIcon extends SpriteIconButton {
        protected CenteredIcon(
            int i,
            int j,
            Component component,
            int k,
            int l,
            ResourceLocation resourceLocation,
            Button.OnPress onPress,
            @Nullable Button.CreateNarration createNarration
        ) {
            super(i, j, component, k, l, resourceLocation, onPress, createNarration);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
            super.renderWidget(guiGraphics, i, j, f);
            int k = this.getX() + this.getWidth() / 2 - this.spriteWidth / 2;
            int l = this.getY() + this.getHeight() / 2 - this.spriteHeight / 2;
            guiGraphics.blitSprite(this.sprite, k, l, this.spriteWidth, this.spriteHeight);
        }

        @Override
        public void renderString(GuiGraphics guiGraphics, Font font, int i) {
        }
    }

    public static class TextAndIcon extends SpriteIconButton {
        protected TextAndIcon(
            int i,
            int j,
            Component component,
            int k,
            int l,
            ResourceLocation resourceLocation,
            Button.OnPress onPress,
            @Nullable Button.CreateNarration createNarration
        ) {
            super(i, j, component, k, l, resourceLocation, onPress, createNarration);
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int i, int j, float f) {
            super.renderWidget(guiGraphics, i, j, f);
            int k = this.getX() + this.getWidth() - this.spriteWidth - 2;
            int l = this.getY() + this.getHeight() / 2 - this.spriteHeight / 2;
            guiGraphics.blitSprite(this.sprite, k, l, this.spriteWidth, this.spriteHeight);
        }

        @Override
        public void renderString(GuiGraphics guiGraphics, Font font, int i) {
            int j = this.getX() + 2;
            int k = this.getX() + this.getWidth() - this.spriteWidth - 4;
            int l = this.getX() + this.getWidth() / 2;
            renderScrollingString(guiGraphics, font, this.getMessage(), l, j, this.getY(), k, this.getY() + this.getHeight(), i);
        }
    }
}
