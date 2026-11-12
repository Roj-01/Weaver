package net.weaver.core.platform;

import net.weaver.core.ClassSpace;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformModeTest {

    @Test
    void sharedModeRequiresExplicitRequest() {
        assertEquals(ClassSpace.Mode.SHARED, ClassSpace.selectMode(true, false));
        assertEquals(ClassSpace.Mode.SHARED, ClassSpace.selectMode(true, true));
        assertEquals(ClassSpace.Mode.SYSTEM, ClassSpace.selectMode(false, true));
        assertEquals(ClassSpace.Mode.SYSTEM, ClassSpace.selectMode(false, false));
    }

    @Test
    void sharedModeIsNeverSelectedImplicitly() {
        for (boolean gameVisibleToSystem : new boolean[]{true, false}) {
            ClassSpace.Mode mode = ClassSpace.selectMode(false, gameVisibleToSystem);
            assertEquals(ClassSpace.Mode.SYSTEM, mode,
                    "unrequested shared mode with gameVisibleToSystem=" + gameVisibleToSystem);
            assertFalse(mode == ClassSpace.Mode.SHARED,
                    "shared mode must not be selected without an explicit request");
        }
    }

    @Test
    void modeDeclaresSystemAndShared() {
        List<ClassSpace.Mode> modes = Arrays.asList(ClassSpace.Mode.values());
        assertEquals(2, modes.size(), "Mode value count");
        assertEquals(List.of(ClassSpace.Mode.SYSTEM, ClassSpace.Mode.SHARED), modes);
        assertTrue(modes.contains(ClassSpace.Mode.SYSTEM));
        assertTrue(modes.contains(ClassSpace.Mode.SHARED));
    }
}
