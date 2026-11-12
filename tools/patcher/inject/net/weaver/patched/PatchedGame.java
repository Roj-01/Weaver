package net.weaver.patched;

/**
 * Marker contract implemented by game classes modified by the Weaver patcher.
 */
public interface PatchedGame {
    int weaver$patchLevel();
}
