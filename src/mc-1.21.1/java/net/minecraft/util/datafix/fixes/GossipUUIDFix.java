package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;

public class GossipUUIDFix extends NamedEntityFix {
    public GossipUUIDFix(Schema outputSchema, String entityName) {
        super(outputSchema, false, "Gossip for for " + entityName, References.ENTITY, entityName);
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(
            DSL.remainderFinder(),
            dynamic -> dynamic.update(
                "Gossips",
                dynamicx -> DataFixUtils.orElse(
                    dynamicx.asStreamOpt()
                        .result()
                        .map(stream -> stream.map(dynamicxx -> AbstractUUIDFix.replaceUUIDLeastMost(dynamicxx, "Target", "Target").orElse(dynamicxx)))
                        .map(dynamicx::createList),
                    dynamicx
                )
            )
        );
    }
}
