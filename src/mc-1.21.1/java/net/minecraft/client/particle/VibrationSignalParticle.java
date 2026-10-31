package net.minecraft.client.particle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Optional;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.VibrationParticleOption;
import net.minecraft.util.Mth;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class VibrationSignalParticle extends TextureSheetParticle {
    private final PositionSource target;
    private float rot;
    private float rotO;
    private float pitch;
    private float pitchO;

    VibrationSignalParticle(ClientLevel level, double x, double y, double z, PositionSource target, int lifetime) {
        super(level, x, y, z, 0.0, 0.0, 0.0);
        this.quadSize = 0.3F;
        this.target = target;
        this.lifetime = lifetime;
        Optional<Vec3> optional = target.getPosition(level);
        if (optional.isPresent()) {
            Vec3 vec3 = optional.get();
            double d = x - vec3.x();
            double e = y - vec3.y();
            double f = z - vec3.z();
            this.rotO = this.rot = (float)Mth.atan2(d, f);
            this.pitchO = this.pitch = (float)Mth.atan2(e, Math.sqrt(d * d + f * f));
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera renderInfo, float partialTicks) {
        float f = Mth.sin((this.age + partialTicks - (float) (Math.PI * 2)) * 0.05F) * 2.0F;
        float g = Mth.lerp(partialTicks, this.rotO, this.rot);
        float h = Mth.lerp(partialTicks, this.pitchO, this.pitch) + (float) (Math.PI / 2);
        Quaternionf quaternionf = new Quaternionf();
        quaternionf.rotationY(g).rotateX(-h).rotateY(f);
        this.renderRotatedQuad(buffer, renderInfo, quaternionf, partialTicks);
        quaternionf.rotationY((float) -Math.PI + g).rotateX(h).rotateY(f);
        this.renderRotatedQuad(buffer, renderInfo, quaternionf, partialTicks);
    }

    @Override
    public int getLightColor(float partialTick) {
        return 240;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            Optional<Vec3> optional = this.target.getPosition(this.level);
            if (optional.isEmpty()) {
                this.remove();
            } else {
                int i = this.lifetime - this.age;
                double d = 1.0 / i;
                Vec3 vec3 = optional.get();
                this.x = Mth.lerp(d, this.x, vec3.x());
                this.y = Mth.lerp(d, this.y, vec3.y());
                this.z = Mth.lerp(d, this.z, vec3.z());
                double e = this.x - vec3.x();
                double f = this.y - vec3.y();
                double g = this.z - vec3.z();
                this.rotO = this.rot;
                this.rot = (float)Mth.atan2(e, g);
                this.pitchO = this.pitch;
                this.pitch = (float)Mth.atan2(f, Math.sqrt(e * e + g * g));
            }
        }
    }

    public static class Provider implements ParticleProvider<VibrationParticleOption> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprites) {
            this.sprite = sprites;
        }

        public Particle createParticle(
            VibrationParticleOption type, ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed
        ) {
            VibrationSignalParticle vibrationSignalParticle = new VibrationSignalParticle(level, x, y, z, type.getDestination(), type.getArrivalInTicks());
            vibrationSignalParticle.pickSprite(this.sprite);
            vibrationSignalParticle.setAlpha(1.0F);
            return vibrationSignalParticle;
        }
    }
}
