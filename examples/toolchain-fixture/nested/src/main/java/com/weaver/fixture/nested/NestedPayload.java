package com.weaver.fixture.nested;

/** Payload loaded reflectively by the main fixture mod. */
public final class NestedPayload {

    private NestedPayload() {
    }

    public static String value() {
        return "nested-1.0.0";
    }
}
