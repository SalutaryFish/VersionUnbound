package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;

public class OverreachingTickFix extends DataFix {
    public OverreachingTickFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(References.CHUNK);
        OpticFinder<?> opticFinder = type.findField("block_ticks");
        return this.fixTypeEverywhereTyped("Handle ticks saved in the wrong chunk", type, typed -> {
            Optional<? extends Typed<?>> optional = typed.getOptionalTyped(opticFinder);
            Optional<? extends Dynamic<?>> optional2 = optional.isPresent() ? optional.get().write().result() : Optional.empty();
            return typed.update(DSL.remainderFinder(), dynamic -> {
                int i = dynamic.get("xPos").asInt(0);
                int j = dynamic.get("zPos").asInt(0);
                Optional<? extends Dynamic<?>> optional2x = dynamic.get("fluid_ticks").get().result();
                dynamic = extractOverreachingTicks(dynamic, i, j, optional2, "neighbor_block_ticks");
                return extractOverreachingTicks(dynamic, i, j, optional2x, "neighbor_fluid_ticks");
            });
        });
    }

    private static Dynamic<?> extractOverreachingTicks(Dynamic<?> tag, int x, int z, Optional<? extends Dynamic<?>> ticks, String id) {
        if (ticks.isPresent()) {
            List<? extends Dynamic<?>> list = ticks.get().asStream().filter(dynamic -> {
                int k = dynamic.get("x").asInt(0);
                int l = dynamic.get("z").asInt(0);
                int m = Math.abs(x - (k >> 4));
                int n = Math.abs(z - (l >> 4));
                return (m != 0 || n != 0) && m <= 1 && n <= 1;
            }).toList();
            if (!list.isEmpty()) {
                tag = tag.set("UpgradeData", tag.get("UpgradeData").orElseEmptyMap().set(id, tag.createList(list.stream())));
            }
        }

        return tag;
    }
}
