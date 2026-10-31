package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.templates.TaggedChoice.TaggedChoiceType;
import java.util.Locale;

public class AddNewChoices extends DataFix {
    private final String name;
    private final TypeReference type;

    public AddNewChoices(Schema outputSchema, String name, TypeReference type) {
        super(outputSchema, true);
        this.name = name;
        this.type = type;
    }

    @Override
    public TypeRewriteRule makeRule() {
        TaggedChoiceType<?> taggedChoiceType = this.getInputSchema().findChoiceType(this.type);
        TaggedChoiceType<?> taggedChoiceType2 = this.getOutputSchema().findChoiceType(this.type);
        return this.cap(taggedChoiceType, taggedChoiceType2);
    }

    private <K> TypeRewriteRule cap(TaggedChoiceType<K> inputChoiceType, TaggedChoiceType<?> outputChoiceType) {
        if (inputChoiceType.getKeyType() != outputChoiceType.getKeyType()) {
            throw new IllegalStateException("Could not inject: key type is not the same");
        }

        TaggedChoiceType<K> taggedChoiceType = (TaggedChoiceType<K>)outputChoiceType;
        return this.fixTypeEverywhere(this.name, inputChoiceType, taggedChoiceType, dynamicOps -> pair -> {
            if (!taggedChoiceType.hasType(pair.getFirst())) {
                throw new IllegalArgumentException(String.format(Locale.ROOT, "%s: Unknown type %s in '%s'", this.name, pair.getFirst(), this.type.typeName()));
            } else {
                return pair;
            }
        });
    }
}
