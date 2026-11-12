package net.weaver.api.config;

/**
 * Upgrades one schema version of a configuration document to the next.
 *
 * <p>A migration is written for exactly one source version: it transforms a
 * document whose {@link ConfigData#schemaVersion()} is
 * {@link #fromVersion()} into the equivalent document at
 * {@code fromVersion() + 1}. The manager applies migrations in ascending order
 * and advances the resulting version automatically after each step, so a
 * migration should only rename, add, or rewrite values; it must not try to
 * change the version itself.
 *
 * <p>A missing migration step or a migration that throws is treated as a load
 * problem: the manager applies defaults, reports a file-level problem, leaves
 * the file untouched, and does not write the partially migrated document.
 */
public interface ConfigMigration
{
    /**
     * The schema version this migration upgrades from, at least {@code 1}.
     *
     * @return the source schema version
     */
    int fromVersion();

    /**
     * Mutates {@code data} in place so that it represents the next schema
     * version.
     *
     * <p>The manager sets the resulting version, so implementations only need
     * to transform keys and values. Throwing from this method is caught by the
     * manager and reported as a load problem; the file is not modified.
     *
     * @param data the mutable document at {@link #fromVersion()}, not
     *             {@code null}
     */
    void migrate(ConfigData data);
}
