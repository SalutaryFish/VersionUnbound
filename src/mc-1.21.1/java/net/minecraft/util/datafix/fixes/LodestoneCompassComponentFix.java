package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class LodestoneCompassComponentFix extends ItemStackComponentRemainderFix {
    public LodestoneCompassComponentFix(Schema outputSchema) {
        super(outputSchema, "LodestoneCompassComponentFix", "minecraft:lodestone_target", "minecraft:lodestone_tracker");
    }

    @Override
    protected <T> Dynamic<T> fixComponent(Dynamic<T> tag) {
        Optional<Dynamic<T>> optional = tag.get("pos").result();
        Optional<Dynamic<T>> optional2 = tag.get("dimension").result();
        tag = tag.remove("pos").remove("dimension");
        if (optional.isPresent() && optional2.isPresent()) {
            tag = tag.set("target", tag.emptyMap().set("pos", optional.get()).set("dimension", optional2.get()));
        }

        return tag;
    }
}
