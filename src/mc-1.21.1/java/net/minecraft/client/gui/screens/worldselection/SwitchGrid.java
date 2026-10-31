package net.minecraft.client.gui.screens.worldselection;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

class SwitchGrid {
    private static final int DEFAULT_SWITCH_BUTTON_WIDTH = 44;
    private final List<SwitchGrid.LabeledSwitch> switches;

    SwitchGrid(List<SwitchGrid.LabeledSwitch> switches) {
        this.switches = switches;
    }

    public void refreshStates() {
        this.switches.forEach(SwitchGrid.LabeledSwitch::refreshState);
    }

    public static SwitchGrid.Builder builder(int width) {
        return new SwitchGrid.Builder(width);
    }

    public static class Builder {
        final int width;
        private final List<SwitchGrid.SwitchBuilder> switchBuilders = new ArrayList<>();
        int paddingLeft;
        int rowSpacing = 4;
        int rowCount;
        Optional<SwitchGrid.InfoUnderneathSettings> infoUnderneath = Optional.empty();

        public Builder(int width) {
            this.width = width;
        }

        void increaseRow() {
            this.rowCount++;
        }

        public SwitchGrid.SwitchBuilder addSwitch(Component label, BooleanSupplier stateSupplier, Consumer<Boolean> onClicked) {
            SwitchGrid.SwitchBuilder switchBuilder = new SwitchGrid.SwitchBuilder(label, stateSupplier, onClicked, 44);
            this.switchBuilders.add(switchBuilder);
            return switchBuilder;
        }

        public SwitchGrid.Builder withPaddingLeft(int paddingLeft) {
            this.paddingLeft = paddingLeft;
            return this;
        }

        public SwitchGrid.Builder withRowSpacing(int rowSpacing) {
            this.rowSpacing = rowSpacing;
            return this;
        }

        public SwitchGrid build(Consumer<LayoutElement> consumer) {
            GridLayout gridLayout = new GridLayout().rowSpacing(this.rowSpacing);
            gridLayout.addChild(SpacerElement.width(this.width - 44), 0, 0);
            gridLayout.addChild(SpacerElement.width(44), 0, 1);
            List<SwitchGrid.LabeledSwitch> list = new ArrayList<>();
            this.rowCount = 0;

            for (SwitchGrid.SwitchBuilder switchBuilder : this.switchBuilders) {
                list.add(switchBuilder.build(this, gridLayout, 0));
            }

            gridLayout.arrangeElements();
            consumer.accept(gridLayout);
            SwitchGrid switchGrid = new SwitchGrid(list);
            switchGrid.refreshStates();
            return switchGrid;
        }

        public SwitchGrid.Builder withInfoUnderneath(int maxInfoRows, boolean alwaysMaxHeight) {
            this.infoUnderneath = Optional.of(new SwitchGrid.InfoUnderneathSettings(maxInfoRows, alwaysMaxHeight));
            return this;
        }
    }

    record InfoUnderneathSettings(int maxInfoRows, boolean alwaysMaxHeight) {
    }

    record LabeledSwitch(CycleButton<Boolean> button, BooleanSupplier stateSupplier, @Nullable BooleanSupplier isActiveCondition) {
        public void refreshState() {
            this.button.setValue(this.stateSupplier.getAsBoolean());
            if (this.isActiveCondition != null) {
                this.button.active = this.isActiveCondition.getAsBoolean();
            }
        }
    }

    public static class SwitchBuilder {
        private final Component label;
        private final BooleanSupplier stateSupplier;
        private final Consumer<Boolean> onClicked;
        @Nullable
        private Component info;
        @Nullable
        private BooleanSupplier isActiveCondition;
        private final int buttonWidth;

        SwitchBuilder(Component label, BooleanSupplier stateSupplier, Consumer<Boolean> onClicked, int buttonWidth) {
            this.label = label;
            this.stateSupplier = stateSupplier;
            this.onClicked = onClicked;
            this.buttonWidth = buttonWidth;
        }

        public SwitchGrid.SwitchBuilder withIsActiveCondition(BooleanSupplier isActiveCondition) {
            this.isActiveCondition = isActiveCondition;
            return this;
        }

        public SwitchGrid.SwitchBuilder withInfo(Component info) {
            this.info = info;
            return this;
        }

        SwitchGrid.LabeledSwitch build(SwitchGrid.Builder builder, GridLayout gridLayout, int column) {
            builder.increaseRow();
            StringWidget stringWidget = new StringWidget(this.label, Minecraft.getInstance().font).alignLeft();
            gridLayout.addChild(stringWidget, builder.rowCount, column, gridLayout.newCellSettings().align(0.0F, 0.5F).paddingLeft(builder.paddingLeft));
            Optional<SwitchGrid.InfoUnderneathSettings> optional = builder.infoUnderneath;
            CycleButton.Builder<Boolean> builder2 = CycleButton.onOffBuilder(this.stateSupplier.getAsBoolean());
            builder2.displayOnlyValue();
            boolean bl = this.info != null && optional.isEmpty();
            if (bl) {
                Tooltip tooltip = Tooltip.create(this.info);
                builder2.withTooltip(boolean_ -> tooltip);
            }

            if (this.info != null && !bl) {
                builder2.withCustomNarration(
                    cycleButtonx -> CommonComponents.joinForNarration(this.label, cycleButtonx.createDefaultNarrationMessage(), this.info)
                );
            } else {
                builder2.withCustomNarration(cycleButtonx -> CommonComponents.joinForNarration(this.label, cycleButtonx.createDefaultNarrationMessage()));
            }

            CycleButton<Boolean> cycleButton = builder2.create(
                0, 0, this.buttonWidth, 20, Component.empty(), (cycleButtonx, boolean_) -> this.onClicked.accept(boolean_)
            );
            if (this.isActiveCondition != null) {
                cycleButton.active = this.isActiveCondition.getAsBoolean();
            }

            gridLayout.addChild(cycleButton, builder.rowCount, column + 1, gridLayout.newCellSettings().alignHorizontallyRight());
            if (this.info != null) {
                optional.ifPresent(
                    infoUnderneathSettings -> {
                        Component component = this.info.copy().withStyle(ChatFormatting.GRAY);
                        Font font = Minecraft.getInstance().font;
                        MultiLineTextWidget multiLineTextWidget = new MultiLineTextWidget(component, font);
                        multiLineTextWidget.setMaxWidth(builder.width - builder.paddingLeft - this.buttonWidth);
                        multiLineTextWidget.setMaxRows(infoUnderneathSettings.maxInfoRows());
                        builder.increaseRow();
                        int j = infoUnderneathSettings.alwaysMaxHeight ? 9 * infoUnderneathSettings.maxInfoRows - multiLineTextWidget.getHeight() : 0;
                        gridLayout.addChild(
                            multiLineTextWidget, builder.rowCount, column, gridLayout.newCellSettings().paddingTop(-builder.rowSpacing).paddingBottom(j)
                        );
                    }
                );
            }

            return new SwitchGrid.LabeledSwitch(cycleButton, this.stateSupplier, this.isActiveCondition);
        }
    }
}
