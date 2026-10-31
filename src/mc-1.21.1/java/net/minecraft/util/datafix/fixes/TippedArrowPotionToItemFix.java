package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class TippedArrowPotionToItemFix extends NamedEntityWriteReadFix {
    public TippedArrowPotionToItemFix(Schema outputSchema) {
        super(outputSchema, false, "TippedArrowPotionToItemFix", References.ENTITY, "minecraft:arrow");
    }

    @Override
    protected <T> Dynamic<T> fix(Dynamic<T> tag) {
        Optional<Dynamic<T>> optional = tag.get("Potion").result();
        Optional<Dynamic<T>> optional2 = tag.get("custom_potion_effects").result();
        Optional<Dynamic<T>> optional3 = tag.get("Color").result();
        return optional.isEmpty() && optional2.isEmpty() && optional3.isEmpty()
            ? tag
            : tag.remove("Potion").remove("custom_potion_effects").remove("Color").update("item", dynamic -> {
                Dynamic<?> dynamic2 = dynamic.get("tag").orElseEmptyMap();
                if (optional.isPresent()) {
                    dynamic2 = dynamic2.set("Potion", optional.get());
                }

                if (optional2.isPresent()) {
                    dynamic2 = dynamic2.set("custom_potion_effects", optional2.get());
                }

                if (optional3.isPresent()) {
                    dynamic2 = dynamic2.set("CustomPotionColor", optional3.get());
                }

                return dynamic.set("tag", dynamic2);
            });
    }
}
