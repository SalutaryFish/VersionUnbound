package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

public class CelebrateVillagersSurvivedRaid extends Behavior<Villager> {
    @Nullable
    private Raid currentRaid;

    public CelebrateVillagersSurvivedRaid(int minDuration, int maxDuration) {
        super(ImmutableMap.of(), minDuration, maxDuration);
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        BlockPos blockPos = owner.blockPosition();
        this.currentRaid = level.getRaidAt(blockPos);
        return this.currentRaid != null && this.currentRaid.isVictory() && MoveToSkySeeingSpot.hasNoBlocksAbove(level, owner, blockPos);
    }

    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.currentRaid != null && !this.currentRaid.isStopped();
    }

    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        this.currentRaid = null;
        entity.getBrain().updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
    }

    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        RandomSource randomSource = owner.getRandom();
        if (randomSource.nextInt(100) == 0) {
            owner.playCelebrateSound();
        }

        if (randomSource.nextInt(200) == 0 && MoveToSkySeeingSpot.hasNoBlocksAbove(level, owner, owner.blockPosition())) {
            DyeColor dyeColor = Util.getRandom(DyeColor.values(), randomSource);
            int i = randomSource.nextInt(3);
            ItemStack itemStack = this.getFirework(dyeColor, i);
            FireworkRocketEntity fireworkRocketEntity = new FireworkRocketEntity(owner.level(), owner, owner.getX(), owner.getEyeY(), owner.getZ(), itemStack);
            owner.level().addFreshEntity(fireworkRocketEntity);
        }
    }

    private ItemStack getFirework(DyeColor color, int flightTime) {
        ItemStack itemStack = new ItemStack(Items.FIREWORK_ROCKET);
        itemStack.set(
            DataComponents.FIREWORKS,
            new Fireworks(
                (byte)flightTime,
                List.of(new FireworkExplosion(FireworkExplosion.Shape.BURST, IntList.of(color.getFireworkColor()), IntList.of(), false, false))
            )
        );
        return itemStack;
    }
}
