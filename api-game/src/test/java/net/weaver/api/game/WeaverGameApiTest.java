package net.weaver.api.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeaverGameApiTest {

    @Test
    void gameApiVersionTracksTheTargetGame() {
        assertEquals("26.3", WeaverGameApi.VERSION);
    }
}
