package net.weaver.core.command;

import net.weaver.api.command.CommandArgumentType;
import net.weaver.api.command.CommandRegistrationException;

/**
 * Maps API argument descriptions onto live Brigadier argument types.
 *
 * <p>Built-in kinds use the no-argument factory methods on the Brigadier
 * argument classes. A custom kind must already be an instance of Brigadier's
 * {@code ArgumentType}; anything else is rejected with a
 * {@link CommandRegistrationException} so one bad spec cannot corrupt the
 * dispatcher.
 */
final class ArgumentTypeMapper
{
    private ArgumentTypeMapper()
    {
    }

    /**
     * Resolves a Brigadier argument type for an API argument description.
     *
     * @param access the Brigadier reflective view
     * @param type   the API argument type, not {@code null}
     * @return the Brigadier argument type, never {@code null}
     * @throws CommandRegistrationException when the type cannot be mapped
     */
    static Object map(BrigadierReflection.Access access, CommandArgumentType type)
    {
        if (type == null)
        {
            throw new CommandRegistrationException("Command argument type is required");
        }
        try
        {
            return switch (type.kind())
            {
                case STRING -> access.stringType();
                case WORD -> access.wordType();
                case GREEDY_STRING -> access.greedyStringType();
                case INTEGER -> access.integerType();
                case LONG -> access.longType();
                case DOUBLE -> access.doubleType();
                case FLOAT -> access.floatType();
                case BOOLEAN -> access.booleanType();
                case CUSTOM -> custom(access, type);
            };
        }
        catch (CommandRegistrationException failure)
        {
            throw failure;
        }
        catch (Throwable failure)
        {
            throw new CommandRegistrationException(
                    "Failed to create Brigadier argument type " + type, failure);
        }
    }

    private static Object custom(BrigadierReflection.Access access, CommandArgumentType type)
    {
        Object customType = type.customType();
        if (customType == null || !access.argumentTypeClass().isInstance(customType))
        {
            throw new CommandRegistrationException("Custom argument type is not a Brigadier "
                    + "ArgumentType: " + (customType == null ? "null"
                    : customType.getClass().getName()));
        }
        return customType;
    }
}
