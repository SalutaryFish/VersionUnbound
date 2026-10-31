package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.Optional;

public class PlayerHeadBlockProfileFix extends NamedEntityFix {
    public PlayerHeadBlockProfileFix(Schema outputSchema) {
        super(outputSchema, false, "PlayerHeadBlockProfileFix", References.BLOCK_ENTITY, "minecraft:skull");
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), this::fix);
    }

    private <T> Dynamic<T> fix(Dynamic<T> tag) {
        Optional<Dynamic<T>> optional = tag.get("SkullOwner").result();
        Optional<Dynamic<T>> optional2 = tag.get("ExtraType").result();
        Optional<Dynamic<T>> optional3 = optional.or(() -> optional2);
        if (optional3.isEmpty()) {
            return tag;
        }

        tag = tag.remove("SkullOwner").remove("ExtraType");
        return tag.set("profile", ItemStackComponentizationFix.fixProfile(optional3.get()));
    }
}
