package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.RewriteResult;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.View;
import com.mojang.datafixers.DSL.TypeReference;
import com.mojang.datafixers.functions.PointFreeRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.serialization.Dynamic;
import java.util.BitSet;
import net.minecraft.Util;

public abstract class NamedEntityWriteReadFix extends DataFix {
    private final String name;
    private final String entityName;
    private final TypeReference type;

    public NamedEntityWriteReadFix(Schema outputSchema, boolean changesType, String name, TypeReference type, String entityName) {
        super(outputSchema, changesType);
        this.name = name;
        this.type = type;
        this.entityName = entityName;
    }

    @Override
    public TypeRewriteRule makeRule() {
        Type<?> type = this.getInputSchema().getType(this.type);
        Type<?> type2 = this.getInputSchema().getChoiceType(this.type, this.entityName);
        Type<?> type3 = this.getOutputSchema().getType(this.type);
        Type<?> type4 = this.getOutputSchema().getChoiceType(this.type, this.entityName);
        OpticFinder<?> opticFinder = DSL.namedChoice(this.entityName, type2);
        Type<?> type5 = type2.all(typePatcher(type, type3), true, false).view().newType();
        return this.fix(type, type3, opticFinder, type4, type5);
    }

    private <S, T, A, B> TypeRewriteRule fix(Type<S> inputType, Type<T> outputType, OpticFinder<A> finder, Type<B> outputChoiceType, Type<?> newType) {
        return this.fixTypeEverywhere(this.name, inputType, outputType, dynamicOps -> object -> {
            Typed<S> typed = new Typed<>(inputType, dynamicOps, object);
            return (T)typed.update(finder, outputChoiceType, objectx -> {
                Typed<A> typedx = new Typed<>((Type<A>)newType, dynamicOps, (A)objectx);
                return Util.<A, B>writeAndReadTypedOrThrow(typedx, outputChoiceType, this::fix).getValue();
            }).getValue();
        });
    }

    private static <A, B> TypeRewriteRule typePatcher(Type<A> type, Type<B> newType) {
        RewriteResult<A, B> rewriteResult = RewriteResult.create(View.create("Patcher", type, newType, dynamicOps -> object -> {
            throw new UnsupportedOperationException();
        }), new BitSet());
        return TypeRewriteRule.everywhere(TypeRewriteRule.ifSame(type, rewriteResult), PointFreeRule.nop(), true, true);
    }

    protected abstract <T> Dynamic<T> fix(Dynamic<T> var1);
}
