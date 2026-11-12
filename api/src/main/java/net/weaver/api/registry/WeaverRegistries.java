package net.weaver.api.registry;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class WeaverRegistries
{
    public static final RegistryKey<Object> BLOCK = key("block");
    public static final RegistryKey<Object> ITEM = key("item");
    public static final RegistryKey<Object> ENTITY_TYPE = key("entity_type");
    public static final RegistryKey<Object> BLOCK_ENTITY_TYPE = key("block_entity_type");
    public static final RegistryKey<Object> FLUID = key("fluid");
    public static final RegistryKey<Object> SOUND_EVENT = key("sound_event");
    public static final RegistryKey<Object> MOB_EFFECT = key("mob_effect");
    public static final RegistryKey<Object> POTION = key("potion");
    public static final RegistryKey<Object> PARTICLE_TYPE = key("particle_type");
    public static final RegistryKey<Object> MENU = key("menu");
    public static final RegistryKey<Object> RECIPE_TYPE = key("recipe_type");
    public static final RegistryKey<Object> RECIPE_SERIALIZER = key("recipe_serializer");
    public static final RegistryKey<Object> ATTRIBUTE = key("attribute");
    public static final RegistryKey<Object> CREATIVE_MODE_TAB = key("creative_mode_tab");
    public static final RegistryKey<Object> COMMAND_ARGUMENT_TYPE =
            key("command_argument_type");
    public static final RegistryKey<Object> DATA_COMPONENT_TYPE = key("data_component_type");
    public static final RegistryKey<Object> STAT_TYPE = key("stat_type");
    public static final RegistryKey<Object> TRIGGER_TYPE = key("trigger_type");
    public static final RegistryKey<Object> LOOT_POOL_ENTRY_TYPE = key("loot_pool_entry_type");
    public static final RegistryKey<Object> LOOT_FUNCTION_TYPE = key("loot_function_type");
    public static final RegistryKey<Object> LOOT_CONDITION_TYPE = key("loot_condition_type");
    public static final RegistryKey<Object> ENCHANTMENT_EFFECT_COMPONENT_TYPE =
            key("enchantment_effect_component_type");
    public static final RegistryKey<Object> GAME_RULE = key("game_rule");
    public static final RegistryKey<Object> TICKET_TYPE = key("ticket_type");
    public static final RegistryKey<Object> STRUCTURE_TYPE = key("worldgen/structure_type");
    public static final RegistryKey<Object> STRUCTURE_PIECE = key("worldgen/structure_piece");
    public static final RegistryKey<Object> FEATURE_TYPE = key("worldgen/feature_type");
    public static final RegistryKey<Object> CHUNK_STATUS = key("chunk_status");
    public static final RegistryKey<Object> CARVER_TYPE = key("worldgen/carver_type");
    public static final RegistryKey<Object> PLACEMENT_MODIFIER_TYPE =
            key("worldgen/placement_modifier_type");
    public static final RegistryKey<Object> MATERIAL_CONDITION_TYPE =
            key("worldgen/material_condition_type");
    public static final RegistryKey<Object> ENTITY_SUB_PREDICATE_TYPE =
            key("entity_sub_predicate_type");
    public static final RegistryKey<Object> DATA_COMPONENT_PREDICATE_TYPE =
            key("data_component_predicate_type");
    public static final RegistryKey<Object> MAP_DECORATION_TYPE = key("map_decoration_type");
    public static final RegistryKey<Object> NUMBER_FORMAT_TYPE = key("number_format_type");
    public static final RegistryKey<Object> DIALOG_TYPE = key("dialog_type");
    public static final RegistryKey<Object> PERMISSION_TYPE = key("permission_type");
    public static final RegistryKey<Object> ENVIRONMENT_ATTRIBUTE = key("environment_attribute");
    public static final RegistryKey<Object> ATTRIBUTE_TYPE = key("attribute_type");

    private WeaverRegistries()
    {
    }

    public static RegistryKey<Object> of(String id)
    {
        return RegistryKey.parse(id);
    }

    public static RegistryKey<Object> of(String namespace, String path)
    {
        return RegistryKey.of(namespace, path);
    }

    public static Set<String> availableIds()
    {
        final List<String> ids = RegistryManager.registryIds();
        if (ids.isEmpty())
        {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }

    private static RegistryKey<Object> key(String path)
    {
        return RegistryKey.of("minecraft", path);
    }
}
