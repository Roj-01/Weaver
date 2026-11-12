package net.weaver.api.game;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameWeaverModTest {

    @Test
    void typedExtensionPointsAreInvoked() {
        RecordingMod mod = new RecordingMod();

        mod.onPlayerJoin(null, (ServerPlayer) null);
        mod.onPlayerLeave(null, (ServerPlayer) null);
        mod.onLevelLoad(null, (Level) null);
        mod.onLevelUnload(null, (Level) null);

        assertEquals(1, mod.joins);
        assertEquals(1, mod.leaves);
        assertEquals(1, mod.loads);
        assertEquals(1, mod.unloads);
    }

    @Test
    void nonMatchingObjectPayloadsAreIgnoredWithoutRecursion() {
        RecordingMod mod = new RecordingMod();
        WeaverMod base = mod;

        base.onPlayerJoin(null, new Object());
        base.onPlayerLeave(null, null);
        base.onLevelLoad(null, "not a level");
        base.onLevelUnload(null, new Object());

        assertEquals(0, mod.joins);
        assertEquals(0, mod.leaves);
        assertEquals(0, mod.loads);
        assertEquals(0, mod.unloads);
    }

    @Test
    void defaultTypedMethodsAreNoOps() {
        GameWeaverMod mod = new GameWeaverMod() {
        };

        mod.onPlayerJoin(null, (ServerPlayer) null);
        mod.onPlayerLeave(null, (ServerPlayer) null);
        mod.onLevelLoad(null, (Level) null);
        mod.onLevelUnload(null, (Level) null);
    }

    private static final class RecordingMod implements GameWeaverMod {

        private int joins;
        private int leaves;
        private int loads;
        private int unloads;

        @Override
        public void onPlayerJoin(ModContext context, ServerPlayer player) {
            joins++;
        }

        @Override
        public void onPlayerLeave(ModContext context, ServerPlayer player) {
            leaves++;
        }

        @Override
        public void onLevelLoad(ModContext context, Level level) {
            loads++;
        }

        @Override
        public void onLevelUnload(ModContext context, Level level) {
            unloads++;
        }
    }
}
