package net.minecraft.client.gui.components.events;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;
import net.minecraft.client.gui.navigation.ScreenAxis;
import net.minecraft.client.gui.navigation.ScreenDirection;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Vector2i;

public interface ContainerEventHandler extends GuiEventListener {
    List<? extends GuiEventListener> children();

    /**
     * Returns the first event listener that intersects with the mouse coordinates.
     */
    default Optional<GuiEventListener> getChildAt(double mouseX, double mouseY) {
        for (GuiEventListener guiEventListener : this.children()) {
            if (guiEventListener.isMouseOver(mouseX, mouseY)) {
                return Optional.of(guiEventListener);
            }
        }

        return Optional.empty();
    }

    /**
     * Called when a mouse button is clicked within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that was clicked.
     */
    @Override
    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (GuiEventListener guiEventListener : this.children()) {
            if (guiEventListener.mouseClicked(mouseX, mouseY, button)) {
                this.setFocused(guiEventListener);
                if (button == 0) {
                    this.setDragging(true);
                }

                return true;
            }
        }

        return false;
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
    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.isDragging()) {
            this.setDragging(false);
            if (this.getFocused() != null) {
                return this.getFocused().mouseReleased(mouseX, mouseY, button);
            }
        }

        return this.getChildAt(mouseX, mouseY).filter(guiEventListener -> guiEventListener.mouseReleased(mouseX, mouseY, button)).isPresent();
    }

    /**
     * Called when the mouse is dragged within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param mouseX the X coordinate of the mouse.
     * @param mouseY the Y coordinate of the mouse.
     * @param button the button that is being dragged.
     * @param dragX  the X distance of the drag.
     * @param dragY  the Y distance of the drag.
     */
    @Override
    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return this.getFocused() != null && this.isDragging() && button == 0 ? this.getFocused().mouseDragged(mouseX, mouseY, button, dragX, dragY) : false;
    }

    boolean isDragging();

    /**
     * Sets if the GUI element is dragging or not.
     *
     * @param var1 the dragging state of the GUI element.
     */
    void setDragging(boolean var1);

    @Override
    default boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return this.getChildAt(mouseX, mouseY).filter(listener -> listener.mouseScrolled(mouseX, mouseY, scrollX, scrollY)).isPresent();
    }

    /**
     * Called when a keyboard key is pressed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the pressed key.
     * @param scanCode  the scan code of the pressed key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return this.getFocused() != null && this.getFocused().keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Called when a keyboard key is released within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param keyCode   the key code of the released key.
     * @param scanCode  the scan code of the released key.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    default boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return this.getFocused() != null && this.getFocused().keyReleased(keyCode, scanCode, modifiers);
    }

    /**
     * Called when a character is typed within the GUI element.
     * <p>
     * @return {@code true} if the event is consumed, {@code false} otherwise.
     *
     * @param codePoint the code point of the typed character.
     * @param modifiers the keyboard modifiers.
     */
    @Override
    default boolean charTyped(char codePoint, int modifiers) {
        return this.getFocused() != null && this.getFocused().charTyped(codePoint, modifiers);
    }

    @Nullable
    GuiEventListener getFocused();

    /**
     * Sets the focus state of the GUI element.
     *
     * @param var1 the focused GUI element.
     */
    void setFocused(@Nullable GuiEventListener var1);

    /**
     * Sets the focus state of the GUI element.
     *
     * @param focused {@code true} to apply focus, {@code false} to remove focus
     */
    @Override
    default void setFocused(boolean focused) {
    }

    @Override
    default boolean isFocused() {
        return this.getFocused() != null;
    }

    @Nullable
    @Override
    default ComponentPath getCurrentFocusPath() {
        GuiEventListener guiEventListener = this.getFocused();
        return guiEventListener != null ? ComponentPath.path(this, guiEventListener.getCurrentFocusPath()) : null;
    }

    /**
     * Retrieves the next focus path based on the given focus navigation event.
     * <p>
     * @return The next focus path as a ComponentPath, or {@code null} if there is no next focus path.
     *
     * @param event the focus navigation event.
     */
    @Nullable
    @Override
    default ComponentPath nextFocusPath(FocusNavigationEvent event) {
        GuiEventListener guiEventListener = this.getFocused();
        if (guiEventListener != null) {
            ComponentPath componentPath = guiEventListener.nextFocusPath(event);
            if (componentPath != null) {
                return ComponentPath.path(this, componentPath);
            }
        }

        if (event instanceof FocusNavigationEvent.TabNavigation tabNavigation) {
            return this.handleTabNavigation(tabNavigation);
        } else {
            return event instanceof FocusNavigationEvent.ArrowNavigation arrowNavigation ? this.handleArrowNavigation(arrowNavigation) : null;
        }
    }

    /**
     * Handles tab-based navigation events.
     * <p>
     * @return The next focus path for tab navigation, or {@code null} if no suitable path is found.
     *
     * @param tabNavigation The tab navigation event.
     */
    @Nullable
    private ComponentPath handleTabNavigation(FocusNavigationEvent.TabNavigation tabNavigation) {
        boolean bl = tabNavigation.forward();
        GuiEventListener guiEventListener = this.getFocused();
        List<? extends GuiEventListener> list = new ArrayList<>(this.children());
        Collections.sort(list, Comparator.comparingInt(listener -> listener.getTabOrderGroup()));
        int i = list.indexOf(guiEventListener);
        int j;
        if (guiEventListener != null && i >= 0) {
            j = i + (bl ? 1 : 0);
        } else if (bl) {
            j = 0;
        } else {
            j = list.size();
        }

        ListIterator<? extends GuiEventListener> listIterator = list.listIterator(j);
        BooleanSupplier booleanSupplier = bl ? listIterator::hasNext : listIterator::hasPrevious;
        Supplier<? extends GuiEventListener> supplier = bl ? listIterator::next : listIterator::previous;

        while (booleanSupplier.getAsBoolean()) {
            GuiEventListener guiEventListener2 = supplier.get();
            ComponentPath componentPath = guiEventListener2.nextFocusPath(tabNavigation);
            if (componentPath != null) {
                return ComponentPath.path(this, componentPath);
            }
        }

        return null;
    }

    /**
     * Handles arrow-based navigation events.
     * <p>
     * @return The next focus path for arrow navigation, or {@code null} if no suitable path is found.
     *
     * @param arrowNavigation The arrow navigation event.
     */
    @Nullable
    private ComponentPath handleArrowNavigation(FocusNavigationEvent.ArrowNavigation arrowNavigation) {
        GuiEventListener guiEventListener = this.getFocused();
        if (guiEventListener == null) {
            ScreenDirection screenDirection = arrowNavigation.direction();
            ScreenRectangle screenRectangle = this.getRectangle().getBorder(screenDirection.getOpposite());
            return ComponentPath.path(this, this.nextFocusPathInDirection(screenRectangle, screenDirection, null, arrowNavigation));
        } else {
            ScreenRectangle screenRectangle2 = guiEventListener.getRectangle();
            return ComponentPath.path(this, this.nextFocusPathInDirection(screenRectangle2, arrowNavigation.direction(), guiEventListener, arrowNavigation));
        }
    }

    /**
     * Calculates the next focus path in a specific direction.
     * <p>
     * @return The next focus path in the specified direction, or {@code null} if no suitable path is found.
     *
     * @param rectangle The screen rectangle.
     * @param direction The direction of navigation.
     * @param listener  The currently focused GUI event listener.
     * @param event     The focus navigation event.
     */
    @Nullable
    private ComponentPath nextFocusPathInDirection(
        ScreenRectangle rectangle, ScreenDirection direction, @Nullable GuiEventListener listener, FocusNavigationEvent event
    ) {
        ScreenAxis screenAxis = direction.getAxis();
        ScreenAxis screenAxis2 = screenAxis.orthogonal();
        ScreenDirection screenDirection = screenAxis2.getPositive();
        int i = rectangle.getBoundInDirection(direction.getOpposite());
        List<GuiEventListener> list = new ArrayList<>();

        for (GuiEventListener guiEventListener : this.children()) {
            if (guiEventListener != listener) {
                ScreenRectangle screenRectangle = guiEventListener.getRectangle();
                if (screenRectangle.overlapsInAxis(rectangle, screenAxis2)) {
                    int j = screenRectangle.getBoundInDirection(direction.getOpposite());
                    if (direction.isAfter(j, i)) {
                        list.add(guiEventListener);
                    } else if (j == i && direction.isAfter(screenRectangle.getBoundInDirection(direction), rectangle.getBoundInDirection(direction))) {
                        list.add(guiEventListener);
                    }
                }
            }
        }

        Comparator<GuiEventListener> comparator = Comparator.comparing(
            listenerx -> listenerx.getRectangle().getBoundInDirection(direction.getOpposite()), direction.coordinateValueComparator()
        );
        Comparator<GuiEventListener> comparator2 = Comparator.comparing(
            listenerx -> listenerx.getRectangle().getBoundInDirection(screenDirection.getOpposite()), screenDirection.coordinateValueComparator()
        );
        list.sort(comparator.thenComparing(comparator2));

        for (GuiEventListener guiEventListener2 : list) {
            ComponentPath componentPath = guiEventListener2.nextFocusPath(event);
            if (componentPath != null) {
                return componentPath;
            }
        }

        return this.nextFocusPathVaguelyInDirection(rectangle, direction, listener, event);
    }

    /**
     * Calculates the next focus path in a vague direction.
     * <p>
     * @return The next focus path in the vague direction, or {@code null} if no suitable path is found.
     *
     * @param rectangle The screen rectangle.
     * @param direction The direction of navigation.
     * @param listener  The currently focused GUI event listener.
     * @param event     The focus navigation event.
     */
    @Nullable
    private ComponentPath nextFocusPathVaguelyInDirection(
        ScreenRectangle rectangle, ScreenDirection direction, @Nullable GuiEventListener listener, FocusNavigationEvent event
    ) {
        ScreenAxis screenAxis = direction.getAxis();
        ScreenAxis screenAxis2 = screenAxis.orthogonal();
        List<Pair<GuiEventListener, Long>> list = new ArrayList<>();
        ScreenPosition screenPosition = ScreenPosition.of(screenAxis, rectangle.getBoundInDirection(direction), rectangle.getCenterInAxis(screenAxis2));

        for (GuiEventListener guiEventListener : this.children()) {
            if (guiEventListener != listener) {
                ScreenRectangle screenRectangle = guiEventListener.getRectangle();
                ScreenPosition screenPosition2 = ScreenPosition.of(
                    screenAxis, screenRectangle.getBoundInDirection(direction.getOpposite()), screenRectangle.getCenterInAxis(screenAxis2)
                );
                if (direction.isAfter(screenPosition2.getCoordinate(screenAxis), screenPosition.getCoordinate(screenAxis))) {
                    long l = Vector2i.distanceSquared(screenPosition.x(), screenPosition.y(), screenPosition2.x(), screenPosition2.y());
                    list.add(Pair.of(guiEventListener, l));
                }
            }
        }

        list.sort(Comparator.comparingDouble(Pair::getSecond));

        for (Pair<GuiEventListener, Long> pair : list) {
            ComponentPath componentPath = pair.getFirst().nextFocusPath(event);
            if (componentPath != null) {
                return componentPath;
            }
        }

        return null;
    }
}
