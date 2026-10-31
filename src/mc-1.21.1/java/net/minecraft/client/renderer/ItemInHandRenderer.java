package net.minecraft.client.renderer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;

public class ItemInHandRenderer {
    private static final RenderType MAP_BACKGROUND = RenderType.text(ResourceLocation.withDefaultNamespace("textures/map/map_background.png"));
    private static final RenderType MAP_BACKGROUND_CHECKERBOARD = RenderType.text(
        ResourceLocation.withDefaultNamespace("textures/map/map_background_checkerboard.png")
    );
    private static final float ITEM_SWING_X_POS_SCALE = -0.4F;
    private static final float ITEM_SWING_Y_POS_SCALE = 0.2F;
    private static final float ITEM_SWING_Z_POS_SCALE = -0.2F;
    private static final float ITEM_HEIGHT_SCALE = -0.6F;
    private static final float ITEM_POS_X = 0.56F;
    private static final float ITEM_POS_Y = -0.52F;
    private static final float ITEM_POS_Z = -0.72F;
    private static final float ITEM_PRESWING_ROT_Y = 45.0F;
    private static final float ITEM_SWING_X_ROT_AMOUNT = -80.0F;
    private static final float ITEM_SWING_Y_ROT_AMOUNT = -20.0F;
    private static final float ITEM_SWING_Z_ROT_AMOUNT = -20.0F;
    private static final float EAT_JIGGLE_X_ROT_AMOUNT = 10.0F;
    private static final float EAT_JIGGLE_Y_ROT_AMOUNT = 90.0F;
    private static final float EAT_JIGGLE_Z_ROT_AMOUNT = 30.0F;
    private static final float EAT_JIGGLE_X_POS_SCALE = 0.6F;
    private static final float EAT_JIGGLE_Y_POS_SCALE = -0.5F;
    private static final float EAT_JIGGLE_Z_POS_SCALE = 0.0F;
    private static final double EAT_JIGGLE_EXPONENT = 27.0;
    private static final float EAT_EXTRA_JIGGLE_CUTOFF = 0.8F;
    private static final float EAT_EXTRA_JIGGLE_SCALE = 0.1F;
    private static final float ARM_SWING_X_POS_SCALE = -0.3F;
    private static final float ARM_SWING_Y_POS_SCALE = 0.4F;
    private static final float ARM_SWING_Z_POS_SCALE = -0.4F;
    private static final float ARM_SWING_Y_ROT_AMOUNT = 70.0F;
    private static final float ARM_SWING_Z_ROT_AMOUNT = -20.0F;
    private static final float ARM_HEIGHT_SCALE = -0.6F;
    private static final float ARM_POS_SCALE = 0.8F;
    private static final float ARM_POS_X = 0.8F;
    private static final float ARM_POS_Y = -0.75F;
    private static final float ARM_POS_Z = -0.9F;
    private static final float ARM_PRESWING_ROT_Y = 45.0F;
    private static final float ARM_PREROTATION_X_OFFSET = -1.0F;
    private static final float ARM_PREROTATION_Y_OFFSET = 3.6F;
    private static final float ARM_PREROTATION_Z_OFFSET = 3.5F;
    private static final float ARM_POSTROTATION_X_OFFSET = 5.6F;
    private static final int ARM_ROT_X = 200;
    private static final int ARM_ROT_Y = -135;
    private static final int ARM_ROT_Z = 120;
    private static final float MAP_SWING_X_POS_SCALE = -0.4F;
    private static final float MAP_SWING_Z_POS_SCALE = -0.2F;
    private static final float MAP_HANDS_POS_X = 0.0F;
    private static final float MAP_HANDS_POS_Y = 0.04F;
    private static final float MAP_HANDS_POS_Z = -0.72F;
    private static final float MAP_HANDS_HEIGHT_SCALE = -1.2F;
    private static final float MAP_HANDS_TILT_SCALE = -0.5F;
    private static final float MAP_PLAYER_PITCH_SCALE = 45.0F;
    private static final float MAP_HANDS_Z_ROT_AMOUNT = -85.0F;
    private static final float MAPHAND_X_ROT_AMOUNT = 45.0F;
    private static final float MAPHAND_Y_ROT_AMOUNT = 92.0F;
    private static final float MAPHAND_Z_ROT_AMOUNT = -41.0F;
    private static final float MAP_HAND_X_POS = 0.3F;
    private static final float MAP_HAND_Y_POS = -1.1F;
    private static final float MAP_HAND_Z_POS = 0.45F;
    private static final float MAP_SWING_X_ROT_AMOUNT = 20.0F;
    private static final float MAP_PRE_ROT_SCALE = 0.38F;
    private static final float MAP_GLOBAL_X_POS = -0.5F;
    private static final float MAP_GLOBAL_Y_POS = -0.5F;
    private static final float MAP_GLOBAL_Z_POS = 0.0F;
    private static final float MAP_FINAL_SCALE = 0.0078125F;
    private static final int MAP_BORDER = 7;
    private static final int MAP_HEIGHT = 128;
    private static final int MAP_WIDTH = 128;
    private static final float BOW_CHARGE_X_POS_SCALE = 0.0F;
    private static final float BOW_CHARGE_Y_POS_SCALE = 0.0F;
    private static final float BOW_CHARGE_Z_POS_SCALE = 0.04F;
    private static final float BOW_CHARGE_SHAKE_X_SCALE = 0.0F;
    private static final float BOW_CHARGE_SHAKE_Y_SCALE = 0.004F;
    private static final float BOW_CHARGE_SHAKE_Z_SCALE = 0.0F;
    private static final float BOW_CHARGE_Z_SCALE = 0.2F;
    private static final float BOW_MIN_SHAKE_CHARGE = 0.1F;
    private final Minecraft minecraft;
    private ItemStack mainHandItem = ItemStack.EMPTY;
    private ItemStack offHandItem = ItemStack.EMPTY;
    private float mainHandHeight;
    private float oMainHandHeight;
    private float offHandHeight;
    private float oOffHandHeight;
    private final EntityRenderDispatcher entityRenderDispatcher;
    private final ItemRenderer itemRenderer;

    public ItemInHandRenderer(Minecraft minecraft, EntityRenderDispatcher entityRenderDispatcher, ItemRenderer itemRenderer) {
        this.minecraft = minecraft;
        this.entityRenderDispatcher = entityRenderDispatcher;
        this.itemRenderer = itemRenderer;
    }

    public void renderItem(
        LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int seed
    ) {
        if (!itemStack.isEmpty()) {
            this.itemRenderer
                .renderStatic(
                    entity,
                    itemStack,
                    displayContext,
                    leftHand,
                    poseStack,
                    buffer,
                    entity.level(),
                    seed,
                    OverlayTexture.NO_OVERLAY,
                    entity.getId() + displayContext.ordinal()
                );
        }
    }

    /**
     * Return the angle to render the Map
     */
    private float calculateMapTilt(float pitch) {
        float f = 1.0F - pitch / 45.0F + 0.1F;
        f = Mth.clamp(f, 0.0F, 1.0F);
        return -Mth.cos(f * (float) Math.PI) * 0.5F + 0.5F;
    }

    private void renderMapHand(PoseStack poseStack, MultiBufferSource buffer, int packedLight, HumanoidArm side) {
        PlayerRenderer playerRenderer = (PlayerRenderer)this.entityRenderDispatcher.<AbstractClientPlayer>getRenderer(this.minecraft.player);
        poseStack.pushPose();
        float f = side == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.mulPose(Axis.YP.rotationDegrees(92.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(f * -41.0F));
        poseStack.translate(f * 0.3F, -1.1F, 0.45F);
        if (side == HumanoidArm.RIGHT) {
            playerRenderer.renderRightHand(poseStack, buffer, packedLight, this.minecraft.player);
        } else {
            playerRenderer.renderLeftHand(poseStack, buffer, packedLight, this.minecraft.player);
        }

        poseStack.popPose();
    }

    private void renderOneHandedMap(
        PoseStack poseStack, MultiBufferSource buffer, int packedLight, float equippedProgress, HumanoidArm hand, float swingProgress, ItemStack stack
    ) {
        float f = hand == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        poseStack.translate(f * 0.125F, -0.125F, 0.0F);
        if (!this.minecraft.player.isInvisible()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(f * 10.0F));
            this.renderPlayerArm(poseStack, buffer, packedLight, equippedProgress, swingProgress, hand);
            poseStack.popPose();
        }

        poseStack.pushPose();
        poseStack.translate(f * 0.51F, -0.08F + equippedProgress * -1.2F, -0.75F);
        float g = Mth.sqrt(swingProgress);
        float h = Mth.sin(g * (float) Math.PI);
        float i = -0.5F * h;
        float j = 0.4F * Mth.sin(g * (float) (Math.PI * 2));
        float k = -0.3F * Mth.sin(swingProgress * (float) Math.PI);
        poseStack.translate(f * i, j - 0.3F * h, k);
        poseStack.mulPose(Axis.XP.rotationDegrees(h * -45.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(f * h * -30.0F));
        this.renderMap(poseStack, buffer, packedLight, stack);
        poseStack.popPose();
    }

    private void renderTwoHandedMap(PoseStack poseStack, MultiBufferSource buffer, int packedLight, float pitch, float equippedProgress, float swingProgress) {
        float f = Mth.sqrt(swingProgress);
        float g = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
        float h = -0.4F * Mth.sin(f * (float) Math.PI);
        poseStack.translate(0.0F, -g / 2.0F, h);
        float i = this.calculateMapTilt(pitch);
        poseStack.translate(0.0F, 0.04F + equippedProgress * -1.2F + i * -0.5F, -0.72F);
        poseStack.mulPose(Axis.XP.rotationDegrees(i * -85.0F));
        if (!this.minecraft.player.isInvisible()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            this.renderMapHand(poseStack, buffer, packedLight, HumanoidArm.RIGHT);
            this.renderMapHand(poseStack, buffer, packedLight, HumanoidArm.LEFT);
            poseStack.popPose();
        }

        float j = Mth.sin(f * (float) Math.PI);
        poseStack.mulPose(Axis.XP.rotationDegrees(j * 20.0F));
        poseStack.scale(2.0F, 2.0F, 2.0F);
        this.renderMap(poseStack, buffer, packedLight, this.mainHandItem);
    }

    private void renderMap(PoseStack poseStack, MultiBufferSource buffer, int packedLight, ItemStack stack) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        poseStack.scale(0.38F, 0.38F, 0.38F);
        poseStack.translate(-0.5F, -0.5F, 0.0F);
        poseStack.scale(0.0078125F, 0.0078125F, 0.0078125F);
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapItemSavedData = MapItem.getSavedData(mapId, this.minecraft.level);
        VertexConsumer vertexConsumer = buffer.getBuffer(mapItemSavedData == null ? MAP_BACKGROUND : MAP_BACKGROUND_CHECKERBOARD);
        Matrix4f matrix4f = poseStack.last().pose();
        vertexConsumer.addVertex(matrix4f, -7.0F, 135.0F, 0.0F).setColor(-1).setUv(0.0F, 1.0F).setLight(packedLight);
        vertexConsumer.addVertex(matrix4f, 135.0F, 135.0F, 0.0F).setColor(-1).setUv(1.0F, 1.0F).setLight(packedLight);
        vertexConsumer.addVertex(matrix4f, 135.0F, -7.0F, 0.0F).setColor(-1).setUv(1.0F, 0.0F).setLight(packedLight);
        vertexConsumer.addVertex(matrix4f, -7.0F, -7.0F, 0.0F).setColor(-1).setUv(0.0F, 0.0F).setLight(packedLight);
        if (mapItemSavedData != null) {
            this.minecraft.gameRenderer.getMapRenderer().render(poseStack, buffer, mapId, mapItemSavedData, false, packedLight);
        }
    }

    private void renderPlayerArm(PoseStack poseStack, MultiBufferSource buffer, int packedLight, float equippedProgress, float swingProgress, HumanoidArm side) {
        boolean bl = side != HumanoidArm.LEFT;
        float f = bl ? 1.0F : -1.0F;
        float g = Mth.sqrt(swingProgress);
        float h = -0.3F * Mth.sin(g * (float) Math.PI);
        float i = 0.4F * Mth.sin(g * (float) (Math.PI * 2));
        float j = -0.4F * Mth.sin(swingProgress * (float) Math.PI);
        poseStack.translate(f * (h + 0.64000005F), i + -0.6F + equippedProgress * -0.6F, j + -0.71999997F);
        poseStack.mulPose(Axis.YP.rotationDegrees(f * 45.0F));
        float k = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        float l = Mth.sin(g * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(f * l * 70.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(f * k * -20.0F));
        AbstractClientPlayer abstractClientPlayer = this.minecraft.player;
        poseStack.translate(f * -1.0F, 3.6F, 3.5F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(f * 120.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(200.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(f * -135.0F));
        poseStack.translate(f * 5.6F, 0.0F, 0.0F);
        PlayerRenderer playerRenderer = (PlayerRenderer)this.entityRenderDispatcher.<AbstractClientPlayer>getRenderer(abstractClientPlayer);
        if (bl) {
            playerRenderer.renderRightHand(poseStack, buffer, packedLight, abstractClientPlayer);
        } else {
            playerRenderer.renderLeftHand(poseStack, buffer, packedLight, abstractClientPlayer);
        }
    }

    private void applyEatTransform(PoseStack poseStack, float partialTick, HumanoidArm arm, ItemStack stack, Player player) {
        float f = player.getUseItemRemainingTicks() - partialTick + 1.0F;
        float g = f / stack.getUseDuration(player);
        if (g < 0.8F) {
            float h = Mth.abs(Mth.cos(f / 4.0F * (float) Math.PI) * 0.1F);
            poseStack.translate(0.0F, h, 0.0F);
        }

        float h = 1.0F - (float)Math.pow(g, 27.0);
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(h * 0.6F * i, h * -0.5F, h * 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(i * h * 90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(h * 10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(i * h * 30.0F));
    }

    private void applyBrushTransform(PoseStack poseStack, float partialTick, HumanoidArm arm, ItemStack stack, Player player, float equippedProgress) {
        this.applyItemArmTransform(poseStack, arm, equippedProgress);
        float f = player.getUseItemRemainingTicks() % 10;
        float g = f - partialTick + 1.0F;
        float h = 1.0F - g / 10.0F;
        float i = -90.0F;
        float j = 60.0F;
        float k = 150.0F;
        float l = -15.0F;
        int m = 2;
        float n = -15.0F + 75.0F * Mth.cos(h * 2.0F * (float) Math.PI);
        if (arm != HumanoidArm.RIGHT) {
            poseStack.translate(0.1, 0.83, 0.35);
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(n));
            poseStack.translate(-0.3, 0.22, 0.35);
        } else {
            poseStack.translate(-0.25, 0.22, 0.35);
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(0.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(n));
        }
    }

    private void applyItemArmAttackTransform(PoseStack poseStack, HumanoidArm hand, float swingProgress) {
        int i = hand == HumanoidArm.RIGHT ? 1 : -1;
        float f = Mth.sin(swingProgress * swingProgress * (float) Math.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(i * (45.0F + f * -20.0F)));
        float g = Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
        poseStack.mulPose(Axis.ZP.rotationDegrees(i * g * -20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(g * -80.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(i * -45.0F));
    }

    private void applyItemArmTransform(PoseStack poseStack, HumanoidArm hand, float equippedProg) {
        int i = hand == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(i * 0.56F, -0.52F + equippedProg * -0.6F, -0.72F);
    }

    public void renderHandsWithItems(
        float partialTicks, PoseStack poseStack, MultiBufferSource.BufferSource buffer, LocalPlayer playerEntity, int combinedLight
    ) {
        float f = playerEntity.getAttackAnim(partialTicks);
        InteractionHand interactionHand = MoreObjects.firstNonNull(playerEntity.swingingArm, InteractionHand.MAIN_HAND);
        float g = Mth.lerp(partialTicks, playerEntity.xRotO, playerEntity.getXRot());
        ItemInHandRenderer.HandRenderSelection handRenderSelection = evaluateWhichHandsToRender(playerEntity);
        float h = Mth.lerp(partialTicks, playerEntity.xBobO, playerEntity.xBob);
        float i = Mth.lerp(partialTicks, playerEntity.yBobO, playerEntity.yBob);
        poseStack.mulPose(Axis.XP.rotationDegrees((playerEntity.getViewXRot(partialTicks) - h) * 0.1F));
        poseStack.mulPose(Axis.YP.rotationDegrees((playerEntity.getViewYRot(partialTicks) - i) * 0.1F));
        if (handRenderSelection.renderMainHand) {
            float j = interactionHand == InteractionHand.MAIN_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(partialTicks, this.oMainHandHeight, this.mainHandHeight);
            this.renderArmWithItem(playerEntity, partialTicks, g, InteractionHand.MAIN_HAND, j, this.mainHandItem, k, poseStack, buffer, combinedLight);
        }

        if (handRenderSelection.renderOffHand) {
            float j = interactionHand == InteractionHand.OFF_HAND ? f : 0.0F;
            float k = 1.0F - Mth.lerp(partialTicks, this.oOffHandHeight, this.offHandHeight);
            this.renderArmWithItem(playerEntity, partialTicks, g, InteractionHand.OFF_HAND, j, this.offHandItem, k, poseStack, buffer, combinedLight);
        }

        buffer.endBatch();
    }

    @VisibleForTesting
    static ItemInHandRenderer.HandRenderSelection evaluateWhichHandsToRender(LocalPlayer player) {
        ItemStack itemStack = player.getMainHandItem();
        ItemStack itemStack2 = player.getOffhandItem();
        boolean bl = itemStack.is(Items.BOW) || itemStack2.is(Items.BOW);
        boolean bl2 = itemStack.is(Items.CROSSBOW) || itemStack2.is(Items.CROSSBOW);
        if (!bl && !bl2) {
            return ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
        } else if (player.isUsingItem()) {
            return selectionUsingItemWhileHoldingBowLike(player);
        } else {
            return isChargedCrossbow(itemStack)
                ? ItemInHandRenderer.HandRenderSelection.RENDER_MAIN_HAND_ONLY
                : ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
        }
    }

    private static ItemInHandRenderer.HandRenderSelection selectionUsingItemWhileHoldingBowLike(LocalPlayer player) {
        ItemStack itemStack = player.getUseItem();
        InteractionHand interactionHand = player.getUsedItemHand();
        if (!itemStack.is(Items.BOW) && !itemStack.is(Items.CROSSBOW)) {
            return interactionHand == InteractionHand.MAIN_HAND && isChargedCrossbow(player.getOffhandItem())
                ? ItemInHandRenderer.HandRenderSelection.RENDER_MAIN_HAND_ONLY
                : ItemInHandRenderer.HandRenderSelection.RENDER_BOTH_HANDS;
        } else {
            return ItemInHandRenderer.HandRenderSelection.onlyForHand(interactionHand);
        }
    }

    private static boolean isChargedCrossbow(ItemStack stack) {
        return stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack);
    }

    private void renderArmWithItem(
        AbstractClientPlayer player,
        float partialTicks,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equippedProgress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int combinedLight
    ) {
        if (!player.isScoping()) {
            boolean bl = hand == InteractionHand.MAIN_HAND;
            HumanoidArm humanoidArm = bl ? player.getMainArm() : player.getMainArm().getOpposite();
            poseStack.pushPose();
            if (stack.isEmpty()) {
                if (bl && !player.isInvisible()) {
                    this.renderPlayerArm(poseStack, buffer, combinedLight, equippedProgress, swingProgress, humanoidArm);
                }
            } else if (stack.is(Items.FILLED_MAP)) {
                if (bl && this.offHandItem.isEmpty()) {
                    this.renderTwoHandedMap(poseStack, buffer, combinedLight, pitch, equippedProgress, swingProgress);
                } else {
                    this.renderOneHandedMap(poseStack, buffer, combinedLight, equippedProgress, humanoidArm, swingProgress, stack);
                }
            } else if (stack.is(Items.CROSSBOW)) {
                boolean bl2 = CrossbowItem.isCharged(stack);
                boolean bl3 = humanoidArm == HumanoidArm.RIGHT;
                int i = bl3 ? 1 : -1;
                if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
                    this.applyItemArmTransform(poseStack, humanoidArm, equippedProgress);
                    poseStack.translate(i * -0.4785682F, -0.094387F, 0.05731531F);
                    poseStack.mulPose(Axis.XP.rotationDegrees(-11.935F));
                    poseStack.mulPose(Axis.YP.rotationDegrees(i * 65.3F));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(i * -9.785F));
                    float f = stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTicks + 1.0F);
                    float g = f / CrossbowItem.getChargeDuration(stack, player);
                    if (g > 1.0F) {
                        g = 1.0F;
                    }

                    if (g > 0.1F) {
                        float h = Mth.sin((f - 0.1F) * 1.3F);
                        float j = g - 0.1F;
                        float k = h * j;
                        poseStack.translate(k * 0.0F, k * 0.004F, k * 0.0F);
                    }

                    poseStack.translate(g * 0.0F, g * 0.0F, g * 0.04F);
                    poseStack.scale(1.0F, 1.0F, 1.0F + g * 0.2F);
                    poseStack.mulPose(Axis.YN.rotationDegrees(i * 45.0F));
                } else {
                    float f = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                    float g = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * (float) (Math.PI * 2));
                    float h = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
                    poseStack.translate(i * f, g, h);
                    this.applyItemArmTransform(poseStack, humanoidArm, equippedProgress);
                    this.applyItemArmAttackTransform(poseStack, humanoidArm, swingProgress);
                    if (bl2 && swingProgress < 0.001F && bl) {
                        poseStack.translate(i * -0.641864F, 0.0F, 0.0F);
                        poseStack.mulPose(Axis.YP.rotationDegrees(i * 10.0F));
                    }
                }

                this.renderItem(
                    player,
                    stack,
                    bl3 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !bl3,
                    poseStack,
                    buffer,
                    combinedLight
                );
            } else {
                boolean bl2 = humanoidArm == HumanoidArm.RIGHT;
                if (player.isUsingItem() && player.getUseItemRemainingTicks() > 0 && player.getUsedItemHand() == hand) {
                    int l = bl2 ? 1 : -1;
                    switch (stack.getUseAnimation()) {
                        case NONE:
                            this.applyItemArmTransform(poseStack, humanoidArm, equippedProgress);
                            break;
                        case EAT:
                        case DRINK:
                            this.applyEatTransform(poseStack, partialTicks, humanoidArm, stack, player);
                            this.applyItemArmTransform(poseStack, humanoidArm, equippedProgress);
                            break;
                        case BLOCK:
                            this.applyItemArmTransform(poseStack, humanoidArm, equippedProgress);
                            break;
                        case BOW:
                            this.applyItemArmTransform(poseStack, humanoidArm, equippedProgress);
                            poseStack.translate(l * -0.2785682F, 0.18344387F, 0.15731531F);
                            poseStack.mulPose(Axis.XP.rotationDegrees(-13.935F));
                            poseStack.mulPose(Axis.YP.rotationDegrees(l * 35.3F));
                            poseStack.mulPose(Axis.ZP.rotationDegrees(l * -9.785F));
                            float m = stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTicks + 1.0F);
                            float f = m / 20.0F;
                            f = (f * f + f * 2.0F) / 3.0F;
                            if (f > 1.0F) {
                                f = 1.0F;
                            }

                            if (f > 0.1F) {
                                float g = Mth.sin((m - 0.1F) * 1.3F);
                                float h = f - 0.1F;
                                float j = g * h;
                                poseStack.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                            }

                            poseStack.translate(f * 0.0F, f * 0.0F, f * 0.04F);
                            poseStack.scale(1.0F, 1.0F, 1.0F + f * 0.2F);
                            poseStack.mulPose(Axis.YN.rotationDegrees(l * 45.0F));
                            break;
                        case SPEAR:
                            this.applyItemArmTransform(poseStack, humanoidArm, equippedProgress);
                            poseStack.translate(l * -0.5F, 0.7F, 0.1F);
                            poseStack.mulPose(Axis.XP.rotationDegrees(-55.0F));
                            poseStack.mulPose(Axis.YP.rotationDegrees(l * 35.3F));
                            poseStack.mulPose(Axis.ZP.rotationDegrees(l * -9.785F));
                            float m = stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTicks + 1.0F);
                            float f = m / 10.0F;
                            if (f > 1.0F) {
                                f = 1.0F;
                            }

                            if (f > 0.1F) {
                                float g = Mth.sin((m - 0.1F) * 1.3F);
                                float h = f - 0.1F;
                                float j = g * h;
                                poseStack.translate(j * 0.0F, j * 0.004F, j * 0.0F);
                            }

                            poseStack.translate(0.0F, 0.0F, f * 0.2F);
                            poseStack.scale(1.0F, 1.0F, 1.0F + f * 0.2F);
                            poseStack.mulPose(Axis.YN.rotationDegrees(l * 45.0F));
                            break;
                        case BRUSH:
                            this.applyBrushTransform(poseStack, partialTicks, humanoidArm, stack, player, equippedProgress);
                    }
                } else if (player.isAutoSpinAttack()) {
                    this.applyItemArmTransform(poseStack, humanoidArm, equippedProgress);
                    int l = bl2 ? 1 : -1;
                    poseStack.translate(l * -0.4F, 0.8F, 0.3F);
                    poseStack.mulPose(Axis.YP.rotationDegrees(l * 65.0F));
                    poseStack.mulPose(Axis.ZP.rotationDegrees(l * -85.0F));
                } else {
                    float n = -0.4F * Mth.sin(Mth.sqrt(swingProgress) * (float) Math.PI);
                    float m = 0.2F * Mth.sin(Mth.sqrt(swingProgress) * (float) (Math.PI * 2));
                    float f = -0.2F * Mth.sin(swingProgress * (float) Math.PI);
                    int o = bl2 ? 1 : -1;
                    poseStack.translate(o * n, m, f);
                    this.applyItemArmTransform(poseStack, humanoidArm, equippedProgress);
                    this.applyItemArmAttackTransform(poseStack, humanoidArm, swingProgress);
                }

                this.renderItem(
                    player,
                    stack,
                    bl2 ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !bl2,
                    poseStack,
                    buffer,
                    combinedLight
                );
            }

            poseStack.popPose();
        }
    }

    public void tick() {
        this.oMainHandHeight = this.mainHandHeight;
        this.oOffHandHeight = this.offHandHeight;
        LocalPlayer localPlayer = this.minecraft.player;
        ItemStack itemStack = localPlayer.getMainHandItem();
        ItemStack itemStack2 = localPlayer.getOffhandItem();
        if (ItemStack.matches(this.mainHandItem, itemStack)) {
            this.mainHandItem = itemStack;
        }

        if (ItemStack.matches(this.offHandItem, itemStack2)) {
            this.offHandItem = itemStack2;
        }

        if (localPlayer.isHandsBusy()) {
            this.mainHandHeight = Mth.clamp(this.mainHandHeight - 0.4F, 0.0F, 1.0F);
            this.offHandHeight = Mth.clamp(this.offHandHeight - 0.4F, 0.0F, 1.0F);
        } else {
            float f = localPlayer.getAttackStrengthScale(1.0F);
            this.mainHandHeight = this.mainHandHeight + Mth.clamp((this.mainHandItem == itemStack ? f * f * f : 0.0F) - this.mainHandHeight, -0.4F, 0.4F);
            this.offHandHeight = this.offHandHeight + Mth.clamp((this.offHandItem == itemStack2 ? 1 : 0) - this.offHandHeight, -0.4F, 0.4F);
        }

        if (this.mainHandHeight < 0.1F) {
            this.mainHandItem = itemStack;
        }

        if (this.offHandHeight < 0.1F) {
            this.offHandItem = itemStack2;
        }
    }

    public void itemUsed(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            this.mainHandHeight = 0.0F;
        } else {
            this.offHandHeight = 0.0F;
        }
    }

    @VisibleForTesting
    enum HandRenderSelection {
        RENDER_BOTH_HANDS(true, true),
        RENDER_MAIN_HAND_ONLY(true, false),
        RENDER_OFF_HAND_ONLY(false, true);

        final boolean renderMainHand;
        final boolean renderOffHand;

        HandRenderSelection(final boolean renderMainHand, final boolean renderOffHand) {
            this.renderMainHand = renderMainHand;
            this.renderOffHand = renderOffHand;
        }

        public static ItemInHandRenderer.HandRenderSelection onlyForHand(InteractionHand hand) {
            return hand == InteractionHand.MAIN_HAND ? RENDER_MAIN_HAND_ONLY : RENDER_OFF_HAND_ONLY;
        }
    }
}
