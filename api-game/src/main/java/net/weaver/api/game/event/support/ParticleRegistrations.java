package net.weaver.api.game.event.support;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleResources;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;

/**
 * Calls the two private {@code ParticleResources#register} overloads. The
 * sprite variant takes a package-private interface, so the factory is
 * wrapped in a proxy of that interface. Client only.
 */
public final class ParticleRegistrations {
    private static final String SPRITE_REGISTRATION =
            "net.minecraft.client.particle.ParticleResources$SpriteParticleRegistration";

    private ParticleRegistrations() {
    }

    public static <T extends ParticleOptions> void register(ParticleResources resources, ParticleType<T> type,
                                                            ParticleProvider<T> provider) {
        invoke(resources, ParticleProvider.class, type, provider);
    }

    public static <T extends ParticleOptions> void registerSprites(ParticleResources resources, ParticleType<T> type,
                                                                   Function<SpriteSet, ParticleProvider<T>> factory) {
        try {
            Class<?> registration = Class.forName(SPRITE_REGISTRATION, false, ParticleResources.class.getClassLoader());
            Object proxy = Proxy.newProxyInstance(registration.getClassLoader(), new Class<?>[] {registration},
                    (self, method, args) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "hashCode" -> System.identityHashCode(self);
                                case "equals" -> self == args[0];
                                default -> "WeaverSpriteParticleRegistration";
                            };
                        }
                        return factory.apply((SpriteSet) args[0]);
                    });
            invoke(resources, registration, type, proxy);
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("particle sprite registration is not available", missing);
        }
    }

    private static void invoke(ParticleResources resources, Class<?> secondParameter, ParticleType<?> type, Object value) {
        try {
            Method register = ParticleResources.class.getDeclaredMethod("register", ParticleType.class, secondParameter);
            register.setAccessible(true);
            register.invoke(resources, type, value);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("particle registration failed for " + type, failure);
        }
    }
}
