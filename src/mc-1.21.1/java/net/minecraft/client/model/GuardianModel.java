package net.minecraft.client.model;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.phys.Vec3;

public class GuardianModel extends HierarchicalModel<Guardian> {
    private static final float[] SPIKE_X_ROT = new float[]{1.75F, 0.25F, 0.0F, 0.0F, 0.5F, 0.5F, 0.5F, 0.5F, 1.25F, 0.75F, 0.0F, 0.0F};
    private static final float[] SPIKE_Y_ROT = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.25F, 1.75F, 1.25F, 0.75F, 0.0F, 0.0F, 0.0F, 0.0F};
    private static final float[] SPIKE_Z_ROT = new float[]{0.0F, 0.0F, 0.25F, 1.75F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.75F, 1.25F};
    private static final float[] SPIKE_X = new float[]{0.0F, 0.0F, 8.0F, -8.0F, -8.0F, 8.0F, 8.0F, -8.0F, 0.0F, 0.0F, 8.0F, -8.0F};
    private static final float[] SPIKE_Y = new float[]{-8.0F, -8.0F, -8.0F, -8.0F, 0.0F, 0.0F, 0.0F, 0.0F, 8.0F, 8.0F, 8.0F, 8.0F};
    private static final float[] SPIKE_Z = new float[]{8.0F, -8.0F, 0.0F, 0.0F, -8.0F, -8.0F, 8.0F, 8.0F, 8.0F, -8.0F, 0.0F, 0.0F};
    private static final String EYE = "eye";
    private static final String TAIL_0 = "tail0";
    private static final String TAIL_1 = "tail1";
    private static final String TAIL_2 = "tail2";
    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart eye;
    private final ModelPart[] spikeParts;
    private final ModelPart[] tailParts;

    public GuardianModel(ModelPart root) {
        this.root = root;
        this.spikeParts = new ModelPart[12];
        this.head = root.getChild("head");

        for (int i = 0; i < this.spikeParts.length; i++) {
            this.spikeParts[i] = this.head.getChild(createSpikeName(i));
        }

        this.eye = this.head.getChild("eye");
        this.tailParts = new ModelPart[3];
        this.tailParts[0] = this.head.getChild("tail0");
        this.tailParts[1] = this.tailParts[0].getChild("tail1");
        this.tailParts[2] = this.tailParts[1].getChild("tail2");
    }

    private static String createSpikeName(int index) {
        return "spike" + index;
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshDefinition = new MeshDefinition();
        PartDefinition partDefinition = meshDefinition.getRoot();
        PartDefinition partDefinition2 = partDefinition.addOrReplaceChild(
            "head",
            CubeListBuilder.create()
                .texOffs(0, 0)
                .addBox(-6.0F, 10.0F, -8.0F, 12.0F, 12.0F, 16.0F)
                .texOffs(0, 28)
                .addBox(-8.0F, 10.0F, -6.0F, 2.0F, 12.0F, 12.0F)
                .texOffs(0, 28)
                .addBox(6.0F, 10.0F, -6.0F, 2.0F, 12.0F, 12.0F, true)
                .texOffs(16, 40)
                .addBox(-6.0F, 8.0F, -6.0F, 12.0F, 2.0F, 12.0F)
                .texOffs(16, 40)
                .addBox(-6.0F, 22.0F, -6.0F, 12.0F, 2.0F, 12.0F),
            PartPose.ZERO
        );
        CubeListBuilder cubeListBuilder = CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -4.5F, -1.0F, 2.0F, 9.0F, 2.0F);

        for (int i = 0; i < 12; i++) {
            float f = getSpikeX(i, 0.0F, 0.0F);
            float g = getSpikeY(i, 0.0F, 0.0F);
            float h = getSpikeZ(i, 0.0F, 0.0F);
            float j = (float) Math.PI * SPIKE_X_ROT[i];
            float k = (float) Math.PI * SPIKE_Y_ROT[i];
            float l = (float) Math.PI * SPIKE_Z_ROT[i];
            partDefinition2.addOrReplaceChild(createSpikeName(i), cubeListBuilder, PartPose.offsetAndRotation(f, g, h, j, k, l));
        }

        partDefinition2.addOrReplaceChild(
            "eye", CubeListBuilder.create().texOffs(8, 0).addBox(-1.0F, 15.0F, 0.0F, 2.0F, 2.0F, 1.0F), PartPose.offset(0.0F, 0.0F, -8.25F)
        );
        PartDefinition partDefinition3 = partDefinition2.addOrReplaceChild(
            "tail0", CubeListBuilder.create().texOffs(40, 0).addBox(-2.0F, 14.0F, 7.0F, 4.0F, 4.0F, 8.0F), PartPose.ZERO
        );
        PartDefinition partDefinition4 = partDefinition3.addOrReplaceChild(
            "tail1", CubeListBuilder.create().texOffs(0, 54).addBox(0.0F, 14.0F, 0.0F, 3.0F, 3.0F, 7.0F), PartPose.offset(-1.5F, 0.5F, 14.0F)
        );
        partDefinition4.addOrReplaceChild(
            "tail2",
            CubeListBuilder.create().texOffs(41, 32).addBox(0.0F, 14.0F, 0.0F, 2.0F, 2.0F, 6.0F).texOffs(25, 19).addBox(1.0F, 10.5F, 3.0F, 1.0F, 9.0F, 9.0F),
            PartPose.offset(0.5F, 0.5F, 6.0F)
        );
        return LayerDefinition.create(meshDefinition, 64, 64);
    }

    @Override
    public ModelPart root() {
        return this.root;
    }

    /**
     * Sets this entity's model rotation angles
     */
    public void setupAnim(Guardian entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        float f = ageInTicks - entity.tickCount;
        this.head.yRot = netHeadYaw * (float) (Math.PI / 180.0);
        this.head.xRot = headPitch * (float) (Math.PI / 180.0);
        float g = (1.0F - entity.getSpikesAnimation(f)) * 0.55F;
        this.setupSpikes(ageInTicks, g);
        Entity entity2 = Minecraft.getInstance().getCameraEntity();
        if (entity.hasActiveAttackTarget()) {
            entity2 = entity.getActiveAttackTarget();
        }

        if (entity2 != null) {
            Vec3 vec3 = entity2.getEyePosition(0.0F);
            Vec3 vec32 = entity.getEyePosition(0.0F);
            double d = vec3.y - vec32.y;
            if (d > 0.0) {
                this.eye.y = 0.0F;
            } else {
                this.eye.y = 1.0F;
            }

            Vec3 vec33 = entity.getViewVector(0.0F);
            vec33 = new Vec3(vec33.x, 0.0, vec33.z);
            Vec3 vec34 = new Vec3(vec32.x - vec3.x, 0.0, vec32.z - vec3.z).normalize().yRot((float) (Math.PI / 2));
            double e = vec33.dot(vec34);
            this.eye.x = Mth.sqrt((float)Math.abs(e)) * 2.0F * (float)Math.signum(e);
        }

        this.eye.visible = true;
        float h = entity.getTailAnimation(f);
        this.tailParts[0].yRot = Mth.sin(h) * (float) Math.PI * 0.05F;
        this.tailParts[1].yRot = Mth.sin(h) * (float) Math.PI * 0.1F;
        this.tailParts[2].yRot = Mth.sin(h) * (float) Math.PI * 0.15F;
    }

    private void setupSpikes(float ageInTicks, float f) {
        for (int i = 0; i < 12; i++) {
            this.spikeParts[i].x = getSpikeX(i, ageInTicks, f);
            this.spikeParts[i].y = getSpikeY(i, ageInTicks, f);
            this.spikeParts[i].z = getSpikeZ(i, ageInTicks, f);
        }
    }

    private static float getSpikeOffset(int index, float ageInTicks, float f) {
        return 1.0F + Mth.cos(ageInTicks * 1.5F + index) * 0.01F - f;
    }

    private static float getSpikeX(int index, float ageInTicks, float f) {
        return SPIKE_X[index] * getSpikeOffset(index, ageInTicks, f);
    }

    private static float getSpikeY(int index, float ageInTicks, float f) {
        return 16.0F + SPIKE_Y[index] * getSpikeOffset(index, ageInTicks, f);
    }

    private static float getSpikeZ(int index, float ageInTicks, float f) {
        return SPIKE_Z[index] * getSpikeOffset(index, ageInTicks, f);
    }
}
