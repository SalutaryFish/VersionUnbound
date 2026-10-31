package net.minecraft.world.entity.ai.behavior;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class GiveGiftToHero extends Behavior<Villager> {
    private static final int THROW_GIFT_AT_DISTANCE = 5;
    private static final int MIN_TIME_BETWEEN_GIFTS = 600;
    private static final int MAX_TIME_BETWEEN_GIFTS = 6600;
    private static final int TIME_TO_DELAY_FOR_HEAD_TO_FINISH_TURNING = 20;
    private static final Map<VillagerProfession, ResourceKey<LootTable>> GIFTS = Util.make(Maps.newHashMap(), map -> {
        map.put(VillagerProfession.ARMORER, BuiltInLootTables.ARMORER_GIFT);
        map.put(VillagerProfession.BUTCHER, BuiltInLootTables.BUTCHER_GIFT);
        map.put(VillagerProfession.CARTOGRAPHER, BuiltInLootTables.CARTOGRAPHER_GIFT);
        map.put(VillagerProfession.CLERIC, BuiltInLootTables.CLERIC_GIFT);
        map.put(VillagerProfession.FARMER, BuiltInLootTables.FARMER_GIFT);
        map.put(VillagerProfession.FISHERMAN, BuiltInLootTables.FISHERMAN_GIFT);
        map.put(VillagerProfession.FLETCHER, BuiltInLootTables.FLETCHER_GIFT);
        map.put(VillagerProfession.LEATHERWORKER, BuiltInLootTables.LEATHERWORKER_GIFT);
        map.put(VillagerProfession.LIBRARIAN, BuiltInLootTables.LIBRARIAN_GIFT);
        map.put(VillagerProfession.MASON, BuiltInLootTables.MASON_GIFT);
        map.put(VillagerProfession.SHEPHERD, BuiltInLootTables.SHEPHERD_GIFT);
        map.put(VillagerProfession.TOOLSMITH, BuiltInLootTables.TOOLSMITH_GIFT);
        map.put(VillagerProfession.WEAPONSMITH, BuiltInLootTables.WEAPONSMITH_GIFT);
    });
    private static final float SPEED_MODIFIER = 0.5F;
    private int timeUntilNextGift = 600;
    private boolean giftGivenDuringThisRun;
    private long timeSinceStart;

    public GiveGiftToHero(int duration) {
        super(
            ImmutableMap.of(
                MemoryModuleType.WALK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.INTERACTION_TARGET,
                MemoryStatus.REGISTERED,
                MemoryModuleType.NEAREST_VISIBLE_PLAYER,
                MemoryStatus.VALUE_PRESENT
            ),
            duration
        );
    }

    protected boolean checkExtraStartConditions(ServerLevel level, Villager owner) {
        if (!this.isHeroVisible(owner)) {
            return false;
        } else if (this.timeUntilNextGift > 0) {
            this.timeUntilNextGift--;
            return false;
        } else {
            return true;
        }
    }

    protected void start(ServerLevel level, Villager entity, long gameTime) {
        this.giftGivenDuringThisRun = false;
        this.timeSinceStart = gameTime;
        Player player = this.getNearestTargetableHero(entity).get();
        entity.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, player);
        BehaviorUtils.lookAtEntity(entity, player);
    }

    protected boolean canStillUse(ServerLevel level, Villager entity, long gameTime) {
        return this.isHeroVisible(entity) && !this.giftGivenDuringThisRun;
    }

    protected void tick(ServerLevel level, Villager owner, long gameTime) {
        Player player = this.getNearestTargetableHero(owner).get();
        BehaviorUtils.lookAtEntity(owner, player);
        if (this.isWithinThrowingDistance(owner, player)) {
            if (gameTime - this.timeSinceStart > 20L) {
                this.throwGift(owner, player);
                this.giftGivenDuringThisRun = true;
            }
        } else {
            BehaviorUtils.setWalkAndLookTargetMemories(owner, player, 0.5F, 5);
        }
    }

    protected void stop(ServerLevel level, Villager entity, long gameTime) {
        this.timeUntilNextGift = calculateTimeUntilNextGift(level);
        entity.getBrain().eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    private void throwGift(Villager villager, LivingEntity hero) {
        for (ItemStack itemStack : this.getItemToThrow(villager)) {
            BehaviorUtils.throwItem(villager, itemStack, hero.position());
        }
    }

    private List<ItemStack> getItemToThrow(Villager villager) {
        if (villager.isBaby()) {
            return ImmutableList.of(new ItemStack(Items.POPPY));
        } else {
            VillagerProfession villagerProfession = villager.getVillagerData().getProfession();
            if (GIFTS.containsKey(villagerProfession)) {
                LootTable lootTable = villager.level().getServer().reloadableRegistries().getLootTable(GIFTS.get(villagerProfession));
                LootParams lootParams = new LootParams.Builder((ServerLevel)villager.level())
                    .withParameter(LootContextParams.ORIGIN, villager.position())
                    .withParameter(LootContextParams.THIS_ENTITY, villager)
                    .create(LootContextParamSets.GIFT);
                return lootTable.getRandomItems(lootParams);
            } else {
                return ImmutableList.of(new ItemStack(Items.WHEAT_SEEDS));
            }
        }
    }

    private boolean isHeroVisible(Villager villager) {
        return this.getNearestTargetableHero(villager).isPresent();
    }

    private Optional<Player> getNearestTargetableHero(Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_PLAYER).filter(this::isHero);
    }

    private boolean isHero(Player player) {
        return player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE);
    }

    private boolean isWithinThrowingDistance(Villager villager, Player hero) {
        BlockPos blockPos = hero.blockPosition();
        BlockPos blockPos2 = villager.blockPosition();
        return blockPos2.closerThan(blockPos, 5.0);
    }

    private static int calculateTimeUntilNextGift(ServerLevel level) {
        return 600 + level.random.nextInt(6001);
    }
}
