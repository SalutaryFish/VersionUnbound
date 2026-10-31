package net.minecraft.world.effect;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public final class MobEffectUtil {
    public static Component formatDuration(MobEffectInstance effect, float durationFactor, float ticksPerSecond) {
        if (effect.isInfiniteDuration()) {
            return Component.translatable("effect.duration.infinite");
        }

        int i = Mth.floor(effect.getDuration() * durationFactor);
        return Component.literal(StringUtil.formatTickDuration(i, ticksPerSecond));
    }

    public static boolean hasDigSpeed(LivingEntity entity) {
        return entity.hasEffect(MobEffects.DIG_SPEED) || entity.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static int getDigSpeedAmplification(LivingEntity entity) {
        int i = 0;
        int j = 0;
        if (entity.hasEffect(MobEffects.DIG_SPEED)) {
            i = entity.getEffect(MobEffects.DIG_SPEED).getAmplifier();
        }

        if (entity.hasEffect(MobEffects.CONDUIT_POWER)) {
            j = entity.getEffect(MobEffects.CONDUIT_POWER).getAmplifier();
        }

        return Math.max(i, j);
    }

    public static boolean hasWaterBreathing(LivingEntity entity) {
        return entity.hasEffect(MobEffects.WATER_BREATHING) || entity.hasEffect(MobEffects.CONDUIT_POWER);
    }

    public static List<ServerPlayer> addEffectToPlayersAround(
        ServerLevel level, @Nullable Entity source, Vec3 pos, double radius, MobEffectInstance effect, int duration
    ) {
        Holder<MobEffect> holder = effect.getEffect();
        List<ServerPlayer> list = level.getPlayers(
            serverPlayer -> serverPlayer.gameMode.isSurvival()
                && (source == null || !source.isAlliedTo(serverPlayer))
                && pos.closerThan(serverPlayer.position(), radius)
                && (
                    !serverPlayer.hasEffect(holder)
                        || serverPlayer.getEffect(holder).getAmplifier() < effect.getAmplifier()
                        || serverPlayer.getEffect(holder).endsWithin(duration - 1)
                )
        );
        list.forEach(serverPlayer -> serverPlayer.addEffect(new MobEffectInstance(effect), source));
        return list;
    }
}
