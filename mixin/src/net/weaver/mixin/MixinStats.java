package net.weaver.mixin;

/**
 * Immutable snapshot of the mixin engine counters.
 *
 * @param configs          active configs
 * @param classes          distinct target classes actually changed by a mixin
 * @param sites            mixin application events (one per mixin merged into a target)
 * @param failures         configs rejected or transforms that fell back to original bytes
 * @param bootMillis       engine boot cost
 * @param addConfigMedian  median cost of a config registration
 * @param addConfigMax     worst config registration
 * @param transformMedian  median cost of one woven target
 * @param transformMax     worst one woven target
 */
public record MixinStats(
        int configs,
        int classes,
        int sites,
        int failures,
        double bootMillis,
        double addConfigMedian,
        double addConfigMax,
        double transformMedian,
        double transformMax) {
}
