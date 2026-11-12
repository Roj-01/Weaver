package net.weaver.proof;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.weaver.nf.EntityRenderersEvent;

/** The client side of {@link NfProof}: its multipart mob draws nothing. */
final class NfProofClient {
    private NfProofClient() {
    }

    static void setup() {
        EntityRenderersEvent.RENDERERS.registerEntityRenderer(NfProof.NF_SERPENT.get(), NoopRenderer::new);
    }
}
