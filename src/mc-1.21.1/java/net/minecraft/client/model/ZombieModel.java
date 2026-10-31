package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.monster.Zombie;

public class ZombieModel<T extends Zombie> extends AbstractZombieModel<T> {
    public ZombieModel(ModelPart root) {
        super(root);
    }

    public boolean isAggressive(T entity) {
        return entity.isAggressive();
    }
}
