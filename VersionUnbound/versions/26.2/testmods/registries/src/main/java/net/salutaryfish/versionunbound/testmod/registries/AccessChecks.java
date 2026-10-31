package net.salutaryfish.versionunbound.testmod.registries;

import com.mojang.serialization.MapCodec;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
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
 * Static methods and a constructor NeoForge's patches add to vanilla classes, reached the way a mod's {@code invokestatic}
 * and {@code new} reach them (this testmod compiles against vanilla, which lacks them; a public lookup resolves them as
 * the JVM resolves a mod's call). And NeoForge's access transformers at run time. The testmod class tweaker carries them, so these calls compile as in a mod
 * built with NeoForge's toolchain; vanilla has the members private, so without Layer A's class tweaker applied at run
 * time they would throw {@code IllegalAccessError}, and the subclass would fail to load.
 */
final class AccessChecks {
    private AccessChecks() {}

    static void checkRegistration(Checks checks) {
        var lookup = MethodHandles.publicLookup();
        checks.check("BuiltInRegistries.getVanillaRegistrationOrder", () -> {
            @SuppressWarnings("unchecked")
            Set<Identifier> order = (Set<Identifier>) lookup.findStatic(BuiltInRegistries.class, "getVanillaRegistrationOrder", MethodType.methodType(Set.class)).invoke();
            return order.contains(Registries.BLOCK.identifier()) && !order.contains(RegistriesMod.SHAPES_KEY.identifier());
        });
        checks.check("RegistryOps.retrieveRegistryLookup", () -> lookup.findStatic(RegistryOps.class, "retrieveRegistryLookup",
                MethodType.methodType(MapCodec.class, ResourceKey.class)).invoke(Registries.ITEM) instanceof MapCodec<?>);
        checks.check("SpawnPlacements.hasPlacement", () -> {
            MethodHandle hasPlacement = lookup.findStatic(SpawnPlacements.class, "hasPlacement", MethodType.methodType(boolean.class, EntityType.class));
            return (boolean) hasPlacement.invoke(EntityTypes.COW) && (boolean) hasPlacement.invoke(RegistriesMod.PROBE_MOB.get())
                    && !(boolean) hasPlacement.invoke(EntityTypes.ITEM);
        });
        checks.check("SpawnPlacements.fireSpawnPlacementEvent exists", () -> lookup.findStatic(SpawnPlacements.class, "fireSpawnPlacementEvent", MethodType.methodType(void.class)) != null);
        checks.check("the AttributeSupplier.Builder(AttributeSupplier) constructor", () -> {
            AttributeSupplier zombie = DefaultAttributes.getSupplier(EntityTypes.ZOMBIE);
            var builder = (AttributeSupplier.Builder) lookup.findConstructor(AttributeSupplier.Builder.class, MethodType.methodType(void.class, AttributeSupplier.class)).invoke(zombie);
            AttributeSupplier copy = builder.add(Attributes.LUCK, 7.0).build();
            return copy.getBaseValue(Attributes.FOLLOW_RANGE) == zombie.getBaseValue(Attributes.FOLLOW_RANGE) && copy.getBaseValue(Attributes.LUCK) == 7.0
                    && zombie.getBaseValue(Attributes.LUCK) == 3.0;
        });

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
