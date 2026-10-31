package net.minecraft.commands.arguments.selector;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EntitySelector {
    public static final int INFINITE = Integer.MAX_VALUE;
    public static final BiConsumer<Vec3, List<? extends Entity>> ORDER_ARBITRARY = (center, entityList) -> {};
    private static final EntityTypeTest<Entity, ?> ANY_TYPE = new EntityTypeTest<Entity, Entity>() {
        public Entity tryCast(Entity entity) {
            return entity;
        }

        @Override
        public Class<? extends Entity> getBaseClass() {
            return Entity.class;
        }
    };
    private final int maxResults;
    private final boolean includesEntities;
    private final boolean worldLimited;
    private final List<Predicate<Entity>> contextFreePredicates;
    private final MinMaxBounds.Doubles range;
    private final Function<Vec3, Vec3> position;
    @Nullable
    private final AABB aabb;
    private final BiConsumer<Vec3, List<? extends Entity>> order;
    private final boolean currentEntity;
    @Nullable
    private final String playerName;
    @Nullable
    private final UUID entityUUID;
    private final EntityTypeTest<Entity, ?> type;
    private final boolean usesSelector;

    public EntitySelector(
        int maxResults,
        boolean includesEntities,
        boolean worldLimited,
        List<Predicate<Entity>> contextFreePredicates,
        MinMaxBounds.Doubles range,
        Function<Vec3, Vec3> position,
        @Nullable AABB aabb,
        BiConsumer<Vec3, List<? extends Entity>> order,
        boolean currentEntity,
        @Nullable String playerName,
        @Nullable UUID entityUUID,
        @Nullable EntityType<?> type,
        boolean usesSelector
    ) {
        this.maxResults = maxResults;
        this.includesEntities = includesEntities;
        this.worldLimited = worldLimited;
        this.contextFreePredicates = contextFreePredicates;
        this.range = range;
        this.position = position;
        this.aabb = aabb;
        this.order = order;
        this.currentEntity = currentEntity;
        this.playerName = playerName;
        this.entityUUID = entityUUID;
        this.type = type == null ? ANY_TYPE : type;
        this.usesSelector = usesSelector;
    }

    public int getMaxResults() {
        return this.maxResults;
    }

    public boolean includesEntities() {
        return this.includesEntities;
    }

    public boolean isSelfSelector() {
        return this.currentEntity;
    }

    public boolean isWorldLimited() {
        return this.worldLimited;
    }

    public boolean usesSelector() {
        return this.usesSelector;
    }

    private void checkPermissions(CommandSourceStack source) throws CommandSyntaxException {
        if (this.usesSelector && !source.hasPermission(2)) {
            throw EntityArgument.ERROR_SELECTORS_NOT_ALLOWED.create();
        }
    }

    public Entity findSingleEntity(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        List<? extends Entity> list = this.findEntities(source);
        if (list.isEmpty()) {
            throw EntityArgument.NO_ENTITIES_FOUND.create();
        } else if (list.size() > 1) {
            throw EntityArgument.ERROR_NOT_SINGLE_ENTITY.create();
        } else {
            return list.get(0);
        }
    }

    public List<? extends Entity> findEntities(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        if (!this.includesEntities) {
            return this.findPlayers(source);
        }

        if (this.playerName != null) {
            ServerPlayer serverPlayer = source.getServer().getPlayerList().getPlayerByName(this.playerName);
            return serverPlayer == null ? List.of() : List.of(serverPlayer);
        }

        if (this.entityUUID != null) {
            for (ServerLevel serverLevel : source.getServer().getAllLevels()) {
                Entity entity = serverLevel.getEntity(this.entityUUID);
                if (entity != null) {
                    if (entity.getType().isEnabled(source.enabledFeatures())) {
                        return List.of(entity);
                    }
                    break;
                }
            }

            return List.of();
        } else {
            Vec3 vec3 = this.position.apply(source.getPosition());
            AABB aABB = this.getAbsoluteAabb(vec3);
            if (this.currentEntity) {
                Predicate<Entity> predicate = this.getPredicate(vec3, aABB, null);
                return source.getEntity() != null && predicate.test(source.getEntity()) ? List.of(source.getEntity()) : List.of();
            }

            Predicate<Entity> predicate = this.getPredicate(vec3, aABB, source.enabledFeatures());
            List<Entity> list = new ObjectArrayList<>();
            if (this.isWorldLimited()) {
                this.addEntities(list, source.getLevel(), aABB, predicate);
            } else {
                for (ServerLevel serverLevel2 : source.getServer().getAllLevels()) {
                    this.addEntities(list, serverLevel2, aABB, predicate);
                }
            }

            return this.sortAndLimit(vec3, list);
        }
    }

    private void addEntities(List<Entity> entities, ServerLevel level, @Nullable AABB box, Predicate<Entity> predicate) {
        int i = this.getResultLimit();
        if (entities.size() < i) {
            if (box != null) {
                level.getEntities(this.type, box, predicate, entities, i);
            } else {
                level.getEntities(this.type, predicate, entities, i);
            }
        }
    }

    private int getResultLimit() {
        return this.order == ORDER_ARBITRARY ? this.maxResults : Integer.MAX_VALUE;
    }

    public ServerPlayer findSinglePlayer(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        List<ServerPlayer> list = this.findPlayers(source);
        if (list.size() != 1) {
            throw EntityArgument.NO_PLAYERS_FOUND.create();
        } else {
            return list.get(0);
        }
    }

    public List<ServerPlayer> findPlayers(CommandSourceStack source) throws CommandSyntaxException {
        this.checkPermissions(source);
        if (this.playerName != null) {
            ServerPlayer serverPlayer = source.getServer().getPlayerList().getPlayerByName(this.playerName);
            return serverPlayer == null ? List.of() : List.of(serverPlayer);
        }

        if (this.entityUUID != null) {
            ServerPlayer serverPlayer = source.getServer().getPlayerList().getPlayer(this.entityUUID);
            return serverPlayer == null ? List.of() : List.of(serverPlayer);
        }

        Vec3 vec3 = this.position.apply(source.getPosition());
        AABB aABB = this.getAbsoluteAabb(vec3);
        Predicate<Entity> predicate = this.getPredicate(vec3, aABB, null);
        if (this.currentEntity) {
            return source.getEntity() instanceof ServerPlayer serverPlayer2 && predicate.test(serverPlayer2) ? List.of(serverPlayer2) : List.of();
        }

        int i = this.getResultLimit();
        List<ServerPlayer> list;
        if (this.isWorldLimited()) {
            list = source.getLevel().getPlayers(predicate, i);
        } else {
            list = new ObjectArrayList<>();

            for (ServerPlayer serverPlayer3 : source.getServer().getPlayerList().getPlayers()) {
                if (predicate.test(serverPlayer3)) {
                    list.add(serverPlayer3);
                    if (list.size() >= i) {
                        return list;
                    }
                }
            }
        }

        return this.sortAndLimit(vec3, list);
    }

    @Nullable
    private AABB getAbsoluteAabb(Vec3 pos) {
        return this.aabb != null ? this.aabb.move(pos) : null;
    }

    private Predicate<Entity> getPredicate(Vec3 pos, @Nullable AABB box, @Nullable FeatureFlagSet enabledFeatures) {
        boolean bl = enabledFeatures != null;
        boolean bl2 = box != null;
        boolean bl3 = !this.range.isAny();
        int i = (bl ? 1 : 0) + (bl2 ? 1 : 0) + (bl3 ? 1 : 0);
        List<Predicate<Entity>> list;
        if (i == 0) {
            list = this.contextFreePredicates;
        } else {
            List<Predicate<Entity>> list2 = new ObjectArrayList<>(this.contextFreePredicates.size() + i);
            list2.addAll(this.contextFreePredicates);
            if (bl) {
                list2.add(entity -> entity.getType().isEnabled(enabledFeatures));
            }

            if (bl2) {
                list2.add(entity -> box.intersects(entity.getBoundingBox()));
            }

            if (bl3) {
                list2.add(entity -> this.range.matchesSqr(entity.distanceToSqr(pos)));
            }

            list = list2;
        }

        return Util.allOf(list);
    }

    private <T extends Entity> List<T> sortAndLimit(Vec3 pos, List<T> entities) {
        if (entities.size() > 1) {
            this.order.accept(pos, entities);
        }

        return entities.subList(0, Math.min(this.maxResults, entities.size()));
    }

    public static Component joinNames(List<? extends Entity> names) {
        return ComponentUtils.formatList(names, Entity::getDisplayName);
    }
}
