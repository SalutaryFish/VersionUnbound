package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;

public class ReversePortalParticle extends PortalParticle {
    ReversePortalParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.quadSize *= 1.5F;
        this.lifetime = (int)(Math.random() * 2.0) + 60;
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        float f = 1.0F - (this.age + scaleFactor) / (this.lifetime * 1.5F);
        return this.quadSize * f;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            float f = (float)this.age / this.lifetime;
            this.x = this.x + this.xd * f;
            this.y = this.y + this.yd * f;
            this.z = this.z + this.zd * f;
        }
    }

    public static class ReversePortalProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public ReversePortalProvider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            ReversePortalParticle reversePortalParticle = new ReversePortalParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            reversePortalParticle.pickSprite(this.sprite);
            return reversePortalParticle;
        }
    }
}
