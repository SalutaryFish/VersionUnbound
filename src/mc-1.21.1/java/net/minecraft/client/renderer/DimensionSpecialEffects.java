package net.minecraft.client.renderer;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

public abstract class DimensionSpecialEffects {
    private static final Object2ObjectMap<ResourceLocation, DimensionSpecialEffects> EFFECTS = Util.make(new Object2ObjectArrayMap<>(), dimensionEffectMap -> {
        DimensionSpecialEffects.OverworldEffects overworldEffects = new DimensionSpecialEffects.OverworldEffects();
        dimensionEffectMap.defaultReturnValue(overworldEffects);
        dimensionEffectMap.put(BuiltinDimensionTypes.OVERWORLD_EFFECTS, overworldEffects);
        dimensionEffectMap.put(BuiltinDimensionTypes.NETHER_EFFECTS, new DimensionSpecialEffects.NetherEffects());
        dimensionEffectMap.put(BuiltinDimensionTypes.END_EFFECTS, new DimensionSpecialEffects.EndEffects());
    });
    private final float[] sunriseCol = new float[4];
    private final float cloudLevel;
    private final boolean hasGround;
    private final DimensionSpecialEffects.SkyType skyType;
    private final boolean forceBrightLightmap;
    private final boolean constantAmbientLight;

    public DimensionSpecialEffects(
        float cloudLevel, boolean hasGround, DimensionSpecialEffects.SkyType skyType, boolean forceBrightLightmap, boolean constantAmbientLight
    ) {
        this.cloudLevel = cloudLevel;
        this.hasGround = hasGround;
        this.skyType = skyType;
        this.forceBrightLightmap = forceBrightLightmap;
        this.constantAmbientLight = constantAmbientLight;
    }

    public static DimensionSpecialEffects forType(DimensionType dimensionType) {
        return EFFECTS.get(dimensionType.effectsLocation());
    }

    @Nullable
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        float f = 0.4F;
        float g = Mth.cos(timeOfDay * (float) (Math.PI * 2)) - 0.0F;
        float h = -0.0F;
        if (g >= -0.4F && g <= 0.4F) {
            float i = (g - -0.0F) / 0.4F * 0.5F + 0.5F;
            float j = 1.0F - (1.0F - Mth.sin(i * (float) Math.PI)) * 0.99F;
            j *= j;
            this.sunriseCol[0] = i * 0.3F + 0.7F;
            this.sunriseCol[1] = i * i * 0.7F + 0.2F;
            this.sunriseCol[2] = i * i * 0.0F + 0.2F;
            this.sunriseCol[3] = j;
            return this.sunriseCol;
        } else {
            return null;
        }
    }

    public float getCloudHeight() {
        return this.cloudLevel;
    }

    public boolean hasGround() {
        return this.hasGround;
    }

    public abstract Vec3 getBrightnessDependentFogColor(Vec3 var1, float var2);

    public abstract boolean isFoggyAt(int var1, int var2);

    public DimensionSpecialEffects.SkyType skyType() {
        return this.skyType;
    }

    public boolean forceBrightLightmap() {
        return this.forceBrightLightmap;
    }

    public boolean constantAmbientLight() {
        return this.constantAmbientLight;
    }

    public static class EndEffects extends DimensionSpecialEffects {
        public EndEffects() {
            super(Float.NaN, false, DimensionSpecialEffects.SkyType.END, true, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 vec3, float f) {
            return vec3.scale(0.15F);
        }

        @Override
        public boolean isFoggyAt(int i, int j) {
            return false;
        }

        @Nullable
        @Override
        public float[] getSunriseColor(float f, float g) {
            return null;
        }
    }

    public static class NetherEffects extends DimensionSpecialEffects {
        public NetherEffects() {
            super(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, true);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 vec3, float f) {
            return vec3;
        }

        @Override
        public boolean isFoggyAt(int i, int j) {
            return true;
        }
    }

    public static class OverworldEffects extends DimensionSpecialEffects {
        public static final int CLOUD_LEVEL = 192;

        public OverworldEffects() {
            super(192.0F, true, DimensionSpecialEffects.SkyType.NORMAL, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 vec3, float f) {
            return vec3.multiply(f * 0.94F + 0.06F, f * 0.94F + 0.06F, f * 0.91F + 0.09F);
        }

        @Override
        public boolean isFoggyAt(int i, int j) {
            return false;
        }
    }

    public enum SkyType {
        NONE,
        NORMAL,
        END;
    }
}
