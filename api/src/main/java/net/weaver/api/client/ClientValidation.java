package net.weaver.api.client;

import java.util.regex.Pattern;

/**
 * Shared validation helpers for the client API value types.
 */
final class ClientValidation
{
    private ClientValidation()
    {
    }

    static String requireNonBlank(String value, String name)
    {
        if (value == null)
        {
            throw new IllegalArgumentException(name + " must not be null");
        }
        if (value.isBlank())
        {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    static String requirePattern(String value, String name, Pattern pattern)
    {
        requireNonBlank(value, name);
        if (!pattern.matcher(value).matches())
        {
            throw new IllegalArgumentException(name + " must match " + pattern.pattern()
                    + " but was \"" + value + "\"");
        }
        return value;
    }

    static int requireNonNegative(int value, String name)
    {
        if (value < 0)
        {
            throw new IllegalArgumentException(name + " must be >= 0 but was " + value);
        }
        return value;
    }
}
