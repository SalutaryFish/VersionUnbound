package net.minecraft.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.Long2FloatLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntLinkedOpenHashMap;
import java.util.BitSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ModelBlockRenderer {
    private static final int FACE_CUBIC = 0;
    private static final int FACE_PARTIAL = 1;
    static final Direction[] DIRECTIONS = Direction.values();
    private final BlockColors blockColors;
    private static final int CACHE_SIZE = 100;
    static final ThreadLocal<ModelBlockRenderer.Cache> CACHE = ThreadLocal.withInitial(ModelBlockRenderer.Cache::new);

    public ModelBlockRenderer(BlockColors blockColors) {
        this.blockColors = blockColors;
    }

    /**
     * @param checkSides if {@code true}, only renders each side if {@link
     *                   net.minecraft.world.level.block.Block#shouldRenderFace(
     *                   net.minecraft.world.level.block.state.BlockState,
     *                   net.minecraft.world.level.BlockGetter,
     *                   net.minecraft.core.BlockPos, net.minecraft.core.Direction,
     *                   net.minecraft.core.BlockPos)} returns {@code true}
     */
    public void tesselateBlock(
        BlockAndTintGetter level,
        BakedModel model,
        BlockState state,
        BlockPos pos,
        PoseStack poseStack,
        VertexConsumer consumer,
        boolean checkSides,
        RandomSource random,
        long seed,
        int packedOverlay
    ) {
        boolean bl = Minecraft.useAmbientOcclusion() && state.getLightEmission() == 0 && model.useAmbientOcclusion();
        Vec3 vec3 = state.getOffset(level, pos);
        poseStack.translate(vec3.x, vec3.y, vec3.z);

        try {
            if (bl) {
                this.tesselateWithAO(level, model, state, pos, poseStack, consumer, checkSides, random, seed, packedOverlay);
            } else {
                this.tesselateWithoutAO(level, model, state, pos, poseStack, consumer, checkSides, random, seed, packedOverlay);
            }
        } catch (Throwable throwable) {
            CrashReport crashReport = CrashReport.forThrowable(throwable, "Tesselating block model");
            CrashReportCategory crashReportCategory = crashReport.addCategory("Block model being tesselated");
            CrashReportCategory.populateBlockDetails(crashReportCategory, level, pos, state);
            crashReportCategory.setDetail("Using AO", bl);
            throw new ReportedException(crashReport);
        }
    }

    /**
     * @param checkSides if {@code true}, only renders each side if {@link
     *                   net.minecraft.world.level.block.Block#shouldRenderFace(
     *                   net.minecraft.world.level.block.state.BlockState,
     *                   net.minecraft.world.level.BlockGetter,
     *                   net.minecraft.core.BlockPos, net.minecraft.core.Direction,
     *                   net.minecraft.core.BlockPos)} returns {@code true}
     */
    public void tesselateWithAO(
        BlockAndTintGetter level,
        BakedModel model,
        BlockState state,
        BlockPos pos,
        PoseStack poseStack,
        VertexConsumer consumer,
        boolean checkSides,
        RandomSource random,
        long seed,
        int packedOverlay
    ) {
        float[] fs = new float[DIRECTIONS.length * 2];
        BitSet bitSet = new BitSet(3);
        ModelBlockRenderer.AmbientOcclusionFace ambientOcclusionFace = new ModelBlockRenderer.AmbientOcclusionFace();
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (Direction direction : DIRECTIONS) {
            random.setSeed(seed);
            List<BakedQuad> list = model.getQuads(state, direction, random);
            if (!list.isEmpty()) {
                mutableBlockPos.setWithOffset(pos, direction);
                if (!checkSides || Block.shouldRenderFace(state, level, pos, direction, mutableBlockPos)) {
                    this.renderModelFaceAO(level, state, pos, poseStack, consumer, list, fs, bitSet, ambientOcclusionFace, packedOverlay);
                }
            }
        }

        random.setSeed(seed);
        List<BakedQuad> list2 = model.getQuads(state, null, random);
        if (!list2.isEmpty()) {
            this.renderModelFaceAO(level, state, pos, poseStack, consumer, list2, fs, bitSet, ambientOcclusionFace, packedOverlay);
        }
    }

    /**
     * @param checkSides if {@code true}, only renders each side if {@link
     *                   net.minecraft.world.level.block.Block#shouldRenderFace(
     *                   net.minecraft.world.level.block.state.BlockState,
     *                   net.minecraft.world.level.BlockGetter,
     *                   net.minecraft.core.BlockPos, net.minecraft.core.Direction,
     *                   net.minecraft.core.BlockPos)} returns {@code true}
     */
    public void tesselateWithoutAO(
        BlockAndTintGetter level,
        BakedModel model,
        BlockState state,
        BlockPos pos,
        PoseStack poseStack,
        VertexConsumer consumer,
        boolean checkSides,
        RandomSource random,
        long seed,
        int packedOverlay
    ) {
        BitSet bitSet = new BitSet(3);
        BlockPos.MutableBlockPos mutableBlockPos = pos.mutable();

        for (Direction direction : DIRECTIONS) {
            random.setSeed(seed);
            List<BakedQuad> list = model.getQuads(state, direction, random);
            if (!list.isEmpty()) {
                mutableBlockPos.setWithOffset(pos, direction);
                if (!checkSides || Block.shouldRenderFace(state, level, pos, direction, mutableBlockPos)) {
                    int i = LevelRenderer.getLightColor(level, state, mutableBlockPos);
                    this.renderModelFaceFlat(level, state, pos, i, packedOverlay, false, poseStack, consumer, list, bitSet);
                }
            }
        }

        random.setSeed(seed);
        List<BakedQuad> list2 = model.getQuads(state, null, random);
        if (!list2.isEmpty()) {
            this.renderModelFaceFlat(level, state, pos, -1, packedOverlay, true, poseStack, consumer, list2, bitSet);
        }
    }

    /**
     * @param shape      the array, of length 12, to store the shape bounds in
     * @param shapeFlags the bit set to store the shape flags in. The first bit will
     *                   be {@code true} if the face should be offset, and the second
     *                   if the face is less than a block in width and height.
     */
    private void renderModelFaceAO(
        BlockAndTintGetter level,
        BlockState state,
        BlockPos pos,
        PoseStack poseStack,
        VertexConsumer consumer,
        List<BakedQuad> quads,
        float[] shape,
        BitSet shapeFlags,
        ModelBlockRenderer.AmbientOcclusionFace aoFace,
        int packedOverlay
    ) {
        for (BakedQuad bakedQuad : quads) {
            this.calculateShape(level, state, pos, bakedQuad.getVertices(), bakedQuad.getDirection(), shape, shapeFlags);
            aoFace.calculate(level, state, pos, bakedQuad.getDirection(), shape, shapeFlags, bakedQuad.isShade());
            this.putQuadData(
                level,
                state,
                pos,
                consumer,
                poseStack.last(),
                bakedQuad,
                aoFace.brightness[0],
                aoFace.brightness[1],
                aoFace.brightness[2],
                aoFace.brightness[3],
                aoFace.lightmap[0],
                aoFace.lightmap[1],
                aoFace.lightmap[2],
                aoFace.lightmap[3],
                packedOverlay
            );
        }
    }

    private void putQuadData(
        BlockAndTintGetter level,
        BlockState state,
        BlockPos pos,
        VertexConsumer consumer,
        PoseStack.Pose pose,
        BakedQuad quad,
        float brightness0,
        float brightness1,
        float brightness2,
        float brightness3,
        int lightmap0,
        int lightmap1,
        int lightmap2,
        int lightmap3,
        int packedOverlay
    ) {
        float f;
        float g;
        float h;
        if (quad.isTinted()) {
            int i = this.blockColors.getColor(state, level, pos, quad.getTintIndex());
            f = (i >> 16 & 0xFF) / 255.0F;
            g = (i >> 8 & 0xFF) / 255.0F;
            h = (i & 0xFF) / 255.0F;
        } else {
            f = 1.0F;
            g = 1.0F;
            h = 1.0F;
        }

        consumer.putBulkData(
            pose,
            quad,
            new float[]{brightness0, brightness1, brightness2, brightness3},
            f,
            g,
            h,
            1.0F,
            new int[]{lightmap0, lightmap1, lightmap2, lightmap3},
            packedOverlay,
            true
        );
    }

    /**
     * Calculates the shape and corresponding flags for the specified {@code direction} and {@code vertices}, storing the resulting shape in the specified {@code shape} array and the shape flags in {@code shapeFlags}.
     *
     * @param shape      the array, of length 12, to store the shape bounds in, or {@
     *                   code null} to only calculate shape flags
     * @param shapeFlags the bit set to store the shape flags in. The first bit will
     *                   be {@code true} if the face should be offset, and the second
     *                   if the face is less than a block in width and height.
     */
    private void calculateShape(
        BlockAndTintGetter level, BlockState state, BlockPos pos, int[] vertices, Direction direction, @Nullable float[] shape, BitSet shapeFlags
    ) {
        float f = 32.0F;
        float g = 32.0F;
        float h = 32.0F;
        float i = -32.0F;
        float j = -32.0F;
        float k = -32.0F;

        for (int l = 0; l < 4; l++) {
            float m = Float.intBitsToFloat(vertices[l * 8]);
            float n = Float.intBitsToFloat(vertices[l * 8 + 1]);
            float o = Float.intBitsToFloat(vertices[l * 8 + 2]);
            f = Math.min(f, m);
            g = Math.min(g, n);
            h = Math.min(h, o);
            i = Math.max(i, m);
            j = Math.max(j, n);
            k = Math.max(k, o);
        }

        if (shape != null) {
            shape[Direction.WEST.get3DDataValue()] = f;
            shape[Direction.EAST.get3DDataValue()] = i;
            shape[Direction.DOWN.get3DDataValue()] = g;
            shape[Direction.UP.get3DDataValue()] = j;
            shape[Direction.NORTH.get3DDataValue()] = h;
            shape[Direction.SOUTH.get3DDataValue()] = k;
            int l = DIRECTIONS.length;
            shape[Direction.WEST.get3DDataValue() + l] = 1.0F - f;
            shape[Direction.EAST.get3DDataValue() + l] = 1.0F - i;
            shape[Direction.DOWN.get3DDataValue() + l] = 1.0F - g;
            shape[Direction.UP.get3DDataValue() + l] = 1.0F - j;
            shape[Direction.NORTH.get3DDataValue() + l] = 1.0F - h;
            shape[Direction.SOUTH.get3DDataValue() + l] = 1.0F - k;
        }

        float p = 1.0E-4F;
        float m = 0.9999F;
        switch (direction) {
            case DOWN:
                shapeFlags.set(1, f >= 1.0E-4F || h >= 1.0E-4F || i <= 0.9999F || k <= 0.9999F);
                shapeFlags.set(0, g == j && (g < 1.0E-4F || state.isCollisionShapeFullBlock(level, pos)));
                break;
            case UP:
                shapeFlags.set(1, f >= 1.0E-4F || h >= 1.0E-4F || i <= 0.9999F || k <= 0.9999F);
                shapeFlags.set(0, g == j && (j > 0.9999F || state.isCollisionShapeFullBlock(level, pos)));
                break;
            case NORTH:
                shapeFlags.set(1, f >= 1.0E-4F || g >= 1.0E-4F || i <= 0.9999F || j <= 0.9999F);
                shapeFlags.set(0, h == k && (h < 1.0E-4F || state.isCollisionShapeFullBlock(level, pos)));
                break;
            case SOUTH:
                shapeFlags.set(1, f >= 1.0E-4F || g >= 1.0E-4F || i <= 0.9999F || j <= 0.9999F);
                shapeFlags.set(0, h == k && (k > 0.9999F || state.isCollisionShapeFullBlock(level, pos)));
                break;
            case WEST:
                shapeFlags.set(1, g >= 1.0E-4F || h >= 1.0E-4F || j <= 0.9999F || k <= 0.9999F);
                shapeFlags.set(0, f == i && (f < 1.0E-4F || state.isCollisionShapeFullBlock(level, pos)));
                break;
            case EAST:
                shapeFlags.set(1, g >= 1.0E-4F || h >= 1.0E-4F || j <= 0.9999F || k <= 0.9999F);
                shapeFlags.set(0, f == i && (i > 0.9999F || state.isCollisionShapeFullBlock(level, pos)));
        }
    }

    /**
     * @param repackLight {@code true} if packed light should be re-calculated
     * @param shapeFlags  the bit set to store the shape flags in. The first bit will
     *                    be {@code true} if the face should be offset, and the second
     *                    if the face is less than a block in width and height.
     */
    private void renderModelFaceFlat(
        BlockAndTintGetter level,
        BlockState state,
        BlockPos pos,
        int packedLight,
        int packedOverlay,
        boolean repackLight,
        PoseStack poseStack,
        VertexConsumer consumer,
        List<BakedQuad> quads,
        BitSet shapeFlags
    ) {
        for (BakedQuad bakedQuad : quads) {
            if (repackLight) {
                this.calculateShape(level, state, pos, bakedQuad.getVertices(), bakedQuad.getDirection(), null, shapeFlags);
                BlockPos blockPos = shapeFlags.get(0) ? pos.relative(bakedQuad.getDirection()) : pos;
                packedLight = LevelRenderer.getLightColor(level, state, blockPos);
            }

            float f = level.getShade(bakedQuad.getDirection(), bakedQuad.isShade());
            this.putQuadData(
                level, state, pos, consumer, poseStack.last(), bakedQuad, f, f, f, f, packedLight, packedLight, packedLight, packedLight, packedOverlay
            );
        }
    }

    public void renderModel(
        PoseStack.Pose pose,
        VertexConsumer consumer,
        @Nullable BlockState state,
        BakedModel model,
        float red,
        float green,
        float blue,
        int packedLight,
        int packedOverlay
    ) {
        RandomSource randomSource = RandomSource.create();
        long l = 42L;

        for (Direction direction : DIRECTIONS) {
            randomSource.setSeed(42L);
            renderQuadList(pose, consumer, red, green, blue, model.getQuads(state, direction, randomSource), packedLight, packedOverlay);
        }

        randomSource.setSeed(42L);
        renderQuadList(pose, consumer, red, green, blue, model.getQuads(state, null, randomSource), packedLight, packedOverlay);
    }

    private static void renderQuadList(
        PoseStack.Pose pose, VertexConsumer consumer, float red, float green, float blue, List<BakedQuad> quads, int packedLight, int packedOverlay
    ) {
        for (BakedQuad bakedQuad : quads) {
            float f;
            float g;
            float h;
            if (bakedQuad.isTinted()) {
                f = Mth.clamp(red, 0.0F, 1.0F);
                g = Mth.clamp(green, 0.0F, 1.0F);
                h = Mth.clamp(blue, 0.0F, 1.0F);
            } else {
                f = 1.0F;
                g = 1.0F;
                h = 1.0F;
            }

            consumer.putBulkData(pose, bakedQuad, f, g, h, 1.0F, packedLight, packedOverlay);
        }
    }

    public static void enableCaching() {
        CACHE.get().enable();
    }

    public static void clearCache() {
        CACHE.get().disable();
    }

    protected enum AdjacencyInfo {
        DOWN(
            new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH},
            0.5F,
            true,
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.SOUTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.NORTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.NORTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.SOUTH
            }
        ),
        UP(
            new Direction[]{Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH},
            1.0F,
            true,
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.SOUTH,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.SOUTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.NORTH,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.NORTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.NORTH,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.NORTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.SOUTH,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.SOUTH
            }
        ),
        NORTH(
            new Direction[]{Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST},
            0.8F,
            true,
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.FLIP_WEST
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.FLIP_EAST
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_EAST
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_WEST
            }
        ),
        SOUTH(
            new Direction[]{Direction.WEST, Direction.EAST, Direction.DOWN, Direction.UP},
            0.8F,
            true,
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.WEST
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_WEST,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.WEST,
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.WEST
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.EAST
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.FLIP_EAST,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.EAST,
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.EAST
            }
        ),
        WEST(
            new Direction[]{Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH},
            0.6F,
            true,
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.SOUTH,
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.SOUTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.NORTH,
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.NORTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.NORTH,
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.NORTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.SOUTH,
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.SOUTH
            }
        ),
        EAST(
            new Direction[]{Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH},
            0.6F,
            true,
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.SOUTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.DOWN,
                ModelBlockRenderer.SizeInfo.NORTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.NORTH,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.FLIP_NORTH,
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.NORTH
            },
            new ModelBlockRenderer.SizeInfo[]{
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.SOUTH,
                ModelBlockRenderer.SizeInfo.FLIP_UP,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.FLIP_SOUTH,
                ModelBlockRenderer.SizeInfo.UP,
                ModelBlockRenderer.SizeInfo.SOUTH
            }
        );

        final Direction[] corners;
        final boolean doNonCubicWeight;
        final ModelBlockRenderer.SizeInfo[] vert0Weights;
        final ModelBlockRenderer.SizeInfo[] vert1Weights;
        final ModelBlockRenderer.SizeInfo[] vert2Weights;
        final ModelBlockRenderer.SizeInfo[] vert3Weights;
        private static final ModelBlockRenderer.AdjacencyInfo[] BY_FACING = Util.make(new ModelBlockRenderer.AdjacencyInfo[6], byFacing -> {
            byFacing[Direction.DOWN.get3DDataValue()] = DOWN;
            byFacing[Direction.UP.get3DDataValue()] = UP;
            byFacing[Direction.NORTH.get3DDataValue()] = NORTH;
            byFacing[Direction.SOUTH.get3DDataValue()] = SOUTH;
            byFacing[Direction.WEST.get3DDataValue()] = WEST;
            byFacing[Direction.EAST.get3DDataValue()] = EAST;
        });

        /**
         * @param shadeBrightness the shade brightness for this direction
         */
        AdjacencyInfo(
            final Direction[] corners,
            final float shadeBrightness,
            final boolean doNonCubicWeight,
            final ModelBlockRenderer.SizeInfo[] vert0Weights,
            final ModelBlockRenderer.SizeInfo[] vert1Weights,
            final ModelBlockRenderer.SizeInfo[] vert2Weights,
            final ModelBlockRenderer.SizeInfo[] vert3Weights
        ) {
            this.corners = corners;
            this.doNonCubicWeight = doNonCubicWeight;
            this.vert0Weights = vert0Weights;
            this.vert1Weights = vert1Weights;
            this.vert2Weights = vert2Weights;
            this.vert3Weights = vert3Weights;
        }

        public static ModelBlockRenderer.AdjacencyInfo fromFacing(Direction facing) {
            return BY_FACING[facing.get3DDataValue()];
        }
    }

    static class AmbientOcclusionFace {
        final float[] brightness = new float[4];
        final int[] lightmap = new int[4];

        public AmbientOcclusionFace() {
        }

        /**
         * @param shape      the array, of length 12, containing the shape bounds
         * @param shapeFlags the bit set to store the shape flags in. The first bit will
         *                   be {@code true} if the face should be offset, and the second
         *                   if the face is less than a block in width and height.
         */
        public void calculate(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction direction, float[] shape, BitSet shapeFlags, boolean shade) {
            BlockPos blockPos = shapeFlags.get(0) ? pos.relative(direction) : pos;
            ModelBlockRenderer.AdjacencyInfo adjacencyInfo = ModelBlockRenderer.AdjacencyInfo.fromFacing(direction);
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            ModelBlockRenderer.Cache cache = ModelBlockRenderer.CACHE.get();
            mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[0]);
            BlockState blockState = level.getBlockState(mutableBlockPos);
            int i = cache.getLightColor(blockState, level, mutableBlockPos);
            float f = cache.getShadeBrightness(blockState, level, mutableBlockPos);
            mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[1]);
            BlockState blockState2 = level.getBlockState(mutableBlockPos);
            int j = cache.getLightColor(blockState2, level, mutableBlockPos);
            float g = cache.getShadeBrightness(blockState2, level, mutableBlockPos);
            mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[2]);
            BlockState blockState3 = level.getBlockState(mutableBlockPos);
            int k = cache.getLightColor(blockState3, level, mutableBlockPos);
            float h = cache.getShadeBrightness(blockState3, level, mutableBlockPos);
            mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[3]);
            BlockState blockState4 = level.getBlockState(mutableBlockPos);
            int l = cache.getLightColor(blockState4, level, mutableBlockPos);
            float m = cache.getShadeBrightness(blockState4, level, mutableBlockPos);
            BlockState blockState5 = level.getBlockState(mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[0]).move(direction));
            boolean bl = !blockState5.isViewBlocking(level, mutableBlockPos) || blockState5.getLightBlock(level, mutableBlockPos) == 0;
            BlockState blockState6 = level.getBlockState(mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[1]).move(direction));
            boolean bl2 = !blockState6.isViewBlocking(level, mutableBlockPos) || blockState6.getLightBlock(level, mutableBlockPos) == 0;
            BlockState blockState7 = level.getBlockState(mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[2]).move(direction));
            boolean bl3 = !blockState7.isViewBlocking(level, mutableBlockPos) || blockState7.getLightBlock(level, mutableBlockPos) == 0;
            BlockState blockState8 = level.getBlockState(mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[3]).move(direction));
            boolean bl4 = !blockState8.isViewBlocking(level, mutableBlockPos) || blockState8.getLightBlock(level, mutableBlockPos) == 0;
            float n;
            int o;
            if (!bl3 && !bl) {
                n = f;
                o = i;
            } else {
                mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[0]).move(adjacencyInfo.corners[2]);
                BlockState blockState9 = level.getBlockState(mutableBlockPos);
                n = cache.getShadeBrightness(blockState9, level, mutableBlockPos);
                o = cache.getLightColor(blockState9, level, mutableBlockPos);
            }

            float p;
            int q;
            if (!bl4 && !bl) {
                p = f;
                q = i;
            } else {
                mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[0]).move(adjacencyInfo.corners[3]);
                BlockState blockState9 = level.getBlockState(mutableBlockPos);
                p = cache.getShadeBrightness(blockState9, level, mutableBlockPos);
                q = cache.getLightColor(blockState9, level, mutableBlockPos);
            }

            float r;
            int s;
            if (!bl3 && !bl2) {
                r = f;
                s = i;
            } else {
                mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[1]).move(adjacencyInfo.corners[2]);
                BlockState blockState9 = level.getBlockState(mutableBlockPos);
                r = cache.getShadeBrightness(blockState9, level, mutableBlockPos);
                s = cache.getLightColor(blockState9, level, mutableBlockPos);
            }

            float t;
            int u;
            if (!bl4 && !bl2) {
                t = f;
                u = i;
            } else {
                mutableBlockPos.setWithOffset(blockPos, adjacencyInfo.corners[1]).move(adjacencyInfo.corners[3]);
                BlockState blockState9 = level.getBlockState(mutableBlockPos);
                t = cache.getShadeBrightness(blockState9, level, mutableBlockPos);
                u = cache.getLightColor(blockState9, level, mutableBlockPos);
            }

            int v = cache.getLightColor(state, level, pos);
            mutableBlockPos.setWithOffset(pos, direction);
            BlockState blockState10 = level.getBlockState(mutableBlockPos);
            if (shapeFlags.get(0) || !blockState10.isSolidRender(level, mutableBlockPos)) {
                v = cache.getLightColor(blockState10, level, mutableBlockPos);
            }

            float w = shapeFlags.get(0)
                ? cache.getShadeBrightness(level.getBlockState(blockPos), level, blockPos)
                : cache.getShadeBrightness(level.getBlockState(pos), level, pos);
            ModelBlockRenderer.AmbientVertexRemap ambientVertexRemap = ModelBlockRenderer.AmbientVertexRemap.fromFacing(direction);
            if (shapeFlags.get(1) && adjacencyInfo.doNonCubicWeight) {
                float x = (m + f + p + w) * 0.25F;
                float y = (h + f + n + w) * 0.25F;
                float z = (h + g + r + w) * 0.25F;
                float aa = (m + g + t + w) * 0.25F;
                float ab = shape[adjacencyInfo.vert0Weights[0].shape] * shape[adjacencyInfo.vert0Weights[1].shape];
                float ac = shape[adjacencyInfo.vert0Weights[2].shape] * shape[adjacencyInfo.vert0Weights[3].shape];
                float ad = shape[adjacencyInfo.vert0Weights[4].shape] * shape[adjacencyInfo.vert0Weights[5].shape];
                float ae = shape[adjacencyInfo.vert0Weights[6].shape] * shape[adjacencyInfo.vert0Weights[7].shape];
                float af = shape[adjacencyInfo.vert1Weights[0].shape] * shape[adjacencyInfo.vert1Weights[1].shape];
                float ag = shape[adjacencyInfo.vert1Weights[2].shape] * shape[adjacencyInfo.vert1Weights[3].shape];
                float ah = shape[adjacencyInfo.vert1Weights[4].shape] * shape[adjacencyInfo.vert1Weights[5].shape];
                float ai = shape[adjacencyInfo.vert1Weights[6].shape] * shape[adjacencyInfo.vert1Weights[7].shape];
                float aj = shape[adjacencyInfo.vert2Weights[0].shape] * shape[adjacencyInfo.vert2Weights[1].shape];
                float ak = shape[adjacencyInfo.vert2Weights[2].shape] * shape[adjacencyInfo.vert2Weights[3].shape];
                float al = shape[adjacencyInfo.vert2Weights[4].shape] * shape[adjacencyInfo.vert2Weights[5].shape];
                float am = shape[adjacencyInfo.vert2Weights[6].shape] * shape[adjacencyInfo.vert2Weights[7].shape];
                float an = shape[adjacencyInfo.vert3Weights[0].shape] * shape[adjacencyInfo.vert3Weights[1].shape];
                float ao = shape[adjacencyInfo.vert3Weights[2].shape] * shape[adjacencyInfo.vert3Weights[3].shape];
                float ap = shape[adjacencyInfo.vert3Weights[4].shape] * shape[adjacencyInfo.vert3Weights[5].shape];
                float aq = shape[adjacencyInfo.vert3Weights[6].shape] * shape[adjacencyInfo.vert3Weights[7].shape];
                this.brightness[ambientVertexRemap.vert0] = x * ab + y * ac + z * ad + aa * ae;
                this.brightness[ambientVertexRemap.vert1] = x * af + y * ag + z * ah + aa * ai;
                this.brightness[ambientVertexRemap.vert2] = x * aj + y * ak + z * al + aa * am;
                this.brightness[ambientVertexRemap.vert3] = x * an + y * ao + z * ap + aa * aq;
                int ar = this.blend(l, i, q, v);
                int as = this.blend(k, i, o, v);
                int at = this.blend(k, j, s, v);
                int au = this.blend(l, j, u, v);
                this.lightmap[ambientVertexRemap.vert0] = this.blend(ar, as, at, au, ab, ac, ad, ae);
                this.lightmap[ambientVertexRemap.vert1] = this.blend(ar, as, at, au, af, ag, ah, ai);
                this.lightmap[ambientVertexRemap.vert2] = this.blend(ar, as, at, au, aj, ak, al, am);
                this.lightmap[ambientVertexRemap.vert3] = this.blend(ar, as, at, au, an, ao, ap, aq);
            } else {
                float x = (m + f + p + w) * 0.25F;
                float y = (h + f + n + w) * 0.25F;
                float z = (h + g + r + w) * 0.25F;
                float aa = (m + g + t + w) * 0.25F;
                this.lightmap[ambientVertexRemap.vert0] = this.blend(l, i, q, v);
                this.lightmap[ambientVertexRemap.vert1] = this.blend(k, i, o, v);
                this.lightmap[ambientVertexRemap.vert2] = this.blend(k, j, s, v);
                this.lightmap[ambientVertexRemap.vert3] = this.blend(l, j, u, v);
                this.brightness[ambientVertexRemap.vert0] = x;
                this.brightness[ambientVertexRemap.vert1] = y;
                this.brightness[ambientVertexRemap.vert2] = z;
                this.brightness[ambientVertexRemap.vert3] = aa;
            }

            float x = level.getShade(direction, shade);

            for (int av = 0; av < this.brightness.length; av++) {
                this.brightness[av] = this.brightness[av] * x;
            }
        }

        /**
         * @return the ambient occlusion light color
         */
        private int blend(int lightColor0, int lightColor1, int lightColor2, int lightColor3) {
            if (lightColor0 == 0) {
                lightColor0 = lightColor3;
            }

            if (lightColor1 == 0) {
                lightColor1 = lightColor3;
            }

            if (lightColor2 == 0) {
                lightColor2 = lightColor3;
            }

            return lightColor0 + lightColor1 + lightColor2 + lightColor3 >> 2 & 16711935;
        }

        private int blend(int brightness0, int brightness1, int brightness2, int brightness3, float weight0, float weight1, float weight2, float weight3) {
            int i = (int)(
                    (brightness0 >> 16 & 0xFF) * weight0
                        + (brightness1 >> 16 & 0xFF) * weight1
                        + (brightness2 >> 16 & 0xFF) * weight2
                        + (brightness3 >> 16 & 0xFF) * weight3
                )
                & 0xFF;
            int j = (int)((brightness0 & 0xFF) * weight0 + (brightness1 & 0xFF) * weight1 + (brightness2 & 0xFF) * weight2 + (brightness3 & 0xFF) * weight3)
                & 0xFF;
            return i << 16 | j;
        }
    }

    enum AmbientVertexRemap {
        DOWN(0, 1, 2, 3),
        UP(2, 3, 0, 1),
        NORTH(3, 0, 1, 2),
        SOUTH(0, 1, 2, 3),
        WEST(3, 0, 1, 2),
        EAST(1, 2, 3, 0);

        final int vert0;
        final int vert1;
        final int vert2;
        final int vert3;
        private static final ModelBlockRenderer.AmbientVertexRemap[] BY_FACING = Util.make(new ModelBlockRenderer.AmbientVertexRemap[6], byFacing -> {
            byFacing[Direction.DOWN.get3DDataValue()] = DOWN;
            byFacing[Direction.UP.get3DDataValue()] = UP;
            byFacing[Direction.NORTH.get3DDataValue()] = NORTH;
            byFacing[Direction.SOUTH.get3DDataValue()] = SOUTH;
            byFacing[Direction.WEST.get3DDataValue()] = WEST;
            byFacing[Direction.EAST.get3DDataValue()] = EAST;
        });

        AmbientVertexRemap(final int vert0, final int vert1, final int vert2, final int vert3) {
            this.vert0 = vert0;
            this.vert1 = vert1;
            this.vert2 = vert2;
            this.vert3 = vert3;
        }

        public static ModelBlockRenderer.AmbientVertexRemap fromFacing(Direction facing) {
            return BY_FACING[facing.get3DDataValue()];
        }
    }

    static class Cache {
        private boolean enabled;
        private final Long2IntLinkedOpenHashMap colorCache = Util.make(() -> {
            Long2IntLinkedOpenHashMap long2IntLinkedOpenHashMap = new Long2IntLinkedOpenHashMap(100, 0.25F) {
                @Override
                protected void rehash(int newN) {
                }
            };
            long2IntLinkedOpenHashMap.defaultReturnValue(Integer.MAX_VALUE);
            return long2IntLinkedOpenHashMap;
        });
        private final Long2FloatLinkedOpenHashMap brightnessCache = Util.make(() -> {
            Long2FloatLinkedOpenHashMap long2FloatLinkedOpenHashMap = new Long2FloatLinkedOpenHashMap(100, 0.25F) {
                @Override
                protected void rehash(int newN) {
                }
            };
            long2FloatLinkedOpenHashMap.defaultReturnValue(Float.NaN);
            return long2FloatLinkedOpenHashMap;
        });

        private Cache() {
        }

        public void enable() {
            this.enabled = true;
        }

        public void disable() {
            this.enabled = false;
            this.colorCache.clear();
            this.brightnessCache.clear();
        }

        public int getLightColor(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            long l = pos.asLong();
            if (this.enabled) {
                int i = this.colorCache.get(l);
                if (i != Integer.MAX_VALUE) {
                    return i;
                }
            }

            int i = LevelRenderer.getLightColor(level, state, pos);
            if (this.enabled) {
                if (this.colorCache.size() == 100) {
                    this.colorCache.removeFirstInt();
                }

                this.colorCache.put(l, i);
            }

            return i;
        }

        public float getShadeBrightness(BlockState state, BlockAndTintGetter level, BlockPos pos) {
            long l = pos.asLong();
            if (this.enabled) {
                float f = this.brightnessCache.get(l);
                if (!Float.isNaN(f)) {
                    return f;
                }
            }

            float f = state.getShadeBrightness(level, pos);
            if (this.enabled) {
                if (this.brightnessCache.size() == 100) {
                    this.brightnessCache.removeFirstFloat();
                }

                this.brightnessCache.put(l, f);
            }

            return f;
        }
    }

    protected enum SizeInfo {
        DOWN(Direction.DOWN, false),
        UP(Direction.UP, false),
        NORTH(Direction.NORTH, false),
        SOUTH(Direction.SOUTH, false),
        WEST(Direction.WEST, false),
        EAST(Direction.EAST, false),
        FLIP_DOWN(Direction.DOWN, true),
        FLIP_UP(Direction.UP, true),
        FLIP_NORTH(Direction.NORTH, true),
        FLIP_SOUTH(Direction.SOUTH, true),
        FLIP_WEST(Direction.WEST, true),
        FLIP_EAST(Direction.EAST, true);

        final int shape;

        SizeInfo(final Direction direction, final boolean flip) {
            this.shape = direction.get3DDataValue() + (flip ? ModelBlockRenderer.DIRECTIONS.length : 0);
        }
    }
}
