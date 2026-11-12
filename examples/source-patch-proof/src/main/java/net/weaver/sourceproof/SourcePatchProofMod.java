package net.weaver.sourceproof;

import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.weaver.api.ModContext;
import net.weaver.api.event.events.ServerStartedEvent;
import net.weaver.api.game.GameWeaverMod;

/**
 * Compile-time and runtime proof for the source-patched game artifact.
 *
 * <p>The direct call to {@code weaver$sourcePatchProbe} cannot compile against
 * the official jar; the build script uses that as a negative control.
 */
public final class SourcePatchProofMod implements GameWeaverMod {

    private static final AtomicBoolean SERVER_DONE = new AtomicBoolean();
    private static final AtomicBoolean CLIENT_DONE = new AtomicBoolean();

    @Override
    public void onInitialize(ModContext context) {
        context.events().addListener(ServerStartedEvent.class, event -> {
            if (!SERVER_DONE.compareAndSet(false, true)) {
                return;
            }
            try {
                verify("server");
            } catch (Throwable failure) {
                fail("server", describe(failure));
            }
        });
    }

    @Override
    public void onClientSetup(ModContext context) {
        SourcePatchProofClient.install();
    }

    static void verifyClientProbe() {
        if (!CLIENT_DONE.compareAndSet(false, true)) {
            return;
        }
        try {
            verify("client");
        } catch (Throwable failure) {
            fail("client", describe(failure));
        }
    }

    private static void verify(String side) {
        ItemStack stack = new ItemStack(Items.STONE);
        String probe = stack.weaver$sourcePatchProbe();
        String stone = Items.STONE.toString();
        boolean ok = probe != null
                && probe.startsWith("source:")
                && probe.contains("x1")
                && probe.contains("stone")
                && probe.contains(stone);
        if (!ok) {
            fail(side, "probe=" + probe + " stone=" + stone);
            return;
        }
        success(side, probe);
    }

    static void success(String side, String probe) {
        System.out.println("SOURCE PATCH PROOF ok=true side=" + side
                + " value=" + sanitize(probe));
        System.out.flush();
    }

    static void fail(String side, String reason) {
        System.out.println("SOURCE PATCH PROOF ok=false side=" + side
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

    static String sanitize(String value) {
        String text = value == null ? "null" : value;
        return text.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }
}
