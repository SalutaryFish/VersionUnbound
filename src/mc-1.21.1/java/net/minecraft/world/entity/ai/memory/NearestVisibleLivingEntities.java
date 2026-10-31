package net.minecraft.world.entity.ai.memory;

import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.Sensor;

public class NearestVisibleLivingEntities {
    private static final NearestVisibleLivingEntities EMPTY = new NearestVisibleLivingEntities();
    private final List<LivingEntity> nearbyEntities;
    private final Predicate<LivingEntity> lineOfSightTest;

    private NearestVisibleLivingEntities() {
        this.nearbyEntities = List.of();
        this.lineOfSightTest = entity -> false;
    }

    public NearestVisibleLivingEntities(LivingEntity livingEntity, List<LivingEntity> nearbyLivingEntities) {
        this.nearbyEntities = nearbyLivingEntities;
        Object2BooleanOpenHashMap<LivingEntity> object2BooleanOpenHashMap = new Object2BooleanOpenHashMap<>(nearbyLivingEntities.size());
        Predicate<LivingEntity> predicate = target -> Sensor.isEntityTargetable(livingEntity, target);
        this.lineOfSightTest = target -> object2BooleanOpenHashMap.computeIfAbsent(target, predicate);
    }

    public static NearestVisibleLivingEntities empty() {
        return EMPTY;
    }

    public Optional<LivingEntity> findClosest(Predicate<LivingEntity> predicate) {
        for (LivingEntity livingEntity : this.nearbyEntities) {
            if (predicate.test(livingEntity) && this.lineOfSightTest.test(livingEntity)) {
                return Optional.of(livingEntity);
            }
        }

        return Optional.empty();
    }

    public Iterable<LivingEntity> findAll(Predicate<LivingEntity> predicate) {
        return Iterables.filter(this.nearbyEntities, target -> predicate.test(target) && this.lineOfSightTest.test(target));
    }

    public Stream<LivingEntity> find(Predicate<LivingEntity> predicate) {
        return this.nearbyEntities.stream().filter(target -> predicate.test(target) && this.lineOfSightTest.test(target));
    }

    public boolean contains(LivingEntity entity) {
        return this.nearbyEntities.contains(entity) && this.lineOfSightTest.test(entity);
    }

    public boolean contains(Predicate<LivingEntity> predicate) {
        for (LivingEntity livingEntity : this.nearbyEntities) {
            if (predicate.test(livingEntity) && this.lineOfSightTest.test(livingEntity)) {
                return true;
            }
        }

        return false;
    }
}
