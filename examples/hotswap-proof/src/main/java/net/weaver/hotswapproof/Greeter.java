package net.weaver.hotswapproof;

/** Swapped: its method body changes between versions. */
final class Greeter {
    private Greeter() {
    }

    static String greet() {
        return "v1";
    }
}
