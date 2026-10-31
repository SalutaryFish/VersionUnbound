package net.minecraft.client.gui.layouts;

public interface LayoutSettings {
    LayoutSettings padding(int var1);

    LayoutSettings padding(int var1, int var2);

    LayoutSettings padding(int var1, int var2, int var3, int var4);

    LayoutSettings paddingLeft(int var1);

    LayoutSettings paddingTop(int var1);

    LayoutSettings paddingRight(int var1);

    LayoutSettings paddingBottom(int var1);

    LayoutSettings paddingHorizontal(int var1);

    LayoutSettings paddingVertical(int var1);

    LayoutSettings align(float var1, float var2);

    LayoutSettings alignHorizontally(float var1);

    LayoutSettings alignVertically(float var1);

    default LayoutSettings alignHorizontallyLeft() {
        return this.alignHorizontally(0.0F);
    }

    default LayoutSettings alignHorizontallyCenter() {
        return this.alignHorizontally(0.5F);
    }

    default LayoutSettings alignHorizontallyRight() {
        return this.alignHorizontally(1.0F);
    }

    default LayoutSettings alignVerticallyTop() {
        return this.alignVertically(0.0F);
    }

    default LayoutSettings alignVerticallyMiddle() {
        return this.alignVertically(0.5F);
    }

    default LayoutSettings alignVerticallyBottom() {
        return this.alignVertically(1.0F);
    }

    LayoutSettings copy();

    LayoutSettings.LayoutSettingsImpl getExposed();

    static LayoutSettings defaults() {
        return new LayoutSettings.LayoutSettingsImpl();
    }

    class LayoutSettingsImpl implements LayoutSettings {
        public int paddingLeft;
        public int paddingTop;
        public int paddingRight;
        public int paddingBottom;
        public float xAlignment;
        public float yAlignment;

        public LayoutSettingsImpl() {
        }

        public LayoutSettingsImpl(LayoutSettings.LayoutSettingsImpl other) {
            this.paddingLeft = other.paddingLeft;
            this.paddingTop = other.paddingTop;
            this.paddingRight = other.paddingRight;
            this.paddingBottom = other.paddingBottom;
            this.xAlignment = other.xAlignment;
            this.yAlignment = other.yAlignment;
        }

        public LayoutSettings.LayoutSettingsImpl padding(int padding) {
            return this.padding(padding, padding);
        }

        public LayoutSettings.LayoutSettingsImpl padding(int horizontalPadding, int verticalPadding) {
            return this.paddingHorizontal(horizontalPadding).paddingVertical(verticalPadding);
        }

        public LayoutSettings.LayoutSettingsImpl padding(int paddingLeft, int paddingTop, int paddingRight, int paddingBottom) {
            return this.paddingLeft(paddingLeft).paddingRight(paddingRight).paddingTop(paddingTop).paddingBottom(paddingBottom);
        }

        public LayoutSettings.LayoutSettingsImpl paddingLeft(int paddingLeft) {
            this.paddingLeft = paddingLeft;
            return this;
        }

        public LayoutSettings.LayoutSettingsImpl paddingTop(int paddingTop) {
            this.paddingTop = paddingTop;
            return this;
        }

        public LayoutSettings.LayoutSettingsImpl paddingRight(int paddingRight) {
            this.paddingRight = paddingRight;
            return this;
        }

        public LayoutSettings.LayoutSettingsImpl paddingBottom(int paddingBottom) {
            this.paddingBottom = paddingBottom;
            return this;
        }

        public LayoutSettings.LayoutSettingsImpl paddingHorizontal(int horizontalPadding) {
            return this.paddingLeft(horizontalPadding).paddingRight(horizontalPadding);
        }

        public LayoutSettings.LayoutSettingsImpl paddingVertical(int verticalPadding) {
            return this.paddingTop(verticalPadding).paddingBottom(verticalPadding);
        }

        public LayoutSettings.LayoutSettingsImpl align(float xAlignment, float yAlignment) {
            this.xAlignment = xAlignment;
            this.yAlignment = yAlignment;
            return this;
        }

        public LayoutSettings.LayoutSettingsImpl alignHorizontally(float xAlignment) {
            this.xAlignment = xAlignment;
            return this;
        }

        public LayoutSettings.LayoutSettingsImpl alignVertically(float yAlignment) {
            this.yAlignment = yAlignment;
            return this;
        }

        public LayoutSettings.LayoutSettingsImpl copy() {
            return new LayoutSettings.LayoutSettingsImpl(this);
        }

        @Override
        public LayoutSettings.LayoutSettingsImpl getExposed() {
            return this;
        }
    }
}
