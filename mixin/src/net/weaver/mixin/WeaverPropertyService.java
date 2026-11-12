package net.weaver.mixin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

/**
 * Global property service for the Weaver environment.
 *
 * The stock service file inside mixin-0.8.7.jar lists only the LaunchWrapper and
 * ModLauncher Blackboards, which cannot load outside their platforms, so a plain
 * JVM needs its own implementation.
 */
public class WeaverPropertyService implements IGlobalPropertyService {

    private final Map<String, Object> properties = new ConcurrentHashMap<String, Object>();

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key) {
        return (T) this.properties.get(keyName(key));
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        if (value == null) {
            this.properties.remove(keyName(key));
        } else {
            this.properties.put(keyName(key), value);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        Object value = this.properties.get(keyName(key));
        return value == null ? defaultValue : (T) value;
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        Object value = this.properties.get(keyName(key));
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static String keyName(IPropertyKey key) {
        return key instanceof Key ? ((Key) key).name : String.valueOf(key);
    }

    static final class Key implements IPropertyKey {
        private final String name;

        Key(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return this.name;
        }
    }
}
