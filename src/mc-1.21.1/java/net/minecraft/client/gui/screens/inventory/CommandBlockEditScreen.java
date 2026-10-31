package net.minecraft.client.gui.screens.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.world.level.BaseCommandBlock;
import net.minecraft.world.level.block.entity.CommandBlockEntity;

public class CommandBlockEditScreen extends AbstractCommandBlockEditScreen {
    private final CommandBlockEntity autoCommandBlock;
    private CycleButton<CommandBlockEntity.Mode> modeButton;
    private CycleButton<Boolean> conditionalButton;
    private CycleButton<Boolean> autoexecButton;
    private CommandBlockEntity.Mode mode = CommandBlockEntity.Mode.REDSTONE;
    private boolean conditional;
    private boolean autoexec;

    public CommandBlockEditScreen(CommandBlockEntity autoCommandBlock) {
        this.autoCommandBlock = autoCommandBlock;
    }

    @Override
    BaseCommandBlock getCommandBlock() {
        return this.autoCommandBlock.getCommandBlock();
    }

    @Override
    int getPreviousY() {
        return 135;
    }

    @Override
    protected void init() {
        super.init();
        this.modeButton = this.addRenderableWidget(
            CycleButton.<CommandBlockEntity.Mode>builder(mode -> {
                    return switch (mode) {
                        case SEQUENCE -> Component.translatable("advMode.mode.sequence");
                        case AUTO -> Component.translatable("advMode.mode.auto");
                        case REDSTONE -> Component.translatable("advMode.mode.redstone");
                    };
                })
                .withValues(CommandBlockEntity.Mode.values())
                .displayOnlyValue()
                .withInitialValue(this.mode)
                .create(this.width / 2 - 50 - 100 - 4, 165, 100, 20, Component.translatable("advMode.mode"), (cycleButton, mode) -> this.mode = mode)
        );
        this.conditionalButton = this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.translatable("advMode.mode.conditional"), Component.translatable("advMode.mode.unconditional"))
                .displayOnlyValue()
                .withInitialValue(this.conditional)
                .create(this.width / 2 - 50, 165, 100, 20, Component.translatable("advMode.type"), (cycleButton, conditional) -> this.conditional = conditional)
        );
        this.autoexecButton = this.addRenderableWidget(
            CycleButton.booleanBuilder(Component.translatable("advMode.mode.autoexec.bat"), Component.translatable("advMode.mode.redstoneTriggered"))
                .displayOnlyValue()
                .withInitialValue(this.autoexec)
                .create(
                    this.width / 2 + 50 + 4, 165, 100, 20, Component.translatable("advMode.triggering"), (cycleButton, autoexec) -> this.autoexec = autoexec
                )
        );
        this.enableControls(false);
    }

    private void enableControls(boolean active) {
        this.doneButton.active = active;
        this.outputButton.active = active;
        this.modeButton.active = active;
        this.conditionalButton.active = active;
        this.autoexecButton.active = active;
    }

    public void updateGui() {
        BaseCommandBlock baseCommandBlock = this.autoCommandBlock.getCommandBlock();
        this.commandEdit.setValue(baseCommandBlock.getCommand());
        boolean bl = baseCommandBlock.isTrackOutput();
        this.mode = this.autoCommandBlock.getMode();
        this.conditional = this.autoCommandBlock.isConditional();
        this.autoexec = this.autoCommandBlock.isAutomatic();
        this.outputButton.setValue(bl);
        this.modeButton.setValue(this.mode);
        this.conditionalButton.setValue(this.conditional);
        this.autoexecButton.setValue(this.autoexec);
        this.updatePreviousOutput(bl);
        this.enableControls(true);
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        this.enableControls(true);
    }

    @Override
    protected void populateAndSendPacket(BaseCommandBlock commandBlock) {
        this.minecraft
            .getConnection()
            .send(
                new ServerboundSetCommandBlockPacket(
                    BlockPos.containing(commandBlock.getPosition()),
                    this.commandEdit.getValue(),
                    this.mode,
                    commandBlock.isTrackOutput(),
                    this.conditional,
                    this.autoexec
                )
            );
    }
}
