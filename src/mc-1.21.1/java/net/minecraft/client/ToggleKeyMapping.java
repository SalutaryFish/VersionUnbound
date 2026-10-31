package net.minecraft.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.BooleanSupplier;

public class ToggleKeyMapping extends KeyMapping {
    private final BooleanSupplier needsToggle;

    public ToggleKeyMapping(String name, int keyCode, String category, BooleanSupplier needsToggle) {
        super(name, InputConstants.Type.KEYSYM, keyCode, category);
        this.needsToggle = needsToggle;
    }

    @Override
    public void setDown(boolean value) {
        if (this.needsToggle.getAsBoolean()) {
            if (value) {
                super.setDown(!this.isDown());
            }
        } else {
            super.setDown(value);
        }
    }

    protected void reset() {
        super.setDown(false);
    }
}
