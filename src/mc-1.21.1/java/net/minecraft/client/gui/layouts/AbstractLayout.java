package net.minecraft.client.gui.layouts;

import net.minecraft.util.Mth;

public abstract class AbstractLayout implements Layout {
    private int x;
    private int y;
    protected int width;
    protected int height;

    public AbstractLayout(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public void setX(int x) {
        this.visitChildren(layoutElement -> {
            int j = layoutElement.getX() + (x - this.getX());
            layoutElement.setX(j);
        });
        this.x = x;
    }

    @Override
    public void setY(int y) {
        this.visitChildren(layoutElement -> {
            int j = layoutElement.getY() + (y - this.getY());
            layoutElement.setY(j);
        });
        this.y = y;
    }

    @Override
    public int getX() {
        return this.x;
    }

    @Override
    public int getY() {
        return this.y;
    }

    @Override
    public int getWidth() {
        return this.width;
    }

    @Override
    public int getHeight() {
        return this.height;
    }

    protected abstract static class AbstractChildWrapper {
        public final LayoutElement child;
        public final LayoutSettings.LayoutSettingsImpl layoutSettings;

        protected AbstractChildWrapper(LayoutElement child, LayoutSettings layoutSettings) {
            this.child = child;
            this.layoutSettings = layoutSettings.getExposed();
        }

        public int getHeight() {
            return this.child.getHeight() + this.layoutSettings.paddingTop + this.layoutSettings.paddingBottom;
        }

        public int getWidth() {
            return this.child.getWidth() + this.layoutSettings.paddingLeft + this.layoutSettings.paddingRight;
        }

        public void setX(int x, int width) {
            float f = this.layoutSettings.paddingLeft;
            float g = width - this.child.getWidth() - this.layoutSettings.paddingRight;
            int i = (int)Mth.lerp(this.layoutSettings.xAlignment, f, g);
            this.child.setX(i + x);
        }

        public void setY(int y, int height) {
            float f = this.layoutSettings.paddingTop;
            float g = height - this.child.getHeight() - this.layoutSettings.paddingBottom;
            int i = Math.round(Mth.lerp(this.layoutSettings.yAlignment, f, g));
            this.child.setY(i + y);
        }
    }
}
