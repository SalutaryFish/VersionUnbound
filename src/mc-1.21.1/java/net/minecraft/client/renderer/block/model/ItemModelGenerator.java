package net.minecraft.client.renderer.block.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.datafixers.util.Either;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

public class ItemModelGenerator {
    public static final List<String> LAYERS = Lists.newArrayList("layer0", "layer1", "layer2", "layer3", "layer4");
    private static final float MIN_Z = 7.5F;
    private static final float MAX_Z = 8.5F;

    public BlockModel generateBlockModel(Function<Material, TextureAtlasSprite> spriteGetter, BlockModel model) {
        Map<String, Either<Material, String>> map = Maps.newHashMap();
        List<BlockElement> list = Lists.newArrayList();

        for (int i = 0; i < LAYERS.size(); i++) {
            String string = LAYERS.get(i);
            if (!model.hasTexture(string)) {
                break;
            }

            Material material = model.getMaterial(string);
            map.put(string, Either.left(material));
            SpriteContents spriteContents = spriteGetter.apply(material).contents();
            list.addAll(this.processFrames(i, string, spriteContents));
        }

        map.put("particle", model.hasTexture("particle") ? Either.left(model.getMaterial("particle")) : map.get("layer0"));
        BlockModel blockModel = new BlockModel(null, list, map, false, model.getGuiLight(), model.getTransforms(), model.getOverrides());
        blockModel.name = model.name;
        return blockModel;
    }

    private List<BlockElement> processFrames(int tintIndex, String texture, SpriteContents sprite) {
        Map<Direction, BlockElementFace> map = Maps.newHashMap();
        map.put(Direction.SOUTH, new BlockElementFace(null, tintIndex, texture, new BlockFaceUV(new float[]{0.0F, 0.0F, 16.0F, 16.0F}, 0)));
        map.put(Direction.NORTH, new BlockElementFace(null, tintIndex, texture, new BlockFaceUV(new float[]{16.0F, 0.0F, 0.0F, 16.0F}, 0)));
        List<BlockElement> list = Lists.newArrayList();
        list.add(new BlockElement(new Vector3f(0.0F, 0.0F, 7.5F), new Vector3f(16.0F, 16.0F, 8.5F), map, null, true));
        list.addAll(this.createSideElements(sprite, texture, tintIndex));
        return list;
    }

    private List<BlockElement> createSideElements(SpriteContents sprite, String texture, int tintIndex) {
        float f = sprite.width();
        float g = sprite.height();
        List<BlockElement> list = Lists.newArrayList();

        for (ItemModelGenerator.Span span : this.getSpans(sprite)) {
            float h = 0.0F;
            float i = 0.0F;
            float j = 0.0F;
            float k = 0.0F;
            float l = 0.0F;
            float m = 0.0F;
            float n = 0.0F;
            float o = 0.0F;
            float p = 16.0F / f;
            float q = 16.0F / g;
            float r = span.getMin();
            float s = span.getMax();
            float t = span.getAnchor();
            ItemModelGenerator.SpanFacing spanFacing = span.getFacing();
            switch (spanFacing) {
                case UP:
                    l = r;
                    h = r;
                    j = m = s + 1.0F;
                    n = t;
                    i = t;
                    k = t;
                    o = t + 1.0F;
                    break;
                case DOWN:
                    n = t;
                    o = t + 1.0F;
                    l = r;
                    h = r;
                    j = m = s + 1.0F;
                    i = t + 1.0F;
                    k = t + 1.0F;
                    break;
                case LEFT:
                    l = t;
                    h = t;
                    j = t;
                    m = t + 1.0F;
                    o = r;
                    i = r;
                    k = n = s + 1.0F;
                    break;
                case RIGHT:
                    l = t;
                    m = t + 1.0F;
                    h = t + 1.0F;
                    j = t + 1.0F;
                    o = r;
                    i = r;
                    k = n = s + 1.0F;
            }

            h *= p;
            j *= p;
            i *= q;
            k *= q;
            i = 16.0F - i;
            k = 16.0F - k;
            l *= p;
            m *= p;
            n *= q;
            o *= q;
            Map<Direction, BlockElementFace> map = Maps.newHashMap();
            map.put(spanFacing.getDirection(), new BlockElementFace(null, tintIndex, texture, new BlockFaceUV(new float[]{l, n, m, o}, 0)));
            switch (spanFacing) {
                case UP:
                    list.add(new BlockElement(new Vector3f(h, i, 7.5F), new Vector3f(j, i, 8.5F), map, null, true));
                    break;
                case DOWN:
                    list.add(new BlockElement(new Vector3f(h, k, 7.5F), new Vector3f(j, k, 8.5F), map, null, true));
                    break;
                case LEFT:
                    list.add(new BlockElement(new Vector3f(h, i, 7.5F), new Vector3f(h, k, 8.5F), map, null, true));
                    break;
                case RIGHT:
                    list.add(new BlockElement(new Vector3f(j, i, 7.5F), new Vector3f(j, k, 8.5F), map, null, true));
            }
        }

        return list;
    }

    private List<ItemModelGenerator.Span> getSpans(SpriteContents sprite) {
        int i = sprite.width();
        int j = sprite.height();
        List<ItemModelGenerator.Span> list = Lists.newArrayList();
        sprite.getUniqueFrames().forEach(frame -> {
            for (int k = 0; k < j; k++) {
                for (int l = 0; l < i; l++) {
                    boolean bl = !this.isTransparent(sprite, frame, l, k, i, j);
                    this.checkTransition(ItemModelGenerator.SpanFacing.UP, list, sprite, frame, l, k, i, j, bl);
                    this.checkTransition(ItemModelGenerator.SpanFacing.DOWN, list, sprite, frame, l, k, i, j, bl);
                    this.checkTransition(ItemModelGenerator.SpanFacing.LEFT, list, sprite, frame, l, k, i, j, bl);
                    this.checkTransition(ItemModelGenerator.SpanFacing.RIGHT, list, sprite, frame, l, k, i, j, bl);
                }
            }
        });
        return list;
    }

    private void checkTransition(
        ItemModelGenerator.SpanFacing spanFacing,
        List<ItemModelGenerator.Span> listSpans,
        SpriteContents contents,
        int frameIndex,
        int pixelX,
        int pixelY,
        int spriteWidth,
        int spriteHeight,
        boolean transparent
    ) {
        boolean bl = this.isTransparent(contents, frameIndex, pixelX + spanFacing.getXOffset(), pixelY + spanFacing.getYOffset(), spriteWidth, spriteHeight)
            && transparent;
        if (bl) {
            this.createOrExpandSpan(listSpans, spanFacing, pixelX, pixelY);
        }
    }

    private void createOrExpandSpan(List<ItemModelGenerator.Span> listSpans, ItemModelGenerator.SpanFacing spanFacing, int pixelX, int pixelY) {
        ItemModelGenerator.Span span = null;

        for (ItemModelGenerator.Span span2 : listSpans) {
            if (span2.getFacing() == spanFacing) {
                int i = spanFacing.isHorizontal() ? pixelY : pixelX;
                if (span2.getAnchor() == i) {
                    span = span2;
                    break;
                }
            }
        }

        int j = spanFacing.isHorizontal() ? pixelY : pixelX;
        int k = spanFacing.isHorizontal() ? pixelX : pixelY;
        if (span == null) {
            listSpans.add(new ItemModelGenerator.Span(spanFacing, k, j));
        } else {
            span.expand(k);
        }
    }

    private boolean isTransparent(SpriteContents sprite, int frameIndex, int pixelX, int pixelY, int spriteWidth, int spriteHeight) {
        return pixelX >= 0 && pixelY >= 0 && pixelX < spriteWidth && pixelY < spriteHeight ? sprite.isTransparent(frameIndex, pixelX, pixelY) : true;
    }

    static class Span {
        private final ItemModelGenerator.SpanFacing facing;
        private int min;
        private int max;
        private final int anchor;

        public Span(ItemModelGenerator.SpanFacing facing, int minMax, int anchor) {
            this.facing = facing;
            this.min = minMax;
            this.max = minMax;
            this.anchor = anchor;
        }

        public void expand(int pos) {
            if (pos < this.min) {
                this.min = pos;
            } else if (pos > this.max) {
                this.max = pos;
            }
        }

        public ItemModelGenerator.SpanFacing getFacing() {
            return this.facing;
        }

        public int getMin() {
            return this.min;
        }

        public int getMax() {
            return this.max;
        }

        public int getAnchor() {
            return this.anchor;
        }
    }

    enum SpanFacing {
        UP(Direction.UP, 0, -1),
        DOWN(Direction.DOWN, 0, 1),
        LEFT(Direction.EAST, -1, 0),
        RIGHT(Direction.WEST, 1, 0);

        private final Direction direction;
        private final int xOffset;
        private final int yOffset;

        SpanFacing(final Direction direction, final int xOffset, final int yOffset) {
            this.direction = direction;
            this.xOffset = xOffset;
            this.yOffset = yOffset;
        }

        public Direction getDirection() {
            return this.direction;
        }

        public int getXOffset() {
            return this.xOffset;
        }

        public int getYOffset() {
            return this.yOffset;
        }

        boolean isHorizontal() {
            return this == DOWN || this == UP;
        }
    }
}
