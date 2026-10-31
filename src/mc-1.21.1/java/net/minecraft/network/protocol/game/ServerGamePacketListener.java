package net.minecraft.network.protocol.game;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ServerCommonPacketListener;
import net.minecraft.network.protocol.ping.ServerPingPacketListener;

/**
 * PacketListener for the server side of the PLAY protocol.
 */
public interface ServerGamePacketListener extends ServerPingPacketListener, ServerCommonPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.PLAY;
    }

    void handleAnimate(ServerboundSwingPacket var1);

    /**
     * Process chat messages (broadcast back to clients) and commands (executes)
     */
    void handleChat(ServerboundChatPacket var1);

    void handleChatCommand(ServerboundChatCommandPacket var1);

    void handleSignedChatCommand(ServerboundChatCommandSignedPacket var1);

    void handleChatAck(ServerboundChatAckPacket var1);

    /**
     * Processes the client status updates: respawn attempt from player, opening statistics or achievements, or acquiring 'open inventory' achievement
     */
    void handleClientCommand(ServerboundClientCommandPacket var1);

    /**
     * Enchants the item identified by the packet given some convoluted conditions (matching window, which should/shouldn't be in use?)
     */
    void handleContainerButtonClick(ServerboundContainerButtonClickPacket var1);

    /**
     * Executes a container/inventory slot manipulation as indicated by the packet. Sends the serverside result if they didn't match the indicated result and prevents further manipulation by the player until he confirms that it has the same open container/inventory
     */
    void handleContainerClick(ServerboundContainerClickPacket var1);

    void handlePlaceRecipe(ServerboundPlaceRecipePacket var1);

    /**
     * Processes the client closing windows (container)
     */
    void handleContainerClose(ServerboundContainerClosePacket var1);

    /**
     * Processes left and right clicks on entities
     */
    void handleInteract(ServerboundInteractPacket var1);

    /**
     * Processes clients perspective on player positioning and/or orientation
     */
    void handleMovePlayer(ServerboundMovePlayerPacket var1);

    /**
     * Processes a player starting/stopping flying
     */
    void handlePlayerAbilities(ServerboundPlayerAbilitiesPacket var1);

    /**
     * Processes the player initiating/stopping digging on a particular spot, as well as a player dropping items
     */
    void handlePlayerAction(ServerboundPlayerActionPacket var1);

    /**
     * Processes a range of action-types: sneaking, sprinting, waking from sleep, opening the inventory or setting jump height of the horse the player is riding
     */
    void handlePlayerCommand(ServerboundPlayerCommandPacket var1);

    /**
     * Processes player movement input. Includes walking, strafing, jumping, and sneaking. Excludes riding and toggling flying/sprinting.
     */
    void handlePlayerInput(ServerboundPlayerInputPacket var1);

    /**
     * Updates which quickbar slot is selected
     */
    void handleSetCarriedItem(ServerboundSetCarriedItemPacket var1);

    /**
     * Update the server with an ItemStack in a slot.
     */
    void handleSetCreativeModeSlot(ServerboundSetCreativeModeSlotPacket var1);

    void handleSignUpdate(ServerboundSignUpdatePacket var1);

    void handleUseItemOn(ServerboundUseItemOnPacket var1);

    /**
     * Called when a client is using an item while not pointing at a block, but simply using an item
     */
    void handleUseItem(ServerboundUseItemPacket var1);

    void handleTeleportToEntityPacket(ServerboundTeleportToEntityPacket var1);

    void handlePaddleBoat(ServerboundPaddleBoatPacket var1);

    void handleMoveVehicle(ServerboundMoveVehiclePacket var1);

    void handleAcceptTeleportPacket(ServerboundAcceptTeleportationPacket var1);

    void handleRecipeBookSeenRecipePacket(ServerboundRecipeBookSeenRecipePacket var1);

    void handleRecipeBookChangeSettingsPacket(ServerboundRecipeBookChangeSettingsPacket var1);

    void handleSeenAdvancements(ServerboundSeenAdvancementsPacket var1);

    /**
     * This method is only called for manual tab-completion (the {@link net.minecraft.commands.synchronization.SuggestionProviders#ASK_SERVER minecraft:ask_server} suggestion provider).
     */
    void handleCustomCommandSuggestions(ServerboundCommandSuggestionPacket var1);

    void handleSetCommandBlock(ServerboundSetCommandBlockPacket var1);

    void handleSetCommandMinecart(ServerboundSetCommandMinecartPacket var1);

    void handlePickItem(ServerboundPickItemPacket var1);

    void handleRenameItem(ServerboundRenameItemPacket var1);

    void handleSetBeaconPacket(ServerboundSetBeaconPacket var1);

    void handleSetStructureBlock(ServerboundSetStructureBlockPacket var1);

    void handleSelectTrade(ServerboundSelectTradePacket var1);

    void handleEditBook(ServerboundEditBookPacket var1);

    void handleEntityTagQuery(ServerboundEntityTagQueryPacket var1);

    void handleContainerSlotStateChanged(ServerboundContainerSlotStateChangedPacket var1);

    void handleBlockEntityTagQuery(ServerboundBlockEntityTagQueryPacket var1);

    void handleSetJigsawBlock(ServerboundSetJigsawBlockPacket var1);

    void handleJigsawGenerate(ServerboundJigsawGeneratePacket var1);

    void handleChangeDifficulty(ServerboundChangeDifficultyPacket var1);

    void handleLockDifficulty(ServerboundLockDifficultyPacket var1);

    void handleChatSessionUpdate(ServerboundChatSessionUpdatePacket var1);

    void handleConfigurationAcknowledged(ServerboundConfigurationAcknowledgedPacket var1);

    void handleChunkBatchReceived(ServerboundChunkBatchReceivedPacket var1);

    void handleDebugSampleSubscription(ServerboundDebugSampleSubscriptionPacket var1);
}
