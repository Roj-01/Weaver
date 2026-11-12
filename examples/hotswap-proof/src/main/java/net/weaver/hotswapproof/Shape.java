package net.weaver.hotswapproof;

/** Changes shape (gains a method), which a standard JVM cannot swap. */
final class Shape {
    private Shape() {
    }

    static int value() {
        return 1;
    }
}
