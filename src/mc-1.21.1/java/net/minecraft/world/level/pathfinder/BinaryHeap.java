package net.minecraft.world.level.pathfinder;

import java.util.Arrays;

public class BinaryHeap {
    private Node[] heap = new Node[128];
    private int size;

    /**
     * Adds a point to the path
     */
    public Node insert(Node point) {
        if (point.heapIdx >= 0) {
            throw new IllegalStateException("OW KNOWS!");
        }

        if (this.size == this.heap.length) {
            Node[] nodes = new Node[this.size << 1];
            System.arraycopy(this.heap, 0, nodes, 0, this.size);
            this.heap = nodes;
        }

        this.heap[this.size] = point;
        point.heapIdx = this.size;
        this.upHeap(this.size++);
        return point;
    }

    public void clear() {
        this.size = 0;
    }

    public Node peek() {
        return this.heap[0];
    }

    public Node pop() {
        Node node = this.heap[0];
        this.heap[0] = this.heap[--this.size];
        this.heap[this.size] = null;
        if (this.size > 0) {
            this.downHeap(0);
        }

        node.heapIdx = -1;
        return node;
    }

    public void remove(Node node) {
        this.heap[node.heapIdx] = this.heap[--this.size];
        this.heap[this.size] = null;
        if (this.size > node.heapIdx) {
            if (this.heap[node.heapIdx].f < node.f) {
                this.upHeap(node.heapIdx);
            } else {
                this.downHeap(node.heapIdx);
            }
        }

        node.heapIdx = -1;
    }

    /**
     * Changes the provided point's total cost if costIn is smaller
     */
    public void changeCost(Node point, float cost) {
        float f = point.f;
        point.f = cost;
        if (cost < f) {
            this.upHeap(point.heapIdx);
        } else {
            this.downHeap(point.heapIdx);
        }
    }

    public int size() {
        return this.size;
    }

    /**
     * Sorts a point to the left
     */
    private void upHeap(int index) {
        Node node = this.heap[index];
        float f = node.f;

        while (index > 0) {
            int i = index - 1 >> 1;
            Node node2 = this.heap[i];
            if (!(f < node2.f)) {
                break;
            }

            this.heap[index] = node2;
            node2.heapIdx = index;
            index = i;
        }

        this.heap[index] = node;
        node.heapIdx = index;
    }

    /**
     * Sorts a point to the right
     */
    private void downHeap(int index) {
        Node node = this.heap[index];
        float f = node.f;

        while (true) {
            int i = 1 + (index << 1);
            int j = i + 1;
            if (i >= this.size) {
                break;
            }

            Node node2 = this.heap[i];
            float g = node2.f;
            Node node3;
            float h;
            if (j >= this.size) {
                node3 = null;
                h = Float.POSITIVE_INFINITY;
            } else {
                node3 = this.heap[j];
                h = node3.f;
            }

            if (g < h) {
                if (!(g < f)) {
                    break;
                }

                this.heap[index] = node2;
                node2.heapIdx = index;
                index = i;
            } else {
                if (!(h < f)) {
                    break;
                }

                this.heap[index] = node3;
                node3.heapIdx = index;
                index = j;
            }
        }

        this.heap[index] = node;
        node.heapIdx = index;
    }

    public boolean isEmpty() {
        return this.size == 0;
    }

    public Node[] getHeap() {
        return Arrays.copyOf(this.heap, this.size);
    }
}
