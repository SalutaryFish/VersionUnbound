package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;

public class LavaParticle extends TextureSheetParticle {
    LavaParticle(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.gravity = 0.75F;
        this.friction = 0.999F;
        this.xd *= 0.8F;
        this.yd *= 0.8F;
        this.zd *= 0.8F;
        this.yd = this.random.nextFloat() * 0.4F + 0.05F;
        this.quadSize = this.quadSize * (this.random.nextFloat() * 2.0F + 0.2F);
        this.lifetime = (int)(16.0 / (Math.random() * 0.8 + 0.2));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public int getLightColor(float partialTick) {
        int i = super.getLightColor(partialTick);
        int j = 240;
        int k = i >> 16 & 0xFF;
        return 240 | k << 16;
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        float f = (this.age + scaleFactor) / this.lifetime;
        return this.quadSize * (1.0F - f * f);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            float f = (float)this.age / this.lifetime;
            if (this.random.nextFloat() > f) {
                this.level.addParticle(ParticleTypes.SMOKE, this.x, this.y, this.z, this.xd, this.yd, this.zd);
            }
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            LavaParticle lavaParticle = new LavaParticle(level, x, y, z);
            lavaParticle.pickSprite(this.sprite);
            return lavaParticle;
        }
    }
}
