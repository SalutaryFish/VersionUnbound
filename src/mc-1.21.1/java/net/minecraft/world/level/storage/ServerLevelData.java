package net.minecraft.world.level.storage;

import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.CrashReportCategory;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.timers.TimerQueue;

public interface ServerLevelData extends WritableLevelData {
    String getLevelName();

    /**
     * Sets whether it is thundering or not.
     */
    void setThundering(boolean var1);

    int getRainTime();

    /**
     * Sets the number of ticks until rain.
     */
    void setRainTime(int var1);

    /**
     * Defines the number of ticks until next thunderbolt.
     */
    void setThunderTime(int var1);

    int getThunderTime();

    @Override
    default void fillCrashReportCategory(CrashReportCategory crashReportCategory, LevelHeightAccessor level) {
        WritableLevelData.super.fillCrashReportCategory(crashReportCategory, level);
        crashReportCategory.setDetail("Level name", this::getLevelName);
        crashReportCategory.setDetail(
            "Level game mode",
            () -> String.format(
                Locale.ROOT,
                "Game mode: %s (ID %d). Hardcore: %b. Commands: %b",
                this.getGameType().getName(),
                this.getGameType().getId(),
                this.isHardcore(),
                this.isAllowCommands()
            )
        );
        crashReportCategory.setDetail(
            "Level weather",
            () -> String.format(
                Locale.ROOT,
                "Rain time: %d (now: %b), thunder time: %d (now: %b)",
                this.getRainTime(),
                this.isRaining(),
                this.getThunderTime(),
                this.isThundering()
            )
        );
    }

    int getClearWeatherTime();

    void setClearWeatherTime(int var1);

    int getWanderingTraderSpawnDelay();

    void setWanderingTraderSpawnDelay(int var1);

    int getWanderingTraderSpawnChance();

    void setWanderingTraderSpawnChance(int var1);

    @Nullable
    UUID getWanderingTraderId();

    void setWanderingTraderId(UUID var1);

    GameType getGameType();

    void setWorldBorder(WorldBorder.Settings var1);

    WorldBorder.Settings getWorldBorder();

    boolean isInitialized();

    /**
     * Sets the initialization status of the World.
     */
    void setInitialized(boolean var1);

    boolean isAllowCommands();

    void setGameType(GameType var1);

    TimerQueue<MinecraftServer> getScheduledEvents();

    void setGameTime(long var1);

    /**
     * Set current world time
     */
    void setDayTime(long var1);
}
