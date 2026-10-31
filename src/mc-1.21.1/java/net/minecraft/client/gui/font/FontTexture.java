package net.minecraft.client.gui.font;

import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.Dumpable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public class FontTexture extends AbstractTexture implements Dumpable {
    private static final int SIZE = 256;
    private final GlyphRenderTypes renderTypes;
    private final boolean colored;
    private final FontTexture.Node root;

    public FontTexture(GlyphRenderTypes renderTypes, boolean colored) {
        this.colored = colored;
        this.root = new FontTexture.Node(0, 0, 256, 256);
        TextureUtil.prepareImage(colored ? NativeImage.InternalGlFormat.RGBA : NativeImage.InternalGlFormat.RED, this.getId(), 256, 256);
        this.renderTypes = renderTypes;
    }

    @Override
    public void load(ResourceManager manager) {
    }

    @Override
    public void close() {
        this.releaseId();
    }

    @Nullable
    public BakedGlyph add(SheetGlyphInfo glyphInfo) {
        if (glyphInfo.isColored() != this.colored) {
            return null;
        } else {
            FontTexture.Node node = this.root.insert(glyphInfo);
            if (node != null) {
                this.bind();
                glyphInfo.upload(node.x, node.y);
                float f = 256.0F;
                float g = 256.0F;
                float h = 0.01F;
                return new BakedGlyph(
                    this.renderTypes,
                    (node.x + 0.01F) / 256.0F,
                    (node.x - 0.01F + glyphInfo.getPixelWidth()) / 256.0F,
                    (node.y + 0.01F) / 256.0F,
                    (node.y - 0.01F + glyphInfo.getPixelHeight()) / 256.0F,
                    glyphInfo.getLeft(),
                    glyphInfo.getRight(),
                    glyphInfo.getTop(),
                    glyphInfo.getBottom()
                );
            } else {
                return null;
            }
        }
    }

    @Override
    public void dumpContents(ResourceLocation resourceLocation, Path path) {
        String string = resourceLocation.toDebugFileName();
        TextureUtil.writeAsPNG(path, string, this.getId(), 0, 256, 256, i -> (i & 0xFF000000) == 0 ? -16777216 : i);
    }

    static class Node {
        final int x;
        final int y;
        private final int width;
        private final int height;
        @Nullable
        private FontTexture.Node left;
        @Nullable
        private FontTexture.Node right;
        private boolean occupied;

        Node(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        @Nullable
        FontTexture.Node insert(SheetGlyphInfo glyphInfo) {
            if (this.left != null && this.right != null) {
                FontTexture.Node node = this.left.insert(glyphInfo);
                if (node == null) {
                    node = this.right.insert(glyphInfo);
                }

                return node;
            } else {
                if (this.occupied) {
                    return null;
                }

                int i = glyphInfo.getPixelWidth();
                int j = glyphInfo.getPixelHeight();
                if (i > this.width || j > this.height) {
                    return null;
                }

                if (i == this.width && j == this.height) {
                    this.occupied = true;
                    return this;
                }

                int k = this.width - i;
                int l = this.height - j;
                if (k > l) {
                    this.left = new FontTexture.Node(this.x, this.y, i, this.height);
                    this.right = new FontTexture.Node(this.x + i + 1, this.y, this.width - i - 1, this.height);
                } else {
                    this.left = new FontTexture.Node(this.x, this.y, this.width, j);
                    this.right = new FontTexture.Node(this.x, this.y + j + 1, this.width, this.height - j - 1);
                }

                return this.left.insert(glyphInfo);
            }
        }
    }
}
