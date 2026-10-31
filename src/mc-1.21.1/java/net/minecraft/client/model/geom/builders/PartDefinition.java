package net.minecraft.client.model.geom.builders;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;

public class PartDefinition {
    private final List<CubeDefinition> cubes;
    private final PartPose partPose;
    private final Map<String, PartDefinition> children = Maps.newHashMap();

    PartDefinition(List<CubeDefinition> cubes, PartPose partPose) {
        this.cubes = cubes;
        this.partPose = partPose;
    }

    public PartDefinition addOrReplaceChild(String name, CubeListBuilder cubes, PartPose partPose) {
        PartDefinition partDefinition = new PartDefinition(cubes.getCubes(), partPose);
        PartDefinition partDefinition2 = this.children.put(name, partDefinition);
        if (partDefinition2 != null) {
            partDefinition.children.putAll(partDefinition2.children);
        }

        return partDefinition;
    }

    public ModelPart bake(int texWidth, int texHeight) {
        Object2ObjectArrayMap<String, ModelPart> object2ObjectArrayMap = this.children
            .entrySet()
            .stream()
            .collect(
                Collectors.toMap(
                    Entry::getKey,
                    entry -> ((PartDefinition)entry.getValue()).bake(texWidth, texHeight),
                    (oldModelPart, modelPartx) -> oldModelPart,
                    Object2ObjectArrayMap::new
                )
            );
        List<ModelPart.Cube> list = this.cubes.stream().map(cube -> cube.bake(texWidth, texHeight)).collect(ImmutableList.toImmutableList());
        ModelPart modelPart = new ModelPart(list, object2ObjectArrayMap);
        modelPart.setInitialPose(this.partPose);
        modelPart.loadPose(this.partPose);
        return modelPart;
    }

    public PartDefinition getChild(String name) {
        return this.children.get(name);
    }
}
