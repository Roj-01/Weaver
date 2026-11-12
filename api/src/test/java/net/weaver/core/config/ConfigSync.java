package net.weaver.core.config;

import net.weaver.api.config.ConfigManager;
import net.weaver.api.config.ConfigSyncDocument;
import net.weaver.api.config.ConfigSyncResult;

import java.util.Map;

/**
 * Test-only stand-in for the core sync layer.
 *
 * <p>{@link ConfigManager#applySyncedValues(Map, int)} verifies the immediate
 * caller class name. The API module cannot depend on the real core module, so
 * this helper mirrors the production call site for API-level tests. The core
 * module has its own real {@code ConfigSync} and never loads this class.</p>
 */
public final class ConfigSync
{
    private ConfigSync()
    {
        throw new AssertionError("No ConfigSync instances");
    }

    public static ConfigSyncResult apply(ConfigManager manager, ConfigSyncDocument document)
    {
        return manager.applySyncedValues(document.values(), document.schemaVersion());
    }
}
