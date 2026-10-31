package net.minecraft.server.commands;

import com.google.common.collect.Lists;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceOrIdArgument;
import net.minecraft.commands.arguments.SlotArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.ReloadableServerRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public class LootCommand {
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_LOOT_TABLE = (context, builder) -> {
        ReloadableServerRegistries.Holder holder = context.getSource().getServer().reloadableRegistries();
        return SharedSuggestionProvider.suggestResource(holder.getKeys(Registries.LOOT_TABLE), builder);
    };
    private static final DynamicCommandExceptionType ERROR_NO_HELD_ITEMS = new DynamicCommandExceptionType(
        target -> Component.translatableEscape("commands.drop.no_held_items", target)
    );
    private static final DynamicCommandExceptionType ERROR_NO_LOOT_TABLE = new DynamicCommandExceptionType(
        target -> Component.translatableEscape("commands.drop.no_loot_table", target)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(
            addTargets(
                Commands.literal("loot").requires(source -> source.hasPermission(2)),
                (argumentBuilder, dropConsumer) -> argumentBuilder.then(
                        Commands.literal("fish")
                            .then(
                                Commands.argument("loot_table", ResourceOrIdArgument.lootTable(context))
                                    .suggests(SUGGEST_LOOT_TABLE)
                                    .then(
                                        Commands.argument("pos", BlockPosArgument.blockPos())
                                            .executes(
                                                contextx -> dropFishingLoot(
                                                    contextx,
                                                    ResourceOrIdArgument.getLootTable(contextx, "loot_table"),
                                                    BlockPosArgument.getLoadedBlockPos(contextx, "pos"),
                                                    ItemStack.EMPTY,
                                                    dropConsumer
                                                )
                                            )
                                            .then(
                                                Commands.argument("tool", ItemArgument.item(context))
                                                    .executes(
                                                        contextx -> dropFishingLoot(
                                                            contextx,
                                                            ResourceOrIdArgument.getLootTable(contextx, "loot_table"),
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "pos"),
                                                            ItemArgument.getItem(contextx, "tool").createItemStack(1, false),
                                                            dropConsumer
                                                        )
                                                    )
                                            )
                                            .then(
                                                Commands.literal("mainhand")
                                                    .executes(
                                                        contextx -> dropFishingLoot(
                                                            contextx,
                                                            ResourceOrIdArgument.getLootTable(contextx, "loot_table"),
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "pos"),
                                                            getSourceHandItem((CommandSourceStack)contextx.getSource(), EquipmentSlot.MAINHAND),
                                                            dropConsumer
                                                        )
                                                    )
                                            )
                                            .then(
                                                Commands.literal("offhand")
                                                    .executes(
                                                        contextx -> dropFishingLoot(
                                                            contextx,
                                                            ResourceOrIdArgument.getLootTable(contextx, "loot_table"),
                                                            BlockPosArgument.getLoadedBlockPos(contextx, "pos"),
                                                            getSourceHandItem((CommandSourceStack)contextx.getSource(), EquipmentSlot.OFFHAND),
                                                            dropConsumer
                                                        )
                                                    )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("loot")
                            .then(
                                Commands.argument("loot_table", ResourceOrIdArgument.lootTable(context))
                                    .suggests(SUGGEST_LOOT_TABLE)
                                    .executes(contextx -> dropChestLoot(contextx, ResourceOrIdArgument.getLootTable(contextx, "loot_table"), dropConsumer))
                            )
                    )
                    .then(
                        Commands.literal("kill")
                            .then(
                                Commands.argument("target", EntityArgument.entity())
                                    .executes(contextx -> dropKillLoot(contextx, EntityArgument.getEntity(contextx, "target"), dropConsumer))
                            )
                    )
                    .then(
                        Commands.literal("mine")
                            .then(
                                Commands.argument("pos", BlockPosArgument.blockPos())
                                    .executes(
                                        contextx -> dropBlockLoot(contextx, BlockPosArgument.getLoadedBlockPos(contextx, "pos"), ItemStack.EMPTY, dropConsumer)
                                    )
                                    .then(
                                        Commands.argument("tool", ItemArgument.item(context))
                                            .executes(
                                                contextx -> dropBlockLoot(
                                                    contextx,
                                                    BlockPosArgument.getLoadedBlockPos(contextx, "pos"),
                                                    ItemArgument.getItem(contextx, "tool").createItemStack(1, false),
                                                    dropConsumer
                                                )
                                            )
                                    )
                                    .then(
                                        Commands.literal("mainhand")
                                            .executes(
                                                contextx -> dropBlockLoot(
                                                    contextx,
                                                    BlockPosArgument.getLoadedBlockPos(contextx, "pos"),
                                                    getSourceHandItem((CommandSourceStack)contextx.getSource(), EquipmentSlot.MAINHAND),
                                                    dropConsumer
                                                )
                                            )
                                    )
                                    .then(
                                        Commands.literal("offhand")
                                            .executes(
                                                contextx -> dropBlockLoot(
                                                    contextx,
                                                    BlockPosArgument.getLoadedBlockPos(contextx, "pos"),
                                                    getSourceHandItem((CommandSourceStack)contextx.getSource(), EquipmentSlot.OFFHAND),
                                                    dropConsumer
                                                )
                                            )
                                    )
                            )
                    )
            )
        );
    }

    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T addTargets(T builder, LootCommand.TailProvider tailProvider) {
        return builder.then(
                Commands.literal("replace")
                    .then(
                        Commands.literal("entity")
                            .then(
                                Commands.argument("entities", EntityArgument.entities())
                                    .then(
                                        tailProvider.construct(
                                                Commands.argument("slot", SlotArgument.slot()),
                                                (context, items, callback) -> entityReplace(
                                                    EntityArgument.getEntities(context, "entities"),
                                                    SlotArgument.getSlot(context, "slot"),
                                                    items.size(),
                                                    items,
                                                    callback
                                                )
                                            )
                                            .then(
                                                tailProvider.construct(
                                                    Commands.argument("count", IntegerArgumentType.integer(0)),
                                                    (context, items, callback) -> entityReplace(
                                                        EntityArgument.getEntities(context, "entities"),
                                                        SlotArgument.getSlot(context, "slot"),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        items,
                                                        callback
                                                    )
                                                )
                                            )
                                    )
                            )
                    )
                    .then(
                        Commands.literal("block")
                            .then(
                                Commands.argument("targetPos", BlockPosArgument.blockPos())
                                    .then(
                                        tailProvider.construct(
                                                Commands.argument("slot", SlotArgument.slot()),
                                                (context, items, callback) -> blockReplace(
                                                    context.getSource(),
                                                    BlockPosArgument.getLoadedBlockPos(context, "targetPos"),
                                                    SlotArgument.getSlot(context, "slot"),
                                                    items.size(),
                                                    items,
                                                    callback
                                                )
                                            )
                                            .then(
                                                tailProvider.construct(
                                                    Commands.argument("count", IntegerArgumentType.integer(0)),
                                                    (context, items, callback) -> blockReplace(
                                                        context.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "targetPos"),
                                                        IntegerArgumentType.getInteger(context, "slot"),
                                                        IntegerArgumentType.getInteger(context, "count"),
                                                        items,
                                                        callback
                                                    )
                                                )
                                            )
                                    )
                            )
                    )
            )
            .then(
                Commands.literal("insert")
                    .then(
                        tailProvider.construct(
                            Commands.argument("targetPos", BlockPosArgument.blockPos()),
                            (context, items, callback) -> blockDistribute(
                                context.getSource(), BlockPosArgument.getLoadedBlockPos(context, "targetPos"), items, callback
                            )
                        )
                    )
            )
            .then(
                Commands.literal("give")
                    .then(
                        tailProvider.construct(
                            Commands.argument("players", EntityArgument.players()),
                            (context, items, callback) -> playerGive(EntityArgument.getPlayers(context, "players"), items, callback)
                        )
                    )
            )
            .then(
                Commands.literal("spawn")
                    .then(
                        tailProvider.construct(
                            Commands.argument("targetPos", Vec3Argument.vec3()),
                            (context, items, callback) -> dropInWorld(context.getSource(), Vec3Argument.getVec3(context, "targetPos"), items, callback)
                        )
                    )
            );
    }

    private static Container getContainer(CommandSourceStack source, BlockPos pos) throws CommandSyntaxException {
        BlockEntity blockEntity = source.getLevel().getBlockEntity(pos);
        if (!(blockEntity instanceof Container)) {
            throw ItemCommands.ERROR_TARGET_NOT_A_CONTAINER.create(pos.getX(), pos.getY(), pos.getZ());
        } else {
            return (Container)blockEntity;
        }
    }

    private static int blockDistribute(CommandSourceStack source, BlockPos pos, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        Container container = getContainer(source, pos);
        List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

        for (ItemStack itemStack : items) {
            if (distributeToContainer(container, itemStack.copy())) {
                container.setChanged();
                list.add(itemStack);
            }
        }

        callback.accept(list);
        return list.size();
    }

    private static boolean distributeToContainer(Container container, ItemStack item) {
        boolean bl = false;

        for (int i = 0; i < container.getContainerSize() && !item.isEmpty(); i++) {
            ItemStack itemStack = container.getItem(i);
            if (container.canPlaceItem(i, item)) {
                if (itemStack.isEmpty()) {
                    container.setItem(i, item);
                    bl = true;
                    break;
                }

                if (canMergeItems(itemStack, item)) {
                    int j = item.getMaxStackSize() - itemStack.getCount();
                    int k = Math.min(item.getCount(), j);
                    item.shrink(k);
                    itemStack.grow(k);
                    bl = true;
                }
            }
        }

        return bl;
    }

    private static int blockReplace(CommandSourceStack source, BlockPos pos, int slot, int numSlots, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        Container container = getContainer(source, pos);
        int i = container.getContainerSize();
        if (slot >= 0 && slot < i) {
            List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

            for (int j = 0; j < numSlots; j++) {
                int k = slot + j;
                ItemStack itemStack = j < items.size() ? items.get(j) : ItemStack.EMPTY;
                if (container.canPlaceItem(k, itemStack)) {
                    container.setItem(k, itemStack);
                    list.add(itemStack);
                }
            }

            callback.accept(list);
            return list.size();
        } else {
            throw ItemCommands.ERROR_TARGET_INAPPLICABLE_SLOT.create(slot);
        }
    }

    private static boolean canMergeItems(ItemStack first, ItemStack second) {
        return first.getCount() <= first.getMaxStackSize() && ItemStack.isSameItemSameComponents(first, second);
    }

    private static int playerGive(Collection<ServerPlayer> targets, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

        for (ItemStack itemStack : items) {
            for (ServerPlayer serverPlayer : targets) {
                if (serverPlayer.getInventory().add(itemStack.copy())) {
                    list.add(itemStack);
                }
            }
        }

        callback.accept(list);
        return list.size();
    }

    private static void setSlots(Entity target, List<ItemStack> items, int startSlot, int numSlots, List<ItemStack> setItems) {
        for (int i = 0; i < numSlots; i++) {
            ItemStack itemStack = i < items.size() ? items.get(i) : ItemStack.EMPTY;
            SlotAccess slotAccess = target.getSlot(startSlot + i);
            if (slotAccess != SlotAccess.NULL && slotAccess.set(itemStack.copy())) {
                setItems.add(itemStack);
            }
        }
    }

    private static int entityReplace(Collection<? extends Entity> targets, int startSlot, int numSlots, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        List<ItemStack> list = Lists.newArrayListWithCapacity(items.size());

        for (Entity entity : targets) {
            if (entity instanceof ServerPlayer serverPlayer) {
                setSlots(entity, items, startSlot, numSlots, list);
                serverPlayer.containerMenu.broadcastChanges();
            } else {
                setSlots(entity, items, startSlot, numSlots, list);
            }
        }

        callback.accept(list);
        return list.size();
    }

    private static int dropInWorld(CommandSourceStack source, Vec3 pos, List<ItemStack> items, LootCommand.Callback callback) throws CommandSyntaxException {
        ServerLevel serverLevel = source.getLevel();
        items.forEach(itemStack -> {
            ItemEntity itemEntity = new ItemEntity(serverLevel, pos.x, pos.y, pos.z, itemStack.copy());
            itemEntity.setDefaultPickUpDelay();
            serverLevel.addFreshEntity(itemEntity);
        });
        callback.accept(items);
        return items.size();
    }

    private static void callback(CommandSourceStack source, List<ItemStack> items) {
        if (items.size() == 1) {
            ItemStack itemStack = items.get(0);
            source.sendSuccess(() -> Component.translatable("commands.drop.success.single", itemStack.getCount(), itemStack.getDisplayName()), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.drop.success.multiple", items.size()), false);
        }
    }

    private static void callback(CommandSourceStack source, List<ItemStack> items, ResourceKey<LootTable> lootTable) {
        if (items.size() == 1) {
            ItemStack itemStack = items.get(0);
            source.sendSuccess(
                () -> Component.translatable(
                    "commands.drop.success.single_with_table", itemStack.getCount(), itemStack.getDisplayName(), Component.translationArg(lootTable.location())
                ),
                false
            );
        } else {
            source.sendSuccess(
                () -> Component.translatable("commands.drop.success.multiple_with_table", items.size(), Component.translationArg(lootTable.location())), false
            );
        }
    }

    private static ItemStack getSourceHandItem(CommandSourceStack source, EquipmentSlot slot) throws CommandSyntaxException {
        Entity entity = source.getEntityOrException();
        if (entity instanceof LivingEntity) {
            return ((LivingEntity)entity).getItemBySlot(slot);
        } else {
            throw ERROR_NO_HELD_ITEMS.create(entity.getDisplayName());
        }
    }

    private static int dropBlockLoot(CommandContext<CommandSourceStack> context, BlockPos pos, ItemStack tool, LootCommand.DropConsumer dropConsumer) throws CommandSyntaxException {
        CommandSourceStack commandSourceStack = context.getSource();
        ServerLevel serverLevel = commandSourceStack.getLevel();
        BlockState blockState = serverLevel.getBlockState(pos);
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        LootParams.Builder builder = new LootParams.Builder(serverLevel)
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.BLOCK_STATE, blockState)
            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, commandSourceStack.getEntity())
            .withParameter(LootContextParams.TOOL, tool);
        List<ItemStack> list = blockState.getDrops(builder);
        return dropConsumer.accept(context, list, itemsDropped -> callback(commandSourceStack, itemsDropped, blockState.getBlock().getLootTable()));
    }

    private static int dropKillLoot(CommandContext<CommandSourceStack> context, Entity entity, LootCommand.DropConsumer dropConsumer) throws CommandSyntaxException {
        if (!(entity instanceof LivingEntity)) {
            throw ERROR_NO_LOOT_TABLE.create(entity.getDisplayName());
        }

        ResourceKey<LootTable> resourceKey = ((LivingEntity)entity).getLootTable();
        CommandSourceStack commandSourceStack = context.getSource();
        LootParams.Builder builder = new LootParams.Builder(commandSourceStack.getLevel());
        Entity entity2 = commandSourceStack.getEntity();
        if (entity2 instanceof Player player) {
            builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player);
        }

        builder.withParameter(LootContextParams.DAMAGE_SOURCE, entity.damageSources().magic());
        builder.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, entity2);
        builder.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, entity2);
        builder.withParameter(LootContextParams.THIS_ENTITY, entity);
        builder.withParameter(LootContextParams.ORIGIN, commandSourceStack.getPosition());
        LootParams lootParams = builder.create(LootContextParamSets.ENTITY);
        LootTable lootTable = commandSourceStack.getServer().reloadableRegistries().getLootTable(resourceKey);
        List<ItemStack> list = lootTable.getRandomItems(lootParams);
        return dropConsumer.accept(context, list, listx -> callback(commandSourceStack, listx, resourceKey));
    }

    private static int dropChestLoot(CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, LootCommand.DropConsumer dropCOnsimer) throws CommandSyntaxException {
        CommandSourceStack commandSourceStack = context.getSource();
        LootParams lootParams = new LootParams.Builder(commandSourceStack.getLevel())
            .withOptionalParameter(LootContextParams.THIS_ENTITY, commandSourceStack.getEntity())
            .withParameter(LootContextParams.ORIGIN, commandSourceStack.getPosition())
            .create(LootContextParamSets.CHEST);
        return drop(context, lootTable, lootParams, dropCOnsimer);
    }

    private static int dropFishingLoot(
        CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, BlockPos pos, ItemStack tool, LootCommand.DropConsumer dropConsumet
    ) throws CommandSyntaxException {
        CommandSourceStack commandSourceStack = context.getSource();
        LootParams lootParams = new LootParams.Builder(commandSourceStack.getLevel())
            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
            .withParameter(LootContextParams.TOOL, tool)
            .withOptionalParameter(LootContextParams.THIS_ENTITY, commandSourceStack.getEntity())
            .create(LootContextParamSets.FISHING);
        return drop(context, lootTable, lootParams, dropConsumet);
    }

    private static int drop(CommandContext<CommandSourceStack> context, Holder<LootTable> lootTable, LootParams params, LootCommand.DropConsumer dropConsumer) throws CommandSyntaxException {
        CommandSourceStack commandSourceStack = context.getSource();
        List<ItemStack> list = lootTable.value().getRandomItems(params);
        return dropConsumer.accept(context, list, itemsDropped -> callback(commandSourceStack, itemsDropped));
    }

    @FunctionalInterface
    interface Callback {
        void accept(List<ItemStack> var1) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface DropConsumer {
        int accept(CommandContext<CommandSourceStack> var1, List<ItemStack> var2, LootCommand.Callback var3) throws CommandSyntaxException;
    }

    @FunctionalInterface
    interface TailProvider {
        ArgumentBuilder<CommandSourceStack, ?> construct(ArgumentBuilder<CommandSourceStack, ?> var1, LootCommand.DropConsumer var2);
    }
}
