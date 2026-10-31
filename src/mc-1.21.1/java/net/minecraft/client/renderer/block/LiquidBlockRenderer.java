package net.minecraft.client.renderer.block;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LiquidBlockRenderer {
    private static final float MAX_FLUID_HEIGHT = 0.8888889F;
    private final TextureAtlasSprite[] lavaIcons = new TextureAtlasSprite[2];
    private final TextureAtlasSprite[] waterIcons = new TextureAtlasSprite[2];
    private TextureAtlasSprite waterOverlay;

    protected void setupSprites() {
        this.lavaIcons[0] = Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(Blocks.LAVA.defaultBlockState()).getParticleIcon();
        this.lavaIcons[1] = ModelBakery.LAVA_FLOW.sprite();
        this.waterIcons[0] = Minecraft.getInstance().getModelManager().getBlockModelShaper().getBlockModel(Blocks.WATER.defaultBlockState()).getParticleIcon();
        this.waterIcons[1] = ModelBakery.WATER_FLOW.sprite();
        this.waterOverlay = ModelBakery.WATER_OVERLAY.sprite();
    }

    private static boolean isNeighborSameFluid(FluidState firstState, FluidState secondState) {
        return secondState.getType().isSame(firstState.getType());
    }

    private static boolean isFaceOccludedByState(BlockGetter level, Direction face, float height, BlockPos pos, BlockState state) {
        if (state.canOcclude()) {
            VoxelShape voxelShape = Shapes.box(0.0, 0.0, 0.0, 1.0, height, 1.0);
            VoxelShape voxelShape2 = state.getOcclusionShape(level, pos);
            return Shapes.blockOccudes(voxelShape, voxelShape2, face);
        } else {
            return false;
        }
    }

    private static boolean isFaceOccludedByNeighbor(BlockGetter level, BlockPos pos, Direction side, float height, BlockState blockState) {
        return isFaceOccludedByState(level, side, height, pos.relative(side), blockState);
    }

    private static boolean isFaceOccludedBySelf(BlockGetter level, BlockPos pos, BlockState state, Direction face) {
        return isFaceOccludedByState(level, face.getOpposite(), 1.0F, pos, state);
    }

    public static boolean shouldRenderFace(
        BlockAndTintGetter level, BlockPos pos, FluidState fluidState, BlockState blockState, Direction side, FluidState neighborFluid
    ) {
        return !isFaceOccludedBySelf(level, pos, blockState, side) && !isNeighborSameFluid(fluidState, neighborFluid);
    }

    public void tesselate(BlockAndTintGetter level, BlockPos pos, VertexConsumer buffer, BlockState blockState, FluidState fluidState) {
        boolean bl = fluidState.is(FluidTags.LAVA);
        TextureAtlasSprite[] textureAtlasSprites = bl ? this.lavaIcons : this.waterIcons;
        int i = bl ? 16777215 : BiomeColors.getAverageWaterColor(level, pos);
        float f = (i >> 16 & 0xFF) / 255.0F;
        float g = (i >> 8 & 0xFF) / 255.0F;
        float h = (i & 0xFF) / 255.0F;
        BlockState blockState2 = level.getBlockState(pos.relative(Direction.DOWN));
        FluidState fluidState2 = blockState2.getFluidState();
        BlockState blockState3 = level.getBlockState(pos.relative(Direction.UP));
        FluidState fluidState3 = blockState3.getFluidState();
        BlockState blockState4 = level.getBlockState(pos.relative(Direction.NORTH));
        FluidState fluidState4 = blockState4.getFluidState();
        BlockState blockState5 = level.getBlockState(pos.relative(Direction.SOUTH));
        FluidState fluidState5 = blockState5.getFluidState();
        BlockState blockState6 = level.getBlockState(pos.relative(Direction.WEST));
        FluidState fluidState6 = blockState6.getFluidState();
        BlockState blockState7 = level.getBlockState(pos.relative(Direction.EAST));
        FluidState fluidState7 = blockState7.getFluidState();
        boolean bl2 = !isNeighborSameFluid(fluidState, fluidState3);
        boolean bl3 = shouldRenderFace(level, pos, fluidState, blockState, Direction.DOWN, fluidState2)
            && !isFaceOccludedByNeighbor(level, pos, Direction.DOWN, 0.8888889F, blockState2);
        boolean bl4 = shouldRenderFace(level, pos, fluidState, blockState, Direction.NORTH, fluidState4);
        boolean bl5 = shouldRenderFace(level, pos, fluidState, blockState, Direction.SOUTH, fluidState5);
        boolean bl6 = shouldRenderFace(level, pos, fluidState, blockState, Direction.WEST, fluidState6);
        boolean bl7 = shouldRenderFace(level, pos, fluidState, blockState, Direction.EAST, fluidState7);
        if (bl2 || bl3 || bl7 || bl6 || bl4 || bl5) {
            float j = level.getShade(Direction.DOWN, true);
            float k = level.getShade(Direction.UP, true);
            float l = level.getShade(Direction.NORTH, true);
            float m = level.getShade(Direction.WEST, true);
            Fluid fluid = fluidState.getType();
            float n = this.getHeight(level, fluid, pos, blockState, fluidState);
            float o;
            float p;
            float q;
            float r;
            if (n >= 1.0F) {
                o = 1.0F;
                p = 1.0F;
                q = 1.0F;
                r = 1.0F;
            } else {
                float s = this.getHeight(level, fluid, pos.north(), blockState4, fluidState4);
                float t = this.getHeight(level, fluid, pos.south(), blockState5, fluidState5);
                float u = this.getHeight(level, fluid, pos.east(), blockState7, fluidState7);
                float v = this.getHeight(level, fluid, pos.west(), blockState6, fluidState6);
                o = this.calculateAverageHeight(level, fluid, n, s, u, pos.relative(Direction.NORTH).relative(Direction.EAST));
                p = this.calculateAverageHeight(level, fluid, n, s, v, pos.relative(Direction.NORTH).relative(Direction.WEST));
                q = this.calculateAverageHeight(level, fluid, n, t, u, pos.relative(Direction.SOUTH).relative(Direction.EAST));
                r = this.calculateAverageHeight(level, fluid, n, t, v, pos.relative(Direction.SOUTH).relative(Direction.WEST));
            }

            float s = pos.getX() & 15;
            float t = pos.getY() & 15;
            float u = pos.getZ() & 15;
            float v = 0.001F;
            float w = bl3 ? 0.001F : 0.0F;
            if (bl2 && !isFaceOccludedByNeighbor(level, pos, Direction.UP, Math.min(Math.min(p, r), Math.min(q, o)), blockState3)) {
                p -= 0.001F;
                r -= 0.001F;
                q -= 0.001F;
                o -= 0.001F;
                Vec3 vec3 = fluidState.getFlow(level, pos);
                float x;
                float z;
                float ab;
                float ad;
                float y;
                float aa;
                float ac;
                float ae;
                if (vec3.x == 0.0 && vec3.z == 0.0) {
                    TextureAtlasSprite textureAtlasSprite = textureAtlasSprites[0];
                    x = textureAtlasSprite.getU(0.0F);
                    y = textureAtlasSprite.getV(0.0F);
                    z = x;
                    aa = textureAtlasSprite.getV(1.0F);
                    ab = textureAtlasSprite.getU(1.0F);
                    ac = aa;
                    ad = ab;
                    ae = y;
                } else {
                    TextureAtlasSprite textureAtlasSprite = textureAtlasSprites[1];
                    float af = (float)Mth.atan2(vec3.z, vec3.x) - (float) (Math.PI / 2);
                    float ag = Mth.sin(af) * 0.25F;
                    float ah = Mth.cos(af) * 0.25F;
                    float ai = 0.5F;
                    x = textureAtlasSprite.getU(0.5F + (-ah - ag));
                    y = textureAtlasSprite.getV(0.5F + (-ah + ag));
                    z = textureAtlasSprite.getU(0.5F + (-ah + ag));
                    aa = textureAtlasSprite.getV(0.5F + (ah + ag));
                    ab = textureAtlasSprite.getU(0.5F + (ah + ag));
                    ac = textureAtlasSprite.getV(0.5F + (ah - ag));
                    ad = textureAtlasSprite.getU(0.5F + (ah - ag));
                    ae = textureAtlasSprite.getV(0.5F + (-ah - ag));
                }

                float aj = (x + z + ab + ad) / 4.0F;
                float af = (y + aa + ac + ae) / 4.0F;
                float ag = textureAtlasSprites[0].uvShrinkRatio();
                x = Mth.lerp(ag, x, aj);
                z = Mth.lerp(ag, z, aj);
                ab = Mth.lerp(ag, ab, aj);
                ad = Mth.lerp(ag, ad, aj);
                y = Mth.lerp(ag, y, af);
                aa = Mth.lerp(ag, aa, af);
                ac = Mth.lerp(ag, ac, af);
                ae = Mth.lerp(ag, ae, af);
                int ak = this.getLightColor(level, pos);
                float ai = k * f;
                float al = k * g;
                float am = k * h;
                this.vertex(buffer, s + 0.0F, t + p, u + 0.0F, ai, al, am, x, y, ak);
                this.vertex(buffer, s + 0.0F, t + r, u + 1.0F, ai, al, am, z, aa, ak);
                this.vertex(buffer, s + 1.0F, t + q, u + 1.0F, ai, al, am, ab, ac, ak);
                this.vertex(buffer, s + 1.0F, t + o, u + 0.0F, ai, al, am, ad, ae, ak);
                if (fluidState.shouldRenderBackwardUpFace(level, pos.above())) {
                    this.vertex(buffer, s + 0.0F, t + p, u + 0.0F, ai, al, am, x, y, ak);
                    this.vertex(buffer, s + 1.0F, t + o, u + 0.0F, ai, al, am, ad, ae, ak);
                    this.vertex(buffer, s + 1.0F, t + q, u + 1.0F, ai, al, am, ab, ac, ak);
                    this.vertex(buffer, s + 0.0F, t + r, u + 1.0F, ai, al, am, z, aa, ak);
                }
            }

            if (bl3) {
                float x = textureAtlasSprites[0].getU0();
                float z = textureAtlasSprites[0].getU1();
                float ab = textureAtlasSprites[0].getV0();
                float ad = textureAtlasSprites[0].getV1();
                int an = this.getLightColor(level, pos.below());
                float aa = j * f;
                float ac = j * g;
                float ae = j * h;
                this.vertex(buffer, s, t + w, u + 1.0F, aa, ac, ae, x, ad, an);
                this.vertex(buffer, s, t + w, u, aa, ac, ae, x, ab, an);
                this.vertex(buffer, s + 1.0F, t + w, u, aa, ac, ae, z, ab, an);
                this.vertex(buffer, s + 1.0F, t + w, u + 1.0F, aa, ac, ae, z, ad, an);
            }

            int ao = this.getLightColor(level, pos);

            for (Direction direction : Direction.Plane.HORIZONTAL) {
                float ad;
                float y;
                float aa;
                float ac;
                float ae;
                float ap;
                boolean bl8;
                switch (direction) {
                    case NORTH:
                        ad = p;
                        y = o;
                        aa = s;
                        ae = s + 1.0F;
                        ac = u + 0.001F;
                        ap = u + 0.001F;
                        bl8 = bl4;
                        break;
                    case SOUTH:
                        ad = q;
                        y = r;
                        aa = s + 1.0F;
                        ae = s;
                        ac = u + 1.0F - 0.001F;
                        ap = u + 1.0F - 0.001F;
                        bl8 = bl5;
                        break;
                    case WEST:
                        ad = r;
                        y = p;
                        aa = s + 0.001F;
                        ae = s + 0.001F;
                        ac = u + 1.0F;
                        ap = u;
                        bl8 = bl6;
                        break;
                    default:
                        ad = o;
                        y = q;
                        aa = s + 1.0F - 0.001F;
                        ae = s + 1.0F - 0.001F;
                        ac = u;
                        ap = u + 1.0F;
                        bl8 = bl7;
                }

                if (bl8 && !isFaceOccludedByNeighbor(level, pos, direction, Math.max(ad, y), level.getBlockState(pos.relative(direction)))) {
                    BlockPos blockPos = pos.relative(direction);
                    TextureAtlasSprite textureAtlasSprite2 = textureAtlasSprites[1];
                    if (!bl) {
                        Block block = level.getBlockState(blockPos).getBlock();
                        if (block instanceof HalfTransparentBlock || block instanceof LeavesBlock) {
                            textureAtlasSprite2 = this.waterOverlay;
                        }
                    }

                    float ah = textureAtlasSprite2.getU(0.0F);
                    float ai = textureAtlasSprite2.getU(0.5F);
                    float al = textureAtlasSprite2.getV((1.0F - ad) * 0.5F);
                    float am = textureAtlasSprite2.getV((1.0F - y) * 0.5F);
                    float aq = textureAtlasSprite2.getV(0.5F);
                    float ar = direction.getAxis() == Direction.Axis.Z ? l : m;
                    float as = k * ar * f;
                    float at = k * ar * g;
                    float au = k * ar * h;
                    this.vertex(buffer, aa, t + ad, ac, as, at, au, ah, al, ao);
                    this.vertex(buffer, ae, t + y, ap, as, at, au, ai, am, ao);
                    this.vertex(buffer, ae, t + w, ap, as, at, au, ai, aq, ao);
                    this.vertex(buffer, aa, t + w, ac, as, at, au, ah, aq, ao);
                    if (textureAtlasSprite2 != this.waterOverlay) {
                        this.vertex(buffer, aa, t + w, ac, as, at, au, ah, aq, ao);
                        this.vertex(buffer, ae, t + w, ap, as, at, au, ai, aq, ao);
                        this.vertex(buffer, ae, t + y, ap, as, at, au, ai, am, ao);
                        this.vertex(buffer, aa, t + ad, ac, as, at, au, ah, al, ao);
                    }
                }
            }
        }
    }

    private float calculateAverageHeight(BlockAndTintGetter level, Fluid fluid, float currentHeight, float height1, float height2, BlockPos pos) {
        if (!(height2 >= 1.0F) && !(height1 >= 1.0F)) {
            float[] fs = new float[2];
            if (height2 > 0.0F || height1 > 0.0F) {
                float f = this.getHeight(level, fluid, pos);
                if (f >= 1.0F) {
                    return 1.0F;
                }

                this.addWeightedHeight(fs, f);
            }

            this.addWeightedHeight(fs, currentHeight);
            this.addWeightedHeight(fs, height2);
            this.addWeightedHeight(fs, height1);
            return fs[0] / fs[1];
        } else {
            return 1.0F;
        }
    }

    private void addWeightedHeight(float[] output, float height) {
        if (height >= 0.8F) {
            output[0] += height * 10.0F;
            output[1] += 10.0F;
        } else if (height >= 0.0F) {
            output[0] += height;
            output[1]++;
        }
    }

    private float getHeight(BlockAndTintGetter level, Fluid fluid, BlockPos pos) {
        BlockState blockState = level.getBlockState(pos);
        return this.getHeight(level, fluid, pos, blockState, blockState.getFluidState());
    }

    private float getHeight(BlockAndTintGetter level, Fluid fluid, BlockPos pos, BlockState blockState, FluidState fluidState) {
        if (fluid.isSame(fluidState.getType())) {
            BlockState blockState2 = level.getBlockState(pos.above());
            return fluid.isSame(blockState2.getFluidState().getType()) ? 1.0F : fluidState.getOwnHeight();
        } else {
            return !blockState.isSolid() ? 0.0F : -1.0F;
        }
    }

    private void vertex(VertexConsumer buffer, float x, float y, float z, float red, float green, float blue, float u, float v, int packedLight) {
        buffer.addVertex(x, y, z).setColor(red, green, blue, 1.0F).setUv(u, v).setLight(packedLight).setNormal(0.0F, 1.0F, 0.0F);
    }

    private int getLightColor(BlockAndTintGetter level, BlockPos pos) {
        int i = LevelRenderer.getLightColor(level, pos);
        int j = LevelRenderer.getLightColor(level, pos.above());
        int k = i & 0xFF;
        int l = j & 0xFF;
        int m = i >> 16 & 0xFF;
        int n = j >> 16 & 0xFF;
        return (k > l ? k : l) | (m > n ? m : n) << 16;
    }
}
