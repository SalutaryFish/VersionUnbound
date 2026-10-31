package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public abstract class DisplayRenderer<T extends Display, S> extends EntityRenderer<T> {
    private final EntityRenderDispatcher entityRenderDispatcher;

    protected DisplayRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.entityRenderDispatcher = context.getEntityRenderDispatcher();
    }

    /**
     * Returns the location of an entity's texture.
     */
    public ResourceLocation getTextureLocation(T entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Display.RenderState renderState = entity.renderState();
        if (renderState != null) {
            S object = this.getSubState(entity);
            if (object != null) {
                float f = entity.calculateInterpolationProgress(partialTick);
                this.shadowRadius = renderState.shadowRadius().get(f);
                this.shadowStrength = renderState.shadowStrength().get(f);
                int i = renderState.brightnessOverride();
                int j = i != -1 ? i : packedLight;
                super.render(entity, entityYaw, partialTick, poseStack, buffer, j);
                poseStack.pushPose();
                poseStack.mulPose(this.calculateOrientation(renderState, entity, partialTick, new Quaternionf()));
                Transformation transformation = renderState.transformation().get(f);
                poseStack.mulPose(transformation.getMatrix());
                this.renderInner(entity, object, poseStack, buffer, j, f);
                poseStack.popPose();
            }
        }
    }

    private Quaternionf calculateOrientation(Display.RenderState renderState, T entity, float partialTick, Quaternionf quaternion) {
        Camera camera = this.entityRenderDispatcher.camera;

        return switch (renderState.billboardConstraints()) {
            case FIXED -> quaternion.rotationYXZ(
                (float) (-Math.PI / 180.0) * entityYRot(entity, partialTick), (float) (Math.PI / 180.0) * entityXRot(entity, partialTick), 0.0F
            );
            case HORIZONTAL -> quaternion.rotationYXZ(
                (float) (-Math.PI / 180.0) * entityYRot(entity, partialTick), (float) (Math.PI / 180.0) * cameraXRot(camera), 0.0F
            );
            case VERTICAL -> quaternion.rotationYXZ(
                (float) (-Math.PI / 180.0) * cameraYrot(camera), (float) (Math.PI / 180.0) * entityXRot(entity, partialTick), 0.0F
            );
            case CENTER -> quaternion.rotationYXZ((float) (-Math.PI / 180.0) * cameraYrot(camera), (float) (Math.PI / 180.0) * cameraXRot(camera), 0.0F);
        };
    }

    private static float cameraYrot(Camera camera) {
        return camera.getYRot() - 180.0F;
    }

    private static float cameraXRot(Camera camera) {
        return -camera.getXRot();
    }

    private static <T extends Display> float entityYRot(T entity, float partialTick) {
        return Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
    }

    private static <T extends Display> float entityXRot(T entity, float partialTick) {
        return Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
    }

    @Nullable
    protected abstract S getSubState(T var1);

    protected abstract void renderInner(T var1, S var2, PoseStack var3, MultiBufferSource var4, int var5, float var6);

    public static class BlockDisplayRenderer extends DisplayRenderer<Display.BlockDisplay, Display.BlockDisplay.BlockRenderState> {
        private final BlockRenderDispatcher blockRenderer;

        protected BlockDisplayRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.blockRenderer = context.getBlockRenderDispatcher();
        }

        @Nullable
        protected Display.BlockDisplay.BlockRenderState getSubState(Display.BlockDisplay blockDisplay) {
            return blockDisplay.blockRenderState();
        }

        public void renderInner(
            Display.BlockDisplay blockDisplay,
            Display.BlockDisplay.BlockRenderState blockRenderState,
            PoseStack poseStack,
            MultiBufferSource multiBufferSource,
            int i,
            float f
        ) {
            this.blockRenderer.renderSingleBlock(blockRenderState.blockState(), poseStack, multiBufferSource, i, OverlayTexture.NO_OVERLAY);
        }
    }

    public static class ItemDisplayRenderer extends DisplayRenderer<Display.ItemDisplay, Display.ItemDisplay.ItemRenderState> {
        private final ItemRenderer itemRenderer;

        protected ItemDisplayRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.itemRenderer = context.getItemRenderer();
        }

        @Nullable
        protected Display.ItemDisplay.ItemRenderState getSubState(Display.ItemDisplay itemDisplay) {
            return itemDisplay.itemRenderState();
        }

        public void renderInner(
            Display.ItemDisplay itemDisplay,
            Display.ItemDisplay.ItemRenderState itemRenderState,
            PoseStack poseStack,
            MultiBufferSource multiBufferSource,
            int i,
            float f
        ) {
            poseStack.mulPose(Axis.YP.rotation((float) Math.PI));
            this.itemRenderer
                .renderStatic(
                    itemRenderState.itemStack(),
                    itemRenderState.itemTransform(),
                    i,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    multiBufferSource,
                    itemDisplay.level(),
                    itemDisplay.getId()
                );
        }
    }

    public static class TextDisplayRenderer extends DisplayRenderer<Display.TextDisplay, Display.TextDisplay.TextRenderState> {
        private final Font font;

        protected TextDisplayRenderer(EntityRendererProvider.Context context) {
            super(context);
            this.font = context.getFont();
        }

        private Display.TextDisplay.CachedInfo splitLines(Component text, int maxWidth) {
            List<FormattedCharSequence> list = this.font.split(text, maxWidth);
            List<Display.TextDisplay.CachedLine> list2 = new ArrayList<>(list.size());
            int i = 0;

            for (FormattedCharSequence formattedCharSequence : list) {
                int j = this.font.width(formattedCharSequence);
                i = Math.max(i, j);
                list2.add(new Display.TextDisplay.CachedLine(formattedCharSequence, j));
            }

            return new Display.TextDisplay.CachedInfo(list2, i);
        }

        @Nullable
        protected Display.TextDisplay.TextRenderState getSubState(Display.TextDisplay textDisplay) {
            return textDisplay.textRenderState();
        }

        public void renderInner(
            Display.TextDisplay textDisplay,
            Display.TextDisplay.TextRenderState renderState,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int lightmapUV,
            float partialTick
        ) {
            byte b = renderState.flags();
            boolean bl = (b & 2) != 0;
            boolean bl2 = (b & 4) != 0;
            boolean bl3 = (b & 1) != 0;
            Display.TextDisplay.Align align = Display.TextDisplay.getAlign(b);
            byte c = (byte)renderState.textOpacity().get(partialTick);
            int i;
            if (bl2) {
                float f = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
                i = (int)(f * 255.0F) << 24;
            } else {
                i = renderState.backgroundColor().get(partialTick);
            }

            float f = 0.0F;
            Matrix4f matrix4f = poseStack.last().pose();
            matrix4f.rotate((float) Math.PI, 0.0F, 1.0F, 0.0F);
            matrix4f.scale(-0.025F, -0.025F, -0.025F);
            Display.TextDisplay.CachedInfo cachedInfo = textDisplay.cacheDisplay(this::splitLines);
            int j = 9 + 1;
            int k = cachedInfo.width();
            int l = cachedInfo.lines().size() * j;
            matrix4f.translate(1.0F - k / 2.0F, -l, 0.0F);
            if (i != 0) {
                VertexConsumer vertexConsumer = buffer.getBuffer(bl ? RenderType.textBackgroundSeeThrough() : RenderType.textBackground());
                vertexConsumer.addVertex(matrix4f, -1.0F, -1.0F, 0.0F).setColor(i).setLight(lightmapUV);
                vertexConsumer.addVertex(matrix4f, -1.0F, l, 0.0F).setColor(i).setLight(lightmapUV);
                vertexConsumer.addVertex(matrix4f, k, l, 0.0F).setColor(i).setLight(lightmapUV);
                vertexConsumer.addVertex(matrix4f, k, -1.0F, 0.0F).setColor(i).setLight(lightmapUV);
            }

            for (Display.TextDisplay.CachedLine cachedLine : cachedInfo.lines()) {
                float g = switch (align) {
                    case LEFT -> 0.0F;
                    case RIGHT -> k - cachedLine.width();
                    case CENTER -> k / 2.0F - cachedLine.width() / 2.0F;
                };
                this.font
                    .drawInBatch(
                        cachedLine.contents(),
                        g,
                        f,
                        c << 24 | 16777215,
                        bl3,
                        matrix4f,
                        buffer,
                        bl ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.POLYGON_OFFSET,
                        0,
                        lightmapUV
                    );
                f += j;
            }
        }
    }
}
