package net.minecraft.client.gui.components.toasts;

import com.google.common.collect.Queues;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public class ToastComponent {
    private static final int SLOT_COUNT = 5;
    private static final int NO_SPACE = -1;
    final Minecraft minecraft;
    private final List<ToastComponent.ToastInstance<?>> visible = new ArrayList<>();
    private final BitSet occupiedSlots = new BitSet(5);
    private final Deque<Toast> queued = Queues.newArrayDeque();

    public ToastComponent(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void render(GuiGraphics guiGraphics) {
        if (!this.minecraft.options.hideGui) {
            int i = guiGraphics.guiWidth();
            this.visible.removeIf(toast -> {
                if (toast != null && toast.render(i, guiGraphics)) {
                    this.occupiedSlots.clear(toast.index, toast.index + toast.slotCount);
                    return true;
                } else {
                    return false;
                }
            });
            if (!this.queued.isEmpty() && this.freeSlots() > 0) {
                this.queued.removeIf(toast -> {
                    int ix = toast.slotCount();
                    int j = this.findFreeIndex(ix);
                    if (j != -1) {
                        this.visible.add(new ToastComponent.ToastInstance<>(toast, j, ix));
                        this.occupiedSlots.set(j, j + ix);
                        return true;
                    } else {
                        return false;
                    }
                });
            }
        }
    }

    private int findFreeIndex(int slotCount) {
        if (this.freeSlots() >= slotCount) {
            int i = 0;

            for (int j = 0; j < 5; j++) {
                if (this.occupiedSlots.get(j)) {
                    i = 0;
                } else if (++i == slotCount) {
                    return j + 1 - i;
                }
            }
        }

        return -1;
    }

    private int freeSlots() {
        return 5 - this.occupiedSlots.cardinality();
    }

    @Nullable
    public <T extends Toast> T getToast(Class<? extends T> toastClass, Object token) {
        for (ToastComponent.ToastInstance<?> toastInstance : this.visible) {
            if (toastInstance != null && toastClass.isAssignableFrom(toastInstance.getToast().getClass()) && toastInstance.getToast().getToken().equals(token)) {
                return (T)toastInstance.getToast();
            }
        }

        for (Toast toast : this.queued) {
            if (toastClass.isAssignableFrom(toast.getClass()) && toast.getToken().equals(token)) {
                return (T)toast;
            }
        }

        return null;
    }

    public void clear() {
        this.occupiedSlots.clear();
        this.visible.clear();
        this.queued.clear();
    }

    public void addToast(Toast toast) {
        this.queued.add(toast);
    }

    public Minecraft getMinecraft() {
        return this.minecraft;
    }

    public double getNotificationDisplayTimeMultiplier() {
        return this.minecraft.options.notificationDisplayTime().get();
    }

    class ToastInstance<T extends Toast> {
        private static final long ANIMATION_TIME = 600L;
        private final T toast;
        final int index;
        final int slotCount;
        private long animationTime = -1L;
        private long visibleTime = -1L;
        private Toast.Visibility visibility = Toast.Visibility.SHOW;

        ToastInstance(final T toast, final int index, final int slotCount) {
            this.toast = toast;
            this.index = index;
            this.slotCount = slotCount;
        }

        public T getToast() {
            return this.toast;
        }

        private float getVisibility(long time) {
            float f = Mth.clamp((float)(time - this.animationTime) / 600.0F, 0.0F, 1.0F);
            f *= f;
            return this.visibility == Toast.Visibility.HIDE ? 1.0F - f : f;
        }

        public boolean render(int screenWidth, GuiGraphics guiGraphics) {
            long l = Util.getMillis();
            if (this.animationTime == -1L) {
                this.animationTime = l;
                this.visibility.playSound(ToastComponent.this.minecraft.getSoundManager());
            }

            if (this.visibility == Toast.Visibility.SHOW && l - this.animationTime <= 600L) {
                this.visibleTime = l;
            }

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(screenWidth - this.toast.width() * this.getVisibility(l), this.index * 32, 800.0F);
            Toast.Visibility visibility = this.toast.render(guiGraphics, ToastComponent.this, l - this.visibleTime);
            guiGraphics.pose().popPose();
            if (visibility != this.visibility) {
                this.animationTime = l - (int)((1.0F - this.getVisibility(l)) * 600.0F);
                this.visibility = visibility;
                this.visibility.playSound(ToastComponent.this.minecraft.getSoundManager());
            }

            return this.visibility == Toast.Visibility.HIDE && l - this.animationTime > 600L;
        }
    }
}
