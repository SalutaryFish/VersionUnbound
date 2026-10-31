package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.RotationArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class TeleportCommand {
    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(
        Component.translatable("commands.teleport.invalidPosition")
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralCommandNode<CommandSourceStack> literalCommandNode = dispatcher.register(
            Commands.literal("teleport")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("location", Vec3Argument.vec3())
                        .executes(
                            context -> teleportToPos(
                                context.getSource(),
                                Collections.singleton(context.getSource().getEntityOrException()),
                                context.getSource().getLevel(),
                                Vec3Argument.getCoordinates(context, "location"),
                                WorldCoordinates.current(),
                                null
                            )
                        )
                )
                .then(
                    Commands.argument("destination", EntityArgument.entity())
                        .executes(
                            context -> teleportToEntity(
                                context.getSource(),
                                Collections.singleton(context.getSource().getEntityOrException()),
                                EntityArgument.getEntity(context, "destination")
                            )
                        )
                )
                .then(
                    Commands.argument("targets", EntityArgument.entities())
                        .then(
                            Commands.argument("location", Vec3Argument.vec3())
                                .executes(
                                    context -> teleportToPos(
                                        context.getSource(),
                                        EntityArgument.getEntities(context, "targets"),
                                        context.getSource().getLevel(),
                                        Vec3Argument.getCoordinates(context, "location"),
                                        null,
                                        null
                                    )
                                )
                                .then(
                                    Commands.argument("rotation", RotationArgument.rotation())
                                        .executes(
                                            context -> teleportToPos(
                                                context.getSource(),
                                                EntityArgument.getEntities(context, "targets"),
                                                context.getSource().getLevel(),
                                                Vec3Argument.getCoordinates(context, "location"),
                                                RotationArgument.getRotation(context, "rotation"),
                                                null
                                            )
                                        )
                                )
                                .then(
                                    Commands.literal("facing")
                                        .then(
                                            Commands.literal("entity")
                                                .then(
                                                    Commands.argument("facingEntity", EntityArgument.entity())
                                                        .executes(
                                                            context -> teleportToPos(
                                                                context.getSource(),
                                                                EntityArgument.getEntities(context, "targets"),
                                                                context.getSource().getLevel(),
                                                                Vec3Argument.getCoordinates(context, "location"),
                                                                null,
                                                                new TeleportCommand.LookAtEntity(
                                                                    EntityArgument.getEntity(context, "facingEntity"), EntityAnchorArgument.Anchor.FEET
                                                                )
                                                            )
                                                        )
                                                        .then(
                                                            Commands.argument("facingAnchor", EntityAnchorArgument.anchor())
                                                                .executes(
                                                                    context -> teleportToPos(
                                                                        context.getSource(),
                                                                        EntityArgument.getEntities(context, "targets"),
                                                                        context.getSource().getLevel(),
                                                                        Vec3Argument.getCoordinates(context, "location"),
                                                                        null,
                                                                        new TeleportCommand.LookAtEntity(
                                                                            EntityArgument.getEntity(context, "facingEntity"),
                                                                            EntityAnchorArgument.getAnchor(context, "facingAnchor")
                                                                        )
                                                                    )
                                                                )
                                                        )
                                                )
                                        )
                                        .then(
                                            Commands.argument("facingLocation", Vec3Argument.vec3())
                                                .executes(
                                                    context -> teleportToPos(
                                                        context.getSource(),
                                                        EntityArgument.getEntities(context, "targets"),
                                                        context.getSource().getLevel(),
                                                        Vec3Argument.getCoordinates(context, "location"),
                                                        null,
                                                        new TeleportCommand.LookAtPosition(Vec3Argument.getVec3(context, "facingLocation"))
                                                    )
                                                )
                                        )
                                )
                        )
                        .then(
                            Commands.argument("destination", EntityArgument.entity())
                                .executes(
                                    context -> teleportToEntity(
                                        context.getSource(), EntityArgument.getEntities(context, "targets"), EntityArgument.getEntity(context, "destination")
                                    )
                                )
                        )
                )
        );
        dispatcher.register(Commands.literal("tp").requires(source -> source.hasPermission(2)).redirect(literalCommandNode));
    }

    private static int teleportToEntity(CommandSourceStack source, Collection<? extends Entity> targets, Entity destination) throws CommandSyntaxException {
        for (Entity entity : targets) {
            performTeleport(
                source,
                entity,
                (ServerLevel)destination.level(),
                destination.getX(),
                destination.getY(),
                destination.getZ(),
                EnumSet.noneOf(RelativeMovement.class),
                destination.getYRot(),
                destination.getXRot(),
                null
            );
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.teleport.success.entity.single", targets.iterator().next().getDisplayName(), destination.getDisplayName()
                ),
                true
            );
        } else {
            source.sendSuccess(() -> Component.translatable("commands.teleport.success.entity.multiple", targets.size(), destination.getDisplayName()), true);
        }

        return targets.size();
    }

    private static int teleportToPos(
        CommandSourceStack source,
        Collection<? extends Entity> targets,
        ServerLevel level,
        Coordinates position,
        @Nullable Coordinates rotation,
        @Nullable TeleportCommand.LookAt facing
    ) throws CommandSyntaxException {
        Vec3 vec3 = position.getPosition(source);
        Vec2 vec2 = rotation == null ? null : rotation.getRotation(source);
        Set<RelativeMovement> set = EnumSet.noneOf(RelativeMovement.class);
        if (position.isXRelative()) {
            set.add(RelativeMovement.X);
        }

        if (position.isYRelative()) {
            set.add(RelativeMovement.Y);
        }

        if (position.isZRelative()) {
            set.add(RelativeMovement.Z);
        }

        if (rotation == null) {
            set.add(RelativeMovement.X_ROT);
            set.add(RelativeMovement.Y_ROT);
        } else {
            if (rotation.isXRelative()) {
                set.add(RelativeMovement.X_ROT);
            }

            if (rotation.isYRelative()) {
                set.add(RelativeMovement.Y_ROT);
            }
        }

        for (Entity entity : targets) {
            if (rotation == null) {
                performTeleport(source, entity, level, vec3.x, vec3.y, vec3.z, set, entity.getYRot(), entity.getXRot(), facing);
            } else {
                performTeleport(source, entity, level, vec3.x, vec3.y, vec3.z, set, vec2.y, vec2.x, facing);
            }
        }

        if (targets.size() == 1) {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.teleport.success.location.single",
                    targets.iterator().next().getDisplayName(),
                    formatDouble(vec3.x),
                    formatDouble(vec3.y),
                    formatDouble(vec3.z)
                ),
                true
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.teleport.success.location.multiple", targets.size(), formatDouble(vec3.x), formatDouble(vec3.y), formatDouble(vec3.z)
                ),
                true
            );
        }

        return targets.size();
    }

    private static String formatDouble(double value) {
        return String.format(Locale.ROOT, "%f", value);
    }

    private static void performTeleport(
        CommandSourceStack source,
        Entity entity,
        ServerLevel level,
        double x,
        double y,
        double z,
        Set<RelativeMovement> relativeList,
        float yaw,
        float pitch,
        @Nullable TeleportCommand.LookAt facing
    ) throws CommandSyntaxException {
        BlockPos blockPos = BlockPos.containing(x, y, z);
        if (!Level.isInSpawnableBounds(blockPos)) {
            throw INVALID_POSITION.create();
        }

        float f = Mth.wrapDegrees(yaw);
        float g = Mth.wrapDegrees(pitch);
        if (entity.teleportTo(level, x, y, z, relativeList, f, g)) {
            if (facing != null) {
                facing.perform(source, entity);
            }

            if (!(entity instanceof LivingEntity livingEntity && livingEntity.isFallFlying())) {
                entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
                entity.setOnGround(true);
            }

            if (entity instanceof PathfinderMob pathfinderMob) {
                pathfinderMob.getNavigation().stop();
            }
        }
    }

    @FunctionalInterface
    interface LookAt {
        void perform(CommandSourceStack var1, Entity var2);
    }

    record LookAtEntity(Entity entity, EntityAnchorArgument.Anchor anchor) implements TeleportCommand.LookAt {
        @Override
        public void perform(CommandSourceStack commandSourceStack, Entity entity) {
            if (entity instanceof ServerPlayer serverPlayer) {
                serverPlayer.lookAt(commandSourceStack.getAnchor(), this.entity, this.anchor);
            } else {
                entity.lookAt(commandSourceStack.getAnchor(), this.anchor.apply(this.entity));
            }
        }
    }

    record LookAtPosition(Vec3 position) implements TeleportCommand.LookAt {
        @Override
        public void perform(CommandSourceStack commandSourceStack, Entity entity) {
            entity.lookAt(commandSourceStack.getAnchor(), this.position);
        }
    }
}
