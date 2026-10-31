package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ShriekParticleOption;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;

public class ShriekParticle extends TextureSheetParticle {
    private static final float MAGICAL_X_ROT = 1.0472F;
    private int delay;

    ShriekParticle(ClientLevel level, double x, double y, double z, int delay) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.quadSize = 0.85F;
        this.delay = delay;
        this.lifetime = 30;
        this.gravity = 0.0F;
        this.xd = 0.0;
        this.yd = 0.1;
        this.zd = 0.0;
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        return this.quadSize * Mth.clamp((this.age + scaleFactor) / this.lifetime * 0.75F, 0.0F, 1.0F);
    }

    @Override
    public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
        if (this.delay <= 0) {
            this.alpha = 1.0F - Mth.clamp((this.age + partialTicks) / this.lifetime, 0.0F, 1.0F);
            Quaternionf quaternionf = new Quaternionf();
            quaternionf.rotationX(-1.0472F);
            this.renderRotatedQuad(buffer, renderInfo, quaternionf, partialTicks);
            quaternionf.rotationYXZ((float) -Math.PI, 1.0472F, 0.0F);
            this.renderRotatedQuad(buffer, renderInfo, quaternionf, partialTicks);
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return 240;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        if (this.delay > 0) {
            this.delay--;
        } else {
            super.tick();
        }
    }

    public static class Provider implements ParticleProvider<ShriekParticleOption> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(ShriekParticleOption type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            ShriekParticle shriekParticle = new ShriekParticle(level, x, y, z, type.getDelay());
            shriekParticle.pickSprite(this.sprite);
            shriekParticle.setAlpha(1.0F);
            return shriekParticle;
        }
    }
}
