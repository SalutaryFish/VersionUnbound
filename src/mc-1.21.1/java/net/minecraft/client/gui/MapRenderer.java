package net.minecraft.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.MapDecorationTextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;

public class MapRenderer implements AutoCloseable {
    private static final int WIDTH = 128;
    private static final int HEIGHT = 128;
    final TextureManager textureManager;
    final MapDecorationTextureManager decorationTextures;
    private final Int2ObjectMap<MapRenderer.MapInstance> maps = new Int2ObjectOpenHashMap<>();

    public MapRenderer(TextureManager textureManager, MapDecorationTextureManager decorationTextures) {
        this.textureManager = textureManager;
        this.decorationTextures = decorationTextures;
    }

    public void update(MapId mapId, MapItemSavedData mapData) {
        this.getOrCreateMapInstance(mapId, mapData).forceUpload();
    }

    public void render(PoseStack poseStack, MultiBufferSource buffer, MapId mapId, MapItemSavedData mapData, boolean active, int packedLight) {
        this.getOrCreateMapInstance(mapId, mapData).draw(poseStack, buffer, active, packedLight);
    }

    private MapRenderer.MapInstance getOrCreateMapInstance(MapId mapId, MapItemSavedData mapData) {
        return this.maps.compute(mapId.id(), (id, mapInstance) -> {
            if (mapInstance == null) {
                return new MapRenderer.MapInstance(id, mapData);
            }

            mapInstance.replaceMapData(mapData);
            return (MapRenderer.MapInstance)mapInstance;
        });
    }

    public void resetData() {
        for (MapRenderer.MapInstance mapInstance : this.maps.values()) {
            mapInstance.close();
        }

        this.maps.clear();
    }

    @Override
    public void close() {
        this.resetData();
    }

    class MapInstance implements AutoCloseable {
        private MapItemSavedData data;
        private final DynamicTexture texture;
        private final RenderType renderType;
        private boolean requiresUpload = true;

        MapInstance(final int id, final MapItemSavedData data) {
            this.data = data;
            this.texture = new DynamicTexture(128, 128, true);
            ResourceLocation resourceLocation = MapRenderer.this.textureManager.register("map/" + id, this.texture);
            this.renderType = RenderType.text(resourceLocation);
        }

        void replaceMapData(MapItemSavedData data) {
            boolean bl = this.data != data;
            this.data = data;
            this.requiresUpload |= bl;
        }

        public void forceUpload() {
            this.requiresUpload = true;
        }

        private void updateTexture() {
            for (int i = 0; i < 128; i++) {
                for (int j = 0; j < 128; j++) {
                    int k = j + i * 128;
                    this.texture.getPixels().setPixelRGBA(j, i, MapColor.getColorFromPackedId(this.data.colors[k]));
                }
            }

            this.texture.upload();
        }

        void draw(PoseStack poseStack, MultiBufferSource bufferSource, boolean active, int packedLight) {
            if (this.requiresUpload) {
                this.updateTexture();
                this.requiresUpload = false;
            }

            int i = 0;
            int j = 0;
            float f = 0.0F;
            Matrix4f matrix4f = poseStack.last().pose();
            VertexConsumer vertexConsumer = bufferSource.getBuffer(this.renderType);
            vertexConsumer.addVertex(matrix4f, 0.0F, 128.0F, -0.01F).setColor(-1).setUv(0.0F, 1.0F).setLight(packedLight);
            vertexConsumer.addVertex(matrix4f, 128.0F, 128.0F, -0.01F).setColor(-1).setUv(1.0F, 1.0F).setLight(packedLight);
            vertexConsumer.addVertex(matrix4f, 128.0F, 0.0F, -0.01F).setColor(-1).setUv(1.0F, 0.0F).setLight(packedLight);
            vertexConsumer.addVertex(matrix4f, 0.0F, 0.0F, -0.01F).setColor(-1).setUv(0.0F, 0.0F).setLight(packedLight);
            int k = 0;

            for (MapDecoration mapDecoration : this.data.getDecorations()) {
                if (!active || mapDecoration.renderOnFrame()) {
                    poseStack.pushPose();
                    poseStack.translate(0.0F + mapDecoration.x() / 2.0F + 64.0F, 0.0F + mapDecoration.y() / 2.0F + 64.0F, -0.02F);
                    poseStack.mulPose(Axis.ZP.rotationDegrees(mapDecoration.rot() * 360 / 16.0F));
                    poseStack.scale(4.0F, 4.0F, 3.0F);
                    poseStack.translate(-0.125F, 0.125F, 0.0F);
                    Matrix4f matrix4f2 = poseStack.last().pose();
                    float g = -0.001F;
                    TextureAtlasSprite textureAtlasSprite = MapRenderer.this.decorationTextures.get(mapDecoration);
                    float h = textureAtlasSprite.getU0();
                    float l = textureAtlasSprite.getV0();
                    float m = textureAtlasSprite.getU1();
                    float n = textureAtlasSprite.getV1();
                    VertexConsumer vertexConsumer2 = bufferSource.getBuffer(RenderType.text(textureAtlasSprite.atlasLocation()));
                    vertexConsumer2.addVertex(matrix4f2, -1.0F, 1.0F, k * -0.001F).setColor(-1).setUv(h, l).setLight(packedLight);
                    vertexConsumer2.addVertex(matrix4f2, 1.0F, 1.0F, k * -0.001F).setColor(-1).setUv(m, l).setLight(packedLight);
                    vertexConsumer2.addVertex(matrix4f2, 1.0F, -1.0F, k * -0.001F).setColor(-1).setUv(m, n).setLight(packedLight);
                    vertexConsumer2.addVertex(matrix4f2, -1.0F, -1.0F, k * -0.001F).setColor(-1).setUv(h, n).setLight(packedLight);
                    poseStack.popPose();
                    if (mapDecoration.name().isPresent()) {
                        Font font = Minecraft.getInstance().font;
                        Component component = mapDecoration.name().get();
                        float o = font.width(component);
                        float p = Mth.clamp(25.0F / o, 0.0F, 6.0F / 9.0F);
                        poseStack.pushPose();
                        poseStack.translate(0.0F + mapDecoration.x() / 2.0F + 64.0F - o * p / 2.0F, 0.0F + mapDecoration.y() / 2.0F + 64.0F + 4.0F, -0.025F);
                        poseStack.scale(p, p, 1.0F);
                        poseStack.translate(0.0F, 0.0F, -0.1F);
                        font.drawInBatch(
                            component, 0.0F, 0.0F, -1, false, poseStack.last().pose(), bufferSource, Font.DisplayMode.NORMAL, Integer.MIN_VALUE, packedLight
                        );
                        poseStack.popPose();
                    }

                    k++;
                }
            }
        }

        @Override
        public void close() {
            this.texture.close();
        }
    }
}
