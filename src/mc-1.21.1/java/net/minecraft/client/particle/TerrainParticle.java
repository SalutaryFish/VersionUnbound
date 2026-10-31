package net.minecraft.client.particle;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class TerrainParticle extends TextureSheetParticle {
    private final BlockPos pos;
    private final float uo;
    private final float vo;

    public TerrainParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, BlockState state) {
        this(level, x, y, z, xSpeed, ySpeed, zSpeed, state, BlockPos.containing(x, y, z));
    }

    public TerrainParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, BlockState state, BlockPos pos) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.pos = pos;
        this.setSprite(Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getParticleIcon(state));
        this.gravity = 1.0F;
        this.rCol = 0.6F;
        this.gCol = 0.6F;
        this.bCol = 0.6F;
        if (!state.is(Blocks.GRASS_BLOCK)) {
            int i = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
            this.rCol *= (i >> 16 & 0xFF) / 255.0F;
            this.gCol *= (i >> 8 & 0xFF) / 255.0F;
            this.bCol *= (i & 0xFF) / 255.0F;
        }

        this.quadSize /= 2.0F;
        this.uo = this.random.nextFloat() * 3.0F;
        this.vo = this.random.nextFloat() * 3.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    @Override
    protected float getU0() {
        return this.sprite.getU((this.uo + 1.0F) / 4.0F);
    }

    @Override
    protected float getU1() {
        return this.sprite.getU(this.uo / 4.0F);
    }

    @Override
    protected float getV0() {
        return this.sprite.getV(this.vo / 4.0F);
    }

    @Override
    protected float getV1() {
        return this.sprite.getV((this.vo + 1.0F) / 4.0F);
    }

    @Override
    public int getLightColor(float partialTick) {
        int i = super.getLightColor(partialTick);
        return i == 0 && this.level.hasChunkAt(this.pos) ? LevelRenderer.getLightColor(this.level, this.pos) : i;
    }

    @Nullable
    static TerrainParticle createTerrainParticle(
        BlockParticleOption type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        BlockState blockState = type.getState();
        return !blockState.isAir() && !blockState.is(Blocks.MOVING_PISTON) && blockState.shouldSpawnTerrainParticles()
            ? new TerrainParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, blockState)
            : null;
    }

    public static class DustPillarProvider implements ParticleProvider<BlockParticleOption> {
        @Nullable
        public Particle createParticle(
            BlockParticleOption blockParticleOption, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i
        ) {
            Particle particle = TerrainParticle.createTerrainParticle(blockParticleOption, clientLevel, d, e, f, g, h, i);
            if (particle != null) {
                particle.setParticleSpeed(
                    clientLevel.random.nextGaussian() / 30.0, h + clientLevel.random.nextGaussian() / 2.0, clientLevel.random.nextGaussian() / 30.0
                );
                particle.setLifetime(clientLevel.random.nextInt(20) + 20);
            }

            return particle;
        }
    }

    public static class Provider implements ParticleProvider<BlockParticleOption> {
        @Nullable
        public Particle createParticle(BlockParticleOption type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return TerrainParticle.createTerrainParticle(type, level, x, y, z, xSpeed, ySpeed, zSpeed);
        }
    }
}
