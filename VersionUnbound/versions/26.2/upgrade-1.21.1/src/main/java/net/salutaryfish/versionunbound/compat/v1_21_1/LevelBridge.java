package net.salutaryfish.versionunbound.compat.v1_21_1;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** 1.21.1's {@code Level} members (also called on {@code ServerLevel}). */
public final class LevelBridge {
    private LevelBridge() {}

    /** {@code getSharedSpawnPos()}: the world spawn, 26.2's respawn data. */
    public static BlockPos getSharedSpawnPos(Level level) {
        return level.getRespawnData().pos();
    }

    /** {@code getSharedSpawnAngle()}: the world spawn's yaw, 26.2's respawn data. */
    public static float getSharedSpawnAngle(Level level) {
        return level.getRespawnData().yaw();
    }

    /** {@code playSound(Player except, x, y, z, ...)}: 26.2 takes any entity to leave out. */
    public static void playSound(Level level, @Nullable Player except, double x, double y, double z, SoundEvent sound, SoundSource source, float volume,
            float pitch) {
        level.playSound(except, x, y, z, sound, source, volume, pitch);
    }
}
