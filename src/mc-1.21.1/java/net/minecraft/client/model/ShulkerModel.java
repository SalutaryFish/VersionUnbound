package net.minecraft.client.model;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Shulker;

public class ShulkerModel<T extends Shulker> extends ListModel<T> {
    private static final String LID = "lid";
    private static final String BASE = "base";
    private final ModelPart base;
    private final ModelPart lid;
    private final ModelPart head;

    public ShulkerModel(ModelPart root) {
        super(RenderType::entityCutoutNoCullZOffset);
        this.lid = root.getChild("lid");
        this.base = root.getChild("base");
        this.head = root.getChild("head");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        partDefinition.addOrReplaceChild(
            "lid", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -16.0F, -8.0F, 16.0F, 12.0F, 16.0F), PartPose.offset(0.0F, 24.0F, 0.0F)
        );
        partDefinition.addOrReplaceChild(
            "base", CubeListBuilder.create().texOffs(0, 28).addBox(-8.0F, -8.0F, -8.0F, 16.0F, 8.0F, 16.0F), PartPose.offset(0.0F, 24.0F, 0.0F)
        );
        partDefinition.addOrReplaceChild(
            "head", CubeListBuilder.create().texOffs(0, 52).addBox(-3.0F, 0.0F, -3.0F, 6.0F, 6.0F, 6.0F), PartPose.offset(0.0F, 12.0F, 0.0F)
        );
        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    /**
     * Sets this entity's model rotation angles
     */
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float f = ageInTicks - entity.tickCount;
        float g = (0.5F + entity.getClientPeekAmount(f)) * (float) Math.PI;
        float h = -1.0F + Mth.sin(g);
        float i = 0.0F;
        if (g > (float) Math.PI) {
            i = Mth.sin(ageInTicks * 0.1F) * 0.7F;
        }

        this.lid.setPos(0.0F, 16.0F + Mth.sin(g) * 8.0F + i, 0.0F);
        if (entity.getClientPeekAmount(f) > 0.3F) {
            this.lid.yRot = h * h * h * h * (float) Math.PI * 0.125F;
        } else {
            this.lid.yRot = 0.0F;
        }

        this.head.xRot = headPitch * (float) (Math.PI / 180.0);
        this.head.yRot = (entity.yHeadRot - 180.0F - entity.yBodyRot) * (float) (Math.PI / 180.0);
    }

    @Override
    public Iterable<ModelPart> parts() {
        return ImmutableList.of(this.base, this.lid);
    }

    public ModelPart getLid() {
        return this.lid;
    }

    public ModelPart getHead() {
        return this.head;
    }
}
