package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record SummonEntityEffect(HolderSet<EntityType<?>> entityTypes, boolean joinTeam) implements EnchantmentEntityEffect {
    public static final MapCodec<SummonEntityEffect> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
                RegistryCodecs.homogeneousList(Registries.ENTITY_TYPE).fieldOf("entity").forGetter(SummonEntityEffect::entityTypes),
                Codec.BOOL.optionalFieldOf("join_team", false).forGetter(SummonEntityEffect::joinTeam)
            )
            .apply(instance, SummonEntityEffect::new)
    );

    @Override
    public void apply(ServerLevel level, int enchantmentLevel, EnchantedItemInUse item, Entity entity, Vec3 origin) {
        BlockPos blockPos = BlockPos.containing(origin);
        if (Level.isInSpawnableBounds(blockPos)) {
            Optional<Holder<EntityType<?>>> optional = this.entityTypes().getRandomElement(level.getRandom());
            if (!optional.isEmpty()) {
                Entity entity2 = optional.get().value().spawn(level, blockPos, MobSpawnType.TRIGGERED);
                if (entity2 != null) {
                    if (entity2 instanceof LightningBolt lightningBolt && item.owner() instanceof ServerPlayer serverPlayer) {
                        lightningBolt.setCause(serverPlayer);
                    }

                    if (this.joinTeam && entity.getTeam() != null) {
                        level.getScoreboard().addPlayerToTeam(entity2.getScoreboardName(), entity.getTeam());
                    }

                    entity2.moveTo(origin.x, origin.y, origin.z, entity2.getYRot(), entity2.getXRot());
                }
            }
        }
    }

    @Override
    public MapCodec<SummonEntityEffect> codec() {
        return CODEC;
    }
}
