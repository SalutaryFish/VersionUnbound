package net.minecraft.client.multiplayer;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerSlotStateChangedPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlaceRecipePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HasCustomInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableObject;
import org.slf4j.Logger;

public class MultiPlayerGameMode {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Minecraft minecraft;
    private final ClientPacketListener connection;
    private BlockPos destroyBlockPos = new BlockPos(-1, -1, -1);
    private ItemStack destroyingItem = ItemStack.EMPTY;
    private float destroyProgress;
    private float destroyTicks;
    private int destroyDelay;
    private boolean isDestroying;
    private GameType localPlayerMode = GameType.DEFAULT_MODE;
    @Nullable
    private GameType previousLocalPlayerMode;
    private int carriedIndex;

    public MultiPlayerGameMode(Minecraft minecraft, ClientPacketListener connection) {
        this.minecraft = minecraft;
        this.connection = connection;
    }

    /**
     * Sets player capabilities depending on current gametype.
     */
    public void adjustPlayer(Player player) {
        this.localPlayerMode.updatePlayerAbilities(player.getAbilities());
    }

    public void setLocalMode(GameType localPlayerMode, @Nullable GameType previousLocalPlayerMode) {
        this.localPlayerMode = localPlayerMode;
        this.previousLocalPlayerMode = previousLocalPlayerMode;
        this.localPlayerMode.updatePlayerAbilities(this.minecraft.player.getAbilities());
    }

    /**
     * Sets the game type for the player.
     */
    public void setLocalMode(GameType type) {
        if (type != this.localPlayerMode) {
            this.previousLocalPlayerMode = this.localPlayerMode;
        }

        this.localPlayerMode = type;
        this.localPlayerMode.updatePlayerAbilities(this.minecraft.player.getAbilities());
    }

    public boolean canHurtPlayer() {
        return this.localPlayerMode.isSurvival();
    }

    public boolean destroyBlock(BlockPos pos) {
        if (this.minecraft.player.blockActionRestricted(this.minecraft.level, pos, this.localPlayerMode)) {
            return false;
        }

        Level level = this.minecraft.level;
        BlockState blockState = level.getBlockState(pos);
        if (!this.minecraft.player.getMainHandItem().getItem().canAttackBlock(blockState, level, pos, this.minecraft.player)) {
            return false;
        }

        Block block = blockState.getBlock();
        if (block instanceof GameMasterBlock && !this.minecraft.player.canUseGameMasterBlocks()) {
            return false;
        }

        if (blockState.isAir()) {
            return false;
        }

        block.playerWillDestroy(level, pos, blockState, this.minecraft.player);
        FluidState fluidState = level.getFluidState(pos);
        boolean bl = level.setBlock(pos, fluidState.createLegacyBlock(), 11);
        if (bl) {
            block.destroy(level, pos, blockState);
        }

        return bl;
    }

    /**
     * Called when the player is hitting a block with an item.
     */
    public boolean startDestroyBlock(BlockPos loc, Direction face) {
        if (this.minecraft.player.blockActionRestricted(this.minecraft.level, loc, this.localPlayerMode)) {
            return false;
        }

        if (!this.minecraft.level.getWorldBorder().isWithinBounds(loc)) {
            return false;
        }

        if (this.localPlayerMode.isCreative()) {
            BlockState blockState = this.minecraft.level.getBlockState(loc);
            this.minecraft.getTutorial().onDestroyBlock(this.minecraft.level, loc, blockState, 1.0F);
            this.startPrediction(this.minecraft.level, sequence -> {
                this.destroyBlock(loc);
                return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, loc, face, sequence);
            });
            this.destroyDelay = 5;
        } else if (!this.isDestroying || !this.sameDestroyTarget(loc)) {
            if (this.isDestroying) {
                this.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, this.destroyBlockPos, face));
            }

            BlockState blockState = this.minecraft.level.getBlockState(loc);
            this.minecraft.getTutorial().onDestroyBlock(this.minecraft.level, loc, blockState, 0.0F);
            this.startPrediction(this.minecraft.level, sequence -> {
                boolean bl = !blockState.isAir();
                if (bl && this.destroyProgress == 0.0F) {
                    blockState.attack(this.minecraft.level, loc, this.minecraft.player);
                }

                if (bl && blockState.getDestroyProgress(this.minecraft.player, this.minecraft.player.level(), loc) >= 1.0F) {
                    this.destroyBlock(loc);
                } else {
                    this.isDestroying = true;
                    this.destroyBlockPos = loc;
                    this.destroyingItem = this.minecraft.player.getMainHandItem();
                    this.destroyProgress = 0.0F;
                    this.destroyTicks = 0.0F;
                    this.minecraft.level.destroyBlockProgress(this.minecraft.player.getId(), this.destroyBlockPos, this.getDestroyStage());
                }

                return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, loc, face, sequence);
            });
        }

        return true;
    }

    public void stopDestroyBlock() {
        if (this.isDestroying) {
            BlockState blockState = this.minecraft.level.getBlockState(this.destroyBlockPos);
            this.minecraft.getTutorial().onDestroyBlock(this.minecraft.level, this.destroyBlockPos, blockState, -1.0F);
            this.connection
                .send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, this.destroyBlockPos, Direction.DOWN));
            this.isDestroying = false;
            this.destroyProgress = 0.0F;
            this.minecraft.level.destroyBlockProgress(this.minecraft.player.getId(), this.destroyBlockPos, -1);
            this.minecraft.player.resetAttackStrengthTicker();
        }
    }

    public boolean continueDestroyBlock(BlockPos posBlock, Direction directionFacing) {
        this.ensureHasSentCarriedItem();
        if (this.destroyDelay > 0) {
            this.destroyDelay--;
            return true;
        }

        if (this.localPlayerMode.isCreative() && this.minecraft.level.getWorldBorder().isWithinBounds(posBlock)) {
            this.destroyDelay = 5;
            BlockState blockState = this.minecraft.level.getBlockState(posBlock);
            this.minecraft.getTutorial().onDestroyBlock(this.minecraft.level, posBlock, blockState, 1.0F);
            this.startPrediction(this.minecraft.level, sequence -> {
                this.destroyBlock(posBlock);
                return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, posBlock, directionFacing, sequence);
            });
            return true;
        }

        if (this.sameDestroyTarget(posBlock)) {
            BlockState blockState = this.minecraft.level.getBlockState(posBlock);
            if (blockState.isAir()) {
                this.isDestroying = false;
                return false;
            }

            this.destroyProgress = this.destroyProgress + blockState.getDestroyProgress(this.minecraft.player, this.minecraft.player.level(), posBlock);
            if (this.destroyTicks % 4.0F == 0.0F) {
                SoundType soundType = blockState.getSoundType();
                this.minecraft
                    .getSoundManager()
                    .play(
                        new SimpleSoundInstance(
                            soundType.getHitSound(),
                            SoundSource.BLOCKS,
                            (soundType.getVolume() + 1.0F) / 8.0F,
                            soundType.getPitch() * 0.5F,
                            SoundInstance.createUnseededRandom(),
                            posBlock
                        )
                    );
            }

            this.destroyTicks++;
            this.minecraft.getTutorial().onDestroyBlock(this.minecraft.level, posBlock, blockState, Mth.clamp(this.destroyProgress, 0.0F, 1.0F));
            if (this.destroyProgress >= 1.0F) {
                this.isDestroying = false;
                this.startPrediction(this.minecraft.level, sequence -> {
                    this.destroyBlock(posBlock);
                    return new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, posBlock, directionFacing, sequence);
                });
                this.destroyProgress = 0.0F;
                this.destroyTicks = 0.0F;
                this.destroyDelay = 5;
            }

            this.minecraft.level.destroyBlockProgress(this.minecraft.player.getId(), this.destroyBlockPos, this.getDestroyStage());
            return true;
        } else {
            return this.startDestroyBlock(posBlock, directionFacing);
        }
    }

    private void startPrediction(ClientLevel level, PredictiveAction action) {
        try (BlockStatePredictionHandler blockStatePredictionHandler = level.getBlockStatePredictionHandler().startPredicting()) {
            int i = blockStatePredictionHandler.currentSequence();
            Packet<ServerGamePacketListener> packet = action.predict(i);
            this.connection.send(packet);
        }
    }

    public void tick() {
        this.ensureHasSentCarriedItem();
        if (this.connection.getConnection().isConnected()) {
            this.connection.getConnection().tick();
        } else {
            this.connection.getConnection().handleDisconnection();
        }
    }

    private boolean sameDestroyTarget(BlockPos pos) {
        ItemStack itemStack = this.minecraft.player.getMainHandItem();
        return pos.equals(this.destroyBlockPos) && ItemStack.isSameItemSameComponents(itemStack, this.destroyingItem);
    }

    private void ensureHasSentCarriedItem() {
        int i = this.minecraft.player.getInventory().selected;
        if (i != this.carriedIndex) {
            this.carriedIndex = i;
            this.connection.send(new ServerboundSetCarriedItemPacket(this.carriedIndex));
        }
    }

    public InteractionResult useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult result) {
        this.ensureHasSentCarriedItem();
        if (!this.minecraft.level.getWorldBorder().isWithinBounds(result.getBlockPos())) {
            return InteractionResult.FAIL;
        }

        MutableObject<InteractionResult> mutableObject = new MutableObject<>();
        this.startPrediction(this.minecraft.level, sequence -> {
            mutableObject.setValue(this.performUseItemOn(player, hand, result));
            return new ServerboundUseItemOnPacket(hand, result, sequence);
        });
        return mutableObject.getValue();
    }

    private InteractionResult performUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult result) {
        BlockPos blockPos = result.getBlockPos();
        ItemStack itemStack = player.getItemInHand(hand);
        if (this.localPlayerMode == GameType.SPECTATOR) {
            return InteractionResult.SUCCESS;
        }

        boolean bl = !player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty();
        boolean bl2 = player.isSecondaryUseActive() && bl;
        if (!bl2) {
            BlockState blockState = this.minecraft.level.getBlockState(blockPos);
            if (!this.connection.isFeatureEnabled(blockState.getBlock().requiredFeatures())) {
                return InteractionResult.FAIL;
            }

            ItemInteractionResult itemInteractionResult = blockState.useItemOn(player.getItemInHand(hand), this.minecraft.level, player, hand, result);
            if (itemInteractionResult.consumesAction()) {
                return itemInteractionResult.result();
            }

            if (itemInteractionResult == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION && hand == InteractionHand.MAIN_HAND) {
                InteractionResult interactionResult = blockState.useWithoutItem(this.minecraft.level, player, result);
                if (interactionResult.consumesAction()) {
                    return interactionResult;
                }
            }
        }

        if (!itemStack.isEmpty() && !player.getCooldowns().isOnCooldown(itemStack.getItem())) {
            UseOnContext useOnContext = new UseOnContext(player, hand, result);
            InteractionResult interactionResult2;
            if (this.localPlayerMode.isCreative()) {
                int i = itemStack.getCount();
                interactionResult2 = itemStack.useOn(useOnContext);
                itemStack.setCount(i);
            } else {
                interactionResult2 = itemStack.useOn(useOnContext);
            }

            return interactionResult2;
        } else {
            return InteractionResult.PASS;
        }
    }

    public InteractionResult useItem(Player player, InteractionHand hand) {
        if (this.localPlayerMode == GameType.SPECTATOR) {
            return InteractionResult.PASS;
        }

        this.ensureHasSentCarriedItem();
        MutableObject<InteractionResult> mutableObject = new MutableObject<>();
        this.startPrediction(this.minecraft.level, sequence -> {
            ServerboundUseItemPacket serverboundUseItemPacket = new ServerboundUseItemPacket(hand, sequence, player.getYRot(), player.getXRot());
            ItemStack itemStack = player.getItemInHand(hand);
            if (player.getCooldowns().isOnCooldown(itemStack.getItem())) {
                mutableObject.setValue(InteractionResult.PASS);
                return serverboundUseItemPacket;
            }

            InteractionResultHolder<ItemStack> interactionResultHolder = itemStack.use(this.minecraft.level, player, hand);
            ItemStack itemStack2 = interactionResultHolder.getObject();
            if (itemStack2 != itemStack) {
                player.setItemInHand(hand, itemStack2);
            }

            mutableObject.setValue(interactionResultHolder.getResult());
            return serverboundUseItemPacket;
        });
        return mutableObject.getValue();
    }

    public LocalPlayer createPlayer(ClientLevel level, StatsCounter statsManager, ClientRecipeBook recipes) {
        return this.createPlayer(level, statsManager, recipes, false, false);
    }

    public LocalPlayer createPlayer(ClientLevel level, StatsCounter statsManager, ClientRecipeBook recipes, boolean wasShiftKeyDown, boolean wasSprinting) {
        return new LocalPlayer(this.minecraft, level, this.connection, statsManager, recipes, wasShiftKeyDown, wasSprinting);
    }

    /**
     * Attacks an entity
     */
    public void attack(Player player, Entity targetEntity) {
        this.ensureHasSentCarriedItem();
        this.connection.send(ServerboundInteractPacket.createAttackPacket(targetEntity, player.isShiftKeyDown()));
        if (this.localPlayerMode != GameType.SPECTATOR) {
            player.attack(targetEntity);
            player.resetAttackStrengthTicker();
        }
    }

    /**
     * Handles right-clicking an entity, sends a packet to the server.
     */
    public InteractionResult interact(Player player, Entity target, InteractionHand hand) {
        this.ensureHasSentCarriedItem();
        this.connection.send(ServerboundInteractPacket.createInteractionPacket(target, player.isShiftKeyDown(), hand));
        return this.localPlayerMode == GameType.SPECTATOR ? InteractionResult.PASS : player.interactOn(target, hand);
    }

    /**
     * Handles right-clicking an entity from the entities side, sends a packet to the server.
     */
    public InteractionResult interactAt(Player player, Entity target, EntityHitResult ray, InteractionHand hand) {
        this.ensureHasSentCarriedItem();
        Vec3 vec3 = ray.getLocation().subtract(target.getX(), target.getY(), target.getZ());
        this.connection.send(ServerboundInteractPacket.createInteractionPacket(target, player.isShiftKeyDown(), hand, vec3));
        return this.localPlayerMode == GameType.SPECTATOR ? InteractionResult.PASS : target.interactAt(player, vec3, hand);
    }

    public void handleInventoryMouseClick(int containerId, int slotId, int mouseButton, ClickType clickType, Player player) {
        AbstractContainerMenu abstractContainerMenu = player.containerMenu;
        if (containerId != abstractContainerMenu.containerId) {
            LOGGER.warn("Ignoring click in mismatching container. Click in {}, player has {}.", containerId, abstractContainerMenu.containerId);
        } else {
            NonNullList<Slot> nonNullList = abstractContainerMenu.slots;
            int i = nonNullList.size();
            List<ItemStack> list = Lists.newArrayListWithCapacity(i);

            for (Slot slot : nonNullList) {
                list.add(slot.getItem().copy());
            }

            abstractContainerMenu.clicked(slotId, mouseButton, clickType, player);
            Int2ObjectMap<ItemStack> int2ObjectMap = new Int2ObjectOpenHashMap<>();

            for (int j = 0; j < i; j++) {
                ItemStack itemStack = list.get(j);
                ItemStack itemStack2 = nonNullList.get(j).getItem();
                if (!ItemStack.matches(itemStack, itemStack2)) {
                    int2ObjectMap.put(j, itemStack2.copy());
                }
            }

            this.connection
                .send(
                    new ServerboundContainerClickPacket(
                        containerId,
                        abstractContainerMenu.getStateId(),
                        slotId,
                        mouseButton,
                        clickType,
                        abstractContainerMenu.getCarried().copy(),
                        int2ObjectMap
                    )
                );
        }
    }

    public void handlePlaceRecipe(int containerId, RecipeHolder<?> recipe, boolean shiftDown) {
        this.connection.send(new ServerboundPlaceRecipePacket(containerId, recipe, shiftDown));
    }

    /**
     * GuiEnchantment uses this during multiplayer to tell PlayerControllerMP to send a packet indicating the enchantment action the player has taken.
     */
    public void handleInventoryButtonClick(int containerId, int buttonId) {
        this.connection.send(new ServerboundContainerButtonClickPacket(containerId, buttonId));
    }

    /**
     * Used in PlayerControllerMP to update the server with an ItemStack in a slot.
     */
    public void handleCreativeModeItemAdd(ItemStack stack, int slotId) {
        if (this.localPlayerMode.isCreative() && this.connection.isFeatureEnabled(stack.getItem().requiredFeatures())) {
            this.connection.send(new ServerboundSetCreativeModeSlotPacket(slotId, stack));
        }
    }

    /**
     * Sends a Packet107 to the server to drop the item on the ground
     */
    public void handleCreativeModeItemDrop(ItemStack stack) {
        if (this.localPlayerMode.isCreative() && !stack.isEmpty() && this.connection.isFeatureEnabled(stack.getItem().requiredFeatures())) {
            this.connection.send(new ServerboundSetCreativeModeSlotPacket(-1, stack));
        }
    }

    public void releaseUsingItem(Player player) {
        this.ensureHasSentCarriedItem();
        this.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
        player.releaseUsingItem();
    }

    public boolean hasExperience() {
        return this.localPlayerMode.isSurvival();
    }

    public boolean hasMissTime() {
        return !this.localPlayerMode.isCreative();
    }

    public boolean hasInfiniteItems() {
        return this.localPlayerMode.isCreative();
    }

    public boolean isServerControlledInventory() {
        return this.minecraft.player.isPassenger() && this.minecraft.player.getVehicle() instanceof HasCustomInventoryScreen;
    }

    public boolean isAlwaysFlying() {
        return this.localPlayerMode == GameType.SPECTATOR;
    }

    @Nullable
    public GameType getPreviousPlayerMode() {
        return this.previousLocalPlayerMode;
    }

    public GameType getPlayerMode() {
        return this.localPlayerMode;
    }

    public boolean isDestroying() {
        return this.isDestroying;
    }

    public int getDestroyStage() {
        return this.destroyProgress > 0.0F ? (int)(this.destroyProgress * 10.0F) : -1;
    }

    public void handlePickItem(int index) {
        this.connection.send(new ServerboundPickItemPacket(index));
    }

    public void handleSlotStateChanged(int slotId, int containerId, boolean newState) {
        this.connection.send(new ServerboundContainerSlotStateChangedPacket(slotId, containerId, newState));
    }
}
