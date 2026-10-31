package net.minecraft.server.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.datafixers.util.Either;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.apache.commons.lang3.mutable.MutableInt;

public class FillBiomeCommand {
    public static final SimpleCommandExceptionType ERROR_NOT_LOADED = new SimpleCommandExceptionType(Component.translatable("argument.pos.unloaded"));
    private static final Dynamic2CommandExceptionType ERROR_VOLUME_TOO_LARGE = new Dynamic2CommandExceptionType(
        (maxBlocks, specifiedBlocks) -> Component.translatableEscape("commands.fillbiome.toobig", maxBlocks, specifiedBlocks)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            Commands.literal("fillbiome")
                .requires(source -> source.hasPermission(2))
                .then(
                    Commands.argument("from", BlockPosArgument.blockPos())
                        .then(
                            Commands.argument("to", BlockPosArgument.blockPos())
                                .then(
                                    Commands.argument("biome", ResourceArgument.resource(context, Registries.BIOME))
                                        .executes(
                                            contextx -> fill(
                                                (CommandSourceStack)contextx.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(contextx, "from"),
                                                BlockPosArgument.getLoadedBlockPos(contextx, "to"),
                                                ResourceArgument.getResource(contextx, "biome", Registries.BIOME),
                                                biome -> true
                                            )
                                        )
                                        .then(
                                            Commands.literal("replace")
                                                .then(
                                                    Commands.argument("filter", ResourceOrTagArgument.resourceOrTag(context, Registries.BIOME))
                                                        .executes(
                                                            contextx -> fill(
                                                                (CommandSourceStack)contextx.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(contextx, "from"),
                                                                BlockPosArgument.getLoadedBlockPos(contextx, "to"),
                                                                ResourceArgument.getResource(contextx, "biome", Registries.BIOME),
                                                                ResourceOrTagArgument.getResourceOrTag(contextx, "filter", Registries.BIOME)::test
                                                            )
                                                        )
                                                )
                                        )
                                )
                        )
                )
        );
    }

    private static int quantize(int value) {
        return QuartPos.toBlock(QuartPos.fromBlock(value));
    }

    private static BlockPos quantize(BlockPos pos) {
        return new BlockPos(quantize(pos.getX()), quantize(pos.getY()), quantize(pos.getZ()));
    }

    private static BiomeResolver makeResolver(
        MutableInt biomeEntries, ChunkAccess chunk, BoundingBox targetRegion, Holder<Biome> replacementBiome, Predicate<Holder<Biome>> filter
    ) {
        return (x, y, z, sampler) -> {
            int i = QuartPos.toBlock(x);
            int j = QuartPos.toBlock(y);
            int k = QuartPos.toBlock(z);
            Holder<Biome> holder2 = chunk.getNoiseBiome(x, y, z);
            if (targetRegion.isInside(i, j, k) && filter.test(holder2)) {
                biomeEntries.increment();
                return replacementBiome;
            } else {
                return holder2;
            }
        };
    }

    public static Either<Integer, CommandSyntaxException> fill(ServerLevel level, BlockPos from, BlockPos to, Holder<Biome> biome) {
        return fill(level, from, to, biome, biomex -> true, message -> {});
    }

    public static Either<Integer, CommandSyntaxException> fill(
        ServerLevel level, BlockPos from, BlockPos to, Holder<Biome> biome, Predicate<Holder<Biome>> filter, Consumer<Supplier<Component>> messageOutput
    ) {
        BlockPos blockPos = quantize(from);
        BlockPos blockPos2 = quantize(to);
        BoundingBox boundingBox = BoundingBox.fromCorners(blockPos, blockPos2);
        int i = boundingBox.getXSpan() * boundingBox.getYSpan() * boundingBox.getZSpan();
        int j = level.getGameRules().getInt(GameRules.RULE_COMMAND_MODIFICATION_BLOCK_LIMIT);
        if (i > j) {
            return Either.right(ERROR_VOLUME_TOO_LARGE.create(j, i));
        }

        List<ChunkAccess> list = new ArrayList<>();

        for (int k = SectionPos.blockToSectionCoord(boundingBox.minZ()); k <= SectionPos.blockToSectionCoord(boundingBox.maxZ()); k++) {
            for (int l = SectionPos.blockToSectionCoord(boundingBox.minX()); l <= SectionPos.blockToSectionCoord(boundingBox.maxX()); l++) {
                ChunkAccess chunkAccess = level.getChunk(l, k, ChunkStatus.FULL, false);
                if (chunkAccess == null) {
                    return Either.right(ERROR_NOT_LOADED.create());
                }

                list.add(chunkAccess);
            }
        }

        MutableInt mutableInt = new MutableInt(0);

        for (ChunkAccess chunkAccess : list) {
            chunkAccess.fillBiomesFromNoise(makeResolver(mutableInt, chunkAccess, boundingBox, biome, filter), level.getChunkSource().randomState().sampler());
            chunkAccess.setUnsaved(true);
        }

        level.getChunkSource().chunkMap.resendBiomesForChunks(list);
        messageOutput.accept(
            () -> Component.translatable(
                "commands.fillbiome.success.count",
                mutableInt.getValue(),
                boundingBox.minX(),
                boundingBox.minY(),
                boundingBox.minZ(),
                boundingBox.maxX(),
                boundingBox.maxY(),
                boundingBox.maxZ()
            )
        );
        return Either.left(mutableInt.getValue());
    }

    private static int fill(CommandSourceStack source, BlockPos from, BlockPos to, Holder.Reference<Biome> biome, Predicate<Holder<Biome>> filter) throws CommandSyntaxException {
        Either<Integer, CommandSyntaxException> either = fill(source.getLevel(), from, to, biome, filter, supplier -> source.sendSuccess(supplier, true));
        Optional<CommandSyntaxException> optional = either.right();
        if (optional.isPresent()) {
            throw (CommandSyntaxException)optional.get();
        } else {
            return either.left().get();
        }
    }
}
