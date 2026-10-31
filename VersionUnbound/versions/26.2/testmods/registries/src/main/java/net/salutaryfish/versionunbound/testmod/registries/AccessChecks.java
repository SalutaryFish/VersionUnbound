package net.salutaryfish.versionunbound.testmod.registries;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.CombatEntry;
import net.minecraft.world.damagesource.CombatTracker;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's access transformers at run time. The testmod class tweaker carries them, so these calls compile as in a mod
 * built with NeoForge's toolchain; vanilla has the members private, so without Layer A's class tweaker applied at run
 * time they would throw {@code IllegalAccessError}, and the subclass would fail to load.
 */
final class AccessChecks {
    private AccessChecks() {}

    static void checkRegistration(Checks checks) {
        // Both private in vanilla 26.2; NeoForge's AT makes them public.
        checks.check("an AT-widened private method can be called", () -> ((FireBlock) Blocks.FIRE).getBurnOdds(Blocks.OAK_PLANKS.defaultBlockState()) == 20);
        checks.check("an AT-widened private static field can be read", () -> CombatTracker.INTENTIONAL_GAME_DESIGN_STYLE != null);
    }

    static void checkWorld(Checks checks, ServerLevel level) {
        // getMostSignificantFall is private in vanilla: overriding it needs it non-final, and vanilla's own call in
        // getDeathMessage reaching the override needs that call turned from invokespecial into invokevirtual.
        checks.check("an override of an AT-widened private method is called by vanilla", () -> {
            LivingEntity zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
            CountingTracker tracker = new CountingTracker(zombie);
            tracker.recordDamage(level.damageSources().fall(), 1.0F);
            tracker.getDeathMessage();
            zombie.discard();
            return tracker.calls.get() == 1;
        });
    }

    private static final class CountingTracker extends CombatTracker {
        final AtomicInteger calls = new AtomicInteger();

        CountingTracker(LivingEntity mob) {
            super(mob);
        }

        @Override
        public @Nullable CombatEntry getMostSignificantFall() {
            calls.incrementAndGet();
            return super.getMostSignificantFall();
        }
    }
}
