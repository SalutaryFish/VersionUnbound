package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.util.Collection;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ParticleArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class ParticleCommand {
    private static final SimpleCommandExceptionType ERROR_FAILED = new SimpleCommandExceptionType(Component.translatable("commands.particle.failed"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("particle")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("name", ParticleArgument.particle(context))
                        .executes(
                            contextx -> sendParticles(
                                (CommandSourceStack)contextx.getSource(),
                                ParticleArgument.getParticle(contextx, "name"),
                                ((CommandSourceStack)contextx.getSource()).getPosition(),
                                Vec3.ZERO,
                                0.0F,
                                0,
                                false,
                                ((CommandSourceStack)contextx.getSource()).getServer().getPlayerList().getPlayers()
                            )
                        )
                        .then(
                            Commands.argument("pos", Vec3Argument.vec3())
                                .executes(
                                    contextx -> sendParticles(
                                        (CommandSourceStack)contextx.getSource(),
                                        ParticleArgument.getParticle(contextx, "name"),
                                        Vec3Argument.getVec3(contextx, "pos"),
                                        Vec3.ZERO,
                                        0.0F,
                                        0,
                                        false,
                                        ((CommandSourceStack)contextx.getSource()).getServer().getPlayerList().getPlayers()
                                    )
                                )
                                .then(
                                    Commands.argument("delta", Vec3Argument.vec3(false))
                                        .then(
                                            Commands.argument("speed", FloatArgumentType.floatArg(0.0F))
                                                .then(
                                                    Commands.argument("count", IntegerArgumentType.integer(0))
                                                        .executes(
                                                            contextx -> sendParticles(
                                                                (CommandSourceStack)contextx.getSource(),
                                                                ParticleArgument.getParticle(contextx, "name"),
                                                                Vec3Argument.getVec3(contextx, "pos"),
                                                                Vec3Argument.getVec3(contextx, "delta"),
                                                                FloatArgumentType.getFloat(contextx, "speed"),
                                                                IntegerArgumentType.getInteger(contextx, "count"),
                                                                false,
                                                                ((CommandSourceStack)contextx.getSource()).getServer().getPlayerList().getPlayers()
                                                            )
                                                        )
                                                        .then(
                                                            Commands.literal("force")
                                                                .executes(
                                                                    contextx -> sendParticles(
                                                                        (CommandSourceStack)contextx.getSource(),
                                                                        ParticleArgument.getParticle(contextx, "name"),
                                                                        Vec3Argument.getVec3(contextx, "pos"),
                                                                        Vec3Argument.getVec3(contextx, "delta"),
                                                                        FloatArgumentType.getFloat(contextx, "speed"),
                                                                        IntegerArgumentType.getInteger(contextx, "count"),
                                                                        true,
                                                                        ((CommandSourceStack)contextx.getSource()).getServer().getPlayerList().getPlayers()
                                                                    )
                                                                )
                                                                .then(
                                                                    Commands.argument("viewers", EntityArgument.players())
                                                                        .executes(
                                                                            contextx -> sendParticles(
                                                                                (CommandSourceStack)contextx.getSource(),
                                                                                ParticleArgument.getParticle(contextx, "name"),
                                                                                Vec3Argument.getVec3(contextx, "pos"),
                                                                                Vec3Argument.getVec3(contextx, "delta"),
                                                                                FloatArgumentType.getFloat(contextx, "speed"),
                                                                                IntegerArgumentType.getInteger(contextx, "count"),
                                                                                true,
                                                                                EntityArgument.getPlayers(contextx, "viewers")
                                                                            )
                                                                        )
                                                                )
                                                        )
                                                        .then(
                                                            Commands.literal("normal")
                                                                .executes(
                                                                    contextx -> sendParticles(
                                                                        (CommandSourceStack)contextx.getSource(),
                                                                        ParticleArgument.getParticle(contextx, "name"),
                                                                        Vec3Argument.getVec3(contextx, "pos"),
                                                                        Vec3Argument.getVec3(contextx, "delta"),
                                                                        FloatArgumentType.getFloat(contextx, "speed"),
                                                                        IntegerArgumentType.getInteger(contextx, "count"),
                                                                        false,
                                                                        ((CommandSourceStack)contextx.getSource()).getServer().getPlayerList().getPlayers()
                                                                    )
                                                                )
                                                                .then(
                                                                    Commands.argument("viewers", EntityArgument.players())
                                                                        .executes(
                                                                            contextx -> sendParticles(
                                                                                (CommandSourceStack)contextx.getSource(),
                                                                                ParticleArgument.getParticle(contextx, "name"),
                                                                                Vec3Argument.getVec3(contextx, "pos"),
                                                                                Vec3Argument.getVec3(contextx, "delta"),
                                                                                FloatArgumentType.getFloat(contextx, "speed"),
                                                                                IntegerArgumentType.getInteger(contextx, "count"),
                                                                                false,
                                                                                EntityArgument.getPlayers(contextx, "viewers")
                                                                            )
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

    private static int sendParticles(
        CommandSourceStack source, ParticleOptions particleData, Vec3 pos, Vec3 delta, float speed, int count, boolean force, Collection<ServerPlayer> viewers
    ) throws CommandSyntaxException {
        int i = 0;

        for (ServerPlayer serverPlayer : viewers) {
            if (source.getLevel().sendParticles(serverPlayer, particleData, force, pos.x, pos.y, pos.z, count, delta.x, delta.y, delta.z, speed)) {
                i++;
            }
        }

        if (i == 0) {
            throw ERROR_FAILED.create();
        }

        source.sendSuccess(
            () -> Component.translatable("commands.particle.success", BuiltInRegistries.PARTICLE_TYPE.getKey(particleData.getType()).toString()), true
        );
        return i;
    }
}
