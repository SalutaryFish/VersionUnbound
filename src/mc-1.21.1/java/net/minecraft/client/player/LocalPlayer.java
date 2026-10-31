package net.minecraft.client.player;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.inventory.CommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.HangingSignEditScreen;
import net.minecraft.client.gui.screens.inventory.JigsawBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.MinecartCommandBlockEditScreen;
import net.minecraft.client.gui.screens.inventory.SignEditScreen;
import net.minecraft.client.gui.screens.inventory.StructureBlockEditScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.AmbientSoundHandler;
import net.minecraft.client.resources.sounds.BiomeAmbientSoundsHandler;
import net.minecraft.client.resources.sounds.BubbleColumnAmbientSoundHandler;
import net.minecraft.client.resources.sounds.ElytraOnPlayerSoundInstance;
import net.minecraft.client.resources.sounds.RidingMinecartSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.UnderwaterAmbientSoundHandler;
import net.minecraft.client.resources.sounds.UnderwaterAmbientSoundInstances;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundMoveVehiclePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundRecipeBookSeenRecipePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.StatsCounter;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Portal;
import net.minecraft.world.level.block.entity.CommandBlockEntity;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.slf4j.Logger;

public class LocalPlayer extends AbstractClientPlayer {
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final int POSITION_REMINDER_INTERVAL = 20;
    private static final int WATER_VISION_MAX_TIME = 600;
    private static final int WATER_VISION_QUICK_TIME = 100;
    private static final float WATER_VISION_QUICK_PERCENT = 0.6F;
    private static final double SUFFOCATING_COLLISION_CHECK_SCALE = 0.35;
    private static final double MINOR_COLLISION_ANGLE_THRESHOLD_RADIAN = 0.13962634F;
    public final ClientPacketListener connection;
    private final StatsCounter stats;
    private final ClientRecipeBook recipeBook;
    private final List<AmbientSoundHandler> ambientSoundHandlers = Lists.newArrayList();
    private int permissionLevel = 0;
    /**
     * The last X position which was transmitted to the server, used to determine when the X position changes and needs to be re-transmitted
     */
    private double xLast;
    /**
     * The last Y position which was transmitted to the server, used to determine when the Y position changes and needs to be re-transmitted
     */
    private double yLast1;
    /**
     * The last Z position which was transmitted to the server, used to determine when the Z position changes and needs to be re-transmitted
     */
    private double zLast;
    /**
     * The last yaw value which was transmitted to the server, used to determine when the yaw changes and needs to be re-transmitted
     */
    private float yRotLast;
    /**
     * The last pitch value which was transmitted to the server, used to determine when the pitch changes and needs to be re-transmitted
     */
    private float xRotLast;
    private boolean lastOnGround;
    private boolean crouching;
    private boolean wasShiftKeyDown;
    /**
     * the last sprinting state sent to the server
     */
    private boolean wasSprinting;
    /**
     * Reset to 0 every time position is sent to the server, used to send periodic updates every 20 ticks even when the player is not moving.
     */
    private int positionReminder;
    private boolean flashOnSetHealth;
    public Input input;
    protected final Minecraft minecraft;
    protected int sprintTriggerTime;
    public float yBob;
    public float xBob;
    public float yBobO;
    public float xBobO;
    private int jumpRidingTicks;
    private float jumpRidingScale;
    public float spinningEffectIntensity;
    public float oSpinningEffectIntensity;
    private boolean startedUsingItem;
    @Nullable
    private InteractionHand usingItemHand;
    private boolean handsBusy;
    private boolean autoJumpEnabled = true;
    private int autoJumpTime;
    private boolean wasFallFlying;
    private int waterVisionTime;
    private boolean showDeathScreen = true;
    private boolean doLimitedCrafting = false;

    public LocalPlayer(
        Minecraft minecraft,
        ClientLevel clientLevel,
        ClientPacketListener connection,
        StatsCounter stats,
        ClientRecipeBook recipeBook,
        boolean wasShiftKeyDown,
        boolean wasSprinting
    ) {
        super(clientLevel, connection.getLocalGameProfile());
        this.minecraft = minecraft;
        this.connection = connection;
        this.stats = stats;
        this.recipeBook = recipeBook;
        this.wasShiftKeyDown = wasShiftKeyDown;
        this.wasSprinting = wasSprinting;
        this.ambientSoundHandlers.add(new UnderwaterAmbientSoundHandler(this, minecraft.getSoundManager()));
        this.ambientSoundHandlers.add(new BubbleColumnAmbientSoundHandler(this));
        this.ambientSoundHandlers.add(new BiomeAmbientSoundsHandler(this, minecraft.getSoundManager(), clientLevel.getBiomeManager()));
    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /**
     * Heal living entity (param: amount of half-hearts)
     */
    @Override
    public void heal(float healAmount) {
    }

    @Override
    public boolean startRiding(Entity entity, boolean force) {
        if (!super.startRiding(entity, force)) {
            return false;
        }

        if (entity instanceof AbstractMinecart) {
            this.minecraft.getSoundManager().play(new RidingMinecartSoundInstance(this, (AbstractMinecart)entity, true));
            this.minecraft.getSoundManager().play(new RidingMinecartSoundInstance(this, (AbstractMinecart)entity, false));
        }

        return true;
    }

    @Override
    public void removeVehicle() {
        super.removeVehicle();
        this.handsBusy = false;
    }

    /**
     * Gets the current pitch of the entity.
     */
    @Override
    public float getViewXRot(float partialTick) {
        return this.getXRot();
    }

    /**
     * Gets the current yaw of the entity
     */
    @Override
    public float getViewYRot(float partialTick) {
        return this.isPassenger() ? super.getViewYRot(partialTick) : this.getYRot();
    }

    @Override
    public void tick() {
        if (this.level().hasChunkAt(this.getBlockX(), this.getBlockZ())) {
            super.tick();
            if (this.isPassenger()) {
                this.connection.send(new ServerboundMovePlayerPacket.Rot(this.getYRot(), this.getXRot(), this.onGround()));
                this.connection.send(new ServerboundPlayerInputPacket(this.xxa, this.zza, this.input.jumping, this.input.shiftKeyDown));
                Entity entity = this.getRootVehicle();
                if (entity != this && entity.isControlledByLocalInstance()) {
                    this.connection.send(new ServerboundMoveVehiclePacket(entity));
                    this.sendIsSprintingIfNeeded();
                }
            } else {
                this.sendPosition();
            }

            for (AmbientSoundHandler ambientSoundHandler : this.ambientSoundHandlers) {
                ambientSoundHandler.tick();
            }
        }
    }

    public float getCurrentMood() {
        for (AmbientSoundHandler ambientSoundHandler : this.ambientSoundHandlers) {
            if (ambientSoundHandler instanceof BiomeAmbientSoundsHandler) {
                return ((BiomeAmbientSoundsHandler)ambientSoundHandler).getMoodiness();
            }
        }

        return 0.0F;
    }

    private void sendPosition() {
        this.sendIsSprintingIfNeeded();
        boolean bl = this.isShiftKeyDown();
        if (bl != this.wasShiftKeyDown) {
            ServerboundPlayerCommandPacket.Action action = bl
                ? ServerboundPlayerCommandPacket.Action.PRESS_SHIFT_KEY
                : ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY;
            this.connection.send(new ServerboundPlayerCommandPacket(this, action));
            this.wasShiftKeyDown = bl;
        }

        if (this.isControlledCamera()) {
            double d = this.getX() - this.xLast;
            double e = this.getY() - this.yLast1;
            double f = this.getZ() - this.zLast;
            double g = this.getYRot() - this.yRotLast;
            double h = this.getXRot() - this.xRotLast;
            this.positionReminder++;
            boolean bl2 = Mth.lengthSquared(d, e, f) > Mth.square(2.0E-4) || this.positionReminder >= 20;
            boolean bl3 = g != 0.0 || h != 0.0;
            if (this.isPassenger()) {
                Vec3 vec3 = this.getDeltaMovement();
                this.connection.send(new ServerboundMovePlayerPacket.PosRot(vec3.x, -999.0, vec3.z, this.getYRot(), this.getXRot(), this.onGround()));
                bl2 = false;
            } else if (bl2 && bl3) {
                this.connection
                    .send(new ServerboundMovePlayerPacket.PosRot(this.getX(), this.getY(), this.getZ(), this.getYRot(), this.getXRot(), this.onGround()));
            } else if (bl2) {
                this.connection.send(new ServerboundMovePlayerPacket.Pos(this.getX(), this.getY(), this.getZ(), this.onGround()));
            } else if (bl3) {
                this.connection.send(new ServerboundMovePlayerPacket.Rot(this.getYRot(), this.getXRot(), this.onGround()));
            } else if (this.lastOnGround != this.onGround()) {
                this.connection.send(new ServerboundMovePlayerPacket.StatusOnly(this.onGround()));
            }

            if (bl2) {
                this.xLast = this.getX();
                this.yLast1 = this.getY();
                this.zLast = this.getZ();
                this.positionReminder = 0;
            }

            if (bl3) {
                this.yRotLast = this.getYRot();
                this.xRotLast = this.getXRot();
            }

            this.lastOnGround = this.onGround();
            this.autoJumpEnabled = this.minecraft.options.autoJump().get();
        }
    }

    private void sendIsSprintingIfNeeded() {
        boolean bl = this.isSprinting();
        if (bl != this.wasSprinting) {
            ServerboundPlayerCommandPacket.Action action = bl
                ? ServerboundPlayerCommandPacket.Action.START_SPRINTING
                : ServerboundPlayerCommandPacket.Action.STOP_SPRINTING;
            this.connection.send(new ServerboundPlayerCommandPacket(this, action));
            this.wasSprinting = bl;
        }
    }

    public boolean drop(boolean fullStack) {
        ServerboundPlayerActionPacket.Action action = fullStack
            ? ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS
            : ServerboundPlayerActionPacket.Action.DROP_ITEM;
        ItemStack itemStack = this.getInventory().removeFromSelected(fullStack);
        this.connection.send(new ServerboundPlayerActionPacket(action, BlockPos.ZERO, Direction.DOWN));
        return !itemStack.isEmpty();
    }

    @Override
    public void swing(InteractionHand hand) {
        super.swing(hand);
        this.connection.send(new ServerboundSwingPacket(hand));
    }

    @Override
    public void respawn() {
        this.connection.send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
        KeyMapping.resetToggleKeys();
    }

    /**
     * Deals damage to the entity. This will take the armor of the entity into consideration before damaging the health bar.
     */
    @Override
    protected void actuallyHurt(DamageSource damageSrc, float damageAmount) {
        if (!this.isInvulnerableTo(damageSrc)) {
            this.setHealth(this.getHealth() - damageAmount);
        }
    }

    @Override
    public void closeContainer() {
        this.connection.send(new ServerboundContainerClosePacket(this.containerMenu.containerId));
        this.clientSideCloseContainer();
    }

    public void clientSideCloseContainer() {
        super.closeContainer();
        this.minecraft.setScreen(null);
    }

    /**
     * Updates health locally.
     */
    public void hurtTo(float health) {
        if (this.flashOnSetHealth) {
            float f = this.getHealth() - health;
            if (f <= 0.0F) {
                this.setHealth(health);
                if (f < 0.0F) {
                    this.invulnerableTime = 10;
                }
            } else {
                this.lastHurt = f;
                this.invulnerableTime = 20;
                this.setHealth(health);
                this.hurtDuration = 10;
                this.hurtTime = this.hurtDuration;
            }
        } else {
            this.setHealth(health);
            this.flashOnSetHealth = true;
        }
    }

    @Override
    public void onUpdateAbilities() {
        this.connection.send(new ServerboundPlayerAbilitiesPacket(this.getAbilities()));
    }

    @Override
    public boolean isLocalPlayer() {
        return true;
    }

    @Override
    public boolean isSuppressingSlidingDownLadder() {
        return !this.getAbilities().flying && super.isSuppressingSlidingDownLadder();
    }

    @Override
    public boolean canSpawnSprintParticle() {
        return !this.getAbilities().flying && super.canSpawnSprintParticle();
    }

    protected void sendRidingJump() {
        this.connection
            .send(
                new ServerboundPlayerCommandPacket(this, ServerboundPlayerCommandPacket.Action.START_RIDING_JUMP, Mth.floor(this.getJumpRidingScale() * 100.0F))
            );
    }

    public void sendOpenInventory() {
        this.connection.send(new ServerboundPlayerCommandPacket(this, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
    }

    public StatsCounter getStats() {
        return this.stats;
    }

    public ClientRecipeBook getRecipeBook() {
        return this.recipeBook;
    }

    public void removeRecipeHighlight(RecipeHolder<?> recipe) {
        if (this.recipeBook.willHighlight(recipe)) {
            this.recipeBook.removeHighlight(recipe);
            this.connection.send(new ServerboundRecipeBookSeenRecipePacket(recipe));
        }
    }

    @Override
    protected int getPermissionLevel() {
        return this.permissionLevel;
    }

    public void setPermissionLevel(int permissionLevel) {
        this.permissionLevel = permissionLevel;
    }

    @Override
    public void displayClientMessage(Component chatComponent, boolean actionBar) {
        this.minecraft.getChatListener().handleSystemMessage(chatComponent, actionBar);
    }

    private void moveTowardsClosestSpace(double x, double z) {
        BlockPos blockPos = BlockPos.containing(x, this.getY(), z);
        if (this.suffocatesAt(blockPos)) {
            double d = x - blockPos.getX();
            double e = z - blockPos.getZ();
            Direction direction = null;
            double f = Double.MAX_VALUE;
            Direction[] directions = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

            for (Direction direction2 : directions) {
                double g = direction2.getAxis().choose(d, 0.0, e);
                double h = direction2.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - g : g;
                if (h < f && !this.suffocatesAt(blockPos.relative(direction2))) {
                    f = h;
                    direction = direction2;
                }
            }

            if (direction != null) {
                Vec3 vec3 = this.getDeltaMovement();
                if (direction.getAxis() == Direction.Axis.X) {
                    this.setDeltaMovement(0.1 * direction.getStepX(), vec3.y, vec3.z);
                } else {
                    this.setDeltaMovement(vec3.x, vec3.y, 0.1 * direction.getStepZ());
                }
            }
        }
    }

    private boolean suffocatesAt(BlockPos pos) {
        AABB aABB = this.getBoundingBox();
        AABB aABB2 = new AABB(pos.getX(), aABB.minY, pos.getZ(), pos.getX() + 1.0, aABB.maxY, pos.getZ() + 1.0).deflate(1.0E-7);
        return this.level().collidesWithSuffocatingBlock(this, aABB2);
    }

    /**
     * Sets the current XP, total XP, and level number.
     */
    public void setExperienceValues(float currentXP, int maxXP, int level) {
        this.experienceProgress = currentXP;
        this.totalExperience = maxXP;
        this.experienceLevel = level;
    }

    @Override
    public void sendSystemMessage(Component component) {
        this.minecraft.gui.getChat().addMessage(component);
    }

    /**
     * Handler for {@link World#setEntityState}
     */
    @Override
    public void handleEntityEvent(byte id) {
        if (id >= 24 && id <= 28) {
            this.setPermissionLevel(id - 24);
        } else {
            super.handleEntityEvent(id);
        }
    }

    public void setShowDeathScreen(boolean show) {
        this.showDeathScreen = show;
    }

    public boolean shouldShowDeathScreen() {
        return this.showDeathScreen;
    }

    public void setDoLimitedCrafting(boolean doLimitedCrafting) {
        this.doLimitedCrafting = doLimitedCrafting;
    }

    public boolean getDoLimitedCrafting() {
        return this.doLimitedCrafting;
    }

    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), sound, this.getSoundSource(), volume, pitch, false);
    }

    @Override
    public void playNotifySound(SoundEvent sound, SoundSource source, float volume, float pitch) {
        this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), sound, source, volume, pitch, false);
    }

    @Override
    public boolean isEffectiveAi() {
        return true;
    }

    @Override
    public void startUsingItem(InteractionHand hand) {
        ItemStack itemStack = this.getItemInHand(hand);
        if (!itemStack.isEmpty() && !this.isUsingItem()) {
            super.startUsingItem(hand);
            this.startedUsingItem = true;
            this.usingItemHand = hand;
        }
    }

    @Override
    public boolean isUsingItem() {
        return this.startedUsingItem;
    }

    @Override
    public void stopUsingItem() {
        super.stopUsingItem();
        this.startedUsingItem = false;
    }

    @Override
    public InteractionHand getUsedItemHand() {
        return Objects.requireNonNullElse(this.usingItemHand, InteractionHand.MAIN_HAND);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_LIVING_ENTITY_FLAGS.equals(key)) {
            boolean bl = (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 1) > 0;
            InteractionHand interactionHand = (this.entityData.get(DATA_LIVING_ENTITY_FLAGS) & 2) > 0 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            if (bl && !this.startedUsingItem) {
                this.startUsingItem(interactionHand);
            } else if (!bl && this.startedUsingItem) {
                this.stopUsingItem();
            }
        }

        if (DATA_SHARED_FLAGS_ID.equals(key) && this.isFallFlying() && !this.wasFallFlying) {
            this.minecraft.getSoundManager().play(new ElytraOnPlayerSoundInstance(this));
        }
    }

    @Nullable
    public PlayerRideableJumping jumpableVehicle() {
        return this.getControlledVehicle() instanceof PlayerRideableJumping playerRideableJumping && playerRideableJumping.canJump()
            ? playerRideableJumping
            : null;
    }

    public float getJumpRidingScale() {
        return this.jumpRidingScale;
    }

    @Override
    public boolean isTextFilteringEnabled() {
        return this.minecraft.isTextFilteringEnabled();
    }

    @Override
    public void openTextEdit(SignBlockEntity signEntity, boolean isFrontText) {
        if (signEntity instanceof HangingSignBlockEntity hangingSignBlockEntity) {
            this.minecraft.setScreen(new HangingSignEditScreen(hangingSignBlockEntity, isFrontText, this.minecraft.isTextFilteringEnabled()));
        } else {
            this.minecraft.setScreen(new SignEditScreen(signEntity, isFrontText, this.minecraft.isTextFilteringEnabled()));
        }
    }

    @Override
    public void openMinecartCommandBlock(BaseCommandBlock commandBlock) {
        this.minecraft.setScreen(new MinecartCommandBlockEditScreen(commandBlock));
    }

    @Override
    public void openCommandBlock(CommandBlockEntity commandBlock) {
        this.minecraft.setScreen(new CommandBlockEditScreen(commandBlock));
    }

    @Override
    public void openStructureBlock(StructureBlockEntity structure) {
        this.minecraft.setScreen(new StructureBlockEditScreen(structure));
    }

    @Override
    public void openJigsawBlock(JigsawBlockEntity jigsawBlockEntity) {
        this.minecraft.setScreen(new JigsawBlockEditScreen(jigsawBlockEntity));
    }

    @Override
    public void openItemGui(ItemStack stack, InteractionHand hand) {
        if (stack.is(Items.WRITABLE_BOOK)) {
            this.minecraft.setScreen(new BookEditScreen(this, stack, hand));
        }
    }

    /**
     * Called when the entity is dealt a critical hit.
     */
    @Override
    public void crit(Entity entityHit) {
        this.minecraft.particleEngine.createTrackingEmitter(entityHit, ParticleTypes.CRIT);
    }

    /**
     * Called when the entity hit is dealt extra melee damage due to an enchantment.
     */
    @Override
    public void magicCrit(Entity entityHit) {
        this.minecraft.particleEngine.createTrackingEmitter(entityHit, ParticleTypes.ENCHANTED_HIT);
    }

    @Override
    public boolean isShiftKeyDown() {
        return this.input != null && this.input.shiftKeyDown;
    }

    @Override
    public boolean isCrouching() {
        return this.crouching;
    }

    public boolean isMovingSlowly() {
        return this.isCrouching() || this.isVisuallyCrawling();
    }

    @Override
    public void serverAiStep() {
        super.serverAiStep();
        if (this.isControlledCamera()) {
            this.xxa = this.input.leftImpulse;
            this.zza = this.input.forwardImpulse;
            this.jumping = this.input.jumping;
            this.yBobO = this.yBob;
            this.xBobO = this.xBob;
            this.xBob = this.xBob + (this.getXRot() - this.xBob) * 0.5F;
            this.yBob = this.yBob + (this.getYRot() - this.yBob) * 0.5F;
        }
    }

    protected boolean isControlledCamera() {
        return this.minecraft.getCameraEntity() == this;
    }

    public void resetPos() {
        this.setPose(Pose.STANDING);
        if (this.level() != null) {
            for (double d = this.getY(); d > this.level().getMinBuildHeight() && d < this.level().getMaxBuildHeight(); d++) {
                this.setPos(this.getX(), d, this.getZ());
                if (this.level().noCollision(this)) {
                    break;
                }
            }

            this.setDeltaMovement(Vec3.ZERO);
            this.setXRot(0.0F);
        }

        this.setHealth(this.getMaxHealth());
        this.deathTime = 0;
    }

    @Override
    public void aiStep() {
        if (this.sprintTriggerTime > 0) {
            this.sprintTriggerTime--;
        }

        if (!(this.minecraft.screen instanceof ReceivingLevelScreen)) {
            this.handleConfusionTransitionEffect(this.getActivePortalLocalTransition() == Portal.Transition.CONFUSION);
            this.processPortalCooldown();
        }

        boolean bl = this.input.jumping;
        boolean bl2 = this.input.shiftKeyDown;
        boolean bl3 = this.hasEnoughImpulseToStartSprinting();
        Abilities abilities = this.getAbilities();
        this.crouching = !abilities.flying
            && !this.isSwimming()
            && !this.isPassenger()
            && this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.CROUCHING)
            && (this.isShiftKeyDown() || !this.isSleeping() && !this.canPlayerFitWithinBlocksAndEntitiesWhen(Pose.STANDING));
        float f = (float)this.getAttributeValue(Attributes.SNEAKING_SPEED);
        this.input.tick(this.isMovingSlowly(), f);
        this.minecraft.getTutorial().onInput(this.input);
        if (this.isUsingItem() && !this.isPassenger()) {
            this.input.leftImpulse *= 0.2F;
            this.input.forwardImpulse *= 0.2F;
            this.sprintTriggerTime = 0;
        }

        boolean bl4 = false;
        if (this.autoJumpTime > 0) {
            this.autoJumpTime--;
            bl4 = true;
            this.input.jumping = true;
        }

        if (!this.noPhysics) {
            this.moveTowardsClosestSpace(this.getX() - this.getBbWidth() * 0.35, this.getZ() + this.getBbWidth() * 0.35);
            this.moveTowardsClosestSpace(this.getX() - this.getBbWidth() * 0.35, this.getZ() - this.getBbWidth() * 0.35);
            this.moveTowardsClosestSpace(this.getX() + this.getBbWidth() * 0.35, this.getZ() - this.getBbWidth() * 0.35);
            this.moveTowardsClosestSpace(this.getX() + this.getBbWidth() * 0.35, this.getZ() + this.getBbWidth() * 0.35);
        }

        if (bl2) {
            this.sprintTriggerTime = 0;
        }

        boolean bl5 = this.canStartSprinting();
        boolean bl6 = this.isPassenger() ? this.getVehicle().onGround() : this.onGround();
        boolean bl7 = !bl2 && !bl3;
        if ((bl6 || this.isUnderWater()) && bl7 && bl5) {
            if (this.sprintTriggerTime <= 0 && !this.minecraft.options.keySprint.isDown()) {
                this.sprintTriggerTime = 7;
            } else {
                this.setSprinting(true);
            }
        }

        if ((!this.isInWater() || this.isUnderWater()) && bl5 && this.minecraft.options.keySprint.isDown()) {
            this.setSprinting(true);
        }

        if (this.isSprinting()) {
            boolean bl8 = !this.input.hasForwardImpulse() || !this.hasEnoughFoodToStartSprinting();
            boolean bl9 = bl8 || this.horizontalCollision && !this.minorHorizontalCollision || this.isInWater() && !this.isUnderWater();
            if (this.isSwimming()) {
                if (!this.onGround() && !this.input.shiftKeyDown && bl8 || !this.isInWater()) {
                    this.setSprinting(false);
                }
            } else if (bl9) {
                this.setSprinting(false);
            }
        }

        boolean bl8 = false;
        if (abilities.mayfly) {
            if (this.minecraft.gameMode.isAlwaysFlying()) {
                if (!abilities.flying) {
                    abilities.flying = true;
                    bl8 = true;
                    this.onUpdateAbilities();
                }
            } else if (!bl && this.input.jumping && !bl4) {
                if (this.jumpTriggerTime == 0) {
                    this.jumpTriggerTime = 7;
                } else if (!this.isSwimming()) {
                    abilities.flying = !abilities.flying;
                    if (abilities.flying && this.onGround()) {
                        this.jumpFromGround();
                    }

                    bl8 = true;
                    this.onUpdateAbilities();
                    this.jumpTriggerTime = 0;
                }
            }
        }

        if (this.input.jumping && !bl8 && !bl && !abilities.flying && !this.isPassenger() && !this.onClimbable()) {
            ItemStack itemStack = this.getItemBySlot(EquipmentSlot.CHEST);
            if (itemStack.is(Items.ELYTRA) && ElytraItem.isFlyEnabled(itemStack) && this.tryToStartFallFlying()) {
                this.connection.send(new ServerboundPlayerCommandPacket(this, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            }
        }

        this.wasFallFlying = this.isFallFlying();
        if (this.isInWater() && this.input.shiftKeyDown && this.isAffectedByFluids()) {
            this.goDownInWater();
        }

        if (this.isEyeInFluid(FluidTags.WATER)) {
            int i = this.isSpectator() ? 10 : 1;
            this.waterVisionTime = Mth.clamp(this.waterVisionTime + i, 0, 600);
        } else if (this.waterVisionTime > 0) {
            this.isEyeInFluid(FluidTags.WATER);
            this.waterVisionTime = Mth.clamp(this.waterVisionTime - 10, 0, 600);
        }

        if (abilities.flying && this.isControlledCamera()) {
            int i = 0;
            if (this.input.shiftKeyDown) {
                i--;
            }

            if (this.input.jumping) {
                i++;
            }

            if (i != 0) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0, i * abilities.getFlyingSpeed() * 3.0F, 0.0));
            }
        }

        PlayerRideableJumping playerRideableJumping = this.jumpableVehicle();
        if (playerRideableJumping != null && playerRideableJumping.getJumpCooldown() == 0) {
            if (this.jumpRidingTicks < 0) {
                this.jumpRidingTicks++;
                if (this.jumpRidingTicks == 0) {
                    this.jumpRidingScale = 0.0F;
                }
            }

            if (bl && !this.input.jumping) {
                this.jumpRidingTicks = -10;
                playerRideableJumping.onPlayerJump(Mth.floor(this.getJumpRidingScale() * 100.0F));
                this.sendRidingJump();
            } else if (!bl && this.input.jumping) {
                this.jumpRidingTicks = 0;
                this.jumpRidingScale = 0.0F;
            } else if (bl) {
                this.jumpRidingTicks++;
                if (this.jumpRidingTicks < 10) {
                    this.jumpRidingScale = this.jumpRidingTicks * 0.1F;
                } else {
                    this.jumpRidingScale = 0.8F + 2.0F / (this.jumpRidingTicks - 9) * 0.1F;
                }
            }
        } else {
            this.jumpRidingScale = 0.0F;
        }

        super.aiStep();
        if (this.onGround() && abilities.flying && !this.minecraft.gameMode.isAlwaysFlying()) {
            abilities.flying = false;
            this.onUpdateAbilities();
        }
    }

    public Portal.Transition getActivePortalLocalTransition() {
        return this.portalProcess == null ? Portal.Transition.NONE : this.portalProcess.getPortalLocalTransition();
    }

    @Override
    protected void tickDeath() {
        this.deathTime++;
        if (this.deathTime == 20) {
            this.remove(Entity.RemovalReason.KILLED);
        }
    }

    private void handleConfusionTransitionEffect(boolean useConfusion) {
        this.oSpinningEffectIntensity = this.spinningEffectIntensity;
        float f = 0.0F;
        if (useConfusion && this.portalProcess != null && this.portalProcess.isInsidePortalThisTick()) {
            if (this.minecraft.screen != null
                && !this.minecraft.screen.isPauseScreen()
                && !(this.minecraft.screen instanceof DeathScreen)
                && !(this.minecraft.screen instanceof WinScreen)) {
                if (this.minecraft.screen instanceof AbstractContainerScreen) {
                    this.closeContainer();
                }

                this.minecraft.setScreen(null);
            }

            if (this.spinningEffectIntensity == 0.0F) {
                this.minecraft
                    .getSoundManager()
                    .play(SimpleSoundInstance.forLocalAmbience(SoundEvents.PORTAL_TRIGGER, this.random.nextFloat() * 0.4F + 0.8F, 0.25F));
            }

            f = 0.0125F;
            this.portalProcess.setAsInsidePortalThisTick(false);
        } else if (this.hasEffect(MobEffects.CONFUSION) && !this.getEffect(MobEffects.CONFUSION).endsWithin(60)) {
            f = 0.006666667F;
        } else if (this.spinningEffectIntensity > 0.0F) {
            f = -0.05F;
        }

        this.spinningEffectIntensity = Mth.clamp(this.spinningEffectIntensity + f, 0.0F, 1.0F);
    }

    @Override
    public void rideTick() {
        super.rideTick();
        this.handsBusy = false;
        if (this.getControlledVehicle() instanceof Boat boat) {
            boat.setInput(this.input.left, this.input.right, this.input.up, this.input.down);
            this.handsBusy = this.handsBusy | (this.input.left || this.input.right || this.input.up || this.input.down);
        }
    }

    public boolean isHandsBusy() {
        return this.handsBusy;
    }

    @Nullable
    @Override
    public MobEffectInstance removeEffectNoUpdate(Holder<MobEffect> effect) {
        if (effect.is(MobEffects.CONFUSION)) {
            this.oSpinningEffectIntensity = 0.0F;
            this.spinningEffectIntensity = 0.0F;
        }

        return super.removeEffectNoUpdate(effect);
    }

    @Override
    public void move(MoverType type, Vec3 pos) {
        double d = this.getX();
        double e = this.getZ();
        super.move(type, pos);
        this.updateAutoJump((float)(this.getX() - d), (float)(this.getZ() - e));
    }

    public boolean isAutoJumpEnabled() {
        return this.autoJumpEnabled;
    }

    protected void updateAutoJump(float movementX, float movementZ) {
        if (this.canAutoJump()) {
            Vec3 vec3 = this.position();
            Vec3 vec32 = vec3.add(movementX, 0.0, movementZ);
            Vec3 vec33 = new Vec3(movementX, 0.0, movementZ);
            float f = this.getSpeed();
            float g = (float)vec33.lengthSqr();
            if (g <= 0.001F) {
                Vec2 vec2 = this.input.getMoveVector();
                float h = f * vec2.x;
                float i = f * vec2.y;
                float j = Mth.sin(this.getYRot() * (float) (Math.PI / 180.0));
                float k = Mth.cos(this.getYRot() * (float) (Math.PI / 180.0));
                vec33 = new Vec3(h * k - i * j, vec33.y, i * k + h * j);
                g = (float)vec33.lengthSqr();
                if (g <= 0.001F) {
                    return;
                }
            }

            float l = Mth.invSqrt(g);
            Vec3 vec34 = vec33.scale(l);
            Vec3 vec35 = this.getForward();
            float j = (float)(vec35.x * vec34.x + vec35.z * vec34.z);
            if (!(j < -0.15F)) {
                CollisionContext collisionContext = CollisionContext.of(this);
                BlockPos blockPos = BlockPos.containing(this.getX(), this.getBoundingBox().maxY, this.getZ());
                BlockState blockState = this.level().getBlockState(blockPos);
                if (blockState.getCollisionShape(this.level(), blockPos, collisionContext).isEmpty()) {
                    blockPos = blockPos.above();
                    BlockState blockState2 = this.level().getBlockState(blockPos);
                    if (blockState2.getCollisionShape(this.level(), blockPos, collisionContext).isEmpty()) {
                        float m = 7.0F;
                        float n = 1.2F;
                        if (this.hasEffect(MobEffects.JUMP)) {
                            n += (this.getEffect(MobEffects.JUMP).getAmplifier() + 1) * 0.75F;
                        }

                        float o = Math.max(f * 7.0F, 1.0F / l);
                        Vec3 vec36 = vec3;
                        Vec3 vec37 = vec32.add(vec34.scale(o));
                        float p = this.getBbWidth();
                        float q = this.getBbHeight();
                        AABB aABB = new AABB(vec36, vec37.add(0.0, q, 0.0)).inflate(p, 0.0, p);
                        vec36 = vec36.add(0.0, 0.51F, 0.0);
                        vec37 = vec37.add(0.0, 0.51F, 0.0);
                        Vec3 vec38 = vec34.cross(new Vec3(0.0, 1.0, 0.0));
                        Vec3 vec39 = vec38.scale(p * 0.5F);
                        Vec3 vec310 = vec36.subtract(vec39);
                        Vec3 vec311 = vec37.subtract(vec39);
                        Vec3 vec312 = vec36.add(vec39);
                        Vec3 vec313 = vec37.add(vec39);
                        Iterable<VoxelShape> iterable = this.level().getCollisions(this, aABB);
                        Iterator<AABB> iterator = StreamSupport.stream(iterable.spliterator(), false)
                            .flatMap(voxelShapex -> voxelShapex.toAabbs().stream())
                            .iterator();
                        float r = Float.MIN_VALUE;

                        while (iterator.hasNext()) {
                            AABB aABB2 = iterator.next();
                            if (aABB2.intersects(vec310, vec311) || aABB2.intersects(vec312, vec313)) {
                                r = (float)aABB2.maxY;
                                Vec3 vec314 = aABB2.getCenter();
                                BlockPos blockPos2 = BlockPos.containing(vec314);

                                for (int s = 1; s < n; s++) {
                                    BlockPos blockPos3 = blockPos2.above(s);
                                    BlockState blockState3 = this.level().getBlockState(blockPos3);
                                    VoxelShape voxelShape;
                                    if (!(voxelShape = blockState3.getCollisionShape(this.level(), blockPos3, collisionContext)).isEmpty()) {
                                        r = (float)voxelShape.max(Direction.Axis.Y) + blockPos3.getY();
                                        if (r - this.getY() > n) {
                                            return;
                                        }
                                    }

                                    if (s > 1) {
                                        blockPos = blockPos.above();
                                        BlockState blockState4 = this.level().getBlockState(blockPos);
                                        if (!blockState4.getCollisionShape(this.level(), blockPos, collisionContext).isEmpty()) {
                                            return;
                                        }
                                    }
                                }
                                break;
                            }
                        }

                        if (r != Float.MIN_VALUE) {
                            float t = (float)(r - this.getY());
                            if (!(t <= 0.5F) && !(t > n)) {
                                this.autoJumpTime = 1;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    protected boolean isHorizontalCollisionMinor(Vec3 deltaMovement) {
        float f = this.getYRot() * (float) (Math.PI / 180.0);
        double d = Mth.sin(f);
        double e = Mth.cos(f);
        double g = this.xxa * e - this.zza * d;
        double h = this.zza * e + this.xxa * d;
        double i = Mth.square(g) + Mth.square(h);
        double j = Mth.square(deltaMovement.x) + Mth.square(deltaMovement.z);
        if (!(i < 1.0E-5F) && !(j < 1.0E-5F)) {
            double k = g * deltaMovement.x + h * deltaMovement.z;
            double l = Math.acos(k / Math.sqrt(i * j));
            return l < 0.13962634F;
        } else {
            return false;
        }
    }

    private boolean canAutoJump() {
        return this.isAutoJumpEnabled()
            && this.autoJumpTime <= 0
            && this.onGround()
            && !this.isStayingOnGroundSurface()
            && !this.isPassenger()
            && this.isMoving()
            && this.getBlockJumpFactor() >= 1.0;
    }

    private boolean isMoving() {
        Vec2 vec2 = this.input.getMoveVector();
        return vec2.x != 0.0F || vec2.y != 0.0F;
    }

    private boolean canStartSprinting() {
        return !this.isSprinting()
            && this.hasEnoughImpulseToStartSprinting()
            && this.hasEnoughFoodToStartSprinting()
            && !this.isUsingItem()
            && !this.hasEffect(MobEffects.BLINDNESS)
            && (!this.isPassenger() || this.vehicleCanSprint(this.getVehicle()))
            && !this.isFallFlying();
    }

    private boolean vehicleCanSprint(Entity vehicle) {
        return vehicle.canSprint() && vehicle.isControlledByLocalInstance();
    }

    private boolean hasEnoughImpulseToStartSprinting() {
        double d = 0.8;
        return this.isUnderWater() ? this.input.hasForwardImpulse() : this.input.forwardImpulse >= 0.8;
    }

    private boolean hasEnoughFoodToStartSprinting() {
        return this.isPassenger() || this.getFoodData().getFoodLevel() > 6.0F || this.getAbilities().mayfly;
    }

    public float getWaterVision() {
        if (!this.isEyeInFluid(FluidTags.WATER)) {
            return 0.0F;
        }

        float f = 600.0F;
        float g = 100.0F;
        if (this.waterVisionTime >= 600.0F) {
            return 1.0F;
        }

        float h = Mth.clamp(this.waterVisionTime / 100.0F, 0.0F, 1.0F);
        float i = this.waterVisionTime < 100.0F ? 0.0F : Mth.clamp((this.waterVisionTime - 100.0F) / 500.0F, 0.0F, 1.0F);
        return h * 0.6F + i * 0.39999998F;
    }

    public void onGameModeChanged(GameType gameMode) {
        if (gameMode == GameType.SPECTATOR) {
            this.setDeltaMovement(this.getDeltaMovement().with(Direction.Axis.Y, 0.0));
        }
    }

    @Override
    public boolean isUnderWater() {
        return this.wasUnderwater;
    }

    @Override
    protected boolean updateIsUnderwater() {
        boolean bl = this.wasUnderwater;
        boolean bl2 = super.updateIsUnderwater();
        if (this.isSpectator()) {
            return this.wasUnderwater;
        }

        if (!bl && bl2) {
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.AMBIENT, 1.0F, 1.0F, false);
            this.minecraft.getSoundManager().play(new UnderwaterAmbientSoundInstances.UnderwaterAmbientSoundInstance(this));
        }

        if (bl && !bl2) {
            this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.AMBIENT, 1.0F, 1.0F, false);
        }

        return this.wasUnderwater;
    }

    @Override
    public Vec3 getRopeHoldPosition(float partialTick) {
        if (this.minecraft.options.getCameraType().isFirstPerson()) {
            float f = Mth.lerp(partialTick * 0.5F, this.getYRot(), this.yRotO) * (float) (Math.PI / 180.0);
            float g = Mth.lerp(partialTick * 0.5F, this.getXRot(), this.xRotO) * (float) (Math.PI / 180.0);
            double d = this.getMainArm() == HumanoidArm.RIGHT ? -1.0 : 1.0;
            Vec3 vec3 = new Vec3(0.39 * d, -0.6, 0.3);
            return vec3.xRot(-g).yRot(-f).add(this.getEyePosition(partialTick));
        } else {
            return super.getRopeHoldPosition(partialTick);
        }
    }

    @Override
    public void updateTutorialInventoryAction(ItemStack carried, ItemStack clicked, ClickAction action) {
        this.minecraft.getTutorial().onInventoryAction(carried, clicked, action);
    }

    @Override
    public float getVisualRotationYInDegrees() {
        return this.getYRot();
    }
}
