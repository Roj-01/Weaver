package net.example.game;

/** Stand-in for a game class that a mod will extend and a mixin will target. */
public class GameTarget {

    public void tick() {
        System.out.println("GAME tick original");
    }
}
