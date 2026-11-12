package net.example.mod;

import net.example.game.GameTarget;

/** Mod class that extends a game class with the same Class object. */
public final class ModBlock extends GameTarget {

    public String name() {
        return "mod-block";
    }
}
