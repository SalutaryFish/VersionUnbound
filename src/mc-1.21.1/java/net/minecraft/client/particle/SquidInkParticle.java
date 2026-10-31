package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.FastColor;

public class SquidInkParticle extends SimpleAnimatedParticle {
    SquidInkParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int packedColor, SpriteSet sprites) {
        super(level, x, y, z, sprites, 0.0F);
        this.friction = 0.92F;
        this.quadSize = 0.5F;
        this.setAlpha(1.0F);
        this.setColor(FastColor.ARGB32.red(packedColor), FastColor.ARGB32.green(packedColor), FastColor.ARGB32.blue(packedColor));
        this.lifetime = (int)(this.quadSize * 12.0F / (Math.random() * 0.8F + 0.2F));
        this.setSpriteFromAge(sprites);
        this.hasPhysics = false;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
            if (this.age > this.lifetime / 2) {
                this.setAlpha(1.0F - ((float)this.age - this.lifetime / 2) / this.lifetime);
            }

            if (this.level.getBlockState(BlockPos.containing(this.x, this.y, this.z)).isAir()) {
                this.yd -= 0.0074F;
            }
        }
    }

    public static class GlowInkProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public GlowInkProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SquidInkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, FastColor.ARGB32.color(255, 204, 31, 102), this.sprites);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SquidInkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, FastColor.ARGB32.color(255, 255, 255, 255), this.sprites);
        }
    }
}
