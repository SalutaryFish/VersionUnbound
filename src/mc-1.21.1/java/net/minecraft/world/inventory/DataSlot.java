package net.minecraft.world.inventory;

public abstract class DataSlot {
    private int prevValue;

    public static DataSlot forContainer(ContainerData data, int idx) {
        return new DataSlot() {
            @Override
            public int get() {
                return data.get(idx);
            }

            @Override
            public void set(int i) {
                data.set(idx, i);
            }
        };
    }

    public static DataSlot shared(int[] data, int idx) {
        return new DataSlot() {
            @Override
            public int get() {
                return data[idx];
            }

            @Override
            public void set(int i) {
                data[idx] = i;
            }
        };
    }

    public static DataSlot standalone() {
        return new DataSlot() {
            private int value;

            @Override
            public int get() {
                return this.value;
            }

            @Override
            public void set(int i) {
                this.value = i;
            }
        };
    }

    public abstract int get();

    public abstract void set(int var1);

    public boolean checkAndClearUpdateFlag() {
        int i = this.get();
        boolean bl = i != this.prevValue;
        this.prevValue = i;
        return bl;
    }
}
