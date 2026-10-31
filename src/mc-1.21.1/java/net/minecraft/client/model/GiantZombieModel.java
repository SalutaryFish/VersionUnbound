package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.Giant;

public class GiantZombieModel extends AbstractZombieModel<Giant> {
    public GiantZombieModel(ModelPart root) {
        super(root);
    }

    public boolean isAggressive(Giant entity) {
        return false;
    }
}
