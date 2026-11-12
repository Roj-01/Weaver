package net.weaver.minimap.ui.widget;

import net.weaver.minimap.ui.theme.UiMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WidgetMathTest
{
    @Test
    void tabIndexCoversTheWholeStrip()
    {
        assertEquals(0, UiMath.tabIndexAt(0.5D, 5, 0, 0, 300, 24, 3));
        assertEquals(0, UiMath.tabIndexAt(99.9D, 5, 0, 0, 300, 24, 3));
        assertEquals(1, UiMath.tabIndexAt(100, 5, 0, 0, 300, 24, 3));
        assertEquals(2, UiMath.tabIndexAt(299.9D, 5, 0, 0, 300, 24, 3));
        assertEquals(-1, UiMath.tabIndexAt(301, 5, 0, 0, 300, 24, 3));
        assertEquals(-1, UiMath.tabIndexAt(10, -1, 0, 0, 300, 24, 3));
        assertEquals(-1, UiMath.tabIndexAt(10, 5, 0, 0, 300, 24, 0));
    }

    @Test
    void dropdownRowsAccountForPaddingAndScroll()
    {
        assertEquals(1, UiMath.dropdownRowAt(10, 17, 10, 10, 100, 20, 1, 5, 3));
        assertEquals(3, UiMath.dropdownRowAt(10, 57, 10, 10, 100, 20, 1, 5, 3));
        assertEquals(-1, UiMath.dropdownRowAt(10, 11, 10, 10, 100, 20, 1, 5, 3));
        assertEquals(-1, UiMath.dropdownRowAt(10, 75, 10, 10, 100, 20, 1, 5, 3));
        assertEquals(-1, UiMath.dropdownRowAt(5, 40, 10, 10, 100, 20, 1, 5, 3));
        assertEquals(-1, UiMath.dropdownRowAt(10, 40, 10, 10, 100, 20, 4, 5, 3));
    }

    @Test
    void contextMenuRowsMatchTheirWidth()
    {
        assertEquals(0, UiMath.contextMenuItemAt(20, 20, 10, 10, 160, 22, 4, 3));
        assertEquals(2, UiMath.contextMenuItemAt(20, 60, 10, 10, 160, 22, 4, 3));
        assertEquals(-1, UiMath.contextMenuItemAt(20, 11, 10, 10, 160, 22, 4, 3));
        assertEquals(-1, UiMath.contextMenuItemAt(20, 82, 10, 10, 160, 22, 4, 3));
        assertEquals(-1, UiMath.contextMenuItemAt(9, 20, 10, 10, 160, 22, 4, 3));
    }

    @Test
    void colourPickerSwatchesAreStable()
    {
        assertEquals(0, UiMath.swatchIndexAt(20, 20, 0, 0, 120, 80, 24, 6, 4));
        assertEquals(1, UiMath.swatchIndexAt(40, 20, 0, 0, 120, 80, 24, 6, 4));
        assertEquals(-1, UiMath.swatchIndexAt(-1, 20, 0, 0, 120, 80, 24, 6, 4));
        assertEquals(-1, UiMath.swatchIndexAt(1, 1, 0, 0, 120, 80, 24, 6, 4));
    }

    @Test
    void sliderGeometryMatchesThePointer()
    {
        assertEquals(2.5D, UiMath.sliderValue(25, 0, 100, 0, 10), 0.0001D);
        assertEquals(7.5D, UiMath.sliderValue(75, 0, 100, 0, 10), 0.0001D);
        assertEquals(50.0D, UiMath.sliderX(5, 0, 100, 0, 10), 0.0001D);
        assertEquals(0.0D, UiMath.sliderX(5, 0, 100, 5, 5), 0.0001D);
        assertEquals(-20.0D, UiMath.sliderValue(-200, 0, 100, -20, 20), 0.0001D);
    }

    @Test
    void keybindLabelsAreReadable()
    {
        assertEquals("B", UiMath.keyLabel(66));
        assertEquals("Space", UiMath.keyLabel(32));
        assertEquals("Escape", UiMath.keyLabel(256));
        assertEquals("Key 999", UiMath.keyLabel(999));
    }

    @Test
    void scrollbarHitTestUsesTheThumb()
    {
        assertEquals(true, UiMath.hitScrollbar(14, 30, 12, 10, 4, 200, 20, 40));
        assertEquals(false, UiMath.hitScrollbar(14, 70, 12, 10, 4, 200, 20, 40));
        assertEquals(false, UiMath.hitScrollbar(9, 30, 12, 10, 4, 200, 20, 40));
    }
}
