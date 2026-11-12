package net.weaver.api.game.client.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextureOptionsTest
{
    @Test
    void defaultMatchesVanillaSampling()
    {
        assertFalse(TextureOptions.DEFAULT.linearFiltering());
        assertFalse(TextureOptions.DEFAULT.clampToEdge());
        assertFalse(TextureOptions.DEFAULT.mipmaps());
    }

    @Test
    void linearClampIsSmoothAndEdgeClamped()
    {
        TextureOptions options = TextureOptions.linearClamp();

        assertTrue(options.linearFiltering());
        assertTrue(options.clampToEdge());
        assertFalse(options.mipmaps());
    }

    @Test
    void mipmappedOptionEnablesAllThreeFlags()
    {
        TextureOptions options = TextureOptions.linearClampMipmapped();

        assertTrue(options.linearFiltering());
        assertTrue(options.clampToEdge());
        assertTrue(options.mipmaps());
    }
}
