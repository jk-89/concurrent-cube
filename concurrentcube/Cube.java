package concurrentcube;

import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

public class Cube {
    static final int SIDES_NUMBER = 6;
    static final int MOVES = 4;
    static final int SHOW_TYPE = 3;

    private final Side[] sides;
    private final int size;
    private final BiConsumer<Integer, Integer> beforeRotation;
    private final BiConsumer<Integer, Integer> afterRotation;
    private final Runnable beforeShowing;
    private final Runnable afterShowing;

    private int typeWorking = -1;
    private int workingCount = 0;
    private final int[] waitingCount = new int[MOVES];
    private final Semaphore[] waiting = new Semaphore[MOVES];
    private final Semaphore mutex = new Semaphore(1, true);
    private final Semaphore[] layerWaiting;

    public Cube(int size,
                BiConsumer<Integer, Integer> beforeRotation,
                BiConsumer<Integer, Integer> afterRotation,
                Runnable beforeShowing,
                Runnable afterShowing) {
        this.size = size;
        this.beforeRotation = beforeRotation;
        this.afterRotation = afterRotation;
        this.beforeShowing = beforeShowing;
        this.afterShowing = afterShowing;

        sides = new Side[SIDES_NUMBER];
        for (int i = 0; i < SIDES_NUMBER; i++) {
            sides[i] = new Side(size, i);
        }

        layerWaiting = new Semaphore[size];
        for (int i = 0; i < MOVES; i++) {
            waiting[i] = new Semaphore(0, true);
        }
        for (int j = 0; j < size; j++) {
            layerWaiting[j] = new Semaphore(1, true);
        }
    }

    public int getSize() {
        return size;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SIDES_NUMBER; i++) {
            sb.append(sides[i].toStringArray());
            sb.append("\n");
        }
        return sb.toString();
    }

    private int allWaiting() {
        return waitingCount[0] + waitingCount[1] + waitingCount[2] + waitingCount[3];
    }

    // implementation of rotations
    private void properRotate(int side, int layer) throws InterruptedException {
        int reflected = size - layer - 1;
        switch (side) {
            case 0 -> {
                if (layer == 0)
                    sides[0].rotate90Degrees(true);
                if (layer == size - 1)
                    sides[5].rotate90Degrees(false);
                sides[4].swapRowWithRow(layer, layer, false, sides[1]);
                sides[1].swapRowWithRow(layer, layer, false, sides[2]);
                sides[2].swapRowWithRow(layer, layer, false, sides[3]);
            }
            case 1 -> {
                if (layer == 0)
                    sides[1].rotate90Degrees(true);
                if (layer == size - 1)
                    sides[3].rotate90Degrees(false);
                sides[0].swapColumnWithColumn(layer, reflected, true, sides[4]);
                sides[4].swapColumnWithColumn(reflected, layer, true, sides[5]);
                sides[5].swapColumnWithColumn(layer, layer, false, sides[2]);
            }
            case 2 -> {
                if (layer == 0)
                    sides[2].rotate90Degrees(true);
                if (layer == size - 1)
                    sides[4].rotate90Degrees(false);
                sides[0].swapRowWithColumn(reflected, reflected, true, sides[1]);
                sides[1].swapColumnWithRow(reflected, layer, false, sides[5]);
                sides[5].swapRowWithColumn(layer, layer, true, sides[3]);
            }
            case 3 -> {
                if (layer == 0)
                    sides[3].rotate90Degrees(true);
                if (layer == size - 1)
                    sides[1].rotate90Degrees(false);
                sides[0].swapColumnWithColumn(reflected, reflected, false, sides[2]);
                sides[2].swapColumnWithColumn(reflected, reflected, false, sides[5]);
                sides[5].swapColumnWithColumn(reflected, layer, true, sides[4]);
            }
            case 4 -> {
                if (layer == 0)
                    sides[4].rotate90Degrees(true);
                if (layer == size - 1)
                    sides[2].rotate90Degrees(false);
                sides[0].swapRowWithColumn(layer, reflected, false, sides[3]);
                sides[3].swapColumnWithRow(reflected, reflected, true, sides[5]);
                sides[5].swapRowWithColumn(reflected, layer, false, sides[1]);
            }
            case 5 -> {
                if (layer == 0)
                    sides[5].rotate90Degrees(true);
                if (layer == size - 1)
                    sides[0].rotate90Degrees(false);
                sides[2].swapRowWithRow(reflected, reflected, false, sides[1]);
                sides[1].swapRowWithRow(reflected, reflected, false, sides[4]);
                sides[4].swapRowWithRow(reflected, reflected, false, sides[3]);
            }
            default -> {
            }
        }
    }

    // implementation of show
    private String properShow() {
        beforeShowing.run();

        StringBuilder sb = new StringBuilder();
        for (Side side : sides) {
            sb.append(side.toString());
        }

        afterShowing.run();
        return sb.toString();
    }

    // responsible for proper rotations (some threads will wait on semaphores
    // in case they are trying to move the same layer)
    private void rotateViaPlane(int side, int layer, int myLayer) throws InterruptedException {
        layerWaiting[myLayer].acquire();

        beforeRotation.accept(side, layer);
        properRotate(side, layer);
        afterRotation.accept(side, layer);

        layerWaiting[myLayer].release();
    }

    // waits until current plane rotate (or show) can happen
    private void beforeFunction(int moveType) throws InterruptedException {
        mutex.acquire();
        if (allWaiting() != 0 || (workingCount > 0 && typeWorking != moveType)) {
            waitingCount[moveType]++;
            mutex.release();
            waiting[moveType].acquireUninterruptibly();
            waitingCount[moveType]--;
        }
        if (workingCount == 0)
            typeWorking = moveType;
        workingCount++;
        if (waitingCount[moveType] != 0)
            waiting[moveType].release();
        else
            mutex.release();
    }

    private void afterFunction(int moveType) throws InterruptedException {
        mutex.acquireUninterruptibly();
        workingCount--;
        if (workingCount == 0) {
            typeWorking = -1;
            boolean woke = false;
            for (int i = 1; i <= MOVES; i++) {
                int next = (moveType + i) % MOVES;
                if (waitingCount[next] != 0) {
                    waiting[next].release();
                    woke = true;
                    break;
                }
            }
            if (!woke)
                mutex.release();
        }
        else
            mutex.release();
        if (Thread.currentThread().isInterrupted())
            throw new InterruptedException();
    }

    // threads are waiting for a moment when the cube will be able to rotate
    // via given plane
    public void rotate(int side, int layer) throws InterruptedException {
        int currentPlane = -1;
        switch (side) {
            case 0, 5 -> currentPlane = 0;
            case 1, 3 -> currentPlane = 1;
            case 2, 4 -> currentPlane = 2;
            default -> {}
        }
        int myLayer = layer;
        if (side >= 3)
            myLayer = size - layer - 1;

        beforeFunction(currentPlane);

        try {
            if (!Thread.currentThread().isInterrupted())
                rotateViaPlane(side, layer, myLayer);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
        finally {
            afterFunction(currentPlane);
        }
    }

    public String show() throws InterruptedException {
        beforeFunction(SHOW_TYPE);

        String result = properShow();

        afterFunction(SHOW_TYPE);

        return result;
    }
}
