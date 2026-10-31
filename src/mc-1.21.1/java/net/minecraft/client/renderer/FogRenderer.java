package net.minecraft.client.renderer;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.CubicSampler;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public class FogRenderer {
    private static final int WATER_FOG_DISTANCE = 96;
    private static final List<FogRenderer.MobEffectFogFunction> MOB_EFFECT_FOG = Lists.newArrayList(
        new FogRenderer.BlindnessFogFunction(), new FogRenderer.DarknessFogFunction()
    );
    public static final float BIOME_FOG_TRANSITION_TIME = 5000.0F;
    private static float fogRed;
    private static float fogGreen;
    private static float fogBlue;
    private static int targetBiomeFog = -1;
    private static int previousBiomeFog = -1;
    private static long biomeChangedTime = -1L;

    public static void setupColor(Camera activeRenderInfo, float partialTicks, ClientLevel level, int renderDistanceChunks, float bossColorModifier) {
        FogType fogType = activeRenderInfo.getFluidInCamera();
        Entity entity = activeRenderInfo.getEntity();
        if (fogType == FogType.WATER) {
            long l = Util.getMillis();
            int i = level.getBiome(BlockPos.containing(activeRenderInfo.getPosition())).value().getWaterFogColor();
            if (biomeChangedTime < 0L) {
                targetBiomeFog = i;
                previousBiomeFog = i;
                biomeChangedTime = l;
            }

            int j = targetBiomeFog >> 16 & 0xFF;
            int k = targetBiomeFog >> 8 & 0xFF;
            int m = targetBiomeFog & 0xFF;
            int n = previousBiomeFog >> 16 & 0xFF;
            int o = previousBiomeFog >> 8 & 0xFF;
            int p = previousBiomeFog & 0xFF;
            float f = Mth.clamp((float)(l - biomeChangedTime) / 5000.0F, 0.0F, 1.0F);
            float g = Mth.lerp(f, n, j);
            float h = Mth.lerp(f, o, k);
            float q = Mth.lerp(f, p, m);
            fogRed = g / 255.0F;
            fogGreen = h / 255.0F;
            fogBlue = q / 255.0F;
            if (targetBiomeFog != i) {
                targetBiomeFog = i;
                previousBiomeFog = Mth.floor(g) << 16 | Mth.floor(h) << 8 | Mth.floor(q);
                biomeChangedTime = l;
            }
        } else if (fogType == FogType.LAVA) {
            fogRed = 0.6F;
            fogGreen = 0.1F;
            fogBlue = 0.0F;
            biomeChangedTime = -1L;
        } else if (fogType == FogType.POWDER_SNOW) {
            fogRed = 0.623F;
            fogGreen = 0.734F;
            fogBlue = 0.785F;
            biomeChangedTime = -1L;
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
        } else {
            float r = 0.25F + 0.75F * renderDistanceChunks / 32.0F;
            r = 1.0F - (float)Math.pow(r, 0.25);
            Vec3 vec3 = level.getSkyColor(activeRenderInfo.getPosition(), partialTicks);
            float s = (float)vec3.x;
            float t = (float)vec3.y;
            float u = (float)vec3.z;
            float v = Mth.clamp(Mth.cos(level.getTimeOfDay(partialTicks) * (float) (Math.PI * 2)) * 2.0F + 0.5F, 0.0F, 1.0F);
            BiomeManager biomeManager = level.getBiomeManager();
            Vec3 vec32 = activeRenderInfo.getPosition().subtract(2.0, 2.0, 2.0).scale(0.25);
            Vec3 vec33 = CubicSampler.gaussianSampleVec3(
                vec32,
                (x, y, z) -> level.effects()
                    .getBrightnessDependentFogColor(Vec3.fromRGB24(biomeManager.getNoiseBiomeAtQuart(x, y, z).value().getFogColor()), v)
            );
            fogRed = (float)vec33.x();
            fogGreen = (float)vec33.y();
            fogBlue = (float)vec33.z();
            if (renderDistanceChunks >= 4) {
                float f = Mth.sin(level.getSunAngle(partialTicks)) > 0.0F ? -1.0F : 1.0F;
                Vector3f vector3f = new Vector3f(f, 0.0F, 0.0F);
                float h = activeRenderInfo.getLookVector().dot(vector3f);
                if (h < 0.0F) {
                    h = 0.0F;
                }

                if (h > 0.0F) {
                    float[] fs = level.effects().getSunriseColor(level.getTimeOfDay(partialTicks), partialTicks);
                    if (fs != null) {
                        h *= fs[3];
                        fogRed = fogRed * (1.0F - h) + fs[0] * h;
                        fogGreen = fogGreen * (1.0F - h) + fs[1] * h;
                        fogBlue = fogBlue * (1.0F - h) + fs[2] * h;
                    }
                }
            }

            fogRed = fogRed + (s - fogRed) * r;
            fogGreen = fogGreen + (t - fogGreen) * r;
            fogBlue = fogBlue + (u - fogBlue) * r;
            float f = level.getRainLevel(partialTicks);
            if (f > 0.0F) {
                float g = 1.0F - f * 0.5F;
                float h = 1.0F - f * 0.4F;
                fogRed *= g;
                fogGreen *= g;
                fogBlue *= h;
            }

            float g = level.getThunderLevel(partialTicks);
            if (g > 0.0F) {
                float h = 1.0F - g * 0.5F;
                fogRed *= h;
                fogGreen *= h;
                fogBlue *= h;
            }

            biomeChangedTime = -1L;
        }

        float r = ((float)activeRenderInfo.getPosition().y - level.getMinBuildHeight()) * level.getLevelData().getClearColorScale();
        FogRenderer.MobEffectFogFunction mobEffectFogFunction = getPriorityFogFunction(entity, partialTicks);
        if (mobEffectFogFunction != null) {
            LivingEntity livingEntity = (LivingEntity)entity;
            r = mobEffectFogFunction.getModifiedVoidDarkness(livingEntity, livingEntity.getEffect(mobEffectFogFunction.getMobEffect()), r, partialTicks);
        }

        if (r < 1.0F && fogType != FogType.LAVA && fogType != FogType.POWDER_SNOW) {
            if (r < 0.0F) {
                r = 0.0F;
            }

            r *= r;
            fogRed *= r;
            fogGreen *= r;
            fogBlue *= r;
        }

        if (bossColorModifier > 0.0F) {
            fogRed = fogRed * (1.0F - bossColorModifier) + fogRed * 0.7F * bossColorModifier;
            fogGreen = fogGreen * (1.0F - bossColorModifier) + fogGreen * 0.6F * bossColorModifier;
            fogBlue = fogBlue * (1.0F - bossColorModifier) + fogBlue * 0.6F * bossColorModifier;
        }

        float s;
        if (fogType == FogType.WATER) {
            if (entity instanceof LocalPlayer) {
                s = ((LocalPlayer)entity).getWaterVision();
            } else {
                s = 1.0F;
            }
        } else if (entity instanceof LivingEntity livingEntity2
            && livingEntity2.hasEffect(MobEffects.NIGHT_VISION)
            && !livingEntity2.hasEffect(MobEffects.DARKNESS)) {
            s = GameRenderer.getNightVisionScale(livingEntity2, partialTicks);
        } else {
            s = 0.0F;
        }

        if (fogRed != 0.0F && fogGreen != 0.0F && fogBlue != 0.0F) {
            float t = Math.min(1.0F / fogRed, Math.min(1.0F / fogGreen, 1.0F / fogBlue));
            fogRed = fogRed * (1.0F - s) + fogRed * t * s;
            fogGreen = fogGreen * (1.0F - s) + fogGreen * t * s;
            fogBlue = fogBlue * (1.0F - s) + fogBlue * t * s;
        }

        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }

    public static void setupNoFog() {
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
    }

    @Nullable
    private static FogRenderer.MobEffectFogFunction getPriorityFogFunction(Entity entity, float partialTick) {
        return entity instanceof LivingEntity livingEntity
            ? MOB_EFFECT_FOG.stream().filter(effect -> effect.isEnabled(livingEntity, partialTick)).findFirst().orElse(null)
            : null;
    }

    public static void setupFog(Camera camera, FogRenderer.FogMode fogMode, float farPlaneDistance, boolean shouldCreateFog, float partialTick) {
        FogType fogType = camera.getFluidInCamera();
        Entity entity = camera.getEntity();
        FogRenderer.FogData fogData = new FogRenderer.FogData(fogMode);
        FogRenderer.MobEffectFogFunction mobEffectFogFunction = getPriorityFogFunction(entity, partialTick);
        if (fogType == FogType.LAVA) {
            if (entity.isSpectator()) {
                fogData.start = -8.0F;
                fogData.end = farPlaneDistance * 0.5F;
            } else if (entity instanceof LivingEntity && ((LivingEntity)entity).hasEffect(MobEffects.FIRE_RESISTANCE)) {
                fogData.start = 0.0F;
                fogData.end = 5.0F;
            } else {
                fogData.start = 0.25F;
                fogData.end = 1.0F;
            }
        } else if (fogType == FogType.POWDER_SNOW) {
            if (entity.isSpectator()) {
                fogData.start = -8.0F;
                fogData.end = farPlaneDistance * 0.5F;
            } else {
                fogData.start = 0.0F;
                fogData.end = 2.0F;
            }
        } else if (mobEffectFogFunction != null) {
            LivingEntity livingEntity = (LivingEntity)entity;
            MobEffectInstance mobEffectInstance = livingEntity.getEffect(mobEffectFogFunction.getMobEffect());
            if (mobEffectInstance != null) {
                mobEffectFogFunction.setupFog(fogData, livingEntity, mobEffectInstance, farPlaneDistance, partialTick);
            }
        } else if (fogType == FogType.WATER) {
            fogData.start = -8.0F;
            fogData.end = 96.0F;
            if (entity instanceof LocalPlayer localPlayer) {
                fogData.end = fogData.end * Math.max(0.25F, localPlayer.getWaterVision());
                Holder<Biome> holder = localPlayer.level().getBiome(localPlayer.blockPosition());
                if (holder.is(BiomeTags.HAS_CLOSER_WATER_FOG)) {
                    fogData.end *= 0.85F;
                }
            }

            if (fogData.end > farPlaneDistance) {
                fogData.end = farPlaneDistance;
                fogData.shape = FogShape.CYLINDER;
            }
        } else if (shouldCreateFog) {
            fogData.start = farPlaneDistance * 0.05F;
            fogData.end = Math.min(farPlaneDistance, 192.0F) * 0.5F;
        } else if (fogMode == FogRenderer.FogMode.FOG_SKY) {
            fogData.start = 0.0F;
            fogData.end = farPlaneDistance;
            fogData.shape = FogShape.CYLINDER;
        } else {
            float f = Mth.clamp(farPlaneDistance / 10.0F, 4.0F, 64.0F);
            fogData.start = farPlaneDistance - f;
            fogData.end = farPlaneDistance;
            fogData.shape = FogShape.CYLINDER;
        }

        RenderSystem.setShaderFogStart(fogData.start);
        RenderSystem.setShaderFogEnd(fogData.end);
        RenderSystem.setShaderFogShape(fogData.shape);
    }

    public static void levelFogColor() {
        RenderSystem.setShaderFogColor(fogRed, fogGreen, fogBlue);
    }

    static class BlindnessFogFunction implements FogRenderer.MobEffectFogFunction {
        @Override
        public Holder<MobEffect> getMobEffect() {
            return MobEffects.BLINDNESS;
        }

        @Override
        public void setupFog(FogRenderer.FogData fogData, LivingEntity livingEntity, MobEffectInstance mobEffectInstance, float f, float g) {
            float h = mobEffectInstance.isInfiniteDuration() ? 5.0F : Mth.lerp(Math.min(1.0F, mobEffectInstance.getDuration() / 20.0F), f, 5.0F);
            if (fogData.mode == FogRenderer.FogMode.FOG_SKY) {
                fogData.start = 0.0F;
                fogData.end = h * 0.8F;
            } else {
                fogData.start = h * 0.25F;
                fogData.end = h;
            }
        }
    }

    static class DarknessFogFunction implements FogRenderer.MobEffectFogFunction {
        @Override
        public Holder<MobEffect> getMobEffect() {
            return MobEffects.DARKNESS;
        }

        @Override
        public void setupFog(FogRenderer.FogData fogData, LivingEntity livingEntity, MobEffectInstance mobEffectInstance, float f, float g) {
            float h = Mth.lerp(mobEffectInstance.getBlendFactor(livingEntity, g), f, 15.0F);
            fogData.start = fogData.mode == FogRenderer.FogMode.FOG_SKY ? 0.0F : h * 0.75F;
            fogData.end = h;
        }

        @Override
        public float getModifiedVoidDarkness(LivingEntity livingEntity, MobEffectInstance mobEffectInstance, float f, float g) {
            return 1.0F - mobEffectInstance.getBlendFactor(livingEntity, g);
        }
    }

    static class FogData {
        public final FogRenderer.FogMode mode;
        public float start;
        public float end;
        public FogShape shape = FogShape.SPHERE;

        public FogData(FogRenderer.FogMode mode) {
            this.mode = mode;
        }
    }

    public enum FogMode {
        FOG_SKY,
        FOG_TERRAIN;
    }

    interface MobEffectFogFunction {
        Holder<MobEffect> getMobEffect();

        void setupFog(FogRenderer.FogData var1, LivingEntity var2, MobEffectInstance var3, float var4, float var5);

        default boolean isEnabled(LivingEntity entity, float f) {
            return entity.hasEffect(this.getMobEffect());
        }

        default float getModifiedVoidDarkness(LivingEntity entity, MobEffectInstance effectInstance, float f, float partialTick) {
            MobEffectInstance mobEffectInstance = entity.getEffect(this.getMobEffect());
            if (mobEffectInstance != null) {
                if (mobEffectInstance.endsWithin(19)) {
                    f = 1.0F - mobEffectInstance.getDuration() / 20.0F;
                } else {
                    f = 0.0F;
                }
            }

            return f;
        }
    }
}
