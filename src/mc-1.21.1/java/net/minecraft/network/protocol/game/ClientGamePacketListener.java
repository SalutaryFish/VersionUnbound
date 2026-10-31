package net.minecraft.network.protocol.game;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.ClientCommonPacketListener;
import net.minecraft.network.protocol.ping.ClientPongPacketListener;

/**
 * PacketListener for the client side of the PLAY protocol.
 */
public interface ClientGamePacketListener extends ClientPongPacketListener, ClientCommonPacketListener {
    @Override
    default ConnectionProtocol protocol() {
        return ConnectionProtocol.PLAY;
    }

    /**
     * Spawns an instance of the objecttype indicated by the packet and sets its position and momentum
     */
    void handleAddEntity(ClientboundAddEntityPacket var1);

    /**
     * Spawns an experience orb and sets its value (amount of XP)
     */
    void handleAddExperienceOrb(ClientboundAddExperienceOrbPacket var1);

    /**
     * May create a scoreboard objective, remove an objective from the scoreboard or update an objectives' displayname
     */
    void handleAddObjective(ClientboundSetObjectivePacket var1);

    /**
     * Renders a specified animation: Waking up a player, a living entity swinging its currently held item, being hurt or receiving a critical hit by normal or magical means
     */
    void handleAnimate(ClientboundAnimatePacket var1);

    void handleHurtAnimation(ClientboundHurtAnimationPacket var1);

    /**
     * Updates the players statistics or achievements
     */
    void handleAwardStats(ClientboundAwardStatsPacket var1);

    void handleAddOrRemoveRecipes(ClientboundRecipePacket var1);

    /**
     * Updates all registered IWorldAccess instances with destroyBlockInWorldPartially
     */
    void handleBlockDestruction(ClientboundBlockDestructionPacket var1);

    /**
     * Creates a sign in the specified location if it didn't exist and opens the GUI to edit its text
     */
    void handleOpenSignEditor(ClientboundOpenSignEditorPacket var1);

    /**
     * Updates the NBTTagCompound metadata of instances of the following entitytypes: Mob spawners, command blocks, beacons, skulls, flowerpot
     */
    void handleBlockEntityData(ClientboundBlockEntityDataPacket var1);

    /**
     * Triggers Block.onBlockEventReceived, which is implemented in BlockPistonBase for extension/retraction, BlockNote for setting the instrument (including audiovisual feedback) and in BlockContainer to set the number of players accessing a (Ender)Chest
     */
    void handleBlockEvent(ClientboundBlockEventPacket var1);

    /**
     * Updates the block and metadata and generates a blockupdate (and notify the clients)
     */
    void handleBlockUpdate(ClientboundBlockUpdatePacket var1);

    void handleSystemChat(ClientboundSystemChatPacket var1);

    void handlePlayerChat(ClientboundPlayerChatPacket var1);

    void handleDisguisedChat(ClientboundDisguisedChatPacket var1);

    void handleDeleteChat(ClientboundDeleteChatPacket var1);

    /**
     * Received from the servers PlayerManager if between 1 and 64 blocks in a chunk are changed. If only one block requires an update, the server sends S23PacketBlockChange and if 64 or more blocks are changed, the server sends S21PacketChunkData
     */
    void handleChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket var1);

    /**
     * Updates the worlds MapStorage with the specified MapData for the specified map-identifier and invokes a MapItemRenderer for it
     */
    void handleMapItemData(ClientboundMapItemDataPacket var1);

    /**
     * Resets the ItemStack held in hand and closes the window that is opened
     */
    void handleContainerClose(ClientboundContainerClosePacket var1);

    /**
     * Handles the placement of a specified ItemStack in a specified container/inventory slot
     */
    void handleContainerContent(ClientboundContainerSetContentPacket var1);

    void handleHorseScreenOpen(ClientboundHorseScreenOpenPacket var1);

    /**
     * Sets the progressbar of the opened window to the specified value
     */
    void handleContainerSetData(ClientboundContainerSetDataPacket var1);

    /**
     * Handles picking up an ItemStack or dropping one in your inventory or an open (non-creative) container
     */
    void handleContainerSetSlot(ClientboundContainerSetSlotPacket var1);

    /**
     * Invokes the entities' handleUpdateHealth method which is implemented in LivingBase (hurt/death), MinecartMobSpawner (spawn delay), FireworkRocket & MinecartTNT (explosion), IronGolem (throwing,...), Witch (spawn particles), Zombie (villager transformation), Animal (breeding mode particles), Horse (breeding/smoke particles), Sheep (...), Tameable (...), Villager (particles for breeding mode, angry and happy), Wolf (...)
     */
    void handleEntityEvent(ClientboundEntityEventPacket var1);

    void handleEntityLinkPacket(ClientboundSetEntityLinkPacket var1);

    void handleSetEntityPassengersPacket(ClientboundSetPassengersPacket var1);

    /**
     * Initiates a new explosion (sound, particles, drop spawn) for the affected blocks indicated by the packet.
     */
    void handleExplosion(ClientboundExplodePacket var1);

    void handleGameEvent(ClientboundGameEventPacket var1);

    void handleLevelChunkWithLight(ClientboundLevelChunkWithLightPacket var1);

    void handleChunksBiomes(ClientboundChunksBiomesPacket var1);

    void handleForgetLevelChunk(ClientboundForgetLevelChunkPacket var1);

    void handleLevelEvent(ClientboundLevelEventPacket var1);

    /**
     * Registers some server properties (gametype,hardcore-mode,terraintype,difficulty,player limit), creates a new WorldClient and sets the player initial dimension
     */
    void handleLogin(ClientboundLoginPacket var1);

    /**
     * Updates the specified entity's position by the specified relative moment and absolute rotation. Note that subclassing of the packet allows for the specification of a subset of this data (e.g. only rel. position, abs. rotation or both).
     */
    void handleMoveEntity(ClientboundMoveEntityPacket var1);

    void handleMovePlayer(ClientboundPlayerPositionPacket var1);

    /**
     * Spawns a specified number of particles at the specified location with a randomized displacement according to specified bounds
     */
    void handleParticleEvent(ClientboundLevelParticlesPacket var1);

    void handlePlayerAbilities(ClientboundPlayerAbilitiesPacket var1);

    void handlePlayerInfoRemove(ClientboundPlayerInfoRemovePacket var1);

    void handlePlayerInfoUpdate(ClientboundPlayerInfoUpdatePacket var1);

    void handleRemoveEntities(ClientboundRemoveEntitiesPacket var1);

    void handleRemoveMobEffect(ClientboundRemoveMobEffectPacket var1);

    void handleRespawn(ClientboundRespawnPacket var1);

    /**
     * Updates the direction in which the specified entity is looking, normally this head rotation is independent of the rotation of the entity itself
     */
    void handleRotateMob(ClientboundRotateHeadPacket var1);

    /**
     * Updates which hotbar slot of the player is currently selected
     */
    void handleSetCarriedItem(ClientboundSetCarriedItemPacket var1);

    /**
     * Removes or sets the ScoreObjective to be displayed at a particular scoreboard position (list, sidebar, below name)
     */
    void handleSetDisplayObjective(ClientboundSetDisplayObjectivePacket var1);

    /**
     * Invoked when the server registers new proximate objects in your watchlist or when objects in your watchlist have changed -> Registers any changes locally
     */
    void handleSetEntityData(ClientboundSetEntityDataPacket var1);

    /**
     * Sets the velocity of the specified entity to the specified value
     */
    void handleSetEntityMotion(ClientboundSetEntityMotionPacket var1);

    void handleSetEquipment(ClientboundSetEquipmentPacket var1);

    void handleSetExperience(ClientboundSetExperiencePacket var1);

    void handleSetHealth(ClientboundSetHealthPacket var1);

    /**
     * Updates a team managed by the scoreboard: Create/Remove the team registration, Register/Remove the player-team-memberships, Set team displayname/prefix/suffix and/or whether friendly fire is enabled
     */
    void handleSetPlayerTeamPacket(ClientboundSetPlayerTeamPacket var1);

    /**
     * Either updates the score with a specified value or removes the score for an objective
     */
    void handleSetScore(ClientboundSetScorePacket var1);

    void handleResetScore(ClientboundResetScorePacket var1);

    void handleSetSpawn(ClientboundSetDefaultSpawnPositionPacket var1);

    void handleSetTime(ClientboundSetTimePacket var1);

    void handleSoundEvent(ClientboundSoundPacket var1);

    void handleSoundEntityEvent(ClientboundSoundEntityPacket var1);

    void handleTakeItemEntity(ClientboundTakeItemEntityPacket var1);

    /**
     * Updates an entity's position and rotation as specified by the packet
     */
    void handleTeleportEntity(ClientboundTeleportEntityPacket var1);

    void handleTickingState(ClientboundTickingStatePacket var1);

    void handleTickingStep(ClientboundTickingStepPacket var1);

    /**
     * Updates en entity's attributes and their respective modifiers, which are used for speed bonuses (player sprinting, animals fleeing, baby speed), weapon/tool attackDamage, hostiles followRange randomization, zombie maxHealth and knockback resistance as well as reinforcement spawning chance.
     */
    void handleUpdateAttributes(ClientboundUpdateAttributesPacket var1);

    void handleUpdateMobEffect(ClientboundUpdateMobEffectPacket var1);

    void handlePlayerCombatEnd(ClientboundPlayerCombatEndPacket var1);

    void handlePlayerCombatEnter(ClientboundPlayerCombatEnterPacket var1);

    void handlePlayerCombatKill(ClientboundPlayerCombatKillPacket var1);

    void handleChangeDifficulty(ClientboundChangeDifficultyPacket var1);

    void handleSetCamera(ClientboundSetCameraPacket var1);

    void handleInitializeBorder(ClientboundInitializeBorderPacket var1);

    void handleSetBorderLerpSize(ClientboundSetBorderLerpSizePacket var1);

    void handleSetBorderSize(ClientboundSetBorderSizePacket var1);

    void handleSetBorderWarningDelay(ClientboundSetBorderWarningDelayPacket var1);

    void handleSetBorderWarningDistance(ClientboundSetBorderWarningDistancePacket var1);

    void handleSetBorderCenter(ClientboundSetBorderCenterPacket var1);

    void handleTabListCustomisation(ClientboundTabListPacket var1);

    void handleBossUpdate(ClientboundBossEventPacket var1);

    void handleItemCooldown(ClientboundCooldownPacket var1);

    void handleMoveVehicle(ClientboundMoveVehiclePacket var1);

    void handleUpdateAdvancementsPacket(ClientboundUpdateAdvancementsPacket var1);

    void handleSelectAdvancementsTab(ClientboundSelectAdvancementsTabPacket var1);

    void handlePlaceRecipe(ClientboundPlaceGhostRecipePacket var1);

    void handleCommands(ClientboundCommandsPacket var1);

    void handleStopSoundEvent(ClientboundStopSoundPacket var1);

    /**
     * This method is only called for manual tab-completion (the {@link net.minecraft.commands.synchronization.SuggestionProviders#ASK_SERVER minecraft:ask_server} suggestion provider).
     */
    void handleCommandSuggestions(ClientboundCommandSuggestionsPacket var1);

    void handleUpdateRecipes(ClientboundUpdateRecipesPacket var1);

    void handleLookAt(ClientboundPlayerLookAtPacket var1);

    void handleTagQueryPacket(ClientboundTagQueryPacket var1);

    void handleLightUpdatePacket(ClientboundLightUpdatePacket var1);

    void handleOpenBook(ClientboundOpenBookPacket var1);

    void handleOpenScreen(ClientboundOpenScreenPacket var1);

    void handleMerchantOffers(ClientboundMerchantOffersPacket var1);

    void handleSetChunkCacheRadius(ClientboundSetChunkCacheRadiusPacket var1);

    void handleSetSimulationDistance(ClientboundSetSimulationDistancePacket var1);

    void handleSetChunkCacheCenter(ClientboundSetChunkCacheCenterPacket var1);

    void handleBlockChangedAck(ClientboundBlockChangedAckPacket var1);

    void setActionBarText(ClientboundSetActionBarTextPacket var1);

    void setSubtitleText(ClientboundSetSubtitleTextPacket var1);

    void setTitleText(ClientboundSetTitleTextPacket var1);

    void setTitlesAnimation(ClientboundSetTitlesAnimationPacket var1);

    void handleTitlesClear(ClientboundClearTitlesPacket var1);

    void handleServerData(ClientboundServerDataPacket var1);

    void handleCustomChatCompletions(ClientboundCustomChatCompletionsPacket var1);

    void handleBundlePacket(ClientboundBundlePacket var1);

    void handleDamageEvent(ClientboundDamageEventPacket var1);

    void handleConfigurationStart(ClientboundStartConfigurationPacket var1);

    void handleChunkBatchStart(ClientboundChunkBatchStartPacket var1);

    void handleChunkBatchFinished(ClientboundChunkBatchFinishedPacket var1);

    void handleDebugSample(ClientboundDebugSamplePacket var1);

    void handleProjectilePowerPacket(ClientboundProjectilePowerPacket var1);
}
