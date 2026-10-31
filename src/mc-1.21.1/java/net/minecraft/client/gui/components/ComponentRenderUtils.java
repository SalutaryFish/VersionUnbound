package net.minecraft.client.gui.components;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public class ComponentRenderUtils {
    private static final FormattedCharSequence INDENT = FormattedCharSequence.codepoint(32, Style.EMPTY);

    private static String stripColor(String text) {
        return Minecraft.getInstance().options.chatColors().get() ? text : ChatFormatting.stripFormatting(text);
    }

    public static List<FormattedCharSequence> wrapComponents(FormattedText component, int maxWidth, Font font) {
        ComponentCollector componentCollector = new ComponentCollector();
        component.visit((style, content) -> {
            componentCollector.append(FormattedText.of(stripColor(content), style));
            return Optional.empty();
        }, Style.EMPTY);
        List<FormattedCharSequence> list = Lists.newArrayList();
        font.getSplitter().splitLines(componentCollector.getResultOrEmpty(), maxWidth, Style.EMPTY, (formattedText, isNewLineCharacter) -> {
            FormattedCharSequence formattedCharSequence = Language.getInstance().getVisualOrder(formattedText);
            list.add(isNewLineCharacter ? FormattedCharSequence.composite(INDENT, formattedCharSequence) : formattedCharSequence);
        });
        return list.isEmpty() ? Lists.newArrayList(FormattedCharSequence.EMPTY) : list;
    }
}
