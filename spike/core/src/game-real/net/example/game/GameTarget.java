package net.example.game;

/** Real game class: the field is private until the access transformer widens it. */
public class GameTarget {

    private int secret = 7;

    public void tick() {
        System.out.println("GAME tick original");
    }
}
