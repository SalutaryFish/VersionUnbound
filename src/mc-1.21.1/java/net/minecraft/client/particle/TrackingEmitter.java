package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class TrackingEmitter extends NoRenderParticle {
    private final Entity entity;
    private int life;
    private final int lifeTime;
    private final ParticleOptions particleType;

    public TrackingEmitter(ClientLevel level, Entity entity, ParticleOptions particleType) {
        this(level, entity, particleType, 3);
    }

    public TrackingEmitter(ClientLevel level, Entity entity, ParticleOptions particleType, int lifetime) {
        this(level, entity, particleType, lifetime, entity.getDeltaMovement());
    }

    private TrackingEmitter(ClientLevel level, Entity entity, ParticleOptions particleType, int lifetime, Vec3 speedVector) {
        super(level, entity.getX(), entity.getY(0.5), entity.getZ(), speedVector.x, speedVector.y, speedVector.z);
        this.entity = entity;
        this.lifeTime = lifetime;
        this.particleType = particleType;
        this.tick();
    }

    @Override
    public void tick() {
        for (int i = 0; i < 16; i++) {
            double d = this.random.nextFloat() * 2.0F - 1.0F;
            double e = this.random.nextFloat() * 2.0F - 1.0F;
            double f = this.random.nextFloat() * 2.0F - 1.0F;
            if (!(d * d + e * e + f * f > 1.0)) {
                double g = this.entity.getX(d / 4.0);
                double h = this.entity.getY(0.5 + e / 4.0);
                double j = this.entity.getZ(f / 4.0);
                this.level.addParticle(this.particleType, false, g, h, j, d, e + 0.2, f);
            }
        }

        this.life++;
        if (this.life >= this.lifeTime) {
            this.remove();
        }
    }
}
