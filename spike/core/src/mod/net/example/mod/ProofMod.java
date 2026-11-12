package net.example.mod;

import net.weaver.api.ModContext;
import net.weaver.api.WeaverMod;

/** Entrypoint that observes every lifecycle phase and the AT result. */
public final class ProofMod implements WeaverMod {

    @Override
    public void onInitialize(ModContext context) {
        context.log("PROOF INIT id=" + context.id());
        ProofBlock block = new ProofBlock();
        int secret = block.readSecret();
        if (secret != 7) {
            throw new IllegalStateException("access transformer secret=" + secret);
        }
        context.log("PROOF AT ok=true secret=" + secret);
        ProofHooks.phase("initialize");
    }

    @Override
    public void onRegister(ModContext context) {
        ProofHooks.phase("register");
    }

    @Override
    public void onCommonSetup(ModContext context) {
        ProofHooks.phase("common");
    }

    @Override
    public void onClientSetup(ModContext context) {
        ProofHooks.phase("client");
    }

    @Override
    public void onServerSetup(ModContext context) {
        ProofHooks.phase("server");
    }

    @Override
    public void onLoadComplete(ModContext context) {
        ProofHooks.phase("complete");
    }
}
