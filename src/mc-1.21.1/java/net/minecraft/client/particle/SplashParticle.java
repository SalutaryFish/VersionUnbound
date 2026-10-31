package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;

public class SplashParticle extends WaterDropParticle {
    SplashParticle(ClientLevel clientLevel, double d, double e, double f, double g, double h, double i) {
        super(clientLevel, d, e, f);
        this.gravity = 0.04F;
        if (h == 0.0 && (g != 0.0 || i != 0.0)) {
            this.xd = g;
            this.yd = 0.1;
            this.zd = i;
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            SplashParticle splashParticle = new SplashParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            splashParticle.pickSprite(this.sprite);
            return splashParticle;
        }
    }
}
