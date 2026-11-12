package net.weaver.api.config;

import net.weaver.api.event.Side;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The text form of config values that the generated settings screen edits. */
class ConfigValueTextTest
{
    enum Mode
    {
        SLOW,
        FAST
    }

    private static ConfigSpec spec()
    {
        ConfigBuilder builder = ConfigBuilder.create("api.text", Side.BOTH);
        builder.booleanValue("flag", true, "flag");
        builder.intValue("count", 4, 1, 16, "count");
        builder.doubleValue("ratio", 0.5, "ratio");
        builder.stringValue("label", "hi", 1, 8, "label");
        builder.enumValue("mode", Mode.class, Mode.FAST, "mode");
        builder.intListValue("ids", List.of(1, 2), "ids");
        return builder.build();
    }

    @Test
    void writesTextInTheFormItReads()
    {
        ConfigSpec spec = spec();
        assertEquals("true", spec.value("flag").text());
        assertEquals("4", spec.value("count").text());
        assertEquals("FAST", spec.value("mode").text());
        assertEquals("1, 2", spec.value("ids").text());
        assertEquals("4", spec.value("count").defaultText());
    }

    @Test
    void setsParsedText()
    {
        ConfigSpec spec = spec();
        spec.value("flag").setText("false");
        spec.value("count").setText(" 9 ");
        spec.value("ratio").setText("1.25");
        spec.value("mode").setText("SLOW");
        spec.value("ids").setText("3, 4,5");
        assertEquals(false, spec.value("flag").get());
        assertEquals(9, spec.value("count").get());
        assertEquals(1.25, spec.value("ratio").get());
        assertEquals(Mode.SLOW, spec.value("mode").get());
        assertEquals(List.of(3, 4, 5), spec.value("ids").get());
        spec.value("ids").setText("");
        assertEquals(List.of(), spec.value("ids").get());
    }

    @Test
    void rejectsInvalidTextAndKeepsTheValue()
    {
        ConfigSpec spec = spec();
        assertThrows(ConfigValidationException.class, () -> spec.value("count").setText("99"));
        assertThrows(ConfigValidationException.class, () -> spec.value("count").setText("four"));
        assertThrows(ConfigValidationException.class, () -> spec.value("flag").setText("yes"));
        assertThrows(ConfigValidationException.class, () -> spec.value("mode").setText("MEDIUM"));
        assertThrows(ConfigValidationException.class, () -> spec.value("label").setText("far too long"));
        assertThrows(ConfigValidationException.class, () -> spec.value("ratio").setText("NaN"));
        assertThrows(ConfigValidationException.class, () -> spec.value("ids").setText("1, x"));
        assertEquals(4, spec.value("count").get());
        assertEquals(true, spec.value("flag").get());
    }
}
