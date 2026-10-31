package net.minecraft.world.level.levelgen;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

public class PatrolSpawner implements CustomSpawner {
    private int nextTick;

    @Override
    public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {
        if (!spawnEnemies) {
            return 0;
        }

        if (!level.getGameRules().getBoolean(GameRules.RULE_DO_PATROL_SPAWNING)) {
            return 0;
        }

        RandomSource randomSource = level.random;
        this.nextTick--;
        if (this.nextTick > 0) {
            return 0;
        }

        this.nextTick = this.nextTick + 12000 + randomSource.nextInt(1200);
        long l = level.getDayTime() / 24000L;
        if (l < 5L || !level.isDay()) {
            return 0;
        }

        if (randomSource.nextInt(5) != 0) {
            return 0;
        }

        int i = level.players().size();
        if (i < 1) {
            return 0;
        }

        Player player = level.players().get(randomSource.nextInt(i));
        if (player.isSpectator()) {
            return 0;
        }

        if (level.isCloseToVillage(player.blockPosition(), 2)) {
            return 0;
        }

        int j = (24 + randomSource.nextInt(24)) * (randomSource.nextBoolean() ? -1 : 1);
        int k = (24 + randomSource.nextInt(24)) * (randomSource.nextBoolean() ? -1 : 1);
        BlockPos.MutableBlockPos mutableBlockPos = player.blockPosition().mutable().move(j, 0, k);
        int m = 10;
        if (!level.hasChunksAt(mutableBlockPos.getX() - 10, mutableBlockPos.getZ() - 10, mutableBlockPos.getX() + 10, mutableBlockPos.getZ() + 10)) {
            return 0;
        }

        Holder<Biome> holder = level.getBiome(mutableBlockPos);
        if (holder.is(BiomeTags.WITHOUT_PATROL_SPAWNS)) {
            return 0;
        }

        int n = 0;
        int o = (int)Math.ceil(level.getCurrentDifficultyAt(mutableBlockPos).getEffectiveDifficulty()) + 1;

        for (int p = 0; p < o; p++) {
            n++;
            mutableBlockPos.setY(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, mutableBlockPos).getY());
            if (p == 0) {
                if (!this.spawnPatrolMember(level, mutableBlockPos, randomSource, true)) {
                    break;
                }
            } else {
                this.spawnPatrolMember(level, mutableBlockPos, randomSource, false);
            }

            mutableBlockPos.setX(mutableBlockPos.getX() + randomSource.nextInt(5) - randomSource.nextInt(5));
            mutableBlockPos.setZ(mutableBlockPos.getZ() + randomSource.nextInt(5) - randomSource.nextInt(5));
        }

        return n;
    }

    private boolean spawnPatrolMember(ServerLevel level, BlockPos pos, RandomSource random, boolean leader) {
        BlockState blockState = level.getBlockState(pos);
        if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, blockState, blockState.getFluidState(), EntityType.PILLAGER)) {
            return false;
        }

        if (!PatrollingMonster.checkPatrollingMonsterSpawnRules(EntityType.PILLAGER, level, MobSpawnType.PATROL, pos, random)) {
            return false;
        }

        PatrollingMonster patrollingMonster = EntityType.PILLAGER.create(level);
        if (patrollingMonster != null) {
            if (leader) {
                patrollingMonster.setPatrolLeader(true);
                patrollingMonster.findPatrolTarget();
            }

            patrollingMonster.setPos(pos.getX(), pos.getY(), pos.getZ());
            patrollingMonster.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.PATROL, null);
            level.addFreshEntityWithPassengers(patrollingMonster);
            return true;
        } else {
            return false;
        }
    }
}
