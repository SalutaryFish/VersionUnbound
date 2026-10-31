package net.minecraft.client.renderer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.dimension.DimensionType;
import org.joml.Vector3f;

public class LightTexture implements AutoCloseable {
    public static final int FULL_BRIGHT = 15728880;
    public static final int FULL_SKY = 15728640;
    public static final int FULL_BLOCK = 240;
    private final DynamicTexture lightTexture;
    private final NativeImage lightPixels;
    private final ResourceLocation lightTextureLocation;
    private boolean updateLightTexture;
    private float blockLightRedFlicker;
    private final GameRenderer renderer;
    private final Minecraft minecraft;

    public LightTexture(GameRenderer renderer, Minecraft minecraft) {
        this.renderer = renderer;
        this.minecraft = minecraft;
        this.lightTexture = new DynamicTexture(16, 16, false);
        this.lightTextureLocation = this.minecraft.getTextureManager().register("light_map", this.lightTexture);
        this.lightPixels = this.lightTexture.getPixels();

        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                this.lightPixels.setPixelRGBA(j, i, -1);
            }
        }

        this.lightTexture.upload();
    }

    @Override
    public void close() {
        this.lightTexture.close();
    }

    public void tick() {
        this.blockLightRedFlicker = this.blockLightRedFlicker + (float)((Math.random() - Math.random()) * Math.random() * Math.random() * 0.1);
        this.blockLightRedFlicker *= 0.9F;
        this.updateLightTexture = true;
    }

    public void turnOffLightLayer() {
        RenderSystem.setShaderTexture(2, 0);
    }

    public void turnOnLightLayer() {
        RenderSystem.setShaderTexture(2, this.lightTextureLocation);
        this.minecraft.getTextureManager().bindForSetup(this.lightTextureLocation);
        RenderSystem.texParameter(3553, 10241, 9729);
        RenderSystem.texParameter(3553, 10240, 9729);
    }

    private float getDarknessGamma(float partialTick) {
        MobEffectInstance mobEffectInstance = this.minecraft.player.getEffect(MobEffects.DARKNESS);
        return mobEffectInstance != null ? mobEffectInstance.getBlendFactor(this.minecraft.player, partialTick) : 0.0F;
    }

    private float calculateDarknessScale(LivingEntity entity, float gamma, float partialTick) {
        float f = 0.45F * gamma;
        return Math.max(0.0F, Mth.cos((entity.tickCount - partialTick) * (float) Math.PI * 0.025F) * f);
    }

    public void updateLightTexture(float partialTicks) {
        if (this.updateLightTexture) {
            this.updateLightTexture = false;
            this.minecraft.getProfiler().push("lightTex");
            ClientLevel clientLevel = this.minecraft.level;
            if (clientLevel != null) {
                float f = clientLevel.getSkyDarken(1.0F);
                float g;
                if (clientLevel.getSkyFlashTime() > 0) {
                    g = 1.0F;
                } else {
                    g = f * 0.95F + 0.05F;
                }

                float h = this.minecraft.options.darknessEffectScale().get().floatValue();
                float i = this.getDarknessGamma(partialTicks) * h;
                float j = this.calculateDarknessScale(this.minecraft.player, i, partialTicks) * h;
                float k = this.minecraft.player.getWaterVision();
                float l;
                if (this.minecraft.player.hasEffect(MobEffects.NIGHT_VISION)) {
                    l = GameRenderer.getNightVisionScale(this.minecraft.player, partialTicks);
                } else if (k > 0.0F && this.minecraft.player.hasEffect(MobEffects.CONDUIT_POWER)) {
                    l = k;
                } else {
                    l = 0.0F;
                }

                Vector3f vector3f = new Vector3f(f, f, 1.0F).lerp(new Vector3f(1.0F, 1.0F, 1.0F), 0.35F);
                float m = this.blockLightRedFlicker + 1.5F;
                Vector3f vector3f2 = new Vector3f();

                for (int n = 0; n < 16; n++) {
                    for (int o = 0; o < 16; o++) {
                        float p = getBrightness(clientLevel.dimensionType(), n) * g;
                        float q = getBrightness(clientLevel.dimensionType(), o) * m;
                        float r = q;
                        float s = q * ((q * 0.6F + 0.4F) * 0.6F + 0.4F);
                        float t = q * (q * q * 0.6F + 0.4F);
                        vector3f2.set(r, s, t);
                        boolean bl = clientLevel.effects().forceBrightLightmap();
                        if (bl) {
                            vector3f2.lerp(new Vector3f(0.99F, 1.12F, 1.0F), 0.25F);
                            clampColor(vector3f2);
                        } else {
                            Vector3f vector3f3 = new Vector3f(vector3f).mul(p);
                            vector3f2.add(vector3f3);
                            vector3f2.lerp(new Vector3f(0.75F, 0.75F, 0.75F), 0.04F);
                            if (this.renderer.getDarkenWorldAmount(partialTicks) > 0.0F) {
                                float u = this.renderer.getDarkenWorldAmount(partialTicks);
                                Vector3f vector3f4 = new Vector3f(vector3f2).mul(0.7F, 0.6F, 0.6F);
                                vector3f2.lerp(vector3f4, u);
                            }
                        }

                        if (l > 0.0F) {
                            float v = Math.max(vector3f2.x(), Math.max(vector3f2.y(), vector3f2.z()));
                            if (v < 1.0F) {
                                float u = 1.0F / v;
                                Vector3f vector3f4 = new Vector3f(vector3f2).mul(u);
                                vector3f2.lerp(vector3f4, l);
                            }
                        }

                        if (!bl) {
                            if (j > 0.0F) {
                                vector3f2.add(-j, -j, -j);
                            }

                            clampColor(vector3f2);
                        }

                        float v = this.minecraft.options.gamma().get().floatValue();
                        Vector3f vector3f5 = new Vector3f(this.notGamma(vector3f2.x), this.notGamma(vector3f2.y), this.notGamma(vector3f2.z));
                        vector3f2.lerp(vector3f5, Math.max(0.0F, v - i));
                        vector3f2.lerp(new Vector3f(0.75F, 0.75F, 0.75F), 0.04F);
                        clampColor(vector3f2);
                        vector3f2.mul(255.0F);
                        int w = 255;
                        int x = (int)vector3f2.x();
                        int y = (int)vector3f2.y();
                        int z = (int)vector3f2.z();
                        this.lightPixels.setPixelRGBA(o, n, 0xFF000000 | z << 16 | y << 8 | x);
                    }
                }

                this.lightTexture.upload();
                this.minecraft.getProfiler().pop();
            }
        }
    }

    private static void clampColor(Vector3f color) {
        color.set(Mth.clamp(color.x, 0.0F, 1.0F), Mth.clamp(color.y, 0.0F, 1.0F), Mth.clamp(color.z, 0.0F, 1.0F));
    }

    private float notGamma(float value) {
        float f = 1.0F - value;
        return 1.0F - f * f * f * f;
    }

    public static float getBrightness(DimensionType dimensionType, int lightLevel) {
        float f = lightLevel / 15.0F;
        float g = f / (4.0F - 3.0F * f);
        return Mth.lerp(dimensionType.ambientLight(), g, 1.0F);
    }

    public static int pack(int blockLight, int skyLight) {
        return blockLight << 4 | skyLight << 20;
    }

    public static int block(int packedLight) {
        return packedLight >> 4 & 65535;
    }

    public static int sky(int packedLight) {
        return packedLight >> 20 & 65535;
    }
}
