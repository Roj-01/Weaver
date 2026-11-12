package net.weaver.core;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The optional display fields of weaver.mod.json shown in the mod list. */
class ModMetadataDisplayTest
{
    private static ModMetadata parse(String json)
    {
        return ModMetadata.parse("test", json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsDescriptionAuthorsLicenseAndHomepage()
    {
        ModMetadata metadata = parse("""
                {"id": "shown", "mainClass": "a.B", "description": "Does things.",
                 "authors": ["Ana", {"name": "Bo"}, {"email": "no name"}], "license": "MIT",
                 "contact": {"homepage": "https://example.org"}}
                """);
        assertEquals("Does things.", metadata.description());
        assertEquals(List.of("Ana", "Bo"), metadata.authors());
        assertEquals("MIT", metadata.license());
        assertEquals("https://example.org", metadata.homepage());
    }

    @Test
    void acceptsOneAuthorAsAStringAndATopLevelHomepage()
    {
        ModMetadata metadata = parse("""
                {"id": "single", "mainClass": "a.B", "authors": "Solo", "homepage": "https://a.example"}
                """);
        assertEquals(List.of("Solo"), metadata.authors());
        assertEquals("https://a.example", metadata.homepage());
    }

    @Test
    void defaultsToEmptyWhenAbsent()
    {
        ModMetadata metadata = parse("""
                {"id": "plain", "mainClass": "a.B"}
                """);
        assertEquals("", metadata.description());
        assertEquals(List.of(), metadata.authors());
        assertEquals("", metadata.license());
        assertEquals("", metadata.homepage());
    }
}
