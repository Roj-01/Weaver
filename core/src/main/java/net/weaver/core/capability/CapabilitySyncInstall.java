package net.weaver.core.capability;

import net.weaver.api.capability.AttachmentRegistry;
import net.weaver.api.capability.CapabilitySyncProvider;
import net.weaver.api.network.NetworkConnection;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Loader-side installer for the optional game-typed capability sync provider.
 *
 * <p>{@code api-game} owns the game holder identity provider; {@code core} owns
 * the channel. This class bridges the two reflectively so a dedicated server
 * never links client classes at install time. Both calls are failure-isolated:
 * a missing {@code api-game} artifact or game class path leaves the channel
 * uninstalled and the rest of the loader unaffected.</p>
 */
public final class CapabilitySyncInstall
{
    private static final String GAME_PROVIDER =
            "net.weaver.api.game.capability.GameCapabilitySync";

    private CapabilitySyncInstall()
    {
        throw new AssertionError("No CapabilitySyncInstall instances");
    }

    /**
     * Installs the capability channel with the game provider and the bridge
     * recipient list.
     *
     * <p>The provider class is loaded through {@code gameLoader} with
     * initialization enabled so a linkage failure is visible here. The
     * recipient supplier re-invokes the provider's static {@code recipients()}
     * method on every change and returns an empty list on any failure.</p>
     *
     * @param gameLoader the loader that sees {@code api-game} and the game
     * @param log        the diagnostic sink, not {@code null}
     * @return {@code true} when the channel was installed
     * @throws NullPointerException when {@code log} is {@code null}
     */
    public static boolean install(ClassLoader gameLoader, Consumer<String> log)
    {
        Objects.requireNonNull(log, "log");
        net.weaver.core.BootPhaseOrder.requireTransformersInstalled(
                "CapabilitySyncInstall.install");
        if (gameLoader == null)
        {
            log.accept("[weaver] CAPABILITY SYNC skipped reason=no-game-loader");
            return false;
        }
        try
        {
            Class<?> providerType = Class.forName(GAME_PROVIDER, true, gameLoader);
            Object provider = providerType.getMethod("provider").invoke(null);
            if (!(provider instanceof CapabilitySyncProvider syncProvider))
            {
                log.accept("[weaver] CAPABILITY SYNC skipped reason=provider-not-supported");
                return false;
            }
            Method recipients = providerType.getMethod("recipients");
            CapabilitySyncChannel.install(AttachmentRegistry.global(), syncProvider);
            CapabilitySyncChannel.setRecipientsSupplier(() -> recipients(recipients));
            log.accept("[weaver] CAPABILITY SYNC installed provider=game channel="
                    + CapabilitySyncChannel.TYPE.channel());
            return true;
        }
        catch (ClassNotFoundException | LinkageError missing)
        {
            log.accept("[weaver] CAPABILITY SYNC unavailable reason=" + missing);
        }
        catch (Throwable failure)
        {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            log.accept("[weaver] CAPABILITY SYNC install FAILED reason=" + cause);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<NetworkConnection> recipients(Method recipients)
    {
        try
        {
            Object value = recipients.invoke(null);
            if (value instanceof List<?> list)
            {
                return (List<NetworkConnection>) list;
            }
        }
        catch (Throwable ignored)
        {
            // Change forwarding is best-effort.
        }
        return List.of();
    }
}
