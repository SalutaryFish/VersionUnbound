package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;

public class SimpleAnimatedParticle extends TextureSheetParticle {
    protected final SpriteSet sprites;
    private float fadeR;
    private float fadeG;
    private float fadeB;
    private boolean hasFade;

    protected SimpleAnimatedParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, float gravity) {
        super(level, x, y, z);
        this.friction = 0.91F;
        this.gravity = gravity;
        this.sprites = sprites;
    }

    public void setColor(int color) {
        float f = ((color & 0xFF0000) >> 16) / 255.0F;
        float g = ((color & 0xFF00) >> 8) / 255.0F;
        float h = ((color & 0xFF) >> 0) / 255.0F;
        float i = 1.0F;
        this.setColor(f * 1.0F, g * 1.0F, h * 1.0F);
    }

    /**
     * Sets a color for the particle to drift toward (20% closer each tick, never actually getting very close)
     */
    public void setFadeColor(int rgb) {
        this.fadeR = ((rgb & 0xFF0000) >> 16) / 255.0F;
        this.fadeG = ((rgb & 0xFF00) >> 8) / 255.0F;
        this.fadeB = ((rgb & 0xFF) >> 0) / 255.0F;
        this.hasFade = true;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
        if (this.age > this.lifetime / 2) {
            this.setAlpha(1.0F - ((float)this.age - this.lifetime / 2) / this.lifetime);
            if (this.hasFade) {
                this.rCol = this.rCol + (this.fadeR - this.rCol) * 0.2F;
                this.gCol = this.gCol + (this.fadeG - this.gCol) * 0.2F;
                this.bCol = this.bCol + (this.fadeB - this.bCol) * 0.2F;
            }
        }
    }

    @Override
    public int getLightColor(float partialTick) {
        return 15728880;
    }
}
