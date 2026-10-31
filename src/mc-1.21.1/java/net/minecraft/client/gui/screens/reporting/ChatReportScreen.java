package net.minecraft.client.gui.screens.reporting;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.UUID;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.layouts.CommonLayouts;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.chat.report.ChatReport;
import net.minecraft.client.multiplayer.chat.report.ReportReason;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.network.chat.Component;

public class ChatReportScreen extends AbstractReportScreen<ChatReport.Builder> {
    private static final Component TITLE = Component.translatable("gui.chatReport.title");
    private static final Component SELECT_CHAT_MESSAGE = Component.translatable("gui.chatReport.select_chat");
    private MultiLineEditBox commentBox;
    private Button selectMessagesButton;
    private Button selectReasonButton;

    private ChatReportScreen(Screen lastScreen, ReportingContext reportingContext, ChatReport.Builder reportBuilder) {
        super(TITLE, lastScreen, reportingContext, reportBuilder);
    }

    public ChatReportScreen(Screen lastScreen, ReportingContext reportingContext, UUID reportId) {
        this(lastScreen, reportingContext, new ChatReport.Builder(reportId, reportingContext.sender().reportLimits()));
    }

    public ChatReportScreen(Screen lastScreen, ReportingContext reportContext, ChatReport report) {
        this(lastScreen, reportContext, new ChatReport.Builder(report, reportContext.sender().reportLimits()));
    }

    @Override
    protected void addContent() {
        this.selectMessagesButton = this.layout
            .addChild(
                Button.builder(
                        SELECT_CHAT_MESSAGE,
                        button -> this.minecraft.setScreen(new ChatSelectionScreen(this, this.reportingContext, this.reportBuilder, builder -> {
                            this.reportBuilder = builder;
                            this.onReportChanged();
                        }))
                    )
                    .width(280)
                    .build()
            );
        this.selectReasonButton = Button.builder(
                SELECT_REASON, button -> this.minecraft.setScreen(new ReportReasonSelectionScreen(this, this.reportBuilder.reason(), reportReason -> {
                    this.reportBuilder.setReason(reportReason);
                    this.onReportChanged();
                }))
            )
            .width(280)
            .build();
        this.layout.addChild(CommonLayouts.labeledElement(this.font, this.selectReasonButton, OBSERVED_WHAT_LABEL));
        this.commentBox = this.createCommentBox(280, 9 * 8, string -> {
            this.reportBuilder.setComments(string);
            this.onReportChanged();
        });
        this.layout.addChild(CommonLayouts.labeledElement(this.font, this.commentBox, MORE_COMMENTS_LABEL, layoutSettings -> layoutSettings.paddingBottom(12)));
    }

    @Override
    protected void onReportChanged() {
        IntSet intSet = this.reportBuilder.reportedMessages();
        if (intSet.isEmpty()) {
            this.selectMessagesButton.setMessage(SELECT_CHAT_MESSAGE);
        } else {
            this.selectMessagesButton.setMessage(Component.translatable("gui.chatReport.selected_chat", intSet.size()));
        }

        ReportReason reportReason = this.reportBuilder.reason();
        if (reportReason != null) {
            this.selectReasonButton.setMessage(reportReason.title());
        } else {
            this.selectReasonButton.setMessage(SELECT_REASON);
        }

        super.onReportChanged();
    }

    /**
     * Called when a mouse button is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was released.
     */
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(mouseX, mouseY, button) ? true : this.commentBox.mouseReleased(mouseX, mouseY, button);
    }
}
