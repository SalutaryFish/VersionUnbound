package net.minecraft.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class BreakingItemParticle extends TextureSheetParticle {
    private final float uo;
    private final float vo;

    BreakingItemParticle(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, ItemStack stack) {
        this(level, x, y, z, stack);
        this.xd *= 0.1F;
        this.yd *= 0.1F;
        this.zd *= 0.1F;
        this.xd += xSpeed;
        this.yd += ySpeed;
        this.zd += zSpeed;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.TERRAIN_SHEET;
    }

    protected BreakingItemParticle(ClientLevel level, double x, double y, double z, ItemStack stack) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.setSprite(Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0).getParticleIcon());
        this.gravity = 1.0F;
        this.quadSize /= 2.0F;
        this.uo = this.random.nextFloat() * 3.0F;
        this.vo = this.random.nextFloat() * 3.0F;
    }

    @Override
    protected float getU0() {
        return this.sprite.getU((this.uo + 1.0F) / 4.0F);
    }

    @Override
    protected float getU1() {
        return this.sprite.getU(this.uo / 4.0F);
    }

    @Override
    protected float getV0() {
        return this.sprite.getV(this.vo / 4.0F);
    }

    @Override
    protected float getV1() {
        return this.sprite.getV((this.vo + 1.0F) / 4.0F);
    }

    public static class CobwebProvider implements ParticleProvider<SimpleParticleType> {
        public Particle createParticle(
            SimpleParticleType simpleParticleType, ClientLevel clientLevel, double d, double e, double f, double g, double h, double i
        ) {
            return new BreakingItemParticle(clientLevel, d, e, f, new ItemStack(Items.COBWEB));
        }
    }

    public static class Provider implements ParticleProvider<ItemParticleOption> {
        public Particle createParticle(ItemParticleOption type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new BreakingItemParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, type.getItem());
        }
    }

    public static class SlimeProvider implements ParticleProvider<SimpleParticleType> {
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new BreakingItemParticle(level, x, y, z, new ItemStack(Items.SLIME_BALL));
        }
    }

    public static class SnowballProvider implements ParticleProvider<SimpleParticleType> {
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new BreakingItemParticle(level, x, y, z, new ItemStack(Items.SNOWBALL));
        }
    }
}
