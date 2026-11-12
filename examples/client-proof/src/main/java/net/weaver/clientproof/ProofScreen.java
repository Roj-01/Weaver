package net.weaver.clientproof;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ProofScreen extends Screen
{
    public ProofScreen()
    {
        super(Component.literal("Weaver Client Proof"));
    }

    @Override
    protected void init()
    {
        super.init();
        if (ProofState.markScreen())
        {
            ClientProof.out("CLIENT PROOF SCREEN ok=true");
        }
    }
}
