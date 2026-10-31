package net.minecraft.core.cauldron;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public interface CauldronInteraction {
    Map<String, CauldronInteraction.InteractionMap> INTERACTIONS = new Object2ObjectArrayMap<>();
    Codec<CauldronInteraction.InteractionMap> CODEC = Codec.stringResolver(CauldronInteraction.InteractionMap::name, INTERACTIONS::get);
    CauldronInteraction.InteractionMap EMPTY = newInteractionMap("empty");
    CauldronInteraction.InteractionMap WATER = newInteractionMap("water");
    CauldronInteraction.InteractionMap LAVA = newInteractionMap("lava");
    CauldronInteraction.InteractionMap POWDER_SNOW = newInteractionMap("powder_snow");
    CauldronInteraction FILL_WATER = (state, level, pos, player, hand, stack) -> emptyBucket(
        level, pos, player, hand, stack, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3), SoundEvents.BUCKET_EMPTY
    );
    CauldronInteraction FILL_LAVA = (state, level, pos, player, hand, stack) -> emptyBucket(
        level, pos, player, hand, stack, Blocks.LAVA_CAULDRON.defaultBlockState(), SoundEvents.BUCKET_EMPTY_LAVA
    );
    CauldronInteraction FILL_POWDER_SNOW = (state, level, pos, player, hand, stack) -> emptyBucket(
        level,
        pos,
        player,
        hand,
        stack,
        Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3),
        SoundEvents.BUCKET_EMPTY_POWDER_SNOW
    );
    CauldronInteraction SHULKER_BOX = (state, level, pos, player, hand, stack) -> {
        Block block = Block.byItem(stack.getItem());
        if (!(block instanceof ShulkerBoxBlock)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            ItemStack itemStack = stack.transmuteCopy(Blocks.SHULKER_BOX, 1);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, itemStack, false));
            player.awardStat(Stats.CLEAN_SHULKER_BOX);
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    };
    CauldronInteraction BANNER = (state, level, pos, player, hand, stack) -> {
        BannerPatternLayers bannerPatternLayers = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        if (bannerPatternLayers.layers().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            ItemStack itemStack = stack.copyWithCount(1);
            itemStack.set(DataComponents.BANNER_PATTERNS, bannerPatternLayers.removeLast());
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, itemStack, false));
            player.awardStat(Stats.CLEAN_BANNER);
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    };
    CauldronInteraction DYED_ITEM = (state, level, pos, player, hand, stack) -> {
        if (!stack.is(ItemTags.DYEABLE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!stack.has(DataComponents.DYED_COLOR)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            stack.remove(DataComponents.DYED_COLOR);
            player.awardStat(Stats.CLEAN_ARMOR);
            LayeredCauldronBlock.lowerFillLevel(state, level, pos);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    };

    static CauldronInteraction.InteractionMap newInteractionMap(String name) {
        Object2ObjectOpenHashMap<Item, CauldronInteraction> object2ObjectOpenHashMap = new Object2ObjectOpenHashMap<>();
        object2ObjectOpenHashMap.defaultReturnValue((state, level, pos, player, hand, stack) -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION);
        CauldronInteraction.InteractionMap interactionMap = new CauldronInteraction.InteractionMap(name, object2ObjectOpenHashMap);
        INTERACTIONS.put(name, interactionMap);
        return interactionMap;
    }

    ItemInteractionResult interact(BlockState var1, Level var2, BlockPos var3, Player var4, InteractionHand var5, ItemStack var6);

    static void bootStrap() {
        Map<Item, CauldronInteraction> map = EMPTY.map();
        addDefaultInteractions(map);
        map.put(Items.POTION, (state, level, pos, player, hand, stack) -> {
            PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
            if (potionContents != null && potionContents.is(Potions.WATER)) {
                if (!level.isClientSide) {
                    Item item = stack.getItem();
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    player.awardStat(Stats.USE_CAULDRON);
                    player.awardStat(Stats.ITEM_USED.get(item));
                    level.setBlockAndUpdate(pos, Blocks.WATER_CAULDRON.defaultBlockState());
                    level.playSound((Player)null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                }

                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            } else {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
        });
        Map<Item, CauldronInteraction> map2 = WATER.map();
        addDefaultInteractions(map2);
        map2.put(
            Items.BUCKET,
            (state, level, pos, player, hand, stack) -> fillBucket(
                state,
                level,
                pos,
                player,
                hand,
                stack,
                new ItemStack(Items.WATER_BUCKET),
                blockState -> blockState.getValue(LayeredCauldronBlock.LEVEL) == 3,
                SoundEvents.BUCKET_FILL
            )
        );
        map2.put(Items.GLASS_BOTTLE, (state, level, pos, player, hand, stack) -> {
            if (!level.isClientSide) {
                Item item = stack.getItem();
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, PotionContents.createItemStack(Items.POTION, Potions.WATER)));
                player.awardStat(Stats.USE_CAULDRON);
                player.awardStat(Stats.ITEM_USED.get(item));
                LayeredCauldronBlock.lowerFillLevel(state, level, pos);
                level.playSound((Player)null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
            }

            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        });
        map2.put(Items.POTION, (state, level, pos, player, hand, stack) -> {
            if (state.getValue(LayeredCauldronBlock.LEVEL) == 3) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }

            PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
            if (potionContents != null && potionContents.is(Potions.WATER)) {
                if (!level.isClientSide) {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.GLASS_BOTTLE)));
                    player.awardStat(Stats.USE_CAULDRON);
                    player.awardStat(Stats.ITEM_USED.get(stack.getItem()));
                    level.setBlockAndUpdate(pos, state.cycle(LayeredCauldronBlock.LEVEL));
                    level.playSound((Player)null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
                }

                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            } else {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
        });
        map2.put(Items.LEATHER_BOOTS, DYED_ITEM);
        map2.put(Items.LEATHER_LEGGINGS, DYED_ITEM);
        map2.put(Items.LEATHER_CHESTPLATE, DYED_ITEM);
        map2.put(Items.LEATHER_HELMET, DYED_ITEM);
        map2.put(Items.LEATHER_HORSE_ARMOR, DYED_ITEM);
        map2.put(Items.WOLF_ARMOR, DYED_ITEM);
        map2.put(Items.WHITE_BANNER, BANNER);
        map2.put(Items.GRAY_BANNER, BANNER);
        map2.put(Items.BLACK_BANNER, BANNER);
        map2.put(Items.BLUE_BANNER, BANNER);
        map2.put(Items.BROWN_BANNER, BANNER);
        map2.put(Items.CYAN_BANNER, BANNER);
        map2.put(Items.GREEN_BANNER, BANNER);
        map2.put(Items.LIGHT_BLUE_BANNER, BANNER);
        map2.put(Items.LIGHT_GRAY_BANNER, BANNER);
        map2.put(Items.LIME_BANNER, BANNER);
        map2.put(Items.MAGENTA_BANNER, BANNER);
        map2.put(Items.ORANGE_BANNER, BANNER);
        map2.put(Items.PINK_BANNER, BANNER);
        map2.put(Items.PURPLE_BANNER, BANNER);
        map2.put(Items.RED_BANNER, BANNER);
        map2.put(Items.YELLOW_BANNER, BANNER);
        map2.put(Items.WHITE_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.GRAY_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.BLACK_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.BLUE_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.BROWN_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.CYAN_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.GREEN_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.LIGHT_BLUE_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.LIGHT_GRAY_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.LIME_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.MAGENTA_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.ORANGE_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.PINK_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.PURPLE_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.RED_SHULKER_BOX, SHULKER_BOX);
        map2.put(Items.YELLOW_SHULKER_BOX, SHULKER_BOX);
        Map<Item, CauldronInteraction> map3 = LAVA.map();
        map3.put(
            Items.BUCKET,
            (state, level, pos, player, hand, stack) -> fillBucket(
                state, level, pos, player, hand, stack, new ItemStack(Items.LAVA_BUCKET), blockState -> true, SoundEvents.BUCKET_FILL_LAVA
            )
        );
        addDefaultInteractions(map3);
        Map<Item, CauldronInteraction> map4 = POWDER_SNOW.map();
        map4.put(
            Items.BUCKET,
            (state, level, pos, player, hand, stack) -> fillBucket(
                state,
                level,
                pos,
                player,
                hand,
                stack,
                new ItemStack(Items.POWDER_SNOW_BUCKET),
                blockState -> blockState.getValue(LayeredCauldronBlock.LEVEL) == 3,
                SoundEvents.BUCKET_FILL_POWDER_SNOW
            )
        );
        addDefaultInteractions(map4);
    }

    static void addDefaultInteractions(Map<Item, CauldronInteraction> interactionsMap) {
        interactionsMap.put(Items.LAVA_BUCKET, FILL_LAVA);
        interactionsMap.put(Items.WATER_BUCKET, FILL_WATER);
        interactionsMap.put(Items.POWDER_SNOW_BUCKET, FILL_POWDER_SNOW);
    }

    static ItemInteractionResult fillBucket(
        BlockState state,
        Level level,
        BlockPos pos,
        Player player,
        InteractionHand hand,
        ItemStack emptyStack,
        ItemStack filledStack,
        Predicate<BlockState> statePredicate,
        SoundEvent fillSound
    ) {
        if (!statePredicate.test(state)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            Item item = emptyStack.getItem();
            player.setItemInHand(hand, ItemUtils.createFilledResult(emptyStack, player, filledStack));
            player.awardStat(Stats.USE_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(item));
            level.setBlockAndUpdate(pos, Blocks.CAULDRON.defaultBlockState());
            level.playSound((Player)null, pos, fillSound, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(null, GameEvent.FLUID_PICKUP, pos);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    static ItemInteractionResult emptyBucket(
        Level level, BlockPos pos, Player player, InteractionHand hand, ItemStack filledStack, BlockState state, SoundEvent emptySound
    ) {
        if (!level.isClientSide) {
            Item item = filledStack.getItem();
            player.setItemInHand(hand, ItemUtils.createFilledResult(filledStack, player, new ItemStack(Items.BUCKET)));
            player.awardStat(Stats.FILL_CAULDRON);
            player.awardStat(Stats.ITEM_USED.get(item));
            level.setBlockAndUpdate(pos, state);
            level.playSound((Player)null, pos, emptySound, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(null, GameEvent.FLUID_PLACE, pos);
        }

        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    record InteractionMap(String name, Map<Item, CauldronInteraction> map) {
    }
}
