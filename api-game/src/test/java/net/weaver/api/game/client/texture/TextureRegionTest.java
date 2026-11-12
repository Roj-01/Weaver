package net.weaver.api.game.client.texture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextureRegionTest
{
    @Test
    void partialCopyReadsOnlyTheRequestedRegion()
    {
        int[] source = new int[]{6, 7, 10, 11, 99, 99, 99};

        int[] region = TextureRegion.copyRegion(source, 2, 2);

        assertArrayEquals(new int[]{6, 7, 10, 11}, region);
        System.out.println("[weaver] TEXTURE PARTIAL ok");
    }

    @Test
    void validationRejectsOutOfBoundsAndShortArrays()
    {
        int[] source = new int[4];

        assertThrows(IllegalArgumentException.class,
                () -> TextureRegion.validate(4, 4, 3, 0, 2, 1, source));
        assertThrows(IllegalArgumentException.class,
                () -> TextureRegion.validate(4, 4, 0, 0, 3, 2, source));
        assertThrows(IllegalArgumentException.class,
                () -> TextureRegion.validate(4, 4, 0, 0, 0, 1, source));
        assertThrows(IllegalArgumentException.class,
                () -> TextureRegion.validate(4, 4, -1, 0, 1, 1, source));
    }
}
