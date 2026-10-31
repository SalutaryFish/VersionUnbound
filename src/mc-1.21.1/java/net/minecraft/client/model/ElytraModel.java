package net.minecraft.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class ElytraModel<T extends LivingEntity> extends AgeableListModel<T> {
    private final ModelPart rightWing;
    private final ModelPart leftWing;

    public ElytraModel(ModelPart root) {
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        CubeDeformation cubeDeformation = new CubeDeformation(1.0F);
        partDefinition.addOrReplaceChild(
            "left_wing",
            CubeListBuilder.create().texOffs(22, 0).addBox(-10.0F, 0.0F, 0.0F, 10.0F, 20.0F, 2.0F, cubeDeformation),
            PartPose.offsetAndRotation(5.0F, 0.0F, 0.0F, (float) (Math.PI / 12), 0.0F, (float) (-Math.PI / 12))
        );
        partDefinition.addOrReplaceChild(
            "right_wing",
            CubeListBuilder.create().texOffs(22, 0).mirror().addBox(0.0F, 0.0F, 0.0F, 10.0F, 20.0F, 2.0F, cubeDeformation),
            PartPose.offsetAndRotation(-5.0F, 0.0F, 0.0F, (float) (Math.PI / 12), 0.0F, (float) (Math.PI / 12))
        );
        return LayerDefinition.create(meshDefinition, 64, 32);
    }

    @Override
    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of();
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(this.leftWing, this.rightWing);
    }

    /**
     * Sets this entity's model rotation angles
     */
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float f = (float) (Math.PI / 12);
        float g = (float) (-Math.PI / 12);
        float h = 0.0F;
        float i = 0.0F;
        if (entity.isFallFlying()) {
            float j = 1.0F;
            Vec3 vec3 = entity.getDeltaMovement();
            if (vec3.y < 0.0) {
                Vec3 vec32 = vec3.normalize();
                j = 1.0F - (float)Math.pow(-vec32.y, 1.5);
            }

            f = j * (float) (Math.PI / 9) + (1.0F - j) * f;
            g = j * (float) (-Math.PI / 2) + (1.0F - j) * g;
        } else if (entity.isCrouching()) {
            f = (float) (Math.PI * 2.0 / 9.0);
            g = (float) (-Math.PI / 4);
            h = 3.0F;
            i = 0.08726646F;
        }

        this.leftWing.y = h;
        if (entity instanceof AbstractClientPlayer abstractClientPlayer) {
            abstractClientPlayer.elytraRotX = abstractClientPlayer.elytraRotX + (f - abstractClientPlayer.elytraRotX) * 0.1F;
            abstractClientPlayer.elytraRotY = abstractClientPlayer.elytraRotY + (i - abstractClientPlayer.elytraRotY) * 0.1F;
            abstractClientPlayer.elytraRotZ = abstractClientPlayer.elytraRotZ + (g - abstractClientPlayer.elytraRotZ) * 0.1F;
            this.leftWing.xRot = abstractClientPlayer.elytraRotX;
            this.leftWing.yRot = abstractClientPlayer.elytraRotY;
            this.leftWing.zRot = abstractClientPlayer.elytraRotZ;
        } else {
            this.leftWing.xRot = f;
            this.leftWing.zRot = g;
            this.leftWing.yRot = i;
        }

        this.rightWing.yRot = -this.leftWing.yRot;
        this.rightWing.y = this.leftWing.y;
        this.rightWing.xRot = this.leftWing.xRot;
        this.rightWing.zRot = -this.leftWing.zRot;
    }
}
