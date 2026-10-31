package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

public class NoteParticle extends TextureSheetParticle {
    NoteParticle(ClientLevel level, double x, double y, double z, double color) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.friction = 0.66F;
        this.speedUpWhenYMotionIsBlocked = true;
        this.xd *= 0.01F;
        this.yd *= 0.01F;
        this.zd *= 0.01F;
        this.yd += 0.2;
        this.rCol = Math.max(0.0F, Mth.sin(((float)color + 0.0F) * (float) (Math.PI * 2)) * 0.65F + 0.35F);
        this.gCol = Math.max(0.0F, Mth.sin(((float)color + 0.33333334F) * (float) (Math.PI * 2)) * 0.65F + 0.35F);
        this.bCol = Math.max(0.0F, Mth.sin(((float)color + 0.6666667F) * (float) (Math.PI * 2)) * 0.65F + 0.35F);
        this.quadSize *= 1.5F;
        this.lifetime = 6;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        return this.quadSize * Mth.clamp((this.age + scaleFactor) / this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            NoteParticle noteParticle = new NoteParticle(level, x, y, z, xSpeed);
            noteParticle.pickSprite(this.sprite);
            return noteParticle;
        }
    }
}
