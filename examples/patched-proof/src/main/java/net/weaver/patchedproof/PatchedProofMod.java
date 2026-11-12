package net.weaver.patchedproof;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.weaver.api.ModContext;
import net.weaver.api.event.events.ServerStartedEvent;
import net.weaver.api.game.GameWeaverMod;
import net.weaver.patched.PatchedGame;
import net.weaver.patched.WeaverAttachmentHolder;

/**
 * Compile-time and runtime proof for the patched-game artifact.
 *
 * <p>This source imports {@code net.weaver.patched.*} and calls the injected
 * members directly. It cannot compile against the unpatched client jar; the
 * build script uses that as a negative control.
 */
public final class PatchedProofMod implements GameWeaverMod {

    private static final String PROBE_KEY = "probe";
    private static final String PROBE_VALUE = "ok";

    private static final AtomicBoolean SERVER_DONE = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_DONE = new AtomicBoolean();

    @Override
    public void onInitialize(ModContext context) {
        context.events().addListener(ServerStartedEvent.class, event -> {
            if (!SERVER_DONE.compareAndSet(false, true)) {
                return;
            }
            try {
                verifyServer(event.server());
            } catch (Throwable failure) {
                fail("server", describe(failure));
            }
        });
    }

    @Override
    public void onClientSetup(ModContext context) {
        PatchedProofClient.install();
    }

    static void verifyClientProbe() {
        if (!CLIENT_DONE.compareAndSet(false, true)) {
            return;
        }
        try {
            ItemStack stack = new ItemStack(Items.STONE);
            WeaverAttachmentHolder holder = (WeaverAttachmentHolder) stack;
            holder.weaver$setAttachment(PROBE_KEY, PROBE_VALUE);
            boolean has = holder.weaver$hasAttachment(PROBE_KEY);
            String got = (String) holder.weaver$getAttachment(PROBE_KEY);
            Object removed = holder.weaver$removeAttachment(PROBE_KEY);
            int level = ((PatchedGame) stack).weaver$patchLevel();
            if (!(has && PROBE_VALUE.equals(got) && PROBE_VALUE.equals(removed)
                    && level == 1 && stack instanceof WeaverAttachmentHolder)) {
                fail("client", "stack has=" + has + " got=" + got
                        + " removed=" + removed + " patchLevel=" + level);
                return;
            }
            success("client");
        } catch (Throwable failure) {
            fail("client", describe(failure));
        }
    }

    private static void verifyServer(Object serverObject) {
        if (!(serverObject instanceof MinecraftServer server)) {
            fail("server", "serverType=" + typeName(serverObject));
            return;
        }
        ServerLevel level = server.overworld();
        if (level == null) {
            fail("server", "overworld=null");
            return;
        }
        WeaverAttachmentHolder holder = (WeaverAttachmentHolder) level;
        holder.weaver$setAttachment(PROBE_KEY, PROBE_VALUE);
        boolean has = holder.weaver$hasAttachment(PROBE_KEY);
        String got = (String) holder.weaver$getAttachment(PROBE_KEY);
        Object removed = holder.weaver$removeAttachment(PROBE_KEY);
        int levelPatch = ((PatchedGame) level).weaver$patchLevel();
        if (!(has && PROBE_VALUE.equals(got) && PROBE_VALUE.equals(removed)
                && levelPatch == 1 && level instanceof WeaverAttachmentHolder)) {
            fail("server", "level has=" + has + " got=" + got
                    + " removed=" + removed + " patchLevel=" + levelPatch);
            return;
        }
        success("server");
    }

    static void success(String side) {
        System.out.println("PATCHED PROOF ok=true side=" + side
                + " patchLevel=1 value=" + PROBE_VALUE);
        System.out.flush();
    }

    static void fail(String side, String reason) {
        System.out.println("PATCHED PROOF ok=false side=" + side
                + " reason=" + sanitize(reason));
        System.out.flush();
    }

    static String describe(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            return failure.getClass().getName();
        }
        return failure.getClass().getSimpleName() + ":" + message;
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static String sanitize(String value) {
        String text = value == null ? "unknown" : value;
        return text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }
}
