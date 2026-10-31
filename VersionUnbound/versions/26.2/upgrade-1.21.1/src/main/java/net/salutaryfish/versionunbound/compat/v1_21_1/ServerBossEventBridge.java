package net.salutaryfish.versionunbound.compat.v1_21_1;

import java.util.UUID;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

/** 1.21.1's {@code ServerBossEvent(Component, BossBarColor, BossBarOverlay)}, which 26.2 gives a leading id. */
public final class ServerBossEventBridge {
    /** 1.21.1's {@code Mth.RANDOM}, which its {@code createInsecureUUID()} used. */
    private static final RandomSource RANDOM = RandomSource.createThreadSafe();

    private ServerBossEventBridge() {}

    /** The id 1.21.1's constructor made for the bar: {@code Mth.createInsecureUUID()}. */
    public static UUID id() {
        return Mth.createInsecureUUID(RANDOM);
    }
}
