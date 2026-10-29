package net.salutaryfish.versionunbound.testmod.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.salutaryfish.versionunbound.testmod.events.mixin.LivingEntityInvoker;

/**
 * Mob effects, on the server, with mobs that aren't added to the level: {@code MobEffectEvent.Applicable} and
 * {@code Added} when an effect is added (with its source; again, with the effect it updates; kept off; and forced on an
 * undead that refuses poison), {@code forceAddEffect} asking {@code Applicable} without firing {@code Added},
 * {@code Remove} for one effect and for all of them (canceled for one), {@code Expired} when an effect runs out
 * (canceled, it stays), and {@code EffectParticleModificationEvent} when the synced particles update (one effect's
 * hidden, another's changed). Whether an effect really went is also read from the movement speed modifiers that speed
 * and slowness add. And how they meet Fabric API's own effect events (see {@link #withFabric}). The listeners only look
 * at the scenario's own mobs.
 */
final class EffectScenario {
    private final Checks checks;
    private final Set<Entity> tracked = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    // Each mob's events, in order, as the records below.
    private final Map<Entity, List<Object>> events = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, MobEffectEvent.Applicable.Result> results = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Map<Entity, Holder<MobEffect>> kept = Collections.synchronizedMap(new IdentityHashMap<>());
    private final Set<Entity> lingering = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));
    private final Map<Entity, Consumer<EffectParticleModificationEvent>> particleChanges = Collections.synchronizedMap(new IdentityHashMap<>());

    EffectScenario(Checks checks) {
        this.checks = checks;
    }

    void applicable(MobEffectEvent.Applicable event) {
        if (!tracked.contains(event.getEntity())) return;
        record(event.getEntity(), new Applied(event.getEffectInstance(), event.getEffectSource()));
        MobEffectEvent.Applicable.Result result = results.get(event.getEntity());
        if (result != null) event.setResult(result);
    }

    void added(MobEffectEvent.Added event) {
        if (!tracked.contains(event.getEntity())) return;
        MobEffectInstance old = event.getOldEffectInstance();
        record(event.getEntity(), new Added(old, old == null ? -1 : old.getAmplifier(), event.getEffectInstance(), event.getEffectSource()));
    }

    void remove(MobEffectEvent.Remove event) {
        if (!tracked.contains(event.getEntity())) return;
        record(event.getEntity(), new Removed(event.getEffect(), event.getEffectInstance()));
        Holder<MobEffect> keep = kept.get(event.getEntity());
        if (keep != null && event.getEffect().is(keep)) event.setCanceled(true);
    }

    void expired(MobEffectEvent.Expired event) {
        if (!tracked.contains(event.getEntity())) return;
        record(event.getEntity(), new Expired(event.getEffectInstance()));
        if (lingering.contains(event.getEntity())) event.setCanceled(true);
    }

    void particles(EffectParticleModificationEvent event) {
        if (!tracked.contains(event.getEntity())) return;
        record(event.getEntity(), new Particles(event.getEffect(), event.getOriginalParticleOptions()));
        Consumer<EffectParticleModificationEvent> change = particleChanges.get(event.getEntity());
        if (change != null) change.accept(event);
    }

    void run(ServerLevel level) {
        adding(level);
        removing(level);
        expiring(level);
        particles(level);
        withFabric(level);
    }

    private void adding(ServerLevel level) {
        Zombie source = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        Chicken chicken = mob(level, EntityTypes.CHICKEN);
        MobEffectInstance speed = new MobEffectInstance(MobEffects.SPEED, 200);
        boolean added = chicken.addEffect(speed, source);
        checks.check("MobEffectEvent.Applicable and Added fire when an effect is added, with its source",
                added && chicken.getEffect(MobEffects.SPEED) == speed && take(chicken).equals(List.of(new Applied(speed, source), new Added(null, -1, speed, source))));
        // The entity's effect takes over the new one's details, so the event saw it before the update if it saw amplifier 0.
        MobEffectInstance faster = new MobEffectInstance(MobEffects.SPEED, 400, 1);
        chicken.addEffect(faster);
        checks.check("MobEffectEvent.Added fires before an effect is updated, with the effect the entity had",
                take(chicken).equals(List.of(new Applied(faster, null), new Added(speed, 0, faster, null))) && chicken.getEffect(MobEffects.SPEED) == speed
                        && speed.getAmplifier() == 1);

        Chicken refused = mob(level, EntityTypes.CHICKEN);
        results.put(refused, MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        checks.check("MobEffectEvent.Applicable can keep an effect off",
                !refused.addEffect(new MobEffectInstance(MobEffects.SPEED, 200)) && !refused.hasEffect(MobEffects.SPEED) && take(refused).size() == 1);
        Zombie undead = mob(level, EntityTypes.ZOMBIE);
        Zombie forced = mob(level, EntityTypes.ZOMBIE);
        results.put(forced, MobEffectEvent.Applicable.Result.APPLY);
        checks.check("MobEffectEvent.Applicable leaves it to canBeAffected by default (undead refuse poison), and can apply an effect anyway",
                !undead.addEffect(new MobEffectInstance(MobEffects.POISON, 200)) && !undead.hasEffect(MobEffects.POISON)
                        && forced.addEffect(new MobEffectInstance(MobEffects.POISON, 200)) && forced.hasEffect(MobEffects.POISON));

        Chicken pushed = mob(level, EntityTypes.CHICKEN);
        MobEffectInstance slowness = new MobEffectInstance(MobEffects.SLOWNESS, 200);
        pushed.forceAddEffect(slowness, source);
        Chicken blocked = mob(level, EntityTypes.CHICKEN);
        results.put(blocked, MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        blocked.forceAddEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200), null);
        checks.check("forceAddEffect asks MobEffectEvent.Applicable, and doesn't fire Added",
                pushed.getEffect(MobEffects.SLOWNESS) == slowness && take(pushed).equals(List.of(new Applied(slowness, source)))
                        && !blocked.hasEffect(MobEffects.SLOWNESS) && take(blocked).size() == 1);
    }

    private void removing(ServerLevel level) {
        Chicken chicken = mob(level, EntityTypes.CHICKEN);
        MobEffectInstance speed = new MobEffectInstance(MobEffects.SPEED, 200);
        chicken.addEffect(speed);
        take(chicken);
        kept.put(chicken, MobEffects.SPEED);
        checks.check("canceling MobEffectEvent.Remove keeps the effect",
                !chicken.removeEffect(MobEffects.SPEED) && chicken.getEffect(MobEffects.SPEED) == speed && hasModifier(chicken, "effect.speed")
                        && take(chicken).equals(List.of(new Removed(MobEffects.SPEED, speed))));
        kept.remove(chicken);
        checks.check("MobEffectEvent.Remove fires when an effect is removed",
                chicken.removeEffect(MobEffects.SPEED) && !chicken.hasEffect(MobEffects.SPEED) && !hasModifier(chicken, "effect.speed")
                        && take(chicken).equals(List.of(new Removed(MobEffects.SPEED, speed))));
        checks.check("MobEffectEvent.Remove fires for an effect the entity doesn't have, without an instance",
                !chicken.removeEffect(MobEffects.SLOWNESS) && take(chicken).equals(List.of(new Removed(MobEffects.SLOWNESS, null))));

        Chicken cured = mob(level, EntityTypes.CHICKEN);
        MobEffectInstance slowness = new MobEffectInstance(MobEffects.SLOWNESS, 200);
        cured.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
        cured.addEffect(slowness);
        cured.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200));
        take(cured);
        kept.put(cured, MobEffects.SLOWNESS);
        boolean removedAll = cured.removeAllEffects();
        List<Object> removals = take(cured);
        checks.check("removeAllEffects fires MobEffectEvent.Remove for each effect, and keeps the one whose event is canceled",
                removedAll && removals.size() == 3 && removals.contains(new Removed(MobEffects.SLOWNESS, slowness))
                        && removals.stream().allMatch(Removed.class::isInstance) && cured.getActiveEffects().size() == 1
                        && cured.getEffect(MobEffects.SLOWNESS) == slowness);
        checks.check("removeAllEffects takes off only what the removed effects gave",
                !hasModifier(cured, "effect.speed") && hasModifier(cured, "effect.slowness"));
    }

    private void expiring(ServerLevel level) {
        Chicken chicken = mob(level, EntityTypes.CHICKEN);
        MobEffectInstance speed = new MobEffectInstance(MobEffects.SPEED, 1);
        chicken.addEffect(speed);
        take(chicken);
        ((LivingEntityInvoker) chicken).events$tickEffects();
        checks.check("MobEffectEvent.Expired fires when an effect runs out, and the effect goes",
                take(chicken).equals(List.of(new Expired(speed))) && !chicken.hasEffect(MobEffects.SPEED) && !hasModifier(chicken, "effect.speed"));

        Chicken held = mob(level, EntityTypes.CHICKEN);
        MobEffectInstance slowness = new MobEffectInstance(MobEffects.SLOWNESS, 1);
        held.addEffect(slowness);
        take(held);
        lingering.add(held);
        ((LivingEntityInvoker) held).events$tickEffects();
        ((LivingEntityInvoker) held).events$tickEffects();
        checks.check("canceling MobEffectEvent.Expired keeps the effect, which expires again on the next tick",
                take(held).equals(List.of(new Expired(slowness), new Expired(slowness))) && held.getEffect(MobEffects.SLOWNESS) == slowness
                        && slowness.getDuration() == 0 && hasModifier(held, "effect.slowness"));
    }

    /** The particles are synced data, updated (for effects that changed) just before an entity's data is sent. */
    private void particles(ServerLevel level) {
        Chicken plain = mob(level, EntityTypes.CHICKEN);
        plain.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
        take(plain);
        plain.updateDataBeforeSync();
        List<Object> plainEvents = take(plain);
        checks.check("EffectParticleModificationEvent fires for each effect when the particles update, with the effect's own",
                plainEvents.size() == 1 && plainEvents.get(0) instanceof Particles fired && fired.effect() == plain.getEffect(MobEffects.SPEED)
                        && particles(plain).equals(List.of(fired.original())));

        Chicken changed = mob(level, EntityTypes.CHICKEN);
        changed.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
        changed.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200));
        particleChanges.put(changed, event -> {
            if (event.getEffect().is(MobEffects.SPEED)) {
                event.setVisible(false);
            } else {
                event.setParticleOptions(ParticleTypes.HEART);
            }
        });
        changed.updateDataBeforeSync();
        checks.check("EffectParticleModificationEvent hides one effect's particles and changes another's", particles(changed), List.of(ParticleTypes.HEART));
    }

    /**
     * Fabric API's mob effect events fire from the same methods; the fabricprobe testmod refuses effects for entities
     * with one tag, and keeps regeneration on removeAllEffects for another.
     */
    private void withFabric(ServerLevel level) {
        Chicken refusing = mob(level, EntityTypes.CHICKEN);
        refusing.addTag("versionunbound_fabric_refuses_effects");
        boolean added = refusing.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
        checks.check("Fabric API's ALLOW_ADD still refuses an effect when MobEffectEvent.Applicable leaves it to canBeAffected",
                !added && !refusing.hasEffect(MobEffects.SPEED) && take(refusing).size() == 1);
        Chicken overruled = mob(level, EntityTypes.CHICKEN);
        overruled.addTag("versionunbound_fabric_refuses_effects");
        results.put(overruled, MobEffectEvent.Applicable.Result.APPLY);
        checks.check("MobEffectEvent.Applicable's APPLY skips canBeAffected, Fabric API's ALLOW_ADD included",
                overruled.addEffect(new MobEffectInstance(MobEffects.SPEED, 200)) && overruled.hasEffect(MobEffects.SPEED));

        Chicken keeping = mob(level, EntityTypes.CHICKEN);
        keeping.addTag("versionunbound_fabric_keeps_regeneration");
        keeping.addEffect(new MobEffectInstance(MobEffects.SPEED, 200));
        keeping.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200));
        keeping.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200));
        kept.put(keeping, MobEffects.SLOWNESS);
        keeping.removeAllEffects();
        checks.check("removeAllEffects keeps both the effect MobEffectEvent.Remove keeps and the one Fabric API's ALLOW_EARLY_REMOVE keeps",
                keeping.getActiveEffects().size() == 2 && keeping.hasEffect(MobEffects.SLOWNESS) && keeping.hasEffect(MobEffects.REGENERATION));
    }

    private static List<ParticleOptions> particles(LivingEntity entity) {
        return entity.getEntityData().get(LivingEntityInvoker.events$dataEffectParticles());
    }

    private static boolean hasModifier(LivingEntity entity, String id) {
        return entity.getAttribute(Attributes.MOVEMENT_SPEED).hasModifier(Identifier.withDefaultNamespace(id));
    }

    private void record(Entity entity, Object event) {
        events.computeIfAbsent(entity, key -> new ArrayList<>()).add(event);
    }

    /** The mob's events so far, which are then forgotten. */
    private List<Object> take(Entity entity) {
        List<Object> taken = events.remove(entity);
        return taken == null ? List.of() : taken;
    }

    private <T extends LivingEntity> T mob(ServerLevel level, EntityType<T> type) {
        T entity = type.create(level, EntitySpawnReason.COMMAND);
        tracked.add(entity);
        return entity;
    }

    private record Applied(MobEffectInstance effect, Entity source) {}

    private record Added(MobEffectInstance old, int oldAmplifier, MobEffectInstance effect, Entity source) {}

    private record Removed(Holder<MobEffect> effect, MobEffectInstance instance) {}

    private record Expired(MobEffectInstance effect) {}

    private record Particles(MobEffectInstance effect, ParticleOptions original) {}
}
