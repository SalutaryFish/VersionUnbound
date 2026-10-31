package net.minecraft.client;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;
import net.minecraft.util.SmoothDouble;
import org.lwjgl.glfw.GLFWDropCallback;
import org.slf4j.Logger;

public class MouseHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Minecraft minecraft;
    private boolean isLeftPressed;
    private boolean isMiddlePressed;
    private boolean isRightPressed;
    private double xpos;
    private double ypos;
    private int fakeRightMouse;
    private int activeButton = -1;
    private boolean ignoreFirstMove = true;
    private int clickDepth;
    private double mousePressedTime;
    private final SmoothDouble smoothTurnX = new SmoothDouble();
    private final SmoothDouble smoothTurnY = new SmoothDouble();
    private double accumulatedDX;
    private double accumulatedDY;
    private double accumulatedScrollX;
    private double accumulatedScrollY;
    private double lastHandleMovementTime = Double.MIN_VALUE;
    private boolean mouseGrabbed;

    public MouseHandler(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    /**
     * Will be called when a mouse button is pressed or released.
     *
     * @see GLFWMouseButtonCallbackI
     */
    private void onPress(long windowPointer, int button, int action, int modifiers) {
        if (windowPointer == this.minecraft.getWindow().getWindow()) {
            if (this.minecraft.screen != null) {
                this.minecraft.setLastInputType(InputType.MOUSE);
            }

            boolean bl = action == 1;
            if (Minecraft.ON_OSX && button == 0) {
                if (bl) {
                    if ((modifiers & 2) == 2) {
                        button = 1;
                        this.fakeRightMouse++;
                    }
                } else if (this.fakeRightMouse > 0) {
                    button = 1;
                    this.fakeRightMouse--;
                }
            }

            int i = button;
            if (bl) {
                if (this.minecraft.options.touchscreen().get() && this.clickDepth++ > 0) {
                    return;
                }

                this.activeButton = i;
                this.mousePressedTime = Blaze3D.getTime();
            } else if (this.activeButton != -1) {
                if (this.minecraft.options.touchscreen().get() && --this.clickDepth > 0) {
                    return;
                }

                this.activeButton = -1;
            }

            boolean[] bls = new boolean[]{false};
            if (this.minecraft.getOverlay() == null) {
                if (this.minecraft.screen == null) {
                    if (!this.mouseGrabbed && bl) {
                        this.grabMouse();
                    }
                } else {
                    double d = this.xpos * this.minecraft.getWindow().getGuiScaledWidth() / this.minecraft.getWindow().getScreenWidth();
                    double e = this.ypos * this.minecraft.getWindow().getGuiScaledHeight() / this.minecraft.getWindow().getScreenHeight();
                    Screen screen = this.minecraft.screen;
                    if (bl) {
                        screen.afterMouseAction();
                        Screen.wrapScreenError(() -> bls[0] = screen.mouseClicked(d, e, i), "mouseClicked event handler", screen.getClass().getCanonicalName());
                    } else {
                        Screen.wrapScreenError(
                            () -> bls[0] = screen.mouseReleased(d, e, i), "mouseReleased event handler", screen.getClass().getCanonicalName()
                        );
                    }
                }
            }

            if (!bls[0] && this.minecraft.screen == null && this.minecraft.getOverlay() == null) {
                if (i == 0) {
                    this.isLeftPressed = bl;
                } else if (i == 2) {
                    this.isMiddlePressed = bl;
                } else if (i == 1) {
                    this.isRightPressed = bl;
                }

                KeyMapping.set(InputConstants.Type.MOUSE.getOrCreate(i), bl);
                if (bl) {
                    if (this.minecraft.player.isSpectator() && i == 2) {
                        this.minecraft.gui.getSpectatorGui().onMouseMiddleClick();
                    } else {
                        KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(i));
                    }
                }
            }
        }
    }

    /**
     * Will be called when a scrolling device is used, such as a mouse wheel or scrolling area of a touchpad.
     *
     * @see GLFWScrollCallbackI
     */
    private void onScroll(long windowPointer, double xOffset, double yOffset) {
        if (windowPointer == Minecraft.getInstance().getWindow().getWindow()) {
            boolean bl = this.minecraft.options.discreteMouseScroll().get();
            double d = this.minecraft.options.mouseWheelSensitivity().get();
            double e = (bl ? Math.signum(xOffset) : xOffset) * d;
            double f = (bl ? Math.signum(yOffset) : yOffset) * d;
            if (this.minecraft.getOverlay() == null) {
                if (this.minecraft.screen != null) {
                    double g = this.xpos * this.minecraft.getWindow().getGuiScaledWidth() / this.minecraft.getWindow().getScreenWidth();
                    double h = this.ypos * this.minecraft.getWindow().getGuiScaledHeight() / this.minecraft.getWindow().getScreenHeight();
                    this.minecraft.screen.mouseScrolled(g, h, e, f);
                    this.minecraft.screen.afterMouseAction();
                } else if (this.minecraft.player != null) {
                    if (this.accumulatedScrollX != 0.0 && Math.signum(e) != Math.signum(this.accumulatedScrollX)) {
                        this.accumulatedScrollX = 0.0;
                    }

                    if (this.accumulatedScrollY != 0.0 && Math.signum(f) != Math.signum(this.accumulatedScrollY)) {
                        this.accumulatedScrollY = 0.0;
                    }

                    this.accumulatedScrollX += e;
                    this.accumulatedScrollY += f;
                    int i = (int)this.accumulatedScrollX;
                    int j = (int)this.accumulatedScrollY;
                    if (i == 0 && j == 0) {
                        return;
                    }

                    this.accumulatedScrollX -= i;
                    this.accumulatedScrollY -= j;
                    int k = j == 0 ? -i : j;
                    if (this.minecraft.player.isSpectator()) {
                        if (this.minecraft.gui.getSpectatorGui().isMenuActive()) {
                            this.minecraft.gui.getSpectatorGui().onMouseScrolled(-k);
                        } else {
                            float l = Mth.clamp(this.minecraft.player.getAbilities().getFlyingSpeed() + j * 0.005F, 0.0F, 0.2F);
                            this.minecraft.player.getAbilities().setFlyingSpeed(l);
                        }
                    } else {
                        this.minecraft.player.getInventory().swapPaint(k);
                    }
                }
            }
        }
    }

    private void onDrop(long windowPointer, List<Path> files, int failedFiles) {
        if (this.minecraft.screen != null) {
            this.minecraft.screen.onFilesDrop(files);
        }

        if (failedFiles > 0) {
            SystemToast.onFileDropFailure(this.minecraft, failedFiles);
        }
    }

    public void setup(long windowPointer) {
        InputConstants.setupMouseCallbacks(
            windowPointer,
            (windowPointerx, xpos, ypos) -> this.minecraft.execute(() -> this.onMove(windowPointerx, xpos, ypos)),
            (windowPointerx, button, action, modifiers) -> this.minecraft.execute(() -> this.onPress(windowPointerx, button, action, modifiers)),
            (windowPointerx, xOffset, yOffset) -> this.minecraft.execute(() -> this.onScroll(windowPointerx, xOffset, yOffset)),
            (window, count, names) -> {
                List<Path> list = new ArrayList<>(count);
                int i = 0;

                for (int j = 0; j < count; j++) {
                    String string = GLFWDropCallback.getName(names, j);

                    try {
                        list.add(Paths.get(string));
                    } catch (InvalidPathException invalidPathException) {
                        i++;
                        LOGGER.error("Failed to parse path '{}'", string, invalidPathException);
                    }
                }

                if (!list.isEmpty()) {
                    int j = i;
                    this.minecraft.execute(() -> this.onDrop(window, list, j));
                }
            }
        );
    }

    /**
     * Will be called when the cursor is moved.
     *
     * <p>The callback function receives the cursor position, measured in screen coordinates but relative to the top-left corner of the window client area. On platforms that provide it, the full sub-pixel cursor position is passed on.</p>
     *
     * @see GLFWCursorPosCallbackI
     */
    private void onMove(long windowPointer, double xpos, double ypos) {
        if (windowPointer == Minecraft.getInstance().getWindow().getWindow()) {
            if (this.ignoreFirstMove) {
                this.xpos = xpos;
                this.ypos = ypos;
                this.ignoreFirstMove = false;
            } else {
                if (this.minecraft.isWindowActive()) {
                    this.accumulatedDX = this.accumulatedDX + (xpos - this.xpos);
                    this.accumulatedDY = this.accumulatedDY + (ypos - this.ypos);
                }

                this.xpos = xpos;
                this.ypos = ypos;
            }
        }
    }

    public void handleAccumulatedMovement() {
        double d = Blaze3D.getTime();
        double e = d - this.lastHandleMovementTime;
        this.lastHandleMovementTime = d;
        if (this.minecraft.isWindowActive()) {
            Screen screen = this.minecraft.screen;
            if (screen != null && this.minecraft.getOverlay() == null && (this.accumulatedDX != 0.0 || this.accumulatedDY != 0.0)) {
                double f = this.xpos * this.minecraft.getWindow().getGuiScaledWidth() / this.minecraft.getWindow().getScreenWidth();
                double g = this.ypos * this.minecraft.getWindow().getGuiScaledHeight() / this.minecraft.getWindow().getScreenHeight();
                Screen.wrapScreenError(() -> screen.mouseMoved(f, g), "mouseMoved event handler", screen.getClass().getCanonicalName());
                if (this.activeButton != -1 && this.mousePressedTime > 0.0) {
                    double h = this.accumulatedDX * this.minecraft.getWindow().getGuiScaledWidth() / this.minecraft.getWindow().getScreenWidth();
                    double i = this.accumulatedDY * this.minecraft.getWindow().getGuiScaledHeight() / this.minecraft.getWindow().getScreenHeight();
                    Screen.wrapScreenError(
                        () -> screen.mouseDragged(f, g, this.activeButton, h, i), "mouseDragged event handler", screen.getClass().getCanonicalName()
                    );
                }

                screen.afterMouseMove();
            }

            if (this.isMouseGrabbed() && this.minecraft.player != null) {
                this.turnPlayer(e);
            }
        }

        this.accumulatedDX = 0.0;
        this.accumulatedDY = 0.0;
    }

    private void turnPlayer(double movementTime) {
        double d = this.minecraft.options.sensitivity().get() * 0.6F + 0.2F;
        double e = d * d * d;
        double f = e * 8.0;
        double i;
        double j;
        if (this.minecraft.options.smoothCamera) {
            double g = this.smoothTurnX.getNewDeltaValue(this.accumulatedDX * f, movementTime * f);
            double h = this.smoothTurnY.getNewDeltaValue(this.accumulatedDY * f, movementTime * f);
            i = g;
            j = h;
        } else if (this.minecraft.options.getCameraType().isFirstPerson() && this.minecraft.player.isScoping()) {
            this.smoothTurnX.reset();
            this.smoothTurnY.reset();
            i = this.accumulatedDX * e;
            j = this.accumulatedDY * e;
        } else {
            this.smoothTurnX.reset();
            this.smoothTurnY.reset();
            i = this.accumulatedDX * f;
            j = this.accumulatedDY * f;
        }

        int k = 1;
        if (this.minecraft.options.invertYMouse().get()) {
            k = -1;
        }

        this.minecraft.getTutorial().onMouse(i, j);
        if (this.minecraft.player != null) {
            this.minecraft.player.turn(i, j * k);
        }
    }

    public boolean isLeftPressed() {
        return this.isLeftPressed;
    }

    public boolean isMiddlePressed() {
        return this.isMiddlePressed;
    }

    public boolean isRightPressed() {
        return this.isRightPressed;
    }

    public double xpos() {
        return this.xpos;
    }

    public double ypos() {
        return this.ypos;
    }

    public void setIgnoreFirstMove() {
        this.ignoreFirstMove = true;
    }

    public boolean isMouseGrabbed() {
        return this.mouseGrabbed;
    }

    public void grabMouse() {
        if (this.minecraft.isWindowActive()) {
            if (!this.mouseGrabbed) {
                if (!Minecraft.ON_OSX) {
                    KeyMapping.setAll();
                }

                this.mouseGrabbed = true;
                this.xpos = this.minecraft.getWindow().getScreenWidth() / 2;
                this.ypos = this.minecraft.getWindow().getScreenHeight() / 2;
                InputConstants.grabOrReleaseMouse(this.minecraft.getWindow().getWindow(), 212995, this.xpos, this.ypos);
                this.minecraft.setScreen(null);
                this.minecraft.missTime = 10000;
                this.ignoreFirstMove = true;
            }
        }
    }

    public void releaseMouse() {
        if (this.mouseGrabbed) {
            this.mouseGrabbed = false;
            this.xpos = this.minecraft.getWindow().getScreenWidth() / 2;
            this.ypos = this.minecraft.getWindow().getScreenHeight() / 2;
            InputConstants.grabOrReleaseMouse(this.minecraft.getWindow().getWindow(), 212993, this.xpos, this.ypos);
        }
    }

    public void cursorEntered() {
        this.ignoreFirstMove = true;
    }
}
