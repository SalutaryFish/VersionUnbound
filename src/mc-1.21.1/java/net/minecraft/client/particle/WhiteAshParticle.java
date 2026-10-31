package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.FastColor;
import net.minecraft.util.RandomSource;

public class WhiteAshParticle extends BaseAshSmokeParticle {
    private static final int COLOR_RGB24 = 12235202;

    protected WhiteAshParticle(
        ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, float quadSizeMultiplier, SpriteSet sprites
    ) {
        super(level, x, y, z, 0.1F, -0.1F, 0.1F, xSpeed, ySpeed, zSpeed, quadSizeMultiplier, sprites, 0.0F, 20, 0.0125F, false);
        this.rCol = FastColor.ARGB32.red(12235202) / 255.0F;
        this.gCol = FastColor.ARGB32.green(12235202) / 255.0F;
        this.bCol = FastColor.ARGB32.blue(12235202) / 255.0F;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            RandomSource randomSource = level.random;
            double d = randomSource.nextFloat() * -1.9 * randomSource.nextFloat() * 0.1;
            double e = randomSource.nextFloat() * -0.5 * randomSource.nextFloat() * 0.1 * 5.0;
            double f = randomSource.nextFloat() * -1.9 * randomSource.nextFloat() * 0.1;
            return new WhiteAshParticle(level, x, y, z, d, e, f, 1.0F, this.sprites);
        }
    }
}
