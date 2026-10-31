package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class SpiderModel<T extends Entity> extends HierarchicalModel<T> {
    private static final String BODY_0 = "body0";
    private static final String BODY_1 = "body1";
    private static final String RIGHT_MIDDLE_FRONT_LEG = "right_middle_front_leg";
    private static final String LEFT_MIDDLE_FRONT_LEG = "left_middle_front_leg";
    private static final String RIGHT_MIDDLE_HIND_LEG = "right_middle_hind_leg";
    private static final String LEFT_MIDDLE_HIND_LEG = "left_middle_hind_leg";
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart rightHindLeg;
    private final ModelPart leftHindLeg;
    private final ModelPart rightMiddleHindLeg;
    private final ModelPart leftMiddleHindLeg;
    private final ModelPart rightMiddleFrontLeg;
    private final ModelPart leftMiddleFrontLeg;
    private final ModelPart rightFrontLeg;
    private final ModelPart leftFrontLeg;

    public SpiderModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.rightHindLeg = root.getChild("right_hind_leg");
        this.leftHindLeg = root.getChild("left_hind_leg");
        this.rightMiddleHindLeg = root.getChild("right_middle_hind_leg");
        this.leftMiddleHindLeg = root.getChild("left_middle_hind_leg");
        this.rightMiddleFrontLeg = root.getChild("right_middle_front_leg");
        this.leftMiddleFrontLeg = root.getChild("left_middle_front_leg");
        this.rightFrontLeg = root.getChild("right_front_leg");
        this.leftFrontLeg = root.getChild("left_front_leg");
    }

    public static LayerDefinition createSpiderBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        int i = 15;
        partDefinition.addOrReplaceChild(
            "head", CubeListBuilder.create().texOffs(32, 4).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F), PartPose.offset(0.0F, 15.0F, -3.0F)
        );
        partDefinition.addOrReplaceChild(
            "body0", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.offset(0.0F, 15.0F, 0.0F)
        );
        partDefinition.addOrReplaceChild(
            "body1", CubeListBuilder.create().texOffs(0, 12).addBox(-5.0F, -4.0F, -6.0F, 10.0F, 8.0F, 12.0F), PartPose.offset(0.0F, 15.0F, 9.0F)
        );
        CubeListBuilder cubeListBuilder = CubeListBuilder.create().texOffs(18, 0).addBox(-15.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F);
        CubeListBuilder cubeListBuilder2 = CubeListBuilder.create().texOffs(18, 0).mirror().addBox(-1.0F, -1.0F, -1.0F, 16.0F, 2.0F, 2.0F);
        partDefinition.addOrReplaceChild("right_hind_leg", cubeListBuilder, PartPose.offset(-4.0F, 15.0F, 2.0F));
        partDefinition.addOrReplaceChild("left_hind_leg", cubeListBuilder2, PartPose.offset(4.0F, 15.0F, 2.0F));
        partDefinition.addOrReplaceChild("right_middle_hind_leg", cubeListBuilder, PartPose.offset(-4.0F, 15.0F, 1.0F));
        partDefinition.addOrReplaceChild("left_middle_hind_leg", cubeListBuilder2, PartPose.offset(4.0F, 15.0F, 1.0F));
        partDefinition.addOrReplaceChild("right_middle_front_leg", cubeListBuilder, PartPose.offset(-4.0F, 15.0F, 0.0F));
        partDefinition.addOrReplaceChild("left_middle_front_leg", cubeListBuilder2, PartPose.offset(4.0F, 15.0F, 0.0F));
        partDefinition.addOrReplaceChild("right_front_leg", cubeListBuilder, PartPose.offset(-4.0F, 15.0F, -1.0F));
        partDefinition.addOrReplaceChild("left_front_leg", cubeListBuilder2, PartPose.offset(4.0F, 15.0F, -1.0F));
        return LayerDefinition.create(meshDefinition, 64, 32);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * Sets this entity's model rotation angles
     */
    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.head.yRot = netHeadYaw * (float) (Math.PI / 180.0);
        this.head.xRot = headPitch * (float) (Math.PI / 180.0);
        float f = (float) (Math.PI / 4);
        this.rightHindLeg.zRot = (float) (-Math.PI / 4);
        this.leftHindLeg.zRot = (float) (Math.PI / 4);
        this.rightMiddleHindLeg.zRot = -0.58119464F;
        this.leftMiddleHindLeg.zRot = 0.58119464F;
        this.rightMiddleFrontLeg.zRot = -0.58119464F;
        this.leftMiddleFrontLeg.zRot = 0.58119464F;
        this.rightFrontLeg.zRot = (float) (-Math.PI / 4);
        this.leftFrontLeg.zRot = (float) (Math.PI / 4);
        float g = -0.0F;
        float h = (float) (Math.PI / 8);
        this.rightHindLeg.yRot = (float) (Math.PI / 4);
        this.leftHindLeg.yRot = (float) (-Math.PI / 4);
        this.rightMiddleHindLeg.yRot = (float) (Math.PI / 8);
        this.leftMiddleHindLeg.yRot = (float) (-Math.PI / 8);
        this.rightMiddleFrontLeg.yRot = (float) (-Math.PI / 8);
        this.leftMiddleFrontLeg.yRot = (float) (Math.PI / 8);
        this.rightFrontLeg.yRot = (float) (-Math.PI / 4);
        this.leftFrontLeg.yRot = (float) (Math.PI / 4);
        float i = -(Mth.cos(limbSwing * 0.6662F * 2.0F + 0.0F) * 0.4F) * limbSwingAmount;
        float j = -(Mth.cos(limbSwing * 0.6662F * 2.0F + (float) Math.PI) * 0.4F) * limbSwingAmount;
        float k = -(Mth.cos(limbSwing * 0.6662F * 2.0F + (float) (Math.PI / 2)) * 0.4F) * limbSwingAmount;
        float l = -(Mth.cos(limbSwing * 0.6662F * 2.0F + (float) (Math.PI * 3.0 / 2.0)) * 0.4F) * limbSwingAmount;
        float m = Math.abs(Mth.sin(limbSwing * 0.6662F + 0.0F) * 0.4F) * limbSwingAmount;
        float n = Math.abs(Mth.sin(limbSwing * 0.6662F + (float) Math.PI) * 0.4F) * limbSwingAmount;
        float o = Math.abs(Mth.sin(limbSwing * 0.6662F + (float) (Math.PI / 2)) * 0.4F) * limbSwingAmount;
        float p = Math.abs(Mth.sin(limbSwing * 0.6662F + (float) (Math.PI * 3.0 / 2.0)) * 0.4F) * limbSwingAmount;
        this.rightHindLeg.yRot += i;
        this.leftHindLeg.yRot += -i;
        this.rightMiddleHindLeg.yRot += j;
        this.leftMiddleHindLeg.yRot += -j;
        this.rightMiddleFrontLeg.yRot += k;
        this.leftMiddleFrontLeg.yRot += -k;
        this.rightFrontLeg.yRot += l;
        this.leftFrontLeg.yRot += -l;
        this.rightHindLeg.zRot += m;
        this.leftHindLeg.zRot += -m;
        this.rightMiddleHindLeg.zRot += n;
        this.leftMiddleHindLeg.zRot += -n;
        this.rightMiddleFrontLeg.zRot += o;
        this.leftMiddleFrontLeg.zRot += -o;
        this.rightFrontLeg.zRot += p;
        this.leftFrontLeg.zRot += -p;
    }
}
