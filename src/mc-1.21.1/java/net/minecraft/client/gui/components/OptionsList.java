package net.minecraft.client.gui.components;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;

public class OptionsList extends ContainerObjectSelectionList<OptionsList.Entry> {
    private static final int BIG_BUTTON_WIDTH = 310;
    private static final int DEFAULT_ITEM_HEIGHT = 25;
    private final OptionsSubScreen screen;

    public OptionsList(Minecraft minecraft, int width, OptionsSubScreen screen) {
        super(minecraft, width, screen.layout.getContentHeight(), screen.layout.getHeaderHeight(), 25);
        this.centerListVertically = false;
        this.screen = screen;
    }

    public void addBig(OptionInstance<?> option) {
        this.addEntry(OptionsList.OptionEntry.big(this.minecraft.options, option, this.screen));
    }

    public void addSmall(OptionInstance<?>... options) {
        for (int i = 0; i < options.length; i += 2) {
            OptionInstance<?> optionInstance = i < options.length - 1 ? options[i + 1] : null;
            this.addEntry(OptionsList.OptionEntry.small(this.minecraft.options, options[i], optionInstance, this.screen));
        }
    }

    public void addSmall(List<AbstractWidget> options) {
        for (int i = 0; i < options.size(); i += 2) {
            this.addSmall(options.get(i), i < options.size() - 1 ? options.get(i + 1) : null);
        }
    }

    public void addSmall(AbstractWidget leftOption, @Nullable AbstractWidget rightOption) {
        this.addEntry(OptionsList.Entry.small(leftOption, rightOption, this.screen));
    }

    @Override
    public int getRowWidth() {
        return 310;
    }

    @Nullable
    public AbstractWidget findOption(OptionInstance<?> option) {
        for (OptionsList.Entry entry : this.children()) {
            if (entry instanceof OptionsList.OptionEntry optionEntry) {
                AbstractWidget abstractWidget = optionEntry.options.get(option);
                if (abstractWidget != null) {
                    return abstractWidget;
                }
            }
        }

        return null;
    }

    public void applyUnsavedChanges() {
        for (OptionsList.Entry entry : this.children()) {
            if (entry instanceof OptionsList.OptionEntry optionEntry) {
                for (AbstractWidget abstractWidget : optionEntry.options.values()) {
                    if (abstractWidget instanceof OptionInstance.OptionInstanceSliderButton<?> optionInstanceSliderButton) {
                        optionInstanceSliderButton.applyUnsavedValue();
                    }
                }
            }
        }
    }

    public Optional<GuiEventListener> getMouseOver(double mouseX, double mouseY) {
        for (OptionsList.Entry entry : this.children()) {
            for (GuiEventListener guiEventListener : entry.children()) {
                if (guiEventListener.isMouseOver(mouseX, mouseY)) {
                    return Optional.of(guiEventListener);
                }
            }
        }

        return Optional.empty();
    }

    protected static class Entry extends ContainerObjectSelectionList.Entry<OptionsList.Entry> {
        private final List<AbstractWidget> children;
        private final Screen screen;
        private static final int X_OFFSET = 160;

        Entry(List<AbstractWidget> children, Screen screen) {
            this.children = ImmutableList.copyOf(children);
            this.screen = screen;
        }

        public static OptionsList.Entry big(List<AbstractWidget> options, Screen screen) {
            return new OptionsList.Entry(options, screen);
        }

        public static OptionsList.Entry small(AbstractWidget leftOption, @Nullable AbstractWidget rightOption, Screen screen) {
            return rightOption == null
                ? new OptionsList.Entry(ImmutableList.of(leftOption), screen)
                : new OptionsList.Entry(ImmutableList.of(leftOption, rightOption), screen);
        }

        @Override
        public void render(
            GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick
        ) {
            int i = 0;
            int j = this.screen.width / 2 - 155;

            for (AbstractWidget abstractWidget : this.children) {
                abstractWidget.setPosition(j + i, top);
                abstractWidget.render(guiGraphics, mouseX, mouseY, partialTick);
                i += 160;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.children;
        }
    }

    protected static class OptionEntry extends OptionsList.Entry {
        final Map<OptionInstance<?>, AbstractWidget> options;

        private OptionEntry(Map<OptionInstance<?>, AbstractWidget> options, OptionsSubScreen screen) {
            super(ImmutableList.copyOf(options.values()), screen);
            this.options = options;
        }

        public static OptionsList.OptionEntry big(Options options, OptionInstance<?> option, OptionsSubScreen screen) {
            return new OptionsList.OptionEntry(ImmutableMap.of(option, option.createButton(options, 0, 0, 310)), screen);
        }

        public static OptionsList.OptionEntry small(
            Options options, OptionInstance<?> leftOption, @Nullable OptionInstance<?> rightOption, OptionsSubScreen screen
        ) {
            AbstractWidget abstractWidget = leftOption.createButton(options);
            return rightOption == null
                ? new OptionsList.OptionEntry(ImmutableMap.of(leftOption, abstractWidget), screen)
                : new OptionsList.OptionEntry(ImmutableMap.of(leftOption, abstractWidget, rightOption, rightOption.createButton(options)), screen);
        }
    }
}
