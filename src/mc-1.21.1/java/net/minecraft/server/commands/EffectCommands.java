package net.minecraft.server.commands;

import com.google.common.collect.ImmutableList;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public class EffectCommands {
    private static final SimpleCommandExceptionType ERROR_GIVE_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.effect.give.failed"));
    private static final SimpleCommandExceptionType ERROR_CLEAR_EVERYTHING_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.effect.clear.everything.failed")
    );
    private static final SimpleCommandExceptionType ERROR_CLEAR_SPECIFIC_FAILED = new SimpleCommandExceptionType(
        Component.translatable("commands.effect.clear.specific.failed")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("effect")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.literal("clear")
                        .executes(
                            contextx -> clearEffects(
                                (CommandSourceStack)contextx.getSource(), ImmutableList.of(((CommandSourceStack)contextx.getSource()).getEntityOrException())
                            )
                        )
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .executes(contextx -> clearEffects((CommandSourceStack)contextx.getSource(), EntityArgument.getEntities(contextx, "targets")))
                                .then(
                                    Commands.argument("effect", ResourceArgument.resource(context, Registries.MOB_EFFECT))
                                        .executes(
                                            contextx -> clearEffect(
                                                (CommandSourceStack)contextx.getSource(),
                                                EntityArgument.getEntities(contextx, "targets"),
                                                ResourceArgument.getMobEffect(contextx, "effect")
                                            )
                                        )
                                )
                        )
                )
                .then(
                    Commands.literal("give")
                        .then(
                            Commands.argument("targets", EntityArgument.entities())
                                .then(
                                    Commands.argument("effect", ResourceArgument.resource(context, Registries.MOB_EFFECT))
                                        .executes(
                                            contextx -> giveEffect(
                                                (CommandSourceStack)contextx.getSource(),
                                                EntityArgument.getEntities(contextx, "targets"),
                                                ResourceArgument.getMobEffect(contextx, "effect"),
                                                null,
                                                0,
                                                true
                                            )
                                        )
                                        .then(
                                            Commands.argument("seconds", IntegerArgumentType.integer(1, 1000000))
                                                .executes(
                                                    contextx -> giveEffect(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        EntityArgument.getEntities(contextx, "targets"),
                                                        ResourceArgument.getMobEffect(contextx, "effect"),
                                                        IntegerArgumentType.getInteger(contextx, "seconds"),
                                                        0,
                                                        true
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("amplifier", IntegerArgumentType.integer(0, 255))
                                                        .executes(
                                                            contextx -> giveEffect(
                                                                (CommandSourceStack)contextx.getSource(),
                                                                EntityArgument.getEntities(contextx, "targets"),
                                                                ResourceArgument.getMobEffect(contextx, "effect"),
                                                                IntegerArgumentType.getInteger(contextx, "seconds"),
                                                                IntegerArgumentType.getInteger(contextx, "amplifier"),
                                                                true
                                                            )
                                                        )
                                                        .then(
                                                            Commands.argument("hideParticles", BoolArgumentType.bool())
                                                                .executes(
                                                                    contextx -> giveEffect(
                                                                        (CommandSourceStack)contextx.getSource(),
                                                                        EntityArgument.getEntities(contextx, "targets"),
                                                                        ResourceArgument.getMobEffect(contextx, "effect"),
                                                                        IntegerArgumentType.getInteger(contextx, "seconds"),
                                                                        IntegerArgumentType.getInteger(contextx, "amplifier"),
                                                                        !BoolArgumentType.getBool(contextx, "hideParticles")
                                                                    )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.literal("infinite")
                                                .executes(
                                                    contextx -> giveEffect(
                                                        (CommandSourceStack)contextx.getSource(),
                                                        EntityArgument.getEntities(contextx, "targets"),
                                                        ResourceArgument.getMobEffect(contextx, "effect"),
                                                        -1,
                                                        0,
                                                        true
                                                    )
                                                )
                                                .then(
                                                    Commands.argument("amplifier", IntegerArgumentType.integer(0, 255))
                                                        .executes(
                                                            contextx -> giveEffect(
                                                                (CommandSourceStack)contextx.getSource(),
                                                                EntityArgument.getEntities(contextx, "targets"),
                                                                ResourceArgument.getMobEffect(contextx, "effect"),
                                                                -1,
                                                                IntegerArgumentType.getInteger(contextx, "amplifier"),
                                                                true
                                                            )
                                                        )
                                                        .then(
                                                            Commands.argument("hideParticles", BoolArgumentType.bool())
                                                                .executes(
                                                                    contextx -> giveEffect(
                                                                        (CommandSourceStack)contextx.getSource(),
                                                                        EntityArgument.getEntities(contextx, "targets"),
                                                                        ResourceArgument.getMobEffect(contextx, "effect"),
                                                                        -1,
                                                                        IntegerArgumentType.getInteger(contextx, "amplifier"),
                                                                        !BoolArgumentType.getBool(contextx, "hideParticles")
                                                                    )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int giveEffect(
        CommandSourceStack source,
        Collection<? extends Entity> targets,
        Holder<MobEffect> effect,
        @Nullable Integer seconds,
        int amplifier,
        boolean showParticles
    ) throws CommandSyntaxException {
        MobEffect mobEffect = effect.value();
        int i = 0;
        int j;
        if (seconds != null) {
            if (mobEffect.isInstantenous()) {
                j = seconds;
            } else if (seconds == -1) {
                j = -1;
            } else {
                j = seconds * 20;
            }
        } else if (mobEffect.isInstantenous()) {
            j = 1;
        } else {
            j = 600;
        }

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity) {
                MobEffectInstance mobEffectInstance = new MobEffectInstance(effect, j, amplifier, false, showParticles);
                if (((LivingEntity)entity).addEffect(mobEffectInstance, source.getEntity())) {
                    i++;
                }
            }
        }

        if (i == 0) {
            throw ERROR_GIVE_FAILED.create();
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.effect.give.success.single", mobEffect.getDisplayName(), targets.iterator().next().getDisplayName(), j / 20
                ),
                true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.effect.give.success.multiple", mobEffect.getDisplayName(), targets.size(), j / 20), true);
        }

        return i;
    }

    private static int clearEffects(CommandSourceStack source, Collection<? extends Entity> targets) throws CommandSyntaxException {
        int i = 0;

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity && ((LivingEntity)entity).removeAllEffects()) {
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_CLEAR_EVERYTHING_FAILED.create();
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable("commands.effect.clear.everything.success.single", targets.iterator().next().getDisplayName()), true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.effect.clear.everything.success.multiple", targets.size()), true);
        }

        return i;
    }

    private static int clearEffect(CommandSourceStack source, Collection<? extends Entity> targets, Holder<MobEffect> effect) throws CommandSyntaxException {
        MobEffect mobEffect = effect.value();
        int i = 0;

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity && ((LivingEntity)entity).removeEffect(effect)) {
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_CLEAR_SPECIFIC_FAILED.create();
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.effect.clear.specific.success.single", mobEffect.getDisplayName(), targets.iterator().next().getDisplayName()
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.effect.clear.specific.success.multiple", mobEffect.getDisplayName(), targets.size()), true
            );
        }

        return i;
    }
}
