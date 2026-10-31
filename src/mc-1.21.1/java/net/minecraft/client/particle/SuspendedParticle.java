package net.minecraft.client.particle;

import java.util.Optional;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleGroup;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class SuspendedParticle extends TextureSheetParticle {
    SuspendedParticle(ClientLevel level, SpriteSet sprites, double x, double y, double z) {
        super(level, x, y - 0.125, z);
        this.setSize(0.01F, 0.01F);
        this.pickSprite(sprites);
        this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.2F);
        this.lifetime = (int)(16.0 / (Math.random() * 0.8 + 0.2));
        this.hasPhysics = false;
        this.friction = 1.0F;
        this.gravity = 0.0F;
    }

    SuspendedParticle(ClientLevel level, SpriteSet sprites, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y - 0.125, z, xSpeed, ySpeed, zSpeed);
        this.setSize(0.01F, 0.01F);
        this.pickSprite(sprites);
        this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.6F);
        this.lifetime = (int)(16.0 / (Math.random() * 0.8 + 0.2));
        this.hasPhysics = false;
        this.friction = 1.0F;
        this.gravity = 0.0F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    public static class CrimsonSporeProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public CrimsonSporeProvider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            RandomSource randomSource = level.random;
            double d = randomSource.nextGaussian() * 1.0E-6F;
            double e = randomSource.nextGaussian() * 1.0E-4F;
            double f = randomSource.nextGaussian() * 1.0E-6F;
            SuspendedParticle suspendedParticle = new SuspendedParticle(level, this.sprite, x, y, z, d, e, f);
            suspendedParticle.setColor(0.9F, 0.4F, 0.5F);
            return suspendedParticle;
        }
    }

    public static class SporeBlossomAirProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public SporeBlossomAirProvider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            SuspendedParticle suspendedParticle = new SuspendedParticle(level, this.sprite, x, y, z, 0.0, -0.8F, 0.0) {
                @Override
                public Optional<ParticleGroup> getParticleGroup() {
                    return Optional.of(ParticleGroup.SPORE_BLOSSOM);
                }
            };
            suspendedParticle.lifetime = Mth.randomBetweenInclusive(level.random, 500, 1000);
            suspendedParticle.gravity = 0.01F;
            suspendedParticle.setColor(0.32F, 0.5F, 0.22F);
            return suspendedParticle;
        }
    }

    public static class UnderwaterProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public UnderwaterProvider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            SuspendedParticle suspendedParticle = new SuspendedParticle(level, this.sprite, x, y, z);
            suspendedParticle.setColor(0.4F, 0.4F, 0.7F);
            return suspendedParticle;
        }
    }

    public static class WarpedSporeProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public WarpedSporeProvider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            double d = level.random.nextFloat() * -1.9 * level.random.nextFloat() * 0.1;
            SuspendedParticle suspendedParticle = new SuspendedParticle(level, this.sprite, x, y, z, 0.0, d, 0.0);
            suspendedParticle.setColor(0.1F, 0.1F, 0.3F);
            suspendedParticle.setSize(0.001F, 0.001F);
            return suspendedParticle;
        }
    }
}
