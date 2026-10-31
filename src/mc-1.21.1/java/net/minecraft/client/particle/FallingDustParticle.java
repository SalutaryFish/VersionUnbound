package net.minecraft.client.particle;

import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

public class FallingDustParticle extends TextureSheetParticle {
    private final float rotSpeed;
    private final SpriteSet sprites;

    FallingDustParticle(ClientLevel level, double x, double y, double z, float xSpeed, float ySpeed, float zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.rCol = xSpeed;
        this.gCol = ySpeed;
        this.bCol = zSpeed;
        float f = 0.9F;
        this.quadSize *= 0.67499995F;
        int i = (int)(32.0 / (Math.random() * 0.8 + 0.2));
        this.lifetime = (int)Math.max(i * 0.9F, 1.0F);
        this.setSpriteFromAge(sprites);
        this.rotSpeed = ((float)Math.random() - 0.5F) * 0.1F;
        this.roll = (float)Math.random() * (float) (Math.PI * 2);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        return this.quadSize * Mth.clamp((this.age + scaleFactor) / this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.setSpriteFromAge(this.sprites);
            this.oRoll = this.roll;
            this.roll = this.roll + (float) Math.PI * this.rotSpeed * 2.0F;
            if (this.onGround) {
                this.oRoll = this.roll = 0.0F;
            }

            this.move(this.xd, this.yd, this.zd);
            this.yd -= 0.003F;
            this.yd = Math.max(this.yd, -0.14F);
        }
    }

    public static class Provider implements ParticleProvider<BlockParticleOption> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        @Nullable
        public Particle createParticle(BlockParticleOption type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            BlockState blockState = type.getState();
            if (!blockState.isAir() && blockState.getRenderShape() == RenderShape.INVISIBLE) {
                return null;
            }

            BlockPos blockPos = BlockPos.containing(x, y, z);
            int i = Minecraft.getInstance().getBlockColors().getColor(blockState, level, blockPos);
            if (blockState.getBlock() instanceof FallingBlock) {
                i = ((FallingBlock)blockState.getBlock()).getDustColor(blockState, level, blockPos);
            }

            float f = (i >> 16 & 0xFF) / 255.0F;
            float g = (i >> 8 & 0xFF) / 255.0F;
            float h = (i & 0xFF) / 255.0F;
            return new FallingDustParticle(level, x, y, z, f, g, h, this.sprite);
        }
    }
}
