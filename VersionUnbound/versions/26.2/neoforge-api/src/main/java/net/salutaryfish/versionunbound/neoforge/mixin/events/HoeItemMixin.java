package net.salutaryfish.versionunbound.neoforge.mixin.events;

import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.ItemAbilities;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * NeoForge's patch to {@code HoeItem}: tilling asks the block state's {@code getToolModifiedState} (which fires
 * {@code BlockToolModificationEvent}, then asks the block, whose default has vanilla's tilling) instead of
 * {@code TILLABLES}. An {@code @Overwrite} with NeoForge's patched body, so that mods' mixins into it find NeoForge's
 * shape.
 * <p>
 * VersionUnbound: Fabric API's {@code TillableBlockRegistry} puts Fabric mods' tillable blocks into {@code TILLABLES},
 * which NeoForge's hoe never reads. For a block vanilla doesn't till, when {@code getToolModifiedState} has no answer,
 * the hoe still uses its {@code TILLABLES} entry, so Fabric mods' tilling keeps working next to NeoForge mods (a
 * {@code BlockToolModificationEvent} canceled for such a block doesn't stop it).
 */
@Mixin(HoeItem.class)
abstract class HoeItemMixin {
    @Unique
    private static final Set<Block> VANILLA_TILLABLES = Set.of(Blocks.GRASS_BLOCK, Blocks.DIRT_PATH, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT);

    @Shadow
    @Final
    protected static Map<Block, Pair<Predicate<UseOnContext>, Consumer<UseOnContext>>> TILLABLES;

    /**
     * @author NeoForge (ported by VersionUnbound)
     * @reason NeoForge's patched body
     */
    @Overwrite
    public InteractionResult useOn(final UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState toolModifiedState = level.getBlockState(pos).getToolModifiedState(context, ItemAbilities.HOE_TILL, false);
        Pair<Predicate<UseOnContext>, Consumer<UseOnContext>> logicPair = toolModifiedState == null ? null : Pair.of(ctx -> true, HoeItem.changeIntoState(toolModifiedState));
        if (logicPair == null) {
            Block block = level.getBlockState(pos).getBlock();
            if (!VANILLA_TILLABLES.contains(block)) logicPair = TILLABLES.get(block);
        }
        if (logicPair == null) {
            return InteractionResult.PASS;
        }

        Predicate<UseOnContext> predicate = logicPair.getFirst();
        Consumer<UseOnContext> action = logicPair.getSecond();
        if (predicate.test(context)) {
            Player player = context.getPlayer();
            level.playSound(player, pos, SoundEvents.HOE_TILL, SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!level.isClientSide()) {
                action.accept(context);
                if (player != null) {
                    context.getItemInHand().hurtAndBreak(1, player, context.getHand().asEquipmentSlot());
                }
            }

            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }
}
