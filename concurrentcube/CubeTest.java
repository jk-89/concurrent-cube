package concurrentcube;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class CubeTest {
    static final int SIDES = 6;
    static final Random rng = new Random();

    // random int from range [a, b]
    private int random(int a, int b) {
        return rng.nextInt(b - a + 1) + a;
    }

    // random int from range [0, n - 1]
    private int random(int n) {
        return random(0, n - 1);
    }

    private int oppositeSide(int side) {
        return switch (side) {
            case 0 -> 5;
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 1;
            case 4 -> 2;
            default -> 0;
        };
    }

    // either doesn't change side or change it to opposite one
    private int randomSwapSides(int side) {
        return random(2) == 0 ? side : oppositeSide(side);
    }

    // checks if cube state is same as at the beginning
    private boolean checkIfBeginState(Cube cube) throws InterruptedException {
        int size = cube.getSize();
        String state = cube.show();
        int pnt = 0;
        for (int i = 0; i < 6; i++) {
            for (int j = 0; j < size * size; j++) {
                if (Character.getNumericValue(state.charAt(pnt)) != i)
                    return false;
                pnt++;
            }
        }
        return true;
    }

    // checks if each colour have same amount of occurs
    private boolean properNumberOfColours(Cube cube) throws InterruptedException {
        int size = cube.getSize();
        String state = cube.show();
        int[] cnt = new int[6];
        for (int i = 0; i < state.length(); i++) {
            cnt[Character.getNumericValue(state.charAt(i))]++;
        }
        for (int i = 0; i < 6; i++) {
            if (cnt[i] != size * size) {
                return false;
            }
        }
        return true;
    }

    @ParameterizedTest
    @DisplayName("Creates many threads rotating via one plane, cube should return to the " +
            "beginning state (each thread rotates random layer via random side.")
    @CsvSource({"10,4,0,4", "20,5,1,12", "20,20,2,200", "50,20,1,100", "100,20,2,100"})
    void rotatingViaOnePlane(int size, int threadsNumber, int plane, int rotations) {
        assertEquals(0, rotations % 4);
        var counter = new Object() {
            final AtomicInteger rotates = new AtomicInteger(0);
            final AtomicInteger shows = new AtomicInteger(0);
        };
        Cube cube = new Cube(size,
                (x, y) -> {counter.rotates.incrementAndGet(); },
                (x, y) -> { counter.rotates.incrementAndGet(); },
                counter.shows::incrementAndGet,
                counter.shows::incrementAndGet
        );

        class Worker implements Runnable {

            @Override
            public void run() {
                int side = randomSwapSides(plane);
                int layer = random(size);
                for (int i = 0; i < rotations; i++) {
                    try {
                        cube.rotate(side, layer);
                    } catch (InterruptedException e) {
                        System.out.println("interrupted.");
                        e.printStackTrace();
                    }
                }
                try {
                    cube.show();
                } catch (InterruptedException e) {
                    System.out.println("interrupted.");
                    e.printStackTrace();
                }
            }
        }

        Thread[] threads = new Thread[threadsNumber];
        for (int i = 0; i < threadsNumber; i++) {
            threads[i] = new Thread(new Worker());
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.out.println("Failed to join.");
            }
        }

        assertEquals(threadsNumber * rotations * 2, counter.rotates.intValue());
        assertEquals(threadsNumber * 2, counter.shows.intValue());
        try {
            assertTrue(properNumberOfColours(cube));
            assertTrue(checkIfBeginState(cube));
        } catch (InterruptedException ignored) {

        }
    }

    @Test
    @DisplayName("Rotate one layer via one plane - rotates must happen sequentially.")
    void rotatingSameLayer() {
        final int size = 10, threadsNumber = 20, rotations = 2000, side = 0;
        var checker = new Object() {
            final AtomicInteger inRotate = new AtomicInteger(0);
            final AtomicInteger inShow = new AtomicInteger(0);
            final AtomicInteger error = new AtomicInteger(0);
        };

        Cube cube = new Cube(size,
                (x, y) -> {
                    int howMany = checker.inRotate.incrementAndGet();
                    if (howMany != 1 || checker.inShow.intValue() != 0)
                        checker.error.incrementAndGet();
                },
                (x, y) -> {
                    int howMany = checker.inRotate.decrementAndGet();
                    if (howMany != 0 || checker.inShow.intValue() != 0)
                        checker.error.incrementAndGet();
                },
                () -> {
                    checker.inShow.incrementAndGet();
                    if (checker.inRotate.intValue() != 0)
                        checker.error.incrementAndGet();
                },
                () -> {
                    checker.inShow.decrementAndGet();
                    if (checker.inRotate.intValue() != 0)
                        checker.error.incrementAndGet();
                }
        );

        class Worker implements Runnable {

            @Override
            public void run() {
                for (int i = 0; i < rotations; i++) {
                    try {
                        cube.rotate(side, 0);
                        if (i % 10 == 0)
                            cube.show();
                    } catch (InterruptedException e) {
                        System.out.println("interrupted.");
                        e.printStackTrace();
                    }
                }
            }
        }

        Thread[] threads = new Thread[threadsNumber];
        for (int i = 0; i < threadsNumber; i++) {
            threads[i] = new Thread(new Worker());
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.out.println("Failed to join.");
            }
        }

        assertEquals(0, checker.error.intValue());
        try {
            assertTrue(properNumberOfColours(cube));
            assertTrue(checkIfBeginState(cube));
        } catch (InterruptedException ignored) {

        }
    }

    @ParameterizedTest
    @DisplayName("Performing random moves, checks if two moves happen concurrently " +
            "only if it is allowed.")
    @CsvSource({"3,20,10000", "10,20,1000", "20,20,2000", "1000,5,10000", "10,10,1000"})
    void randomMoves(int size, int threadsNumber, int maxRotations) {
        var checker = new Object() {
            final AtomicInteger[] layers = new AtomicInteger[size];
            final AtomicInteger[] planes = new AtomicInteger[SIDES / 2];
            final AtomicInteger inRotate = new AtomicInteger(0);
            final AtomicInteger inShow = new AtomicInteger(0);
            final AtomicInteger planeError = new AtomicInteger(0);
            final AtomicInteger layerError = new AtomicInteger(0);
            final AtomicInteger rotateWhileShowError = new AtomicInteger(0);
            final AtomicInteger showError = new AtomicInteger(0);
        };

        for (int i = 0; i < size; i++) {
            checker.layers[i] = new AtomicInteger(0);
        }
        for (int i = 0; i < SIDES / 2; i++) {
            checker.planes[i] = new AtomicInteger(0);
        }

        Cube cube = new Cube(size,
                (side, layer) -> {
                    if (side > 2) {
                        side = oppositeSide(side);
                        layer = size - layer - 1;
                    }
                    checker.inRotate.incrementAndGet();
                    checker.planes[side].incrementAndGet();
                    checker.layers[layer].incrementAndGet();
                    for (int i = 0; i < SIDES / 2; i++) {
                        if (i != side && checker.planes[i].intValue() != 0)
                            checker.planeError.incrementAndGet();
                    }
                    if (checker.layers[layer].intValue() != 1)
                        checker.layerError.incrementAndGet();
                    if (checker.inShow.intValue() != 0)
                        checker.rotateWhileShowError.incrementAndGet();
                },
                (side, layer) -> {
                    if (side > 2) {
                        side = oppositeSide(side);
                        layer = size - layer - 1;
                    }
                    checker.inRotate.decrementAndGet();
                    checker.planes[side].decrementAndGet();
                    checker.layers[layer].decrementAndGet();
                    for (int i = 0; i < SIDES / 2; i++) {
                        if (i != side && checker.planes[i].intValue() != 0)
                            checker.planeError.incrementAndGet();
                    }
                    if (checker.inShow.intValue() != 0)
                        checker.rotateWhileShowError.incrementAndGet();
                },
                () -> {
                    checker.inShow.incrementAndGet();
                    if (checker.inRotate.intValue() != 0)
                        checker.showError.incrementAndGet();
                },
                () -> {
                    checker.inShow.decrementAndGet();
                    if (checker.inRotate.intValue() != 0)
                        checker.showError.incrementAndGet();
                }
        );

        class Worker implements Runnable {

            @Override
            public void run() {
                for (int i = 0; i < random(maxRotations); i++) {
                    try {
                        cube.rotate(random(6), random(size));
                        if (i % 100 == 0)
                           cube.show();
                    } catch (InterruptedException e) {
                        System.out.println("interrupted.");
                        e.printStackTrace();
                    }
                }
            }
        }

        Thread[] threads = new Thread[threadsNumber];
        for (int i = 0; i < threadsNumber; i++) {
            threads[i] = new Thread(new Worker());
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.out.println("Failed to join.");
            }
        }

        assertEquals(0, checker.planeError.intValue());
        assertEquals(0, checker.layerError.intValue());
        assertEquals(0, checker.rotateWhileShowError.intValue());
        assertEquals(0, checker.showError.intValue());
        try {
            assertTrue(properNumberOfColours(cube));
        }
        catch (InterruptedException ignored) {

        }
    }

    @Test
    @DisplayName("Small rotate correctness test.")
    void rotateCorectnessTest() {
        final int size = 4;
        Cube cube = new Cube(size,
                (x, y) -> {},
                (x, y) -> {},
                () -> {},
                () -> {}
        );

        try {
            cube.rotate(1, 3);
            cube.rotate(0, 0);
            cube.rotate(4, 1);
            cube.rotate(3, 2);
            cube.rotate(5, 0);
            cube.rotate(2, 3);
            String state = cube.show();
            String expected =
                    "5112" + "4233" + "0200" + "4244" +
                    "2020" + "1011" + "2011" + "2404" +
                    "3533" + "2520" + "2120" + "1011" +
                    "5420" + "3353" + "3350" + "2520" +
                    "1443" + "4035" + "1443" + "1553" +
                    "5255" + "1444" + "5155" + "0334";
            assertEquals(expected, state);
        }
        catch (InterruptedException ignored) {

        }
    }

    @RepeatedTest(100)
    @DisplayName("Checks if result can be obtained from small test.")
    void smallConcurrentCorrectnessTest() {
        final int size = 4;
        Cube cube = new Cube(size,
                (x, y) -> {},
                (x, y) -> {},
                () -> {},
                () -> {}
        );

        final int[] sides = new int[size];
        final int[] layers = new int[size];
        for (int i = 0; i < size; i++) {
            sides[i] = random(SIDES);
            layers[i] = random(size);
        }

        Thread t1 = new Thread(
            () -> {
                try {
                    cube.rotate(sides[0], layers[0]);
                    cube.rotate(sides[1], layers[1]);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        );

        Thread t2 = new Thread(
            () -> {
                try {
                    cube.rotate(sides[2], layers[2]);
                    cube.rotate(sides[3], layers[3]);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        );

        try {
            t1.start();
            t2.start();
            t1.join();
            t2.join();

            // 0123 0213 0231 2301 2013 2031
            final int PERMS = 6;
            final Cube[] cubes = new Cube[PERMS];
            for (int i = 0; i < PERMS; i++) {
                cubes[i] = new Cube(size,
                    (x, y) -> {},
                    (x, y) -> {},
                    () -> {},
                    () -> {}
                );
            }

            final String[] results = new String[PERMS];

            cubes[0].rotate(sides[0], layers[0]);
            cubes[0].rotate(sides[1], layers[1]);
            cubes[0].rotate(sides[2], layers[2]);
            cubes[0].rotate(sides[3], layers[3]);
            results[0] = cubes[0].show();

            cubes[1].rotate(sides[0], layers[0]);
            cubes[1].rotate(sides[2], layers[2]);
            cubes[1].rotate(sides[1], layers[1]);
            cubes[1].rotate(sides[3], layers[3]);
            results[1] = cubes[1].show();

            cubes[2].rotate(sides[0], layers[0]);
            cubes[2].rotate(sides[2], layers[2]);
            cubes[2].rotate(sides[3], layers[3]);
            cubes[2].rotate(sides[1], layers[1]);
            results[2] = cubes[2].show();

            cubes[3].rotate(sides[2], layers[2]);
            cubes[3].rotate(sides[3], layers[3]);
            cubes[3].rotate(sides[0], layers[0]);
            cubes[3].rotate(sides[1], layers[1]);
            results[3] = cubes[3].show();

            cubes[4].rotate(sides[2], layers[2]);
            cubes[4].rotate(sides[0], layers[0]);
            cubes[4].rotate(sides[1], layers[1]);
            cubes[4].rotate(sides[3], layers[3]);
            results[4] = cubes[4].show();

            cubes[5].rotate(sides[2], layers[2]);
            cubes[5].rotate(sides[0], layers[0]);
            cubes[5].rotate(sides[3], layers[3]);
            cubes[5].rotate(sides[1], layers[1]);
            results[5] = cubes[5].show();

            boolean okay = false;
            String result = cube.show();
            for (int i = 0; i < PERMS; i++) {
                if (result.equals(results[i])) {
                    okay = true;
                    break;
                }
            }

            assertTrue(okay);
        }
        catch (InterruptedException ignored) {

        }
    }

    @ParameterizedTest
    @DisplayName("Checks concurrence and potential deadlocks.")
    @CsvSource({"4,4,200", "20,10,1000", "100,20,100"})
    void concurrenceTest(int size, int threadsNumber, int rotations) {

        var checker = new Object() {
            final AtomicInteger inRotate = new AtomicInteger(0);
            final AtomicInteger maxCounter = new AtomicInteger(0);
            final AtomicInteger showCounter = new AtomicInteger(0);
            final AtomicInteger errors = new AtomicInteger(0);
            final CyclicBarrier barrier = new CyclicBarrier(threadsNumber);
        };

        // barrier waits for threads, after threads rotate one time (at once)
        // show() is called. Threads at once rotate via same side (so they can
        // happen concurrently).
        Cube cube = new Cube(size,
                (x, y) -> {
                    try {
                        checker.barrier.await();
                        int inRotate = checker.inRotate.incrementAndGet();
                        if (inRotate == threadsNumber)
                            checker.maxCounter.incrementAndGet();
                        checker.barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        e.printStackTrace();
                    }
                },
                (x, y) -> {
                    checker.inRotate.decrementAndGet();
                },
                () -> {
                    int value = checker.showCounter.incrementAndGet();
                    if (value != checker.maxCounter.intValue() ||
                            checker.inRotate.intValue() != 0) {
                        checker.errors.incrementAndGet();
                    }
                },
                () -> {}
        );

        class Worker implements Runnable {
            static final Semaphore mutex = new Semaphore(1);
            static int finished = 0;
            private final int myLayer;

            public Worker(int myLayer) {
                this.myLayer = myLayer;
            }

            @Override
            public void run() {
                for (int i = 0; i < rotations; i++) {
                    try {
                        cube.rotate(i % 6, myLayer);
                        mutex.acquire();
                        finished++;
                        if (finished == 1) {
                            cube.show();
                        }
                        if (finished == threadsNumber) {
                            finished = 0;
                        }
                        mutex.release();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        Thread[] threads = new Thread[threadsNumber];
        for (int i = 0; i < threadsNumber; i++) {
            threads[i] = new Thread(new Worker(i));
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.out.println("Failed to join.");
            }
        }

        assertEquals(0, checker.errors.intValue());
    }

    @Test
    @DisplayName("Checks concurrence of show().")
    void concurrenceShowTest() {
        final int size = 10, threadsNumber = 10;

        var checker = new Object() {
            final CyclicBarrier barrier = new CyclicBarrier(threadsNumber);
        };

        Cube cube = new Cube(size,
                (x, y) -> {},
                (x, y) -> {},
                () -> {
                    try {
                        checker.barrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        e.printStackTrace();
                    }
                },
                () -> {}
        );

        class Worker implements Runnable {

            @Override
            public void run() {
                try {
                    cube.show();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        Thread[] threads = new Thread[threadsNumber];
        for (int i = 0; i < threadsNumber; i++) {
            threads[i] = new Thread(new Worker());
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                System.out.println("Failed to join.");
            }
        }
    }


    @ParameterizedTest
    @DisplayName("Performing random moves, checks if two moves happen concurrently " +
            "only if it is allowed, interrupts threads sometimes, checks for deadlock.")
    @CsvSource({"3,10,10", "5,20,100", "40,20,200", "20,10,1000"})
    void randomMovesWithInterrupts(int size, int threadsNumber, int maxRotations) {
        var checker = new Object() {
            final AtomicInteger[] layers = new AtomicInteger[size];
            final AtomicInteger[] planes = new AtomicInteger[SIDES / 2];
            final AtomicInteger inRotate = new AtomicInteger(0);
            final AtomicInteger inShow = new AtomicInteger(0);
            final AtomicInteger planeError = new AtomicInteger(0);
            final AtomicInteger layerError = new AtomicInteger(0);
            final AtomicInteger rotateWhileShowError = new AtomicInteger(0);
            final AtomicInteger showError = new AtomicInteger(0);
        };

        for (int i = 0; i < size; i++) {
            checker.layers[i] = new AtomicInteger(0);
        }
        for (int i = 0; i < SIDES / 2; i++) {
            checker.planes[i] = new AtomicInteger(0);
        }

        Cube cube = new Cube(size,
                (side, layer) -> {
                    if (side > 2) {
                        side = oppositeSide(side);
                        layer = size - layer - 1;
                    }
                    checker.inRotate.incrementAndGet();
                    checker.planes[side].incrementAndGet();
                    checker.layers[layer].incrementAndGet();
                    for (int i = 0; i < SIDES / 2; i++) {
                        if (i != side && checker.planes[i].intValue() != 0)
                            checker.planeError.incrementAndGet();
                    }
                    if (checker.layers[layer].intValue() != 1)
                        checker.layerError.incrementAndGet();
                    if (checker.inShow.intValue() != 0)
                        checker.rotateWhileShowError.incrementAndGet();
                },
                (side, layer) -> {
                    if (side > 2) {
                        side = oppositeSide(side);
                        layer = size - layer - 1;
                    }
                    checker.inRotate.decrementAndGet();
                    checker.planes[side].decrementAndGet();
                    checker.layers[layer].decrementAndGet();
                    for (int i = 0; i < SIDES / 2; i++) {
                        if (i != side && checker.planes[i].intValue() != 0)
                            checker.planeError.incrementAndGet();
                    }
                    if (checker.inShow.intValue() != 0)
                        checker.rotateWhileShowError.incrementAndGet();
                },
                () -> {
                    checker.inShow.incrementAndGet();
                    if (checker.inRotate.intValue() != 0)
                        checker.showError.incrementAndGet();
                },
                () -> {
                    checker.inShow.decrementAndGet();
                    if (checker.inRotate.intValue() != 0)
                        checker.showError.incrementAndGet();
                }
        );

        class Worker implements Runnable {

            @Override
            public void run() {
                for (int i = 0; i < random(maxRotations); i++) {
                    try {
                        cube.rotate(random(6), random(size));
                        if (i % 50 == 0)
                            cube.show();
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        }

        Thread[] threads = new Thread[threadsNumber];
        for (int i = 0; i < threadsNumber; i++) {
            threads[i] = new Thread(new Worker());
        }
        for (Thread t : threads) {
            t.start();
        }
        for (Thread t : threads) {
            t.interrupt();
        }

        assertEquals(0, checker.planeError.intValue());
        assertEquals(0, checker.layerError.intValue());
        assertEquals(0, checker.rotateWhileShowError.intValue());
        assertEquals(0, checker.showError.intValue());
        try {
            assertTrue(properNumberOfColours(cube));
        }
        catch (InterruptedException ignored) {

        }
    }
}
