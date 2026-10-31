package net.minecraft.util.datafix.fixes;

import com.google.common.collect.Streams;
import com.mojang.datafixers.DSL;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.util.datafix.ComponentDataFixUtils;

public class BlockEntitySignDoubleSidedEditableTextFix extends NamedEntityFix {
    public static final String FILTERED_CORRECT = "_filtered_correct";
    private static final String DEFAULT_COLOR = "black";

    public BlockEntitySignDoubleSidedEditableTextFix(Schema outputSchema, String name, String entityName) {
        super(outputSchema, false, name, References.BLOCK_ENTITY, entityName);
    }

    private static <T> Dynamic<T> fixTag(Dynamic<T> tag) {
        return tag.set("front_text", fixFrontTextTag(tag)).set("back_text", createDefaultText(tag)).set("is_waxed", tag.createBoolean(false));
    }

    private static <T> Dynamic<T> fixFrontTextTag(Dynamic<T> tag) {
        Dynamic<T> dynamic = ComponentDataFixUtils.createEmptyComponent(tag.getOps());
        List<Dynamic<T>> list = getLines(tag, "Text").map(optional -> optional.orElse(dynamic)).toList();
        Dynamic<T> dynamic2 = tag.emptyMap()
            .set("messages", tag.createList(list.stream()))
            .set("color", tag.get("Color").result().orElse(tag.createString("black")))
            .set("has_glowing_text", tag.get("GlowingText").result().orElse(tag.createBoolean(false)))
            .set("_filtered_correct", tag.createBoolean(true));
        List<Optional<Dynamic<T>>> list2 = getLines(tag, "FilteredText").toList();
        if (list2.stream().anyMatch(Optional::isPresent)) {
            dynamic2 = dynamic2.set("filtered_messages", tag.createList(Streams.mapWithIndex(list2.stream(), (optional, l) -> {
                Dynamic<T> dynamicx = list.get((int)l);
                return optional.orElse(dynamicx);
            })));
        }

        return dynamic2;
    }

    private static <T> Stream<Optional<Dynamic<T>>> getLines(Dynamic<T> dynamic, String prefix) {
        return Stream.of(
            dynamic.get(prefix + "1").result(), dynamic.get(prefix + "2").result(), dynamic.get(prefix + "3").result(), dynamic.get(prefix + "4").result()
        );
    }

    private static <T> Dynamic<T> createDefaultText(Dynamic<T> dynamic) {
        return dynamic.emptyMap()
            .set("messages", createEmptyLines(dynamic))
            .set("color", dynamic.createString("black"))
            .set("has_glowing_text", dynamic.createBoolean(false));
    }

    private static <T> Dynamic<T> createEmptyLines(Dynamic<T> dynamic) {
        Dynamic<T> dynamic2 = ComponentDataFixUtils.createEmptyComponent(dynamic.getOps());
        return dynamic.createList(Stream.of(dynamic2, dynamic2, dynamic2, dynamic2));
    }

    @Override
    protected Typed<?> fix(Typed<?> typed) {
        return typed.update(DSL.remainderFinder(), BlockEntitySignDoubleSidedEditableTextFix::fixTag);
    }
}
