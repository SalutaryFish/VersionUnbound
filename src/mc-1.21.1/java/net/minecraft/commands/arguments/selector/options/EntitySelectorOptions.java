package net.minecraft.commands.arguments.selector.options;

import com.google.common.collect.Maps;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Map.Entry;
import java.util.function.Predicate;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.WrappedMinMaxBounds;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.commands.arguments.selector.EntitySelectorParser;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

public class EntitySelectorOptions {
    private static final Map<String, EntitySelectorOptions.Option> OPTIONS = Maps.newHashMap();
    public static final DynamicCommandExceptionType ERROR_UNKNOWN_OPTION = new DynamicCommandExceptionType(
        option -> Component.translatableEscape("argument.entity.options.unknown", option)
    );
    public static final DynamicCommandExceptionType ERROR_INAPPLICABLE_OPTION = new DynamicCommandExceptionType(
        option -> Component.translatableEscape("argument.entity.options.inapplicable", option)
    );
    public static final SimpleCommandExceptionType ERROR_RANGE_NEGATIVE = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.options.distance.negative")
    );
    public static final SimpleCommandExceptionType ERROR_LEVEL_NEGATIVE = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.options.level.negative")
    );
    public static final SimpleCommandExceptionType ERROR_LIMIT_TOO_SMALL = new SimpleCommandExceptionType(
        Component.translatable("argument.entity.options.limit.toosmall")
    );
    public static final DynamicCommandExceptionType ERROR_SORT_UNKNOWN = new DynamicCommandExceptionType(
        sort -> Component.translatableEscape("argument.entity.options.sort.irreversible", sort)
    );
    public static final DynamicCommandExceptionType ERROR_GAME_MODE_INVALID = new DynamicCommandExceptionType(
        gameMode -> Component.translatableEscape("argument.entity.options.mode.invalid", gameMode)
    );
    public static final DynamicCommandExceptionType ERROR_ENTITY_TYPE_INVALID = new DynamicCommandExceptionType(
        type -> Component.translatableEscape("argument.entity.options.type.invalid", type)
    );

    private static void register(String id, EntitySelectorOptions.Modifier handler, Predicate<EntitySelectorParser> predicate, Component tooltip) {
        OPTIONS.put(id, new EntitySelectorOptions.Option(handler, predicate, tooltip));
    }

    public static void bootStrap() {
        if (OPTIONS.isEmpty()) {
            register("name", parser -> {
                int i = parser.getReader().getCursor();
                boolean bl = parser.shouldInvertValue();
                String string = parser.getReader().readString();
                if (parser.hasNameNotEquals() && !bl) {
                    parser.getReader().setCursor(i);
                    throw ERROR_INAPPLICABLE_OPTION.createWithContext(parser.getReader(), "name");
                }

                if (bl) {
                    parser.setHasNameNotEquals(true);
                } else {
                    parser.setHasNameEquals(true);
                }

                parser.addPredicate(entity -> entity.getName().getString().equals(string) != bl);
            }, parser -> !parser.hasNameEquals(), Component.translatable("argument.entity.options.name.description"));
            register("distance", parser -> {
                int i = parser.getReader().getCursor();
                MinMaxBounds.Doubles doubles = MinMaxBounds.Doubles.fromReader(parser.getReader());
                if ((!doubles.min().isPresent() || !(doubles.min().get() < 0.0)) && (!doubles.max().isPresent() || !(doubles.max().get() < 0.0))) {
                    parser.setDistance(doubles);
                    parser.setWorldLimited();
                } else {
                    parser.getReader().setCursor(i);
                    throw ERROR_RANGE_NEGATIVE.createWithContext(parser.getReader());
                }
            }, parser -> parser.getDistance().isAny(), Component.translatable("argument.entity.options.distance.description"));
            register("level", parser -> {
                int i = parser.getReader().getCursor();
                MinMaxBounds.Ints ints = MinMaxBounds.Ints.fromReader(parser.getReader());
                if ((!ints.min().isPresent() || ints.min().get() >= 0) && (!ints.max().isPresent() || ints.max().get() >= 0)) {
                    parser.setLevel(ints);
                    parser.setIncludesEntities(false);
                } else {
                    parser.getReader().setCursor(i);
                    throw ERROR_LEVEL_NEGATIVE.createWithContext(parser.getReader());
                }
            }, parser -> parser.getLevel().isAny(), Component.translatable("argument.entity.options.level.description"));
            register("x", parser -> {
                parser.setWorldLimited();
                parser.setX(parser.getReader().readDouble());
            }, parser -> parser.getX() == null, Component.translatable("argument.entity.options.x.description"));
            register("y", parser -> {
                parser.setWorldLimited();
                parser.setY(parser.getReader().readDouble());
            }, parser -> parser.getY() == null, Component.translatable("argument.entity.options.y.description"));
            register("z", parser -> {
                parser.setWorldLimited();
                parser.setZ(parser.getReader().readDouble());
            }, parser -> parser.getZ() == null, Component.translatable("argument.entity.options.z.description"));
            register("dx", parser -> {
                parser.setWorldLimited();
                parser.setDeltaX(parser.getReader().readDouble());
            }, parser -> parser.getDeltaX() == null, Component.translatable("argument.entity.options.dx.description"));
            register("dy", parser -> {
                parser.setWorldLimited();
                parser.setDeltaY(parser.getReader().readDouble());
            }, parser -> parser.getDeltaY() == null, Component.translatable("argument.entity.options.dy.description"));
            register("dz", parser -> {
                parser.setWorldLimited();
                parser.setDeltaZ(parser.getReader().readDouble());
            }, parser -> parser.getDeltaZ() == null, Component.translatable("argument.entity.options.dz.description"));
            register(
                "x_rotation",
                parser -> parser.setRotX(WrappedMinMaxBounds.fromReader(parser.getReader(), true, Mth::wrapDegrees)),
                parser -> parser.getRotX() == WrappedMinMaxBounds.ANY,
                Component.translatable("argument.entity.options.x_rotation.description")
            );
            register(
                "y_rotation",
                parser -> parser.setRotY(WrappedMinMaxBounds.fromReader(parser.getReader(), true, Mth::wrapDegrees)),
                parser -> parser.getRotY() == WrappedMinMaxBounds.ANY,
                Component.translatable("argument.entity.options.y_rotation.description")
            );
            register("limit", parser -> {
                int i = parser.getReader().getCursor();
                int j = parser.getReader().readInt();
                if (j < 1) {
                    parser.getReader().setCursor(i);
                    throw ERROR_LIMIT_TOO_SMALL.createWithContext(parser.getReader());
                }

                parser.setMaxResults(j);
                parser.setLimited(true);
            }, parser -> !parser.isCurrentEntity() && !parser.isLimited(), Component.translatable("argument.entity.options.limit.description"));
            register(
                "sort",
                parser -> {
                    int i = parser.getReader().getCursor();
                    String string = parser.getReader().readUnquotedString();
                    parser.setSuggestions(
                        (builder, consumer) -> SharedSuggestionProvider.suggest(Arrays.asList("nearest", "furthest", "random", "arbitrary"), builder)
                    );

                    parser.setOrder(switch (string) {
                        case "nearest" -> EntitySelectorParser.ORDER_NEAREST;
                        case "furthest" -> EntitySelectorParser.ORDER_FURTHEST;
                        case "random" -> EntitySelectorParser.ORDER_RANDOM;
                        case "arbitrary" -> EntitySelector.ORDER_ARBITRARY;
                        default -> {
                            parser.getReader().setCursor(i);
                            throw ERROR_SORT_UNKNOWN.createWithContext(parser.getReader(), string);
                        }
                    });
                    parser.setSorted(true);
                },
                parser -> !parser.isCurrentEntity() && !parser.isSorted(),
                Component.translatable("argument.entity.options.sort.description")
            );
            register("gamemode", parser -> {
                parser.setSuggestions((builder, consumer) -> {
                    String stringx = builder.getRemaining().toLowerCase(Locale.ROOT);
                    boolean blx = !parser.hasGamemodeNotEquals();
                    boolean bl2 = true;
                    if (!stringx.isEmpty()) {
                        if (stringx.charAt(0) == '!') {
                            blx = false;
                            stringx = stringx.substring(1);
                        } else {
                            bl2 = false;
                        }
                    }

                    for (GameType gameTypex : GameType.values()) {
                        if (gameTypex.getName().toLowerCase(Locale.ROOT).startsWith(stringx)) {
                            if (bl2) {
                                builder.suggest("!" + gameTypex.getName());
                            }

                            if (blx) {
                                builder.suggest(gameTypex.getName());
                            }
                        }
                    }

                    return builder.buildFuture();
                });
                int i = parser.getReader().getCursor();
                boolean bl = parser.shouldInvertValue();
                if (parser.hasGamemodeNotEquals() && !bl) {
                    parser.getReader().setCursor(i);
                    throw ERROR_INAPPLICABLE_OPTION.createWithContext(parser.getReader(), "gamemode");
                }

                String string = parser.getReader().readUnquotedString();
                GameType gameType = GameType.byName(string, null);
                if (gameType == null) {
                    parser.getReader().setCursor(i);
                    throw ERROR_GAME_MODE_INVALID.createWithContext(parser.getReader(), string);
                }

                parser.setIncludesEntities(false);
                parser.addPredicate(entity -> {
                    if (!(entity instanceof ServerPlayer)) {
                        return false;
                    }

                    GameType gameType2 = ((ServerPlayer)entity).gameMode.getGameModeForPlayer();
                    return bl ? gameType2 != gameType : gameType2 == gameType;
                });
                if (bl) {
                    parser.setHasGamemodeNotEquals(true);
                } else {
                    parser.setHasGamemodeEquals(true);
                }
            }, parser -> !parser.hasGamemodeEquals(), Component.translatable("argument.entity.options.gamemode.description"));
            register("team", parser -> {
                boolean bl = parser.shouldInvertValue();
                String string = parser.getReader().readUnquotedString();
                parser.addPredicate(entity -> {
                    if (!(entity instanceof LivingEntity)) {
                        return false;
                    }

                    Team team = entity.getTeam();
                    String string2 = team == null ? "" : team.getName();
                    return string2.equals(string) != bl;
                });
                if (bl) {
                    parser.setHasTeamNotEquals(true);
                } else {
                    parser.setHasTeamEquals(true);
                }
            }, parser -> !parser.hasTeamEquals(), Component.translatable("argument.entity.options.team.description"));
            register(
                "type",
                parser -> {
                    parser.setSuggestions(
                        (builder, consumer) -> {
                            SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder, String.valueOf('!'));
                            SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.getTagNames().map(TagKey::location), builder, "!#");
                            if (!parser.isTypeLimitedInversely()) {
                                SharedSuggestionProvider.suggestResource(BuiltInRegistries.ENTITY_TYPE.keySet(), builder);
                                SharedSuggestionProvider.suggestResource(
                                    BuiltInRegistries.ENTITY_TYPE.getTagNames().map(TagKey::location), builder, String.valueOf('#')
                                );
                            }

                            return builder.buildFuture();
                        }
                    );
                    int i = parser.getReader().getCursor();
                    boolean bl = parser.shouldInvertValue();
                    if (parser.isTypeLimitedInversely() && !bl) {
                        parser.getReader().setCursor(i);
                        throw ERROR_INAPPLICABLE_OPTION.createWithContext(parser.getReader(), "type");
                    }

                    if (bl) {
                        parser.setTypeLimitedInversely();
                    }

                    if (parser.isTag()) {
                        TagKey<EntityType<?>> tagKey = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.read(parser.getReader()));
                        parser.addPredicate(entity -> entity.getType().is(tagKey) != bl);
                    } else {
                        ResourceLocation resourceLocation = ResourceLocation.read(parser.getReader());
                        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(resourceLocation).orElseThrow(() -> {
                            parser.getReader().setCursor(i);
                            return ERROR_ENTITY_TYPE_INVALID.createWithContext(parser.getReader(), resourceLocation.toString());
                        });
                        if (Objects.equals(EntityType.PLAYER, entityType) && !bl) {
                            parser.setIncludesEntities(false);
                        }

                        parser.addPredicate(entity -> Objects.equals(entityType, entity.getType()) != bl);
                        if (!bl) {
                            parser.limitToType(entityType);
                        }
                    }
                },
                parser -> !parser.isTypeLimited(),
                Component.translatable("argument.entity.options.type.description")
            );
            register("tag", parser -> {
                boolean bl = parser.shouldInvertValue();
                String string = parser.getReader().readUnquotedString();
                parser.addPredicate(entity -> "".equals(string) ? entity.getTags().isEmpty() != bl : entity.getTags().contains(string) != bl);
            }, parser -> true, Component.translatable("argument.entity.options.tag.description"));
            register("nbt", parser -> {
                boolean bl = parser.shouldInvertValue();
                CompoundTag compoundTag = new TagParser(parser.getReader()).readStruct();
                parser.addPredicate(entity -> {
                    CompoundTag compoundTag2 = entity.saveWithoutId(new CompoundTag());
                    if (entity instanceof ServerPlayer serverPlayer) {
                        ItemStack itemStack = serverPlayer.getInventory().getSelected();
                        if (!itemStack.isEmpty()) {
                            compoundTag2.put("SelectedItem", itemStack.save(serverPlayer.registryAccess()));
                        }
                    }

                    return NbtUtils.compareNbt(compoundTag, compoundTag2, true) != bl;
                });
            }, parser -> true, Component.translatable("argument.entity.options.nbt.description"));
            register("scores", parser -> {
                StringReader stringReader = parser.getReader();
                Map<String, MinMaxBounds.Ints> map = Maps.newHashMap();
                stringReader.expect('{');
                stringReader.skipWhitespace();

                while (stringReader.canRead() && stringReader.peek() != '}') {
                    stringReader.skipWhitespace();
                    String string = stringReader.readUnquotedString();
                    stringReader.skipWhitespace();
                    stringReader.expect('=');
                    stringReader.skipWhitespace();
                    MinMaxBounds.Ints ints = MinMaxBounds.Ints.fromReader(stringReader);
                    map.put(string, ints);
                    stringReader.skipWhitespace();
                    if (stringReader.canRead() && stringReader.peek() == ',') {
                        stringReader.skip();
                    }
                }

                stringReader.expect('}');
                if (!map.isEmpty()) {
                    parser.addPredicate(entity -> {
                        Scoreboard scoreboard = entity.getServer().getScoreboard();

                        for (Entry<String, MinMaxBounds.Ints> entry : map.entrySet()) {
                            Objective objective = scoreboard.getObjective(entry.getKey());
                            if (objective == null) {
                                return false;
                            }

                            ReadOnlyScoreInfo readOnlyScoreInfo = scoreboard.getPlayerScoreInfo(entity, objective);
                            if (readOnlyScoreInfo == null) {
                                return false;
                            }

                            if (!entry.getValue().matches(readOnlyScoreInfo.value())) {
                                return false;
                            }
                        }

                        return true;
                    });
                }

                parser.setHasScores(true);
            }, parser -> !parser.hasScores(), Component.translatable("argument.entity.options.scores.description"));
            register("advancements", parser -> {
                StringReader stringReader = parser.getReader();
                Map<ResourceLocation, Predicate<AdvancementProgress>> map = Maps.newHashMap();
                stringReader.expect('{');
                stringReader.skipWhitespace();

                while (stringReader.canRead() && stringReader.peek() != '}') {
                    stringReader.skipWhitespace();
                    ResourceLocation resourceLocation = ResourceLocation.read(stringReader);
                    stringReader.skipWhitespace();
                    stringReader.expect('=');
                    stringReader.skipWhitespace();
                    if (stringReader.canRead() && stringReader.peek() == '{') {
                        Map<String, Predicate<CriterionProgress>> map2 = Maps.newHashMap();
                        stringReader.skipWhitespace();
                        stringReader.expect('{');
                        stringReader.skipWhitespace();

                        while (stringReader.canRead() && stringReader.peek() != '}') {
                            stringReader.skipWhitespace();
                            String string = stringReader.readUnquotedString();
                            stringReader.skipWhitespace();
                            stringReader.expect('=');
                            stringReader.skipWhitespace();
                            boolean bl = stringReader.readBoolean();
                            map2.put(string, criterionProgress -> criterionProgress.isDone() == bl);
                            stringReader.skipWhitespace();
                            if (stringReader.canRead() && stringReader.peek() == ',') {
                                stringReader.skip();
                            }
                        }

                        stringReader.skipWhitespace();
                        stringReader.expect('}');
                        stringReader.skipWhitespace();
                        map.put(resourceLocation, advancementProgress -> {
                            for (Entry<String, Predicate<CriterionProgress>> entry : map2.entrySet()) {
                                CriterionProgress criterionProgress = advancementProgress.getCriterion(entry.getKey());
                                if (criterionProgress == null || !entry.getValue().test(criterionProgress)) {
                                    return false;
                                }
                            }

                            return true;
                        });
                    } else {
                        boolean bl2 = stringReader.readBoolean();
                        map.put(resourceLocation, advancementProgress -> advancementProgress.isDone() == bl2);
                    }

                    stringReader.skipWhitespace();
                    if (stringReader.canRead() && stringReader.peek() == ',') {
                        stringReader.skip();
                    }
                }

                stringReader.expect('}');
                if (!map.isEmpty()) {
                    parser.addPredicate(entity -> {
                        if (!(entity instanceof ServerPlayer serverPlayer)) {
                            return false;
                        } else {
                            PlayerAdvancements playerAdvancements = serverPlayer.getAdvancements();
                            ServerAdvancementManager serverAdvancementManager = serverPlayer.getServer().getAdvancements();

                            for (Entry<ResourceLocation, Predicate<AdvancementProgress>> entry : map.entrySet()) {
                                AdvancementHolder advancementHolder = serverAdvancementManager.get(entry.getKey());
                                if (advancementHolder == null || !entry.getValue().test(playerAdvancements.getOrStartProgress(advancementHolder))) {
                                    return false;
                                }
                            }

                            return true;
                        }
                    });
                    parser.setIncludesEntities(false);
                }

                parser.setHasAdvancements(true);
            }, parser -> !parser.hasAdvancements(), Component.translatable("argument.entity.options.advancements.description"));
            register(
                "predicate",
                parser -> {
                    boolean bl = parser.shouldInvertValue();
                    ResourceKey<LootItemCondition> resourceKey = ResourceKey.create(Registries.PREDICATE, ResourceLocation.read(parser.getReader()));
                    parser.addPredicate(
                        entity -> {
                            if (!(entity.level() instanceof ServerLevel)) {
                                return false;
                            }

                            ServerLevel serverLevel = (ServerLevel)entity.level();
                            Optional<LootItemCondition> optional = serverLevel.getServer()
                                .reloadableRegistries()
                                .lookup()
                                .get(Registries.PREDICATE, resourceKey)
                                .map(Holder::value);
                            if (optional.isEmpty()) {
                                return false;
                            }

                            LootParams lootParams = new LootParams.Builder(serverLevel)
                                .withParameter(LootContextParams.THIS_ENTITY, entity)
                                .withParameter(LootContextParams.ORIGIN, entity.position())
                                .create(LootContextParamSets.SELECTOR);
                            LootContext lootContext = new LootContext.Builder(lootParams).create(Optional.empty());
                            lootContext.pushVisitedElement(LootContext.createVisitedEntry(optional.get()));
                            return bl ^ optional.get().test(lootContext);
                        }
                    );
                },
                parser -> true,
                Component.translatable("argument.entity.options.predicate.description")
            );
        }
    }

    public static EntitySelectorOptions.Modifier get(EntitySelectorParser parser, String id, int cursor) throws CommandSyntaxException {
        EntitySelectorOptions.Option option = OPTIONS.get(id);
        if (option != null) {
            if (option.canUse.test(parser)) {
                return option.modifier;
            } else {
                throw ERROR_INAPPLICABLE_OPTION.createWithContext(parser.getReader(), id);
            }
        } else {
            parser.getReader().setCursor(cursor);
            throw ERROR_UNKNOWN_OPTION.createWithContext(parser.getReader(), id);
        }
    }

    public static void suggestNames(EntitySelectorParser parser, SuggestionsBuilder builder) {
        String string = builder.getRemaining().toLowerCase(Locale.ROOT);

        for (Entry<String, EntitySelectorOptions.Option> entry : OPTIONS.entrySet()) {
            if (entry.getValue().canUse.test(parser) && entry.getKey().toLowerCase(Locale.ROOT).startsWith(string)) {
                builder.suggest(entry.getKey() + "=", entry.getValue().description);
            }
        }
    }

    public interface Modifier {
        void handle(EntitySelectorParser var1) throws CommandSyntaxException;
    }

    record Option(EntitySelectorOptions.Modifier modifier, Predicate<EntitySelectorParser> canUse, Component description) {
    }
}
