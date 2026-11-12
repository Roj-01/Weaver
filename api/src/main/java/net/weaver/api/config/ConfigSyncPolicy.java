package net.weaver.api.config;

/**
 * The client-server synchronization policy of one configuration.
 *
 * <p>The policy is declared with {@link ConfigBuilder#sync(ConfigSyncPolicy)}.
 * It defaults to {@link #NONE}, so a configuration is process-local unless the
 * mod opts in. A client and a server may declare different policies for the
 * same mod id; only a manager that opts in on both sides participates in a
 * transfer.</p>
 */
public enum ConfigSyncPolicy
{
    /**
     * The configuration stays local to the process that loaded it.
     */
    NONE,

    /**
     * The server is authoritative and may send its loaded document to a
     * connecting client.
     *
     * <p>Only a server-side manager with this policy is sent. The receiving
     * client applies the values read-only: the transfer never writes the
     * client's configuration file, and a later local
     * {@link ConfigManager#save()} still writes whatever the client currently
     * holds. When a client manager also declares this policy, the server
     * document wins for every declared key in the synced snapshot; unknown
     * keys are ignored.</p>
     */
    SERVER_TO_CLIENT
}
