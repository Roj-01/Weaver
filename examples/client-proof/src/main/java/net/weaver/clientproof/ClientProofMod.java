package net.weaver.clientproof;

import net.weaver.api.ModContext;
import net.weaver.api.game.GameWeaverMod;

public final class ClientProofMod implements GameWeaverMod
{
    @Override
    public void onInitialize(ModContext context)
    {
        ClientProof.install(context);
        ClientGuiProof.installConfig(context);
        ProofCreativeTab.register();
    }

    @Override
    public void onRegister(ModContext context)
    {
        ClientGuiProof.registerSteadyItem();
    }

    @Override
    public void onClientSetup(ModContext context)
    {
        ClientProofClient.install(context);
    }

    @Override
    public void onServerTick(ModContext context, long tick)
    {
        ClientProof.onServerTick(context, tick);
    }
}
