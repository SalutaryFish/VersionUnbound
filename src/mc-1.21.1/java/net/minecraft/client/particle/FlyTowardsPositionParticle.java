package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;

public class FlyTowardsPositionParticle extends TextureSheetParticle {
    private final double xStart;
    private final double yStart;
    private final double zStart;
    private final boolean isGlowing;
    private final Particle.LifetimeAlpha lifetimeAlpha;

    FlyTowardsPositionParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
        this(level, x, y, z, xSpeed, ySpeed, zSpeed, false, Particle.LifetimeAlpha.ALWAYS_OPAQUE);
    }

    FlyTowardsPositionParticle(
        ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, boolean isGlowing, Particle.LifetimeAlpha lifetimeAlpha
    ) {
        super(level, x, y, z);
        this.isGlowing = isGlowing;
        this.lifetimeAlpha = lifetimeAlpha;
        this.setAlpha(lifetimeAlpha.startAlpha());
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.xStart = x;
        this.yStart = y;
        this.zStart = z;
        this.xo = x + xSpeed;
        this.yo = y + ySpeed;
        this.zo = z + zSpeed;
        this.x = this.xo;
        this.y = this.yo;
        this.z = this.zo;
        this.quadSize = 0.1F * (this.random.nextFloat() * 0.5F + 0.2F);
        float f = this.random.nextFloat() * 0.6F + 0.4F;
        this.rCol = 0.9F * f;
        this.gCol = 0.9F * f;
        this.bCol = f;
        this.hasPhysics = false;
        this.lifetime = (int)(Math.random() * 10.0) + 30;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return this.lifetimeAlpha.isOpaque() ? ParticleRenderType.PARTICLE_SHEET_OPAQUE : ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void move(double x, double y, double z) {
        this.setBoundingBox(this.getBoundingBox().move(x, y, z));
        this.setLocationFromBoundingbox();
    }

    @Override
    public int getLightColor(float partialTick) {
        if (this.isGlowing) {
            return 240;
        }

        int i = super.getLightColor(partialTick);
        float f = (float)this.age / this.lifetime;
        f *= f;
        f *= f;
        int j = i & 0xFF;
        int k = i >> 16 & 0xFF;
        k += (int)(f * 15.0F * 16.0F);
        if (k > 240) {
            k = 240;
        }

        return j | k << 16;
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
            f = 1.0F - f;
            float g = 1.0F - f;
            g *= g;
            g *= g;
            this.x = this.xStart + this.xd * f;
            this.y = this.yStart + this.yd * f - g * 1.2F;
            this.z = this.zStart + this.zd * f;
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
        this.setAlpha(this.lifetimeAlpha.currentAlphaForAge(this.age, this.lifetime, partialTicks));
        super.render(buffer, renderInfo, partialTicks);
    }

    public static class EnchantProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public EnchantProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            FlyTowardsPositionParticle flyTowardsPositionParticle = new FlyTowardsPositionParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            flyTowardsPositionParticle.pickSprite(this.sprite);
            return flyTowardsPositionParticle;
        }
    }

    public static class NautilusProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public NautilusProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            FlyTowardsPositionParticle flyTowardsPositionParticle = new FlyTowardsPositionParticle(level, x, y, z, xSpeed, ySpeed, zSpeed);
            flyTowardsPositionParticle.pickSprite(this.sprite);
            return flyTowardsPositionParticle;
        }
    }

    public static class VaultConnectionProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public VaultConnectionProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            FlyTowardsPositionParticle flyTowardsPositionParticle = new FlyTowardsPositionParticle(
                level, x, y, z, xSpeed, ySpeed, zSpeed, true, new Particle.LifetimeAlpha(0.0F, 0.6F, 0.25F, 1.0F)
            );
            flyTowardsPositionParticle.scale(1.5F);
            flyTowardsPositionParticle.pickSprite(this.sprite);
            return flyTowardsPositionParticle;
        }
    }
}
