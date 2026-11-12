package net.weaver.hotswapproof;

/** Its value comes from a static initializer, which never runs twice: a change needs a restart. */
final class Settings {
    static final int LIMIT = Integer.parseInt("1");

    private Settings() {
    }

    static int limit() {
        return LIMIT;
    }
}
