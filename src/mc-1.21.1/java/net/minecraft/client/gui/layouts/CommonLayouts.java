package net.minecraft.client.gui.layouts;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;

public class CommonLayouts {
    private static final int LABEL_SPACING = 4;

    private CommonLayouts() {
    }

    public static Layout labeledElement(Font font, LayoutElement element, Component label) {
        return labeledElement(font, element, label, layoutSettings -> {});
    }

    public static Layout labeledElement(Font font, LayoutElement element, Component label, Consumer<LayoutSettings> layoutSettings) {
        LinearLayout linearLayout = LinearLayout.vertical().spacing(4);
        linearLayout.addChild(new StringWidget(label, font));
        linearLayout.addChild(element, layoutSettings);
        return linearLayout;
    }
}
