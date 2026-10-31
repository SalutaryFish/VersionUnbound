package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.CrossbowItem;

public class AnimationUtils {
    public static void animateCrossbowHold(ModelPart rightArm, ModelPart leftArm, ModelPart head, boolean rightHanded) {
        ModelPart modelPart = rightHanded ? rightArm : leftArm;
        ModelPart modelPart2 = rightHanded ? leftArm : rightArm;
        modelPart.yRot = (rightHanded ? -0.3F : 0.3F) + head.yRot;
        modelPart2.yRot = (rightHanded ? 0.6F : -0.6F) + head.yRot;
        modelPart.xRot = (float) (-Math.PI / 2) + head.xRot + 0.1F;
        modelPart2.xRot = -1.5F + head.xRot;
    }

    public static void animateCrossbowCharge(ModelPart rightArm, ModelPart leftArm, LivingEntity livingEntity, boolean rightHanded) {
        ModelPart modelPart = rightHanded ? rightArm : leftArm;
        ModelPart modelPart2 = rightHanded ? leftArm : rightArm;
        modelPart.yRot = rightHanded ? -0.8F : 0.8F;
        modelPart.xRot = -0.97079635F;
        modelPart2.xRot = modelPart.xRot;
        float f = CrossbowItem.getChargeDuration(livingEntity.getUseItem(), livingEntity);
        float g = Mth.clamp(livingEntity.getTicksUsingItem(), 0.0F, f);
        float h = g / f;
        modelPart2.yRot = Mth.lerp(h, 0.4F, 0.85F) * (rightHanded ? 1 : -1);
        modelPart2.xRot = Mth.lerp(h, modelPart2.xRot, (float) (-Math.PI / 2));
    }

    public static <T extends Mob> void swingWeaponDown(ModelPart rightArm, ModelPart leftArm, T mob, float attackTime, float ageInTicks) {
        float f = Mth.sin(attackTime * (float) Math.PI);
        float g = Mth.sin((1.0F - (1.0F - attackTime) * (1.0F - attackTime)) * (float) Math.PI);
        rightArm.zRot = 0.0F;
        leftArm.zRot = 0.0F;
        rightArm.yRot = (float) (Math.PI / 20);
        leftArm.yRot = (float) (-Math.PI / 20);
        if (mob.getMainArm() == HumanoidArm.RIGHT) {
            rightArm.xRot = -1.8849558F + Mth.cos(ageInTicks * 0.09F) * 0.15F;
            leftArm.xRot = -0.0F + Mth.cos(ageInTicks * 0.19F) * 0.5F;
            rightArm.xRot += f * 2.2F - g * 0.4F;
            leftArm.xRot += f * 1.2F - g * 0.4F;
        } else {
            rightArm.xRot = -0.0F + Mth.cos(ageInTicks * 0.19F) * 0.5F;
            leftArm.xRot = -1.8849558F + Mth.cos(ageInTicks * 0.09F) * 0.15F;
            rightArm.xRot += f * 1.2F - g * 0.4F;
            leftArm.xRot += f * 2.2F - g * 0.4F;
        }

        bobArms(rightArm, leftArm, ageInTicks);
    }

    public static void bobModelPart(ModelPart modelPart, float ageInTicks, float multiplier) {
        modelPart.zRot = modelPart.zRot + multiplier * (Mth.cos(ageInTicks * 0.09F) * 0.05F + 0.05F);
        modelPart.xRot = modelPart.xRot + multiplier * (Mth.sin(ageInTicks * 0.067F) * 0.05F);
    }

    public static void bobArms(ModelPart rightArm, ModelPart leftArm, float ageInTicks) {
        bobModelPart(rightArm, ageInTicks, 1.0F);
        bobModelPart(leftArm, ageInTicks, -1.0F);
    }

    public static void animateZombieArms(ModelPart leftArm, ModelPart rightArm, boolean isAggressive, float attackTime, float ageInTicks) {
        float f = Mth.sin(attackTime * (float) Math.PI);
        float g = Mth.sin((1.0F - (1.0F - attackTime) * (1.0F - attackTime)) * (float) Math.PI);
        rightArm.zRot = 0.0F;
        leftArm.zRot = 0.0F;
        rightArm.yRot = -(0.1F - f * 0.6F);
        leftArm.yRot = 0.1F - f * 0.6F;
        float h = (float) -Math.PI / (isAggressive ? 1.5F : 2.25F);
        rightArm.xRot = h;
        leftArm.xRot = h;
        rightArm.xRot += f * 1.2F - g * 0.4F;
        leftArm.xRot += f * 1.2F - g * 0.4F;
        bobArms(rightArm, leftArm, ageInTicks);
    }
}
