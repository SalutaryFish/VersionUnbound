package com.mojang.realmsclient.gui.screens;

import com.mojang.realmsclient.util.task.LongRunningTask;
import net.minecraft.client.gui.screens.Screen;

public class RealmsLongRunningMcoTickTaskScreen extends RealmsLongRunningMcoTaskScreen {
    private final LongRunningTask task;

    public RealmsLongRunningMcoTickTaskScreen(Screen lastScreen, LongRunningTask task) {
        super(lastScreen, task);
        this.task = task;
    }

    @Override
    public void tick() {
        super.tick();
        this.task.tick();
    }

    @Override
    protected void cancel() {
        this.task.abortTask();
        super.cancel();
    }
}
