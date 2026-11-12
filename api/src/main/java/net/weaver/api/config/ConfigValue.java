package net.weaver.api.config;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One configuration value declared in a {@link ConfigSpec} together with its
 * current in-memory value.
 *
 * <p>The declaration (mod id, key, description, type, bounds, default, and
 * allowed enum names) is immutable. The current value is stored in a volatile
 * field, so {@link #get()} is lock-free and safe for concurrent readers.
 * Writers validate before publishing, so a reader only observes a value that
 * satisfied the declaration at the time it was written.
 *
 * <p>Values are always non-null. List values are copied to immutable lists on
 * construction and on every {@link #set(Object)} call, and
 * {@link #defaultValue()} returns the immutable default.
 *
 * @param <T> the Java value type, for example {@link Boolean},
 *            {@link Integer}, {@link String}, an enum type, or an immutable
 *            {@link List}
 */
public final class ConfigValue<T>
{
    private static final double LONG_MIN_AS_DOUBLE = -9.223372036854776E18;
    private static final double LONG_UPPER_EXCLUSIVE = 9.223372036854776E18;
    private static final int MAX_DESCRIBED_LENGTH = 80;

    private final String modId;
    private final String key;
    private final String description;
    private final ConfigType type;
    private final String typeName;
    private final T defaultValue;
    private final Number min;
    private final Number max;
    private final Integer minSize;
    private final Integer maxSize;
    private final Class<?> enumType;
    private final Enum<?>[] enumConstants;
    private final List<String> allowedValues;
    private volatile T value;

    ConfigValue(String modId, String key, String description, ConfigType type, T defaultValue,
                Number min, Number max, Integer minSize, Integer maxSize, Class<?> enumType)
    {
        this.modId = Objects.requireNonNull(modId, "modId");
        this.key = Objects.requireNonNull(key, "key");
        this.description = Objects.requireNonNull(description, "description");
        this.type = Objects.requireNonNull(type, "type");
        this.min = min;
        this.max = max;
        this.minSize = minSize;
        this.maxSize = maxSize;
        this.enumType = enumType;
        this.enumConstants = enumConstants(enumType);
        this.allowedValues = this.enumConstants == null
                ? List.of()
                : Arrays.stream(this.enumConstants).map(Enum::name).toList();
        this.typeName = computeTypeName();
        validateValue(defaultValue);
        this.defaultValue = immutableCopy(defaultValue);
        this.value = this.defaultValue;
    }

    /**
     * The id of the mod that owns this value.
     *
     * @return the mod id, never {@code null}
     */
    public String modId()
    {
        return modId;
    }

    /**
     * The dotted key used in the configuration file.
     *
     * @return the key, never {@code null}
     */
    public String key()
    {
        return key;
    }

    /**
     * The human-readable description of this value.
     *
     * @return the description, never {@code null}, possibly empty
     */
    public String description()
    {
        return description;
    }

    /**
     * The declared value kind.
     *
     * @return the type, never {@code null}
     */
    public ConfigType type()
    {
        return type;
    }

    /**
     * A human-readable description of the accepted values, including bounds
     * when present.
     *
     * <p>Examples: {@code "boolean"}, {@code "integer in [1, 32]"},
     * {@code "enum net.example.Mode"}, and
     * {@code "list of string with size in [0, 8]"}.
     *
     * @return the human-readable type name, never {@code null}
     */
    public String typeName()
    {
        return typeName;
    }

    /**
     * The immutable default value declared when this value was created.
     *
     * @return the default value, never {@code null}
     */
    public T defaultValue()
    {
        return defaultValue;
    }

    /**
     * Returns the current value.
     *
     * <p>This is a plain volatile read: it never blocks and never validates.
     * Every published value has already been validated.
     *
     * @return the current value, never {@code null}
     */
    public T get()
    {
        return value;
    }

    /**
     * Validates and publishes a new current value.
     *
     * <p>List values are copied to an immutable list before they are stored.
     * The new value becomes visible to concurrent readers after this method
     * returns.
     *
     * @param value the new value, not {@code null}
     * @throws NullPointerException       when {@code value} is {@code null}
     * @throws ConfigValidationException when {@code value} violates the
     *                                   declared type, bounds, or size limits
     */
    public void set(T value)
    {
        Objects.requireNonNull(value, "value");
        validateValue(value);
        this.value = immutableCopy(value);
    }

    /**
     * The inclusive lower numeric bound, when one was declared.
     *
     * @return the lower bound, or {@code null} when unbounded
     */
    public Number min()
    {
        return min;
    }

    /**
     * The inclusive upper numeric bound, when one was declared.
     *
     * @return the upper bound, or {@code null} when unbounded
     */
    public Number max()
    {
        return max;
    }

    /**
     * The inclusive minimum string length or list size, when one was declared.
     *
     * @return the minimum size, or {@code null} when unbounded
     */
    public Integer minSize()
    {
        return minSize;
    }

    /**
     * The inclusive maximum string length or list size, when one was declared.
     *
     * @return the maximum size, or {@code null} when unbounded
     */
    public Integer maxSize()
    {
        return maxSize;
    }

    /**
     * The declared enum constant names in declaration order.
     *
     * <p>This is empty for every non-enum type. For {@link ConfigType#ENUM} and
     * {@link ConfigType#ENUM_LIST} it contains exactly the names accepted by
     * the parser.
     *
     * @return an immutable list of allowed names, never {@code null}
     */
    public List<String> allowedValues()
    {
        return allowedValues;
    }

    /**
     * The current value as editable text, the form {@link #setText} reads: numbers
     * and booleans as written in JSON, enums by constant name, and list elements
     * separated by commas.
     *
     * @return the current value as text, never {@code null}
     */
    public String text()
    {
        return textOf(value);
    }

    /**
     * The default value as editable text, in the same form as {@link #text()}.
     *
     * @return the default value as text, never {@code null}
     */
    public String defaultText()
    {
        return textOf(defaultValue);
    }

    /**
     * Parses edited text and validates it without publishing it.
     *
     * @param text the text, in the form {@link #text()} returns
     * @return the parsed value
     * @throws ConfigValidationException when the text is not a valid value
     */
    public T parseText(String text)
    {
        T parsed = parseRaw(rawFromText(Objects.requireNonNull(text, "text")));
        validateValue(parsed);
        return parsed;
    }

    /**
     * Parses, validates, and publishes edited text, as a configuration screen does.
     *
     * @param text the text, in the form {@link #text()} returns
     * @throws ConfigValidationException when the text is not a valid value
     */
    public void setText(String text)
    {
        this.value = immutableCopy(parseText(text));
    }

    private String textOf(Object current)
    {
        if (current instanceof Enum<?> constant)
        {
            return constant.name();
        }
        if (current instanceof List<?> list)
        {
            StringBuilder out = new StringBuilder();
            for (Object element : list)
            {
                if (out.length() > 0)
                {
                    out.append(", ");
                }
                out.append(element instanceof Enum<?> constant ? constant.name() : String.valueOf(element));
            }
            return out.toString();
        }
        return String.valueOf(current);
    }

    private Object rawFromText(String text)
    {
        return switch (type)
        {
            case BOOLEAN -> "true".equalsIgnoreCase(text.trim()) ? Boolean.TRUE
                    : "false".equalsIgnoreCase(text.trim()) ? Boolean.FALSE : text;
            case INT, LONG -> integralFromText(text);
            case DOUBLE -> decimalFromText(text);
            case STRING -> text;
            case ENUM -> text.trim();
            case STRING_LIST, ENUM_LIST, INT_LIST, LONG_LIST, DOUBLE_LIST -> listFromText(text);
        };
    }

    private List<Object> listFromText(String text)
    {
        List<Object> out = new ArrayList<>();
        if (text.isBlank())
        {
            return out;
        }
        for (String part : text.split(",", -1))
        {
            out.add(switch (type)
            {
                case INT_LIST, LONG_LIST -> integralFromText(part);
                case DOUBLE_LIST -> decimalFromText(part);
                case ENUM_LIST -> part.trim();
                default -> part.trim();
            });
        }
        return out;
    }

    private static Object integralFromText(String text)
    {
        try
        {
            return Long.parseLong(text.trim());
        }
        catch (NumberFormatException notInteger)
        {
            return text;
        }
    }

    private static Object decimalFromText(String text)
    {
        try
        {
            double parsed = Double.parseDouble(text.trim());
            return Double.isFinite(parsed) ? (Object) parsed : text;
        }
        catch (NumberFormatException notNumber)
        {
            return text;
        }
    }

    void applyRaw(Object raw)
    {
        T parsed = parseRaw(raw);
        validateValue(parsed);
        this.value = immutableCopy(parsed);
    }

    void resetToDefault()
    {
        this.value = defaultValue;
    }

    Object encode()
    {
        return switch (type)
        {
            case ENUM -> ((Enum<?>) value).name();
            case STRING_LIST, INT_LIST, LONG_LIST, DOUBLE_LIST -> new ArrayList<>((List<?>) value);
            case ENUM_LIST ->
            {
                List<Object> names = new ArrayList<>();
                for (Object element : (List<?>) value)
                {
                    names.add(((Enum<?>) element).name());
                }
                yield names;
            }
            default -> value;
        };
    }

    void ensureValid()
    {
        validateValue(value);
    }

    @SuppressWarnings("unchecked")
    private T parseRaw(Object raw)
    {
        return (T) switch (type)
        {
            case BOOLEAN -> rawBoolean(raw);
            case INT -> rawInteger(raw);
            case LONG -> rawLong(raw);
            case DOUBLE -> rawDouble(raw);
            case STRING -> rawString(raw);
            case ENUM -> rawEnum(raw);
            case STRING_LIST -> rawStringList(raw);
            case INT_LIST -> rawIntegerList(raw);
            case LONG_LIST -> rawLongList(raw);
            case DOUBLE_LIST -> rawDoubleList(raw);
            case ENUM_LIST -> rawEnumList(raw);
        };
    }

    private Boolean rawBoolean(Object raw)
    {
        if (!(raw instanceof Boolean value))
        {
            throw invalid(expectedButGot(raw));
        }
        return value;
    }

    private Integer rawInteger(Object raw)
    {
        long number = rawIntegralLong(raw, "integer", "");
        if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
        {
            throw invalid(expectedButGot(raw));
        }
        return (int) number;
    }

    private Long rawLong(Object raw)
    {
        return rawIntegralLong(raw, "long", "");
    }

    private Double rawDouble(Object raw)
    {
        if (!(raw instanceof Number number))
        {
            throw invalid(expectedButGot(raw));
        }
        return number.doubleValue();
    }

    private String rawString(Object raw)
    {
        if (!(raw instanceof String text))
        {
            throw invalid(expectedButGot(raw));
        }
        return text;
    }

    private Enum<?> rawEnum(Object raw)
    {
        if (!(raw instanceof String name))
        {
            throw invalid("expected enum " + enumType.getName() + " name but got " + describe(raw));
        }
        for (Enum<?> constant : enumConstants)
        {
            if (constant.name().equals(name))
            {
                return constant;
            }
        }
        throw invalid("expected enum " + enumType.getName() + " name but got " + describe(name)
                + "; allowed values: " + allowedValues);
    }

    private List<String> rawStringList(Object raw)
    {
        List<?> list = rawList(raw);
        List<String> parsed = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++)
        {
            Object element = list.get(index);
            if (!(element instanceof String text))
            {
                throw invalid(elementError(index, "string", element));
            }
            parsed.add(text);
        }
        return parsed;
    }

    private List<Integer> rawIntegerList(Object raw)
    {
        List<?> list = rawList(raw);
        List<Integer> parsed = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++)
        {
            Object element = list.get(index);
            String prefix = "element " + index + ": ";
            long number = rawIntegralLong(element, "integer", prefix);
            if (number < Integer.MIN_VALUE || number > Integer.MAX_VALUE)
            {
                throw invalid(prefix + "expected integer but got " + describe(element));
            }
            parsed.add((int) number);
        }
        return parsed;
    }

    private List<Long> rawLongList(Object raw)
    {
        List<?> list = rawList(raw);
        List<Long> parsed = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++)
        {
            Object element = list.get(index);
            parsed.add(rawIntegralLong(element, "long", "element " + index + ": "));
        }
        return parsed;
    }

    private List<Double> rawDoubleList(Object raw)
    {
        List<?> list = rawList(raw);
        List<Double> parsed = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++)
        {
            Object element = list.get(index);
            if (!(element instanceof Number number))
            {
                throw invalid(elementError(index, "decimal", element));
            }
            parsed.add(number.doubleValue());
        }
        return parsed;
    }

    private List<Enum<?>> rawEnumList(Object raw)
    {
        List<?> list = rawList(raw);
        List<Enum<?>> parsed = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++)
        {
            Object element = list.get(index);
            if (!(element instanceof String name))
            {
                throw invalid("element " + index + ": expected enum " + enumType.getName()
                        + " name but got " + describe(element));
            }
            Enum<?> match = null;
            for (Enum<?> constant : enumConstants)
            {
                if (constant.name().equals(name))
                {
                    match = constant;
                    break;
                }
            }
            if (match == null)
            {
                throw invalid("element " + index + ": expected enum " + enumType.getName()
                        + " name but got " + describe(name) + "; allowed values: " + allowedValues);
            }
            parsed.add(match);
        }
        return parsed;
    }

    private List<?> rawList(Object raw)
    {
        if (!(raw instanceof List<?> list))
        {
            throw invalid(expectedButGot(raw));
        }
        return list;
    }

    private long rawIntegralLong(Object raw, String expected, String prefix)
    {
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer
                || raw instanceof Long)
        {
            return ((Number) raw).longValue();
        }
        if (raw instanceof Double number)
        {
            double candidate = number;
            if (!Double.isFinite(candidate) || candidate != Math.rint(candidate)
                    || candidate < LONG_MIN_AS_DOUBLE || candidate >= LONG_UPPER_EXCLUSIVE)
            {
                throw invalid(prefix + "expected " + expected + " but got " + describe(raw));
            }
            return (long) candidate;
        }
        if (raw instanceof BigDecimal decimal)
        {
            try
            {
                return decimal.longValueExact();
            }
            catch (ArithmeticException failure)
            {
                throw invalid(prefix + "expected " + expected + " but got " + describe(raw));
            }
        }
        if (raw instanceof BigInteger integer)
        {
            try
            {
                return integer.longValueExact();
            }
            catch (ArithmeticException failure)
            {
                throw invalid(prefix + "expected " + expected + " but got " + describe(raw));
            }
        }
        if (raw instanceof Number number)
        {
            double candidate = number.doubleValue();
            if (!Double.isFinite(candidate) || candidate != Math.rint(candidate)
                    || candidate < LONG_MIN_AS_DOUBLE || candidate >= LONG_UPPER_EXCLUSIVE)
            {
                throw invalid(prefix + "expected " + expected + " but got " + describe(raw));
            }
            return (long) candidate;
        }
        throw invalid(prefix + "expected " + expected + " but got " + describe(raw));
    }

    private void validateValue(T candidate)
    {
        if (candidate == null)
        {
            throw invalid("expected " + typeName + " but got null");
        }
        switch (type)
        {
            case BOOLEAN ->
            {
                if (!(candidate instanceof Boolean))
                {
                    throw invalid(expectedButGot(candidate));
                }
            }
            case INT ->
            {
                if (!(candidate instanceof Integer number))
                {
                    throw invalid(expectedButGot(candidate));
                }
                long lower = min == null ? Integer.MIN_VALUE : min.longValue();
                long upper = max == null ? Integer.MAX_VALUE : max.longValue();
                if (number < lower || number > upper)
                {
                    throw invalid(expectedButGot(number));
                }
            }
            case LONG ->
            {
                if (!(candidate instanceof Long number))
                {
                    throw invalid(expectedButGot(candidate));
                }
                if ((min != null && number < min.longValue())
                        || (max != null && number > max.longValue()))
                {
                    throw invalid(expectedButGot(number));
                }
            }
            case DOUBLE ->
            {
                if (!(candidate instanceof Double number))
                {
                    throw invalid(expectedButGot(candidate));
                }
                if (!Double.isFinite(number)
                        || (min != null && number < min.doubleValue())
                        || (max != null && number > max.doubleValue()))
                {
                    throw invalid(expectedButGot(number));
                }
            }
            case STRING ->
            {
                if (!(candidate instanceof String text))
                {
                    throw invalid(expectedButGot(candidate));
                }
                validateSize(text.length(), "string length");
            }
            case ENUM -> validateEnum(candidate, -1);
            case STRING_LIST, INT_LIST, LONG_LIST, DOUBLE_LIST, ENUM_LIST ->
                    validateList(candidate);
        }
    }

    private void validateSize(int size, String label)
    {
        if (minSize != null && (size < minSize || size > maxSize))
        {
            throw invalid("expected " + typeName + " but got " + label + " " + size);
        }
    }

    private void validateEnum(Object candidate, int index)
    {
        if (!enumType.isInstance(candidate))
        {
            String prefix = index >= 0 ? "element " + index + ": " : "";
            throw invalid(prefix + "expected enum " + enumType.getName()
                    + " but got " + describe(candidate));
        }
    }

    private void validateList(Object candidate)
    {
        if (!(candidate instanceof List<?> list))
        {
            throw invalid(expectedButGot(candidate));
        }
        validateSize(list.size(), "list size");
        for (int index = 0; index < list.size(); index++)
        {
            Object element = list.get(index);
            if (element == null)
            {
                throw invalid("element " + index + " is null; expected " + elementTypeName());
            }
            switch (type)
            {
                case STRING_LIST ->
                {
                    if (!(element instanceof String))
                    {
                        throw invalid(elementError(index, "string", element));
                    }
                }
                case INT_LIST ->
                {
                    if (!(element instanceof Integer))
                    {
                        throw invalid(elementError(index, "integer", element));
                    }
                }
                case LONG_LIST ->
                {
                    if (!(element instanceof Long))
                    {
                        throw invalid(elementError(index, "long", element));
                    }
                }
                case DOUBLE_LIST ->
                {
                    if (!(element instanceof Double number) || !Double.isFinite(number))
                    {
                        throw invalid(elementError(index, "finite decimal", element));
                    }
                }
                case ENUM_LIST -> validateEnum(element, index);
                default -> throw invalid("unexpected list type " + type);
            }
        }
    }

    private String computeTypeName()
    {
        return switch (type)
        {
            case BOOLEAN -> "boolean";
            case INT -> boundedName("integer");
            case LONG -> boundedName("long");
            case DOUBLE -> boundedName("decimal");
            case STRING -> minSize == null
                    ? "string"
                    : "string with length in [" + minSize + ", " + maxSize + "]";
            case ENUM -> "enum " + enumType.getName();
            case STRING_LIST -> listName("string");
            case INT_LIST -> listName("integer");
            case LONG_LIST -> listName("long");
            case DOUBLE_LIST -> listName("decimal");
            case ENUM_LIST -> listName("enum " + enumType.getName());
        };
    }

    private String boundedName(String base)
    {
        return min == null ? base : base + " in [" + min + ", " + max + "]";
    }

    private String listName(String element)
    {
        String base = "list of " + element;
        return minSize == null ? base : base + " with size in [" + minSize + ", " + maxSize + "]";
    }

    private String elementTypeName()
    {
        return switch (type)
        {
            case STRING_LIST -> "string";
            case INT_LIST -> "integer";
            case LONG_LIST -> "long";
            case DOUBLE_LIST -> "decimal";
            case ENUM_LIST -> "enum " + enumType.getName();
            default -> typeName;
        };
    }

    private String expectedButGot(Object actual)
    {
        return "expected " + typeName + " but got " + describe(actual);
    }

    private String elementError(int index, String expected, Object actual)
    {
        return "element " + index + ": expected " + expected + " but got " + describe(actual);
    }

    private ConfigValidationException invalid(String reason)
    {
        return new ConfigValidationException(modId, key, reason);
    }

    @SuppressWarnings("unchecked")
    private T immutableCopy(T source)
    {
        if (!isListType(type))
        {
            return source;
        }
        return (T) List.copyOf((List<?>) source);
    }

    @SuppressWarnings("unchecked")
    private static Enum<?>[] enumConstants(Class<?> enumType)
    {
        return enumType == null ? null : (Enum<?>[]) enumType.getEnumConstants();
    }

    private static boolean isListType(ConfigType type)
    {
        return switch (type)
        {
            case STRING_LIST, INT_LIST, LONG_LIST, DOUBLE_LIST, ENUM_LIST -> true;
            default -> false;
        };
    }

    private static String describe(Object value)
    {
        if (value == null)
        {
            return "null";
        }
        String text = value instanceof String string
                ? '"' + string + '"'
                : String.valueOf(value);
        return text.length() <= MAX_DESCRIBED_LENGTH
                ? text
                : text.substring(0, MAX_DESCRIBED_LENGTH - 3) + "...";
    }
}
