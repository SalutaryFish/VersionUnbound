package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.function.UnaryOperator;

public class CriteriaRenameFix extends DataFix {
    private final String name;
    private final String advancementId;
    private final UnaryOperator<String> conversions;

    public CriteriaRenameFix(Schema outputSchema, String name, String advancementId, UnaryOperator<String> conversions) {
        super(outputSchema, false);
        this.name = name;
        this.advancementId = advancementId;
        this.conversions = conversions;
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return this.fixTypeEverywhereTyped(
            this.name, this.getInputSchema().getType(References.ADVANCEMENTS), typed -> typed.update(DSL.remainderFinder(), this::fixAdvancements)
        );
    }

    private Dynamic<?> fixAdvancements(Dynamic<?> advancementData) {
        return advancementData.update(
            this.advancementId,
            dynamic -> dynamic.update(
                "criteria",
                dynamicx -> dynamicx.updateMapValues(
                    pair -> pair.mapFirst(
                        dynamicxx -> DataFixUtils.orElse(
                            dynamicxx.asString().map(string -> dynamicxx.createString(this.conversions.apply(string))).result(), dynamicxx
                        )
                    )
                )
            )
        );
    }
}
