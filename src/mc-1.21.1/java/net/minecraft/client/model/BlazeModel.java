package net.minecraft.client.model;

import java.util.Arrays;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public class BlazeModel<T extends Entity> extends HierarchicalModel<T> {
    private final ModelPart root;
    private final ModelPart[] upperBodyParts;
    private final ModelPart head;

    public BlazeModel(ModelPart root) {
        this.root = root;
        this.head = root.getChild("head");
        this.upperBodyParts = new ModelPart[12];
        Arrays.setAll(this.upperBodyParts, index -> root.getChild(getPartName(index)));
    }

    private static String getPartName(int index) {
        return "part" + index;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        partDefinition.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -4.0F, -4.0F, 8.0F, 8.0F, 8.0F), PartPose.ZERO);
        float f = 0.0F;
        CubeListBuilder cubeListBuilder = CubeListBuilder.create().texOffs(0, 16).addBox(0.0F, 0.0F, 0.0F, 2.0F, 8.0F, 2.0F);

        for (int i = 0; i < 4; i++) {
            float g = Mth.cos(f) * 9.0F;
            float h = -2.0F + Mth.cos(i * 2 * 0.25F);
            float j = Mth.sin(f) * 9.0F;
            partDefinition.addOrReplaceChild(getPartName(i), cubeListBuilder, PartPose.offset(g, h, j));
            f += (float) (Math.PI / 2);
        }

        f = (float) (Math.PI / 4);

        for (int i = 4; i < 8; i++) {
            float g = Mth.cos(f) * 7.0F;
            float h = 2.0F + Mth.cos(i * 2 * 0.25F);
            float j = Mth.sin(f) * 7.0F;
            partDefinition.addOrReplaceChild(getPartName(i), cubeListBuilder, PartPose.offset(g, h, j));
            f += (float) (Math.PI / 2);
        }

        f = 0.47123894F;

        for (int i = 8; i < 12; i++) {
            float g = Mth.cos(f) * 5.0F;
            float h = 11.0F + Mth.cos(i * 1.5F * 0.5F);
            float j = Mth.sin(f) * 5.0F;
            partDefinition.addOrReplaceChild(getPartName(i), cubeListBuilder, PartPose.offset(g, h, j));
            f += (float) (Math.PI / 2);
        }

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
        float f = ageInTicks * (float) Math.PI * -0.1F;

        for (int i = 0; i < 4; i++) {
            this.upperBodyParts[i].y = -2.0F + Mth.cos((i * 2 + ageInTicks) * 0.25F);
            this.upperBodyParts[i].x = Mth.cos(f) * 9.0F;
            this.upperBodyParts[i].z = Mth.sin(f) * 9.0F;
            f += (float) (Math.PI / 2);
        }

        f = (float) (Math.PI / 4) + ageInTicks * (float) Math.PI * 0.03F;

        for (int i = 4; i < 8; i++) {
            this.upperBodyParts[i].y = 2.0F + Mth.cos((i * 2 + ageInTicks) * 0.25F);
            this.upperBodyParts[i].x = Mth.cos(f) * 7.0F;
            this.upperBodyParts[i].z = Mth.sin(f) * 7.0F;
            f += (float) (Math.PI / 2);
        }

        f = 0.47123894F + ageInTicks * (float) Math.PI * -0.05F;

        for (int i = 8; i < 12; i++) {
            this.upperBodyParts[i].y = 11.0F + Mth.cos((i * 1.5F + ageInTicks) * 0.5F);
            this.upperBodyParts[i].x = Mth.cos(f) * 5.0F;
            this.upperBodyParts[i].z = Mth.sin(f) * 5.0F;
            f += (float) (Math.PI / 2);
        }

        this.head.yRot = netHeadYaw * (float) (Math.PI / 180.0);
        this.head.xRot = headPitch * (float) (Math.PI / 180.0);
    }
}
