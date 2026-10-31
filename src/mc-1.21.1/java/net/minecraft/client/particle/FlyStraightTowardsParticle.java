package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

public class FlyStraightTowardsParticle extends TextureSheetParticle {
    private final double xStart;
    private final double yStart;
    private final double zStart;
    private final int startColor;
    private final int endColor;

    FlyStraightTowardsParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int startColor, int endColor) {
        super(level, x, y, z);
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
        this.hasPhysics = false;
        this.lifetime = (int)(Math.random() * 5.0) + 25;
        this.startColor = startColor;
        this.endColor = endColor;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public void move(double x, double y, double z) {
    }

    @Override
    public int getLightColor(float partialTick) {
        return 240;
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
            float g = 1.0F - f;
            this.x = this.xStart + this.xd * g;
            this.y = this.yStart + this.yd * g;
            this.z = this.zStart + this.zd * g;
            int i = FastColor.ARGB32.lerp(f, this.startColor, this.endColor);
            this.setColor(FastColor.ARGB32.red(i) / 255.0F, FastColor.ARGB32.green(i) / 255.0F, FastColor.ARGB32.blue(i) / 255.0F);
            this.setAlpha(FastColor.ARGB32.alpha(i) / 255.0F);
        }
    }

    public static class OminousSpawnProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public OminousSpawnProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            FlyStraightTowardsParticle flyStraightTowardsParticle = new FlyStraightTowardsParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, -12210434, -1);
            flyStraightTowardsParticle.scale(Mth.randomBetween(level.getRandom(), 3.0F, 5.0F));
            flyStraightTowardsParticle.pickSprite(this.sprite);
            return flyStraightTowardsParticle;
        }
    }
}
