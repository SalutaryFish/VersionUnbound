package net.minecraft.world.item;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

public class ChorusFruitItem extends Item {
    public ChorusFruitItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * Called when the player finishes using this Item (E.g. finishes eating.). Not called when the player stops using the Item before the action is complete.
     */
    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entityLiving) {
        ItemStack itemStack = super.finishUsingItem(stack, level, entityLiving);
        if (!level.isClientSide) {
            for (int i = 0; i < 16; i++) {
                double d = entityLiving.getX() + (entityLiving.getRandom().nextDouble() - 0.5) * 16.0;
                double e = Mth.clamp(
                    entityLiving.getY() + (entityLiving.getRandom().nextInt(16) - 8),
                    level.getMinBuildHeight(),
                    level.getMinBuildHeight() + ((ServerLevel)level).getLogicalHeight() - 1
                );
                double f = entityLiving.getZ() + (entityLiving.getRandom().nextDouble() - 0.5) * 16.0;
                if (entityLiving.isPassenger()) {
                    entityLiving.stopRiding();
                }

                Vec3 vec3 = entityLiving.position();
                if (entityLiving.randomTeleport(d, e, f, true)) {
                    level.gameEvent(GameEvent.TELEPORT, vec3, GameEvent.Context.of(entityLiving));
                    SoundSource soundSource;
                    SoundEvent soundEvent;
                    if (entityLiving instanceof Fox) {
                        soundEvent = SoundEvents.FOX_TELEPORT;
                        soundSource = SoundSource.NEUTRAL;
                    } else {
                        soundEvent = SoundEvents.CHORUS_FRUIT_TELEPORT;
                        soundSource = SoundSource.PLAYERS;
                    }

                    level.playSound(null, entityLiving.getX(), entityLiving.getY(), entityLiving.getZ(), soundEvent, soundSource);
                    entityLiving.resetFallDistance();
                    break;
                }
            }

            if (entityLiving instanceof Player player) {
                player.resetCurrentImpulseContext();
                player.getCooldowns().addCooldown(this, 20);
            }
        }

        return itemStack;
    }
}
