package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.component.FireworkExplosion;

public class FireworkParticles {
    public static class FlashProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public FlashProvider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            FireworkParticles.OverlayParticle overlayParticle = new FireworkParticles.OverlayParticle(level, x, y, z);
            overlayParticle.pickSprite(this.sprite);
            return overlayParticle;
        }
    }

    public static class OverlayParticle extends TextureSheetParticle {
        OverlayParticle(ClientLevel level, double x, double y, double z) {
            super(level, x, y, z);
            this.lifetime = 4;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
            this.setAlpha(0.6F - (this.age + partialTicks - 1.0F) * 0.25F * 0.5F);
            super.render(buffer, renderInfo, partialTicks);
        }

        @Override
        public float getQuadSize(float scaleFactor) {
            return 7.1F * Mth.sin((this.age + scaleFactor - 1.0F) * 0.25F * (float) Math.PI);
        }
    }

    static class SparkParticle extends SimpleAnimatedParticle {
        private boolean trail;
        private boolean twinkle;
        private final ParticleEngine engine;
        private float fadeR;
        private float fadeG;
        private float fadeB;
        private boolean hasFade;

        SparkParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, ParticleEngine engine, SpriteSet sprites) {
            super(level, x, y, z, sprites, 0.1F);
            this.xd = xSpeed;
            this.yd = ySpeed;
            this.zd = zSpeed;
            this.engine = engine;
            this.quadSize *= 0.75F;
            this.lifetime = 48 + this.random.nextInt(12);
            this.setSpriteFromAge(sprites);
        }

        public void setTrail(boolean trail) {
            this.trail = trail;
        }

        public void setTwinkle(boolean twinkle) {
            this.twinkle = twinkle;
        }

        @Override
        public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
            if (!this.twinkle || this.age < this.lifetime / 3 || (this.age + this.lifetime) / 3 % 2 == 0) {
                super.render(buffer, renderInfo, partialTicks);
            }
        }

        @Override
        public void tick() {
            super.tick();
            if (this.trail && this.age < this.lifetime / 2 && (this.age + this.lifetime) % 2 == 0) {
                FireworkParticles.SparkParticle sparkParticle = new FireworkParticles.SparkParticle(
                    this.level, this.x, this.y, this.z, 0.0, 0.0, 0.0, this.engine, this.sprites
                );
                sparkParticle.setAlpha(0.99F);
                sparkParticle.setColor(this.rCol, this.gCol, this.bCol);
                sparkParticle.age = sparkParticle.lifetime / 2;
                if (this.hasFade) {
                    sparkParticle.hasFade = true;
                    sparkParticle.fadeR = this.fadeR;
                    sparkParticle.fadeG = this.fadeG;
                    sparkParticle.fadeB = this.fadeB;
                }

                sparkParticle.twinkle = this.twinkle;
                this.engine.add(sparkParticle);
            }
        }
    }

    public static class SparkProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public SparkProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            FireworkParticles.SparkParticle sparkParticle = new FireworkParticles.SparkParticle(
                level, x, y, z, xSpeed, ySpeed, zSpeed, Minecraft.getInstance().particleEngine, this.sprites
            );
            sparkParticle.setAlpha(0.99F);
            return sparkParticle;
        }
    }

    public static class Starter extends NoRenderParticle {
        private static final double[][] CREEPER_PARTICLE_COORDS = new double[][]{
            {0.0, 0.2}, {0.2, 0.2}, {0.2, 0.6}, {0.6, 0.6}, {0.6, 0.2}, {0.2, 0.2}, {0.2, 0.0}, {0.4, 0.0}, {0.4, -0.6}, {0.2, -0.6}, {0.2, -0.4}, {0.0, -0.4}
        };
        private static final double[][] STAR_PARTICLE_COORDS = new double[][]{
            {0.0, 1.0},
            {0.3455, 0.309},
            {0.9511, 0.309},
            {0.3795918367346939, -0.12653061224489795},
            {0.6122448979591837, -0.8040816326530612},
            {0.0, -0.35918367346938773}
        };
        private int life;
        private final ParticleEngine engine;
        private final List<FireworkExplosion> explosions;
        private boolean twinkleDelay;

        public Starter(
            ClientLevel level, double x, double y, double z, double xd, double yd, double zd, ParticleEngine engine, List<FireworkExplosion> explosions
        ) {
            super(level, x, y, z);
            this.xd = xd;
            this.yd = yd;
            this.zd = zd;
            this.engine = engine;
            if (explosions.isEmpty()) {
                throw new IllegalArgumentException("Cannot create firework starter with no explosions");
            }

            this.explosions = explosions;
            this.lifetime = explosions.size() * 2 - 1;

            for (FireworkExplosion fireworkExplosion : explosions) {
                if (fireworkExplosion.hasTwinkle()) {
                    this.twinkleDelay = true;
                    this.lifetime += 15;
                    break;
                }
            }
        }

        @Override
        public void tick() {
            if (this.life == 0) {
                boolean bl = this.isFarAwayFromCamera();
                boolean bl2 = false;
                if (this.explosions.size() >= 3) {
                    bl2 = true;
                } else {
                    for (FireworkExplosion fireworkExplosion : this.explosions) {
                        if (fireworkExplosion.shape() == FireworkExplosion.Shape.LARGE_BALL) {
                            bl2 = true;
                            break;
                        }
                    }
                }

                SoundEvent soundEvent;
                if (bl2) {
                    soundEvent = bl ? SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR : SoundEvents.FIREWORK_ROCKET_LARGE_BLAST;
                } else {
                    soundEvent = bl ? SoundEvents.FIREWORK_ROCKET_BLAST_FAR : SoundEvents.FIREWORK_ROCKET_BLAST;
                }

                this.level.playLocalSound(this.x, this.y, this.z, soundEvent, SoundSource.AMBIENT, 20.0F, 0.95F + this.random.nextFloat() * 0.1F, true);
            }

            if (this.life % 2 == 0 && this.life / 2 < this.explosions.size()) {
                int i = this.life / 2;
                FireworkExplosion fireworkExplosion2 = this.explosions.get(i);
                boolean bl3 = fireworkExplosion2.hasTrail();
                boolean bl4 = fireworkExplosion2.hasTwinkle();
                IntList intList = fireworkExplosion2.colors();
                IntList intList2 = fireworkExplosion2.fadeColors();
                if (intList.isEmpty()) {
                    intList = IntList.of(DyeColor.BLACK.getFireworkColor());
                }

                switch (fireworkExplosion2.shape()) {
                    case SMALL_BALL:
                        this.createParticleBall(0.25, 2, intList, intList2, bl3, bl4);
                        break;
                    case LARGE_BALL:
                        this.createParticleBall(0.5, 4, intList, intList2, bl3, bl4);
                        break;
                    case STAR:
                        this.createParticleShape(0.5, STAR_PARTICLE_COORDS, intList, intList2, bl3, bl4, false);
                        break;
                    case CREEPER:
                        this.createParticleShape(0.5, CREEPER_PARTICLE_COORDS, intList, intList2, bl3, bl4, true);
                        break;
                    case BURST:
                        this.createParticleBurst(intList, intList2, bl3, bl4);
                }

                int j = intList.getInt(0);
                Particle particle = this.engine.createParticle(ParticleTypes.FLASH, this.x, this.y, this.z, 0.0, 0.0, 0.0);
                particle.setColor(FastColor.ARGB32.red(j) / 255.0F, FastColor.ARGB32.green(j) / 255.0F, FastColor.ARGB32.blue(j) / 255.0F);
            }

            this.life++;
            if (this.life > this.lifetime) {
                if (this.twinkleDelay) {
                    boolean bl = this.isFarAwayFromCamera();
                    SoundEvent soundEvent2 = bl ? SoundEvents.FIREWORK_ROCKET_TWINKLE_FAR : SoundEvents.FIREWORK_ROCKET_TWINKLE;
                    this.level.playLocalSound(this.x, this.y, this.z, soundEvent2, SoundSource.AMBIENT, 20.0F, 0.9F + this.random.nextFloat() * 0.15F, true);
                }

                this.remove();
            }
        }

        private boolean isFarAwayFromCamera() {
            Minecraft minecraft = Minecraft.getInstance();
            return minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(this.x, this.y, this.z) >= 256.0;
        }

        private void createParticle(
            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, IntList colors, IntList fadeColors, boolean trail, boolean twinkle
        ) {
            FireworkParticles.SparkParticle sparkParticle = (FireworkParticles.SparkParticle)this.engine
                .createParticle(ParticleTypes.FIREWORK, x, y, z, xSpeed, ySpeed, zSpeed);
            sparkParticle.setTrail(trail);
            sparkParticle.setTwinkle(twinkle);
            sparkParticle.setAlpha(0.99F);
            sparkParticle.setColor(Util.getRandom(colors, this.random));
            if (!fadeColors.isEmpty()) {
                sparkParticle.setFadeColor(Util.getRandom(fadeColors, this.random));
            }
        }

        private void createParticleBall(double speed, int radius, IntList colors, IntList fadeColors, boolean trail, boolean twinkle) {
            double d = this.x;
            double e = this.y;
            double f = this.z;

            for (int i = -radius; i <= radius; i++) {
                for (int j = -radius; j <= radius; j++) {
                    for (int k = -radius; k <= radius; k++) {
                        double g = j + (this.random.nextDouble() - this.random.nextDouble()) * 0.5;
                        double h = i + (this.random.nextDouble() - this.random.nextDouble()) * 0.5;
                        double l = k + (this.random.nextDouble() - this.random.nextDouble()) * 0.5;
                        double m = Math.sqrt(g * g + h * h + l * l) / speed + this.random.nextGaussian() * 0.05;
                        this.createParticle(d, e, f, g / m, h / m, l / m, colors, fadeColors, trail, twinkle);
                        if (i != -radius && i != radius && j != -radius && j != radius) {
                            k += radius * 2 - 1;
                        }
                    }
                }
            }
        }

        private void createParticleShape(double speed, double[][] coords, IntList colors, IntList fadeColors, boolean trail, boolean twinkle, boolean isCreeper) {
            double d = coords[0][0];
            double e = coords[0][1];
            this.createParticle(this.x, this.y, this.z, d * speed, e * speed, 0.0, colors, fadeColors, trail, twinkle);
            float f = this.random.nextFloat() * (float) Math.PI;
            double g = isCreeper ? 0.034 : 0.34;

            for (int i = 0; i < 3; i++) {
                double h = f + i * (float) Math.PI * g;
                double j = d;
                double k = e;

                for (int l = 1; l < coords.length; l++) {
                    double m = coords[l][0];
                    double n = coords[l][1];

                    for (double o = 0.25; o <= 1.0; o += 0.25) {
                        double p = Mth.lerp(o, j, m) * speed;
                        double q = Mth.lerp(o, k, n) * speed;
                        double r = p * Math.sin(h);
                        p *= Math.cos(h);

                        for (double s = -1.0; s <= 1.0; s += 2.0) {
                            this.createParticle(this.x, this.y, this.z, p * s, q, r * s, colors, fadeColors, trail, twinkle);
                        }
                    }

                    j = m;
                    k = n;
                }
            }
        }

        private void createParticleBurst(IntList colors, IntList fadeColors, boolean trail, boolean twinkle) {
            double d = this.random.nextGaussian() * 0.05;
            double e = this.random.nextGaussian() * 0.05;

            for (int i = 0; i < 70; i++) {
                double f = this.xd * 0.5 + this.random.nextGaussian() * 0.15 + d;
                double g = this.zd * 0.5 + this.random.nextGaussian() * 0.15 + e;
                double h = this.yd * 0.5 + this.random.nextDouble() * 0.5;
                this.createParticle(this.x, this.y, this.z, f, h, g, colors, fadeColors, trail, twinkle);
            }
        }
    }
}
