package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class DripParticle extends TextureSheetParticle {
    private final Fluid type;
    protected boolean isGlowing;

    DripParticle(ClientLevel level, double x, double y, double z, Fluid type) {
        super(level, x, y, z);
        this.setSize(0.01F, 0.01F);
        this.gravity = 0.06F;
        this.type = type;
    }

    protected Fluid getType() {
        return this.type;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public int getLightColor(float partialTick) {
        return this.isGlowing ? 240 : super.getLightColor(partialTick);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.preMoveUpdate();
        if (!this.removed) {
            this.yd = this.yd - this.gravity;
            this.move(this.xd, this.yd, this.zd);
            this.postMoveUpdate();
            if (!this.removed) {
                this.xd *= 0.98F;
                this.yd *= 0.98F;
                this.zd *= 0.98F;
                if (this.type != Fluids.EMPTY) {
                    BlockPos blockPos = BlockPos.containing(this.x, this.y, this.z);
                    FluidState fluidState = this.level.getFluidState(blockPos);
                    if (fluidState.getType() == this.type && this.y < blockPos.getY() + fluidState.getHeight(this.level, blockPos)) {
                        this.remove();
                    }
                }
            }
        }
    }

    protected void preMoveUpdate() {
        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }

    protected void postMoveUpdate() {
    }

    public static TextureSheetParticle createWaterHangParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.DripHangParticle(level, x, y, z, Fluids.WATER, ParticleTypes.FALLING_WATER);
        dripParticle.setColor(0.2F, 0.3F, 1.0F);
        return dripParticle;
    }

    public static TextureSheetParticle createWaterFallParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.FallAndLandParticle(level, x, y, z, Fluids.WATER, ParticleTypes.SPLASH);
        dripParticle.setColor(0.2F, 0.3F, 1.0F);
        return dripParticle;
    }

    public static TextureSheetParticle createLavaHangParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        return new DripParticle.CoolingDripHangParticle(level, x, y, z, Fluids.LAVA, ParticleTypes.FALLING_LAVA);
    }

    public static TextureSheetParticle createLavaFallParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.FallAndLandParticle(level, x, y, z, Fluids.LAVA, ParticleTypes.LANDING_LAVA);
        dripParticle.setColor(1.0F, 0.2857143F, 0.083333336F);
        return dripParticle;
    }

    public static TextureSheetParticle createLavaLandParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.DripLandParticle(level, x, y, z, Fluids.LAVA);
        dripParticle.setColor(1.0F, 0.2857143F, 0.083333336F);
        return dripParticle;
    }

    public static TextureSheetParticle createHoneyHangParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle.DripHangParticle dripHangParticle = new DripParticle.DripHangParticle(level, x, y, z, Fluids.EMPTY, ParticleTypes.FALLING_HONEY);
        dripHangParticle.gravity *= 0.01F;
        dripHangParticle.lifetime = 100;
        dripHangParticle.setColor(0.622F, 0.508F, 0.082F);
        return dripHangParticle;
    }

    public static TextureSheetParticle createHoneyFallParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.HoneyFallAndLandParticle(level, x, y, z, Fluids.EMPTY, ParticleTypes.LANDING_HONEY);
        dripParticle.gravity = 0.01F;
        dripParticle.setColor(0.582F, 0.448F, 0.082F);
        return dripParticle;
    }

    public static TextureSheetParticle createHoneyLandParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.DripLandParticle(level, x, y, z, Fluids.EMPTY);
        dripParticle.lifetime = (int)(128.0 / (Math.random() * 0.8 + 0.2));
        dripParticle.setColor(0.522F, 0.408F, 0.082F);
        return dripParticle;
    }

    public static TextureSheetParticle createDripstoneWaterHangParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.DripHangParticle(level, x, y, z, Fluids.WATER, ParticleTypes.FALLING_DRIPSTONE_WATER);
        dripParticle.setColor(0.2F, 0.3F, 1.0F);
        return dripParticle;
    }

    public static TextureSheetParticle createDripstoneWaterFallParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.DripstoneFallAndLandParticle(level, x, y, z, Fluids.WATER, ParticleTypes.SPLASH);
        dripParticle.setColor(0.2F, 0.3F, 1.0F);
        return dripParticle;
    }

    public static TextureSheetParticle createDripstoneLavaHangParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        return new DripParticle.CoolingDripHangParticle(level, x, y, z, Fluids.LAVA, ParticleTypes.FALLING_DRIPSTONE_LAVA);
    }

    public static TextureSheetParticle createDripstoneLavaFallParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.DripstoneFallAndLandParticle(level, x, y, z, Fluids.LAVA, ParticleTypes.LANDING_LAVA);
        dripParticle.setColor(1.0F, 0.2857143F, 0.083333336F);
        return dripParticle;
    }

    public static TextureSheetParticle createNectarFallParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.FallingParticle(level, x, y, z, Fluids.EMPTY);
        dripParticle.lifetime = (int)(16.0 / (Math.random() * 0.8 + 0.2));
        dripParticle.gravity = 0.007F;
        dripParticle.setColor(0.92F, 0.782F, 0.72F);
        return dripParticle;
    }

    public static TextureSheetParticle createSporeBlossomFallParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        int i = (int)(64.0F / Mth.randomBetween(level.getRandom(), 0.1F, 0.9F));
        DripParticle dripParticle = new DripParticle.FallingParticle(level, x, y, z, Fluids.EMPTY, i);
        dripParticle.gravity = 0.005F;
        dripParticle.setColor(0.32F, 0.5F, 0.22F);
        return dripParticle;
    }

    public static TextureSheetParticle createObsidianTearHangParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle.DripHangParticle dripHangParticle = new DripParticle.DripHangParticle(level, x, y, z, Fluids.EMPTY, ParticleTypes.FALLING_OBSIDIAN_TEAR);
        dripHangParticle.isGlowing = true;
        dripHangParticle.gravity *= 0.01F;
        dripHangParticle.lifetime = 100;
        dripHangParticle.setColor(0.51171875F, 0.03125F, 0.890625F);
        return dripHangParticle;
    }

    public static TextureSheetParticle createObsidianTearFallParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.FallAndLandParticle(level, x, y, z, Fluids.EMPTY, ParticleTypes.LANDING_OBSIDIAN_TEAR);
        dripParticle.isGlowing = true;
        dripParticle.gravity = 0.01F;
        dripParticle.setColor(0.51171875F, 0.03125F, 0.890625F);
        return dripParticle;
    }

    public static TextureSheetParticle createObsidianTearLandParticle(
        SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
    ) {
        DripParticle dripParticle = new DripParticle.DripLandParticle(level, x, y, z, Fluids.EMPTY);
        dripParticle.isGlowing = true;
        dripParticle.lifetime = (int)(28.0 / (Math.random() * 0.8 + 0.2));
        dripParticle.setColor(0.51171875F, 0.03125F, 0.890625F);
        return dripParticle;
    }

    static class CoolingDripHangParticle extends DripParticle.DripHangParticle {
        CoolingDripHangParticle(ClientLevel clientLevel, double d, double e, double f, Fluid fluid, ParticleOptions particleOptions) {
            super(clientLevel, d, e, f, fluid, particleOptions);
        }

        @Override
        protected void preMoveUpdate() {
            this.rCol = 1.0F;
            this.gCol = 16.0F / (40 - this.lifetime + 16);
            this.bCol = 4.0F / (40 - this.lifetime + 8);
            super.preMoveUpdate();
        }
    }

    static class DripHangParticle extends DripParticle {
        private final ParticleOptions fallingParticle;

        DripHangParticle(ClientLevel level, double x, double y, double z, Fluid type, ParticleOptions fallingParticle) {
            super(level, x, y, z, type);
            this.fallingParticle = fallingParticle;
            this.gravity *= 0.02F;
            this.lifetime = 40;
        }

        @Override
        protected void preMoveUpdate() {
            if (this.lifetime-- <= 0) {
                this.remove();
                this.level.addParticle(this.fallingParticle, this.x, this.y, this.z, this.xd, this.yd, this.zd);
            }
        }

        @Override
        protected void postMoveUpdate() {
            this.xd *= 0.02;
            this.yd *= 0.02;
            this.zd *= 0.02;
        }
    }

    static class DripLandParticle extends DripParticle {
        DripLandParticle(ClientLevel clientLevel, double d, double e, double f, Fluid fluid) {
            super(clientLevel, d, e, f, fluid);
            this.lifetime = (int)(16.0 / (Math.random() * 0.8 + 0.2));
        }
    }

    static class DripstoneFallAndLandParticle extends DripParticle.FallAndLandParticle {
        DripstoneFallAndLandParticle(ClientLevel clientLevel, double d, double e, double f, Fluid fluid, ParticleOptions particleOptions) {
            super(clientLevel, d, e, f, fluid, particleOptions);
        }

        @Override
        protected void postMoveUpdate() {
            if (this.onGround) {
                this.remove();
                this.level.addParticle(this.landParticle, this.x, this.y, this.z, 0.0, 0.0, 0.0);
                SoundEvent soundEvent = this.getType() == Fluids.LAVA ? SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA : SoundEvents.POINTED_DRIPSTONE_DRIP_WATER;
                float f = Mth.randomBetween(this.random, 0.3F, 1.0F);
                this.level.playLocalSound(this.x, this.y, this.z, soundEvent, SoundSource.BLOCKS, f, 1.0F, false);
            }
        }
    }

    static class FallAndLandParticle extends DripParticle.FallingParticle {
        protected final ParticleOptions landParticle;

        FallAndLandParticle(ClientLevel level, double x, double y, double z, Fluid type, ParticleOptions landParticle) {
            super(level, x, y, z, type);
            this.landParticle = landParticle;
        }

        @Override
        protected void postMoveUpdate() {
            if (this.onGround) {
                this.remove();
                this.level.addParticle(this.landParticle, this.x, this.y, this.z, 0.0, 0.0, 0.0);
            }
        }
    }

    static class FallingParticle extends DripParticle {
        FallingParticle(ClientLevel level, double x, double y, double z, Fluid type) {
            this(level, x, y, z, type, (int)(64.0 / (Math.random() * 0.8 + 0.2)));
        }

        FallingParticle(ClientLevel level, double x, double y, double z, Fluid type, int lifetime) {
            super(level, x, y, z, type);
            this.lifetime = lifetime;
        }

        @Override
        protected void postMoveUpdate() {
            if (this.onGround) {
                this.remove();
            }
        }
    }

    static class HoneyFallAndLandParticle extends DripParticle.FallAndLandParticle {
        HoneyFallAndLandParticle(ClientLevel clientLevel, double d, double e, double f, Fluid fluid, ParticleOptions particleOptions) {
            super(clientLevel, d, e, f, fluid, particleOptions);
        }

        @Override
        protected void postMoveUpdate() {
            if (this.onGround) {
                this.remove();
                this.level.addParticle(this.landParticle, this.x, this.y, this.z, 0.0, 0.0, 0.0);
                float f = Mth.randomBetween(this.random, 0.3F, 1.0F);
                this.level.playLocalSound(this.x, this.y, this.z, SoundEvents.BEEHIVE_DRIP, SoundSource.BLOCKS, f, 1.0F, false);
            }
        }
    }
}
