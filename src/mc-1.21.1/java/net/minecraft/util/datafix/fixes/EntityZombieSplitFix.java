package net.minecraft.util.datafix.fixes;

import com.google.common.base.Suppliers;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.function.Supplier;
import net.minecraft.Util;

public class EntityZombieSplitFix extends EntityRenameFix {
    private final Supplier<Type<?>> zombieVillagerType = Suppliers.memoize(() -> this.getOutputSchema().getChoiceType(References.ENTITY, "ZombieVillager"));

    public EntityZombieSplitFix(Schema outputSchema) {
        super("EntityZombieSplitFix", outputSchema, true);
    }

    @Override
    protected Pair<String, Typed<?>> fix(String entityName, Typed<?> typed) {
        if (!entityName.equals("Zombie")) {
            return Pair.of(entityName, typed);
        }

        Dynamic<?> dynamic = typed.getOptional(DSL.remainderFinder()).orElseThrow();
        int i = dynamic.get("ZombieType").asInt(0);
        String string;
        Typed<?> typed2;
        switch (i) {
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                string = "ZombieVillager";
                typed2 = this.changeSchemaToZombieVillager(typed, i - 1);
                break;
            case 6:
                string = "Husk";
                typed2 = typed;
                break;
            default:
                string = "Zombie";
                typed2 = typed;
        }

        return Pair.of(string, typed2.update(DSL.remainderFinder(), dynamicx -> dynamicx.remove("ZombieType")));
    }

    private Typed<?> changeSchemaToZombieVillager(Typed<?> typed, int profession) {
        return Util.writeAndReadTypedOrThrow(typed, this.zombieVillagerType.get(), dynamic -> dynamic.set("Profession", dynamic.createInt(profession)));
    }
}
