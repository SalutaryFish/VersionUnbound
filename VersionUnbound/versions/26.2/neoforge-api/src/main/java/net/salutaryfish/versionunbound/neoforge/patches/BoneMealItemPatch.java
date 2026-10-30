package net.salutaryfish.versionunbound.neoforge.patches;

import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * NeoForge's {@code BoneMealItem.applyBonemeal(ItemStack, Level, BlockPos, Player)}, added to {@code BoneMealItem} by
 * {@code AddedStaticMethods} (Mixin can't add a public static method). NeoForge moves vanilla's {@code growCrop} body
 * into it, behind {@code BonemealEvent}, and makes {@code growCrop} call it without a player (on the server; on the
 * client {@code growCrop} answers false). Here {@code growCrop} keeps its body (other mods' mixins target it) and
 * {@code BoneMealItemMixin} fires the event at its head; this hands it the player through {@link #CALL}.
 */
public final class BoneMealItemPatch {
    /** Set while {@code applyBonemeal} runs {@code growCrop}; unset for a direct {@code growCrop} call. */
    public static final ThreadLocal<Call> CALL = new ThreadLocal<>();

    private BoneMealItemPatch() {}

    public static boolean applyBonemeal(ItemStack itemStack, Level level, BlockPos pos, @Nullable Player player) {
        return withPlayer(player, () -> BoneMealItem.growCrop(itemStack, level, pos));
    }

    /** Runs {@code growCrop} as {@code applyBonemeal} with this player. */
    public static boolean withPlayer(@Nullable Player player, BooleanSupplier growCrop) {
        Call previous = CALL.get();
        CALL.set(new Call(player));
        try {
            return growCrop.getAsBoolean();
        } finally {
            if (previous == null) {
                CALL.remove();
            } else {
                CALL.set(previous);
            }
        }
    }

    public record Call(@Nullable Player player) {}
}
