package net.example.mod;

import net.example.game.GameTarget;

/** Compiled against a public field; the runtime class is widened by the AT. */
public final class ProofBlock extends GameTarget {

    public int readSecret() {
        return secret;
    }
}
