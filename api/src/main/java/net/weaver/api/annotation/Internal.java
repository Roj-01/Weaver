package net.weaver.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public type or member that Weaver itself calls (the loader, the
 * built-in mixins) and that is not part of the frozen API. Mods must not use it:
 * it may change or go away in any release, without a deprecation period.
 *
 * <p>Besides members marked with this annotation, the packages named
 * {@code ...mixin...} and {@code net.weaver.api.game.event.support} are not API
 * either. See {@code docs/api-stability.md}.</p>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.FIELD})
public @interface Internal {
}
