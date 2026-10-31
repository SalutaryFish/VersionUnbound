package net.minecraft.server.level;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class ServerPlayerGameMode {
    private static final Logger LOGGER = LogUtils.getLogger();
    protected ServerLevel level;
    protected final ServerPlayer player;
    private GameType gameModeForPlayer = GameType.DEFAULT_MODE;
    @Nullable
    private GameType previousGameModeForPlayer;
    private boolean isDestroyingBlock;
    private int destroyProgressStart;
    private BlockPos destroyPos = BlockPos.ZERO;
    private int gameTicks;
    private boolean hasDelayedDestroy;
    private BlockPos delayedDestroyPos = BlockPos.ZERO;
    private int delayedTickStart;
    private int lastSentState = -1;

    public ServerPlayerGameMode(ServerPlayer player) {
        this.player = player;
        this.level = player.serverLevel();
    }

    public boolean changeGameModeForPlayer(GameType gameModeForPlayer) {
        if (gameModeForPlayer == this.gameModeForPlayer) {
            return false;
        }

        this.setGameModeForPlayer(gameModeForPlayer, this.previousGameModeForPlayer);
        this.player.onUpdateAbilities();
        this.player
            .server
            .getPlayerList()
            .broadcastAll(new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE, this.player));
        this.level.updateSleepingPlayerList();
        if (gameModeForPlayer == GameType.CREATIVE) {
            this.player.resetCurrentImpulseContext();
        }

        return true;
    }

    protected void setGameModeForPlayer(GameType gameModeForPlayer, @Nullable GameType previousGameModeForPlayer) {
        this.previousGameModeForPlayer = previousGameModeForPlayer;
        this.gameModeForPlayer = gameModeForPlayer;
        gameModeForPlayer.updatePlayerAbilities(this.player.getAbilities());
    }

    public GameType getGameModeForPlayer() {
        return this.gameModeForPlayer;
    }

    @Nullable
    public GameType getPreviousGameModeForPlayer() {
        return this.previousGameModeForPlayer;
    }

    public boolean isSurvival() {
        return this.gameModeForPlayer.isSurvival();
    }

    public boolean isCreative() {
        return this.gameModeForPlayer.isCreative();
    }

    public void tick() {
        this.gameTicks++;
        if (this.hasDelayedDestroy) {
            BlockState blockState = this.level.getBlockState(this.delayedDestroyPos);
            if (blockState.isAir()) {
                this.hasDelayedDestroy = false;
            } else {
                float f = this.incrementDestroyProgress(blockState, this.delayedDestroyPos, this.delayedTickStart);
                if (f >= 1.0F) {
                    this.hasDelayedDestroy = false;
                    this.destroyBlock(this.delayedDestroyPos);
                }
            }
        } else if (this.isDestroyingBlock) {
            BlockState blockState = this.level.getBlockState(this.destroyPos);
            if (blockState.isAir()) {
                this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                this.lastSentState = -1;
                this.isDestroyingBlock = false;
            } else {
                this.incrementDestroyProgress(blockState, this.destroyPos, this.destroyProgressStart);
            }
        }
    }

    private float incrementDestroyProgress(BlockState state, BlockPos pos, int startTick) {
        int i = this.gameTicks - startTick;
        float f = state.getDestroyProgress(this.player, this.player.level(), pos) * (i + 1);
        int j = (int)(f * 10.0F);
        if (j != this.lastSentState) {
            this.level.destroyBlockProgress(this.player.getId(), pos, j);
            this.lastSentState = j;
        }

        return f;
    }

    private void debugLogging(BlockPos pos, boolean terminate, int sequence, String message) {
    }

    public void handleBlockBreakAction(BlockPos pos, ServerboundPlayerActionPacket.Action action, Direction face, int maxBuildHeight, int sequence) {
        if (!this.player.canInteractWithBlock(pos, 1.0)) {
            this.debugLogging(pos, false, sequence, "too far");
        } else if (pos.getY() >= maxBuildHeight) {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
            this.debugLogging(pos, false, sequence, "too high");
        } else {
            if (action == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                if (!this.level.mayInteract(this.player, pos)) {
                    this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
                    this.debugLogging(pos, false, sequence, "may not interact");
                    return;
                }

                if (this.isCreative()) {
                    this.destroyAndAck(pos, sequence, "creative destroy");
                    return;
                }

                if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
                    this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
                    this.debugLogging(pos, false, sequence, "block action restricted");
                    return;
                }

                this.destroyProgressStart = this.gameTicks;
                float f = 1.0F;
                BlockState blockState = this.level.getBlockState(pos);
                if (!blockState.isAir()) {
                    EnchantmentHelper.onHitBlock(
                        this.level,
                        this.player.getMainHandItem(),
                        this.player,
                        this.player,
                        EquipmentSlot.MAINHAND,
                        Vec3.atCenterOf(pos),
                        blockState,
                        item -> this.player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND)
                    );
                    blockState.attack(this.level, pos, this.player);
                    f = blockState.getDestroyProgress(this.player, this.player.level(), pos);
                }

                if (!blockState.isAir() && f >= 1.0F) {
                    this.destroyAndAck(pos, sequence, "insta mine");
                } else {
                    if (this.isDestroyingBlock) {
                        this.player.connection.send(new ClientboundBlockUpdatePacket(this.destroyPos, this.level.getBlockState(this.destroyPos)));
                        this.debugLogging(pos, false, sequence, "abort destroying since another started (client insta mine, server disagreed)");
                    }

                    this.isDestroyingBlock = true;
                    this.destroyPos = pos.immutable();
                    int i = (int)(f * 10.0F);
                    this.level.destroyBlockProgress(this.player.getId(), pos, i);
                    this.debugLogging(pos, true, sequence, "actual start of destroying");
                    this.lastSentState = i;
                }
            } else if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                if (pos.equals(this.destroyPos)) {
                    int j = this.gameTicks - this.destroyProgressStart;
                    BlockState blockState = this.level.getBlockState(pos);
                    if (!blockState.isAir()) {
                        float g = blockState.getDestroyProgress(this.player, this.player.level(), pos) * (j + 1);
                        if (g >= 0.7F) {
                            this.isDestroyingBlock = false;
                            this.level.destroyBlockProgress(this.player.getId(), pos, -1);
                            this.destroyAndAck(pos, sequence, "destroyed");
                            return;
                        }

                        if (!this.hasDelayedDestroy) {
                            this.isDestroyingBlock = false;
                            this.hasDelayedDestroy = true;
                            this.delayedDestroyPos = pos;
                            this.delayedTickStart = this.destroyProgressStart;
                        }
                    }
                }

                this.debugLogging(pos, true, sequence, "stopped destroying");
            } else if (action == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                this.isDestroyingBlock = false;
                if (!Objects.equals(this.destroyPos, pos)) {
                    LOGGER.warn("Mismatch in destroy block pos: {} {}", this.destroyPos, pos);
                    this.level.destroyBlockProgress(this.player.getId(), this.destroyPos, -1);
                    this.debugLogging(pos, true, sequence, "aborted mismatched destroying");
                }

                this.level.destroyBlockProgress(this.player.getId(), pos, -1);
                this.debugLogging(pos, true, sequence, "aborted destroying");
            }
        }
    }

    public void destroyAndAck(BlockPos pos, int sequence, String message) {
        if (this.destroyBlock(pos)) {
            this.debugLogging(pos, true, sequence, message);
        } else {
            this.player.connection.send(new ClientboundBlockUpdatePacket(pos, this.level.getBlockState(pos)));
            this.debugLogging(pos, false, sequence, message);
        }
    }

    /**
     * Attempts to harvest a block
     */
    public boolean destroyBlock(BlockPos pos) {
        BlockState blockState = this.level.getBlockState(pos);
        if (!this.player.getMainHandItem().getItem().canAttackBlock(blockState, this.level, pos, this.player)) {
            return false;
        }

        BlockEntity blockEntity = this.level.getBlockEntity(pos);
        Block block = blockState.getBlock();
        if (block instanceof GameMasterBlock && !this.player.canUseGameMasterBlocks()) {
            this.level.sendBlockUpdated(pos, blockState, blockState, 3);
            return false;
        }

        if (this.player.blockActionRestricted(this.level, pos, this.gameModeForPlayer)) {
            return false;
        }

        BlockState blockState2 = block.playerWillDestroy(this.level, pos, blockState, this.player);
        boolean bl = this.level.removeBlock(pos, false);
        if (bl) {
            block.destroy(this.level, pos, blockState2);
        }

        if (this.isCreative()) {
            return true;
        }

        ItemStack itemStack = this.player.getMainHandItem();
        ItemStack itemStack2 = itemStack.copy();
        boolean bl2 = this.player.hasCorrectToolForDrops(blockState2);
        itemStack.mineBlock(this.level, blockState2, pos, this.player);
        if (bl && bl2) {
            block.playerDestroy(this.level, this.player, pos, blockState2, blockEntity, itemStack2);
        }

        return true;
    }

    public InteractionResult useItem(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand) {
        if (this.gameModeForPlayer == GameType.SPECTATOR) {
            return InteractionResult.PASS;
        }

        if (player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResult.PASS;
        }

        int i = stack.getCount();
        int j = stack.getDamageValue();
        InteractionResultHolder<ItemStack> interactionResultHolder = stack.use(level, player, hand);
        ItemStack itemStack = interactionResultHolder.getObject();
        if (itemStack == stack && itemStack.getCount() == i && itemStack.getUseDuration(player) <= 0 && itemStack.getDamageValue() == j) {
            return interactionResultHolder.getResult();
        }

        if (interactionResultHolder.getResult() == InteractionResult.FAIL && itemStack.getUseDuration(player) > 0 && !player.isUsingItem()) {
            return interactionResultHolder.getResult();
        }

        if (stack != itemStack) {
            player.setItemInHand(hand, itemStack);
        }

        if (itemStack.isEmpty()) {
            player.setItemInHand(hand, ItemStack.EMPTY);
        }

        if (!player.isUsingItem()) {
            player.inventoryMenu.sendAllDataToRemote();
        }

        return interactionResultHolder.getResult();
    }

    public InteractionResult useItemOn(ServerPlayer player, Level level, ItemStack stack, InteractionHand hand, BlockHitResult hitResult) {
        BlockPos blockPos = hitResult.getBlockPos();
        BlockState blockState = level.getBlockState(blockPos);
        if (!blockState.getBlock().isEnabled(level.enabledFeatures())) {
            return InteractionResult.FAIL;
        }

        if (this.gameModeForPlayer == GameType.SPECTATOR) {
            MenuProvider menuProvider = blockState.getMenuProvider(level, blockPos);
            if (menuProvider != null) {
                player.openMenu(menuProvider);
                return InteractionResult.SUCCESS;
            } else {
                return InteractionResult.PASS;
            }
        } else {
            boolean bl = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
            boolean bl2 = player.isSecondaryUseActive() && bl;
            ItemStack itemStack = stack.copy();
            if (!bl2) {
                ItemInteractionResult itemInteractionResult = blockState.useItemOn(player.getItemInHand(hand), level, player, hand, hitResult);
                if (itemInteractionResult.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockPos, itemStack);
                    return itemInteractionResult.result();
                }

                if (itemInteractionResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == InteractionHand.MAIN_HAND) {
                    InteractionResult interactionResult = blockState.useWithoutItem(level, player, hitResult);
                    if (interactionResult.consumesAction()) {
                        CriteriaTriggers.DEFAULT_BLOCK_USE.trigger(player, blockPos);
                        return interactionResult;
                    }
                }
            }

            if (!stack.isEmpty() && !player.getCooldowns().isOnCooldown(stack.getItem())) {
                UseOnContext useOnContext = new UseOnContext(player, hand, hitResult);
                InteractionResult interactionResult;
                if (this.isCreative()) {
                    int i = stack.getCount();
                    interactionResult = stack.useOn(useOnContext);
                    stack.setCount(i);
                } else {
                    interactionResult = stack.useOn(useOnContext);
                }

                if (interactionResult.consumesAction()) {
                    CriteriaTriggers.ITEM_USED_ON_BLOCK.trigger(player, blockPos, itemStack);
                }

                return interactionResult;
            } else {
                return InteractionResult.PASS;
            }
        }
    }

    /**
     * Sets the world instance.
     */
    public void setLevel(ServerLevel serverLevel) {
        this.level = serverLevel;
    }
}
