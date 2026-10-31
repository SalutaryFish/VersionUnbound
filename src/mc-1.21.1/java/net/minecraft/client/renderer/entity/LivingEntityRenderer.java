package net.minecraft.client.renderer.entity;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.scores.Team;
import org.slf4j.Logger;

public abstract class LivingEntityRenderer<T extends LivingEntity, M extends EntityModel<T>> extends EntityRenderer<T> implements RenderLayerParent<T, M> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float EYE_BED_OFFSET = 0.1F;
    protected M model;
    protected final List<RenderLayer<T, M>> layers = Lists.newArrayList();

    public LivingEntityRenderer(EntityRendererProvider.Context context, M model, float shadowRadius) {
        super(context);
        this.model = model;
        this.shadowRadius = shadowRadius;
    }

    protected final boolean addLayer(RenderLayer<T, M> layer) {
        return this.layers.add(layer);
    }

    @Override
    public M getModel() {
        return this.model;
    }

    public void render(T entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        this.model.attackTime = this.getAttackAnim(entity, partialTicks);
        this.model.riding = entity.isPassenger();
        this.model.young = entity.isBaby();
        float f = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        float g = Mth.rotLerp(partialTicks, entity.yHeadRotO, entity.yHeadRot);
        float h = g - f;
        if (entity.isPassenger() && entity.getVehicle() instanceof LivingEntity livingEntity) {
            f = Mth.rotLerp(partialTicks, livingEntity.yBodyRotO, livingEntity.yBodyRot);
            h = g - f;
            float i = Mth.wrapDegrees(h);
            if (i < -85.0F) {
                i = -85.0F;
            }

            if (i >= 85.0F) {
                i = 85.0F;
            }

            f = g - i;
            if (i * i > 2500.0F) {
                f += i * 0.2F;
            }

            h = g - f;
        }

        float j = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());
        if (isEntityUpsideDown(entity)) {
            j *= -1.0F;
            h *= -1.0F;
        }

        h = Mth.wrapDegrees(h);
        if (entity.hasPose(Pose.SLEEPING)) {
            Direction direction = entity.getBedOrientation();
            if (direction != null) {
                float k = entity.getEyeHeight(Pose.STANDING) - 0.1F;
                poseStack.translate(-direction.getStepX() * k, 0.0F, -direction.getStepZ() * k);
            }
        }

        float i = entity.getScale();
        poseStack.scale(i, i, i);
        float k = this.getBob(entity, partialTicks);
        this.setupRotations(entity, poseStack, k, f, partialTicks, i);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        this.scale(entity, poseStack, partialTicks);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        float l = 0.0F;
        float m = 0.0F;
        if (!entity.isPassenger() && entity.isAlive()) {
            l = entity.walkAnimation.speed(partialTicks);
            m = entity.walkAnimation.position(partialTicks);
            if (entity.isBaby()) {
                m *= 3.0F;
            }

            if (l > 1.0F) {
                l = 1.0F;
            }
        }

        this.model.prepareMobModel(entity, m, l, partialTicks);
        this.model.setupAnim(entity, m, l, k, h, j);
        Minecraft minecraft = Minecraft.getInstance();
        boolean bl = this.isBodyVisible(entity);
        boolean bl2 = !bl && !entity.isInvisibleTo(minecraft.player);
        boolean bl3 = minecraft.shouldEntityAppearGlowing(entity);
        RenderType renderType = this.getRenderType(entity, bl, bl2, bl3);
        if (renderType != null) {
            VertexConsumer vertexConsumer = buffer.getBuffer(renderType);
            int n = getOverlayCoords(entity, this.getWhiteOverlayProgress(entity, partialTicks));
            this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, n, bl2 ? 654311423 : -1);
        }

        if (!entity.isSpectator()) {
            for (RenderLayer<T, M> renderLayer : this.layers) {
                renderLayer.render(poseStack, buffer, packedLight, entity, m, l, partialTicks, k, h, j);
            }
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Nullable
    protected RenderType getRenderType(T livingEntity, boolean bodyVisible, boolean translucent, boolean glowing) {
        ResourceLocation resourceLocation = this.getTextureLocation(livingEntity);
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(resourceLocation);
        } else if (bodyVisible) {
            return this.model.renderType(resourceLocation);
        } else {
            return glowing ? RenderType.outline(resourceLocation) : null;
        }
    }

    public static int getOverlayCoords(LivingEntity livingEntity, float u) {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(livingEntity.hurtTime > 0 || livingEntity.deathTime > 0));
    }

    protected boolean isBodyVisible(T livingEntity) {
        return !livingEntity.isInvisible();
    }

    private static float sleepDirectionToRotation(Direction facing) {
        switch (facing) {
            case SOUTH:
                return 90.0F;
            case WEST:
                return 0.0F;
            case NORTH:
                return 270.0F;
            case EAST:
                return 180.0F;
            default:
                return 0.0F;
        }
    }

    protected boolean isShaking(T entity) {
        return entity.isFullyFrozen();
    }

    protected void setupRotations(T entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale) {
        if (this.isShaking(entity)) {
            yBodyRot += (float)(Math.cos(entity.tickCount * 3.25) * Math.PI * 0.4F);
        }

        if (!entity.hasPose(Pose.SLEEPING)) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yBodyRot));
        }

        if (entity.deathTime > 0) {
            float f = (entity.deathTime + partialTick - 1.0F) / 20.0F * 1.6F;
            f = Mth.sqrt(f);
            if (f > 1.0F) {
                f = 1.0F;
            }

            poseStack.mulPose(Axis.ZP.rotationDegrees(f * this.getFlipDegrees(entity)));
        } else if (entity.isAutoSpinAttack()) {
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F - entity.getXRot()));
            poseStack.mulPose(Axis.YP.rotationDegrees((entity.tickCount + partialTick) * -75.0F));
        } else if (entity.hasPose(Pose.SLEEPING)) {
            Direction direction = entity.getBedOrientation();
            float g = direction != null ? sleepDirectionToRotation(direction) : yBodyRot;
            poseStack.mulPose(Axis.YP.rotationDegrees(g));
            poseStack.mulPose(Axis.ZP.rotationDegrees(this.getFlipDegrees(entity)));
            poseStack.mulPose(Axis.YP.rotationDegrees(270.0F));
        } else if (isEntityUpsideDown(entity)) {
            poseStack.translate(0.0F, (entity.getBbHeight() + 0.1F) / scale, 0.0F);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        }
    }

    /**
     * Returns where in the swing animation the living entity is (from 0 to 1).  Args : entity, partialTickTime
     */
    protected float getAttackAnim(T livingBase, float partialTickTime) {
        return livingBase.getAttackAnim(partialTickTime);
    }

    /**
     * Defines what float the third param in setRotationAngles of ModelBase is
     */
    protected float getBob(T livingBase, float partialTick) {
        return livingBase.tickCount + partialTick;
    }

    protected float getFlipDegrees(T livingEntity) {
        return 90.0F;
    }

    protected float getWhiteOverlayProgress(T livingEntity, float partialTicks) {
        return 0.0F;
    }

    protected void scale(T livingEntity, PoseStack poseStack, float partialTickTime) {
    }

    protected boolean shouldShowName(T entity) {
        double d = this.entityRenderDispatcher.distanceToSqr(entity);
        float f = entity.isDiscrete() ? 32.0F : 64.0F;
        if (d >= f * f) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer localPlayer = minecraft.player;
        boolean bl = !entity.isInvisibleTo(localPlayer);
        if (entity != localPlayer) {
            Team team = entity.getTeam();
            Team team2 = localPlayer.getTeam();
            if (team != null) {
                Team.Visibility visibility = team.getNameTagVisibility();
                switch (visibility) {
                    case ALWAYS:
                        return bl;
                    case NEVER:
                        return false;
                    case HIDE_FOR_OTHER_TEAMS:
                        return team2 == null ? bl : team.isAlliedTo(team2) && (team.canSeeFriendlyInvisibles() || bl);
                    case HIDE_FOR_OWN_TEAM:
                        return team2 == null ? bl : !team.isAlliedTo(team2) && bl;
                    default:
                        return true;
                }
            }
        }

        return Minecraft.renderNames() && entity != minecraft.getCameraEntity() && bl && !entity.isVehicle();
    }

    public static boolean isEntityUpsideDown(LivingEntity entity) {
        if (entity instanceof Player || entity.hasCustomName()) {
            String string = ChatFormatting.stripFormatting(entity.getName().getString());
            if ("Dinnerbone".equals(string) || "Grumm".equals(string)) {
                return !(entity instanceof Player) || ((Player)entity).isModelPartShown(PlayerModelPart.CAPE);
            }
        }

        return false;
    }

    protected float getShadowRadius(T entity) {
        return super.getShadowRadius(entity) * entity.getScale();
    }
}
