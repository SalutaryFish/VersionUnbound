package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.state.BlockState;

public class WorkAtComposter extends WorkAtPoi {
    private static final List<Item> COMPOSTABLE_ITEMS = ImmutableList.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS);

    @Override
    protected void useWorkstation(ServerLevel level, Villager villager) {
        Optional<GlobalPos> optional = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
        if (!optional.isEmpty()) {
            GlobalPos globalPos = optional.get();
            BlockState blockState = level.getBlockState(globalPos.pos());
            if (blockState.is(Blocks.COMPOSTER)) {
                this.makeBread(villager);
                this.compostItems(level, villager, globalPos, blockState);
            }
        }
    }

    private void compostItems(ServerLevel level, Villager villager, GlobalPos global, BlockState state) {
        BlockPos blockPos = global.pos();
        if (state.getValue(ComposterBlock.LEVEL) == 8) {
            state = ComposterBlock.extractProduce(villager, state, level, blockPos);
        }

        int i = 20;
        int j = 10;
        int[] is = new int[COMPOSTABLE_ITEMS.size()];
        SimpleContainer simpleContainer = villager.getInventory();
        int k = simpleContainer.getContainerSize();
        BlockState blockState = state;

        for (int l = k - 1; l >= 0 && i > 0; l--) {
            ItemStack itemStack = simpleContainer.getItem(l);
            int m = COMPOSTABLE_ITEMS.indexOf(itemStack.getItem());
            if (m != -1) {
                int n = itemStack.getCount();
                int o = is[m] + n;
                is[m] = o;
                int p = Math.min(Math.min(o - 10, i), n);
                if (p > 0) {
                    i -= p;

                    for (int q = 0; q < p; q++) {
                        blockState = ComposterBlock.insertItem(villager, blockState, level, itemStack, blockPos);
                        if (blockState.getValue(ComposterBlock.LEVEL) == 7) {
                            this.spawnComposterFillEffects(level, state, blockPos, blockState);
                            return;
                        }
                    }
                }
            }
        }

        this.spawnComposterFillEffects(level, state, blockPos, blockState);
    }

    private void spawnComposterFillEffects(ServerLevel level, BlockState preState, BlockPos pos, BlockState postState) {
        level.levelEvent(1500, pos, postState != preState ? 1 : 0);
    }

    private void makeBread(Villager villager) {
        SimpleContainer simpleContainer = villager.getInventory();
        if (simpleContainer.countItem(Items.BREAD) <= 36) {
            int i = simpleContainer.countItem(Items.WHEAT);
            int j = 3;
            int k = 3;
            int l = Math.min(3, i / 3);
            if (l != 0) {
                int m = l * 3;
                simpleContainer.removeItemType(Items.WHEAT, m);
                ItemStack itemStack = simpleContainer.addItem(new ItemStack(Items.BREAD, l));
                if (!itemStack.isEmpty()) {
                    villager.spawnAtLocation(itemStack, 0.5F);
                }
            }
        }
    }
}
