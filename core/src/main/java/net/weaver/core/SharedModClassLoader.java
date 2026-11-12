package net.weaver.core;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * One classloader for the game and every mod.
 *
 * <p>Used only when the game is launched through this loader (our launcher or a
 * launcher that asks for it). The normal path appends mod jars to the system
 * classloader instead.
 *
 * <p>Delegation is parent-first, except for the game-typed API package
 * {@value #API_GAME_PACKAGE} and the NeoForge-shaped library package
 * {@value #NF_PACKAGE}: those classes reference Minecraft types, which
 * only this loader can see, so they are defined here (child-first) on first
 * use instead of by the system parent. They are defined lazily, so no
 * Minecraft supertype is loaded before the class-file transformers exist, and
 * a supertype in the same package is resolved through this method as well, so
 * subtypes and supertypes end up in the same loader.
 */
public final class SharedModClassLoader extends URLClassLoader {

    /** The package prefix that is resolved child-first. */
    static final String API_GAME_PACKAGE = "net.weaver.api.game.";

    /** The package prefix of the {@code weaver-nf} library, also resolved child-first. */
    static final String NF_PACKAGE = "net.weaver.nf.";

    public SharedModClassLoader(URL[] urls, ClassLoader parent) {
        super("weaver-shared", urls, parent);
    }

    /** Adds a jar to the search path (the developer hot reload's overlay of new classes). */
    public void addJar(URL jar) {
        addURL(jar);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.startsWith(API_GAME_PACKAGE) && !name.startsWith(NF_PACKAGE)) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                try {
                    type = findClass(name);
                } catch (ClassNotFoundException notHere) {
                    return super.loadClass(name, resolve);
                }
            }
            if (resolve) {
                resolveClass(type);
            }
            return type;
        }
    }
}
