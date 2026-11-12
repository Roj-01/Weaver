package net.weaver.api.client;

import java.util.regex.Pattern;

/**
 * An immutable description of one client key mapping.
 *
 * <p>The {@code id} is unique within the registering mod and the {@code modId}
 * is the owning mod. Both use the namespace character set
 * {@code [a-z0-9_.-]+}. When no explicit translation key is supplied, the
 * translation key defaults to {@code "key." + modId + "." + id}, and the
 * category defaults to {@code "misc"} whose full translation key is
 * {@code "key.categories.misc"}.
 *
 * <p>The optional screen factory id, when present, names a
 * {@link ScreenFactory} registered through {@link ClientRegistry}. The
 * one-argument registration helpers use it to open a screen when the key is
 * pressed.
 *
 * <p>Instances are immutable and safe to share between threads. The spec does
 * not touch game state; the backend translates it into a real key mapping on
 * the client thread.
 *
 * @param id              the unique mapping id, non-blank and matching
 *                        {@code [a-z0-9_.-]+}
 * @param modId           the owning mod id, non-blank and matching
 *                        {@code [a-z0-9_.-]+}
 * @param translationKey  the non-blank translation key for the mapping name
 * @param categoryId      the non-blank category id; the full category
 *                        translation key is {@code "key.categories." + categoryId}
 * @param keyCode         the non-negative key code
 * @param mouse           whether the code is a mouse button rather than a key
 * @param modifiers       the modifier mask applied to the mapping
 * @param order           the display order; lower values are shown first
 * @param screenFactoryId the optional screen factory id opened by the
 *                        convenience registration, or {@code null}
 */
public record KeyMappingSpec(String id, String modId, String translationKey, String categoryId,
                             int keyCode, boolean mouse, int modifiers, int order,
                             String screenFactoryId)
{
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern MAPPING_ID = Pattern.compile("[a-z0-9_.-]+");

    /**
     * Validates every component and applies the documented constraints.
     *
     * @throws IllegalArgumentException when an id or string component is
     *                                  {@code null} or blank, when an id does
     *                                  not match its character set, or when the
     *                                  key code is negative
     */
    public KeyMappingSpec
    {
        id = ClientValidation.requirePattern(id, "id", MAPPING_ID);
        modId = ClientValidation.requirePattern(modId, "modId", MOD_ID);
        translationKey = ClientValidation.requireNonBlank(translationKey, "translationKey");
        categoryId = ClientValidation.requireNonBlank(categoryId, "categoryId");
        keyCode = ClientValidation.requireNonNegative(keyCode, "keyCode");
        if (screenFactoryId != null)
        {
            screenFactoryId = ClientValidation.requireNonBlank(screenFactoryId, "screenFactoryId");
        }
    }

    /**
     * Starts building a mapping with the required components.
     *
     * <p>The translation key defaults to {@code "key." + modId + "." + id} and
     * the category defaults to {@code "misc"}.
     *
     * @param modId   the owning mod id, not {@code null} and matching
     *                {@code [a-z0-9_.-]+}
     * @param id      the unique mapping id, not {@code null} and matching
     *                {@code [a-z0-9_.-]+}
     * @param keyCode the non-negative key code
     * @return a new builder
     * @throws IllegalArgumentException when an id is {@code null}, blank, or
     *                                  invalid, or when the key code is negative
     */
    public static Builder builder(String modId, String id, int keyCode)
    {
        return new Builder(modId, id, keyCode);
    }

    /**
     * Creates a mapping with default translation key, category, and flags.
     *
     * <p>Equivalent to {@code builder(modId, id, keyCode).build()}.
     *
     * @param modId   the owning mod id, not {@code null} and matching
     *                {@code [a-z0-9_.-]+}
     * @param id      the unique mapping id, not {@code null} and matching
     *                {@code [a-z0-9_.-]+}
     * @param keyCode the non-negative key code
     * @return the immutable mapping
     * @throws IllegalArgumentException when an id is {@code null}, blank, or
     *                                  invalid, or when the key code is negative
     */
    public static KeyMappingSpec key(String modId, String id, int keyCode)
    {
        return builder(modId, id, keyCode).build();
    }

    /**
     * Builds {@link KeyMappingSpec} values.
     *
     * <p>Builders are mutable and not thread-safe; build a spec before sharing
     * it between threads.
     */
    public static final class Builder
    {
        private final String id;
        private final String modId;
        private final int keyCode;
        private String translationKey;
        private String categoryId = "misc";
        private boolean mouse;
        private int modifiers;
        private int order;
        private String screenFactoryId;

        private Builder(String modId, String id, int keyCode)
        {
            this.modId = ClientValidation.requirePattern(modId, "modId", MOD_ID);
            this.id = ClientValidation.requirePattern(id, "id", MAPPING_ID);
            this.keyCode = ClientValidation.requireNonNegative(keyCode, "keyCode");
            this.translationKey = "key." + this.modId + "." + this.id;
        }

        /**
         * Sets an explicit translation key.
         *
         * @param translationKey the non-blank translation key
         * @return this builder
         * @throws IllegalArgumentException when the key is {@code null} or blank
         */
        public Builder translationKey(String translationKey)
        {
            this.translationKey = ClientValidation.requireNonBlank(translationKey,
                    "translationKey");
            return this;
        }

        /**
         * Sets the mapping category id.
         *
         * @param categoryId the non-blank category id, for example {@code "misc"}
         * @return this builder
         * @throws IllegalArgumentException when the id is {@code null} or blank
         */
        public Builder category(String categoryId)
        {
            this.categoryId = ClientValidation.requireNonBlank(categoryId, "categoryId");
            return this;
        }

        /**
         * Marks the key code as a mouse button.
         *
         * @param mouse {@code true} for a mouse button, {@code false} for a key
         * @return this builder
         */
        public Builder mouse(boolean mouse)
        {
            this.mouse = mouse;
            return this;
        }

        /**
         * Sets the modifier mask.
         *
         * @param modifiers the modifier mask
         * @return this builder
         */
        public Builder modifiers(int modifiers)
        {
            this.modifiers = modifiers;
            return this;
        }

        /**
         * Sets the display order.
         *
         * @param order the order; lower values are shown first
         * @return this builder
         */
        public Builder order(int order)
        {
            this.order = order;
            return this;
        }

        /**
         * Sets the screen factory opened by the convenience registration.
         *
         * @param screenFactoryId the screen factory id, or {@code null} for none;
         *                        must not be blank when non-null
         * @return this builder
         * @throws IllegalArgumentException when a non-null id is blank
         */
        public Builder screenFactory(String screenFactoryId)
        {
            this.screenFactoryId = screenFactoryId == null
                    ? null
                    : ClientValidation.requireNonBlank(screenFactoryId, "screenFactoryId");
            return this;
        }

        /**
         * Builds the immutable key mapping.
         *
         * @return the immutable mapping
         * @throws IllegalArgumentException when a collected value violates the
         *                                  documented constraints
         */
        public KeyMappingSpec build()
        {
            return new KeyMappingSpec(id, modId, translationKey, categoryId, keyCode, mouse,
                    modifiers, order, screenFactoryId);
        }
    }
}
