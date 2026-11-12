package net.weaver.api.config;

/**
 * The value kinds supported by a {@link ConfigValue}.
 *
 * <p>The kind determines how a JSON-compatible raw value is parsed and which
 * bounds are meaningful. Scalar kinds map to {@link Boolean}, {@link Integer},
 * {@link Long}, {@link Double}, {@link String}, and enum constants. List kinds
 * map to immutable {@link java.util.List} instances whose elements are the
 * corresponding scalar Java types.
 */
public enum ConfigType
{
    /** A {@link Boolean} value. */
    BOOLEAN,

    /** An {@link Integer} value with optional inclusive numeric bounds. */
    INT,

    /** A {@link Long} value with optional inclusive numeric bounds. */
    LONG,

    /** A finite {@link Double} value with optional inclusive numeric bounds. */
    DOUBLE,

    /** A {@link String} value with optional inclusive length bounds. */
    STRING,

    /** An enum constant with a configurable enum type. */
    ENUM,

    /** An immutable list of {@link String} values. */
    STRING_LIST,

    /** An immutable list of {@link Integer} values. */
    INT_LIST,

    /** An immutable list of {@link Long} values. */
    LONG_LIST,

    /** An immutable list of finite {@link Double} values. */
    DOUBLE_LIST,

    /** An immutable list of enum constants. */
    ENUM_LIST
}
