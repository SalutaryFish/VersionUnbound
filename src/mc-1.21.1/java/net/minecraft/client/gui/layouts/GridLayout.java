package net.minecraft.client.gui.layouts;

import com.mojang.math.Divisor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.util.Mth;

public class GridLayout extends AbstractLayout {
    private final List<LayoutElement> children = new ArrayList<>();
    private final List<GridLayout.CellInhabitant> cellInhabitants = new ArrayList<>();
    private final LayoutSettings defaultCellSettings = LayoutSettings.defaults();
    private int rowSpacing = 0;
    private int columnSpacing = 0;

    public GridLayout() {
        this(0, 0);
    }

    public GridLayout(int x, int y) {
        super(x, y, 0, 0);
    }

    @Override
    public void arrangeElements() {
        super.arrangeElements();
        int i = 0;
        int j = 0;

        for (GridLayout.CellInhabitant cellInhabitant : this.cellInhabitants) {
            i = Math.max(cellInhabitant.getLastOccupiedRow(), i);
            j = Math.max(cellInhabitant.getLastOccupiedColumn(), j);
        }

        int[] is = new int[j + 1];
        int[] js = new int[i + 1];

        for (GridLayout.CellInhabitant cellInhabitant2 : this.cellInhabitants) {
            int k = cellInhabitant2.getHeight() - (cellInhabitant2.occupiedRows - 1) * this.rowSpacing;
            Divisor divisor = new Divisor(k, cellInhabitant2.occupiedRows);

            for (int l = cellInhabitant2.row; l <= cellInhabitant2.getLastOccupiedRow(); l++) {
                js[l] = Math.max(js[l], divisor.nextInt());
            }

            int l = cellInhabitant2.getWidth() - (cellInhabitant2.occupiedColumns - 1) * this.columnSpacing;
            Divisor divisor2 = new Divisor(l, cellInhabitant2.occupiedColumns);

            for (int m = cellInhabitant2.column; m <= cellInhabitant2.getLastOccupiedColumn(); m++) {
                is[m] = Math.max(is[m], divisor2.nextInt());
            }
        }

        int[] ks = new int[j + 1];
        int[] ls = new int[i + 1];
        ks[0] = 0;

        for (int k = 1; k <= j; k++) {
            ks[k] = ks[k - 1] + is[k - 1] + this.columnSpacing;
        }

        ls[0] = 0;

        for (int k = 1; k <= i; k++) {
            ls[k] = ls[k - 1] + js[k - 1] + this.rowSpacing;
        }

        for (GridLayout.CellInhabitant cellInhabitant3 : this.cellInhabitants) {
            int l = 0;

            for (int n = cellInhabitant3.column; n <= cellInhabitant3.getLastOccupiedColumn(); n++) {
                l += is[n];
            }

            l += this.columnSpacing * (cellInhabitant3.occupiedColumns - 1);
            cellInhabitant3.setX(this.getX() + ks[cellInhabitant3.column], l);
            int n = 0;

            for (int m = cellInhabitant3.row; m <= cellInhabitant3.getLastOccupiedRow(); m++) {
                n += js[m];
            }

            n += this.rowSpacing * (cellInhabitant3.occupiedRows - 1);
            cellInhabitant3.setY(this.getY() + ls[cellInhabitant3.row], n);
        }

        this.width = ks[j] + is[j];
        this.height = ls[i] + js[i];
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column) {
        return this.addChild(child, row, column, this.newCellSettings());
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column, LayoutSettings layoutSettings) {
        return this.addChild(child, row, column, 1, 1, layoutSettings);
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column, Consumer<LayoutSettings> layoutSettingsFactory) {
        return this.addChild(child, row, column, 1, 1, Util.make(this.newCellSettings(), layoutSettingsFactory));
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column, int occupiedRows, int occupiedColumns) {
        return this.addChild(child, row, column, occupiedRows, occupiedColumns, this.newCellSettings());
    }

    public <T extends LayoutElement> T addChild(T child, int row, int column, int occupiedRows, int occupiedColumns, LayoutSettings layoutSettings) {
        if (occupiedRows < 1) {
            throw new IllegalArgumentException("Occupied rows must be at least 1");
        }

        if (occupiedColumns < 1) {
            throw new IllegalArgumentException("Occupied columns must be at least 1");
        }

        this.cellInhabitants.add(new GridLayout.CellInhabitant(child, row, column, occupiedRows, occupiedColumns, layoutSettings));
        this.children.add(child);
        return child;
    }

    public <T extends LayoutElement> T addChild(
        T child, int row, int column, int occupiedRows, int occupiedColumns, Consumer<LayoutSettings> layoutSettingsFactory
    ) {
        return this.addChild(child, row, column, occupiedRows, occupiedColumns, Util.make(this.newCellSettings(), layoutSettingsFactory));
    }

    public GridLayout columnSpacing(int columnSpacing) {
        this.columnSpacing = columnSpacing;
        return this;
    }

    public GridLayout rowSpacing(int rowSpacing) {
        this.rowSpacing = rowSpacing;
        return this;
    }

    public GridLayout spacing(int spacing) {
        return this.columnSpacing(spacing).rowSpacing(spacing);
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        this.children.forEach(visitor);
    }

    public LayoutSettings newCellSettings() {
        return this.defaultCellSettings.copy();
    }

    public LayoutSettings defaultCellSetting() {
        return this.defaultCellSettings;
    }

    public GridLayout.RowHelper createRowHelper(int columns) {
        return new GridLayout.RowHelper(columns);
    }

    static class CellInhabitant extends AbstractLayout.AbstractChildWrapper {
        final int row;
        final int column;
        final int occupiedRows;
        final int occupiedColumns;

        CellInhabitant(LayoutElement child, int row, int column, int occupiedRows, int occupiedColumns, LayoutSettings layoutSettings) {
            super(child, layoutSettings.getExposed());
            this.row = row;
            this.column = column;
            this.occupiedRows = occupiedRows;
            this.occupiedColumns = occupiedColumns;
        }

        public int getLastOccupiedRow() {
            return this.row + this.occupiedRows - 1;
        }

        public int getLastOccupiedColumn() {
            return this.column + this.occupiedColumns - 1;
        }
    }

    public final class RowHelper {
        private final int columns;
        private int index;

        RowHelper(final int columns) {
            this.columns = columns;
        }

        public <T extends LayoutElement> T addChild(T child) {
            return this.addChild(child, 1);
        }

        public <T extends LayoutElement> T addChild(T child, int occupiedColumns) {
            return this.addChild(child, occupiedColumns, this.defaultCellSetting());
        }

        public <T extends LayoutElement> T addChild(T child, LayoutSettings layoutSettings) {
            return this.addChild(child, 1, layoutSettings);
        }

        public <T extends LayoutElement> T addChild(T child, int occupiedColumns, LayoutSettings layoutSettings) {
            int i = this.index / this.columns;
            int j = this.index % this.columns;
            if (j + occupiedColumns > this.columns) {
                i++;
                j = 0;
                this.index = Mth.roundToward(this.index, this.columns);
            }

            this.index += occupiedColumns;
            return GridLayout.this.addChild(child, i, j, 1, occupiedColumns, layoutSettings);
        }

        public GridLayout getGrid() {
            return GridLayout.this;
        }

        public LayoutSettings newCellSettings() {
            return GridLayout.this.newCellSettings();
        }

        public LayoutSettings defaultCellSetting() {
            return GridLayout.this.defaultCellSetting();
        }
    }
}
