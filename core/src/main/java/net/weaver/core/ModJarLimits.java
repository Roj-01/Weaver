package net.weaver.core;

import java.io.IOException;
import java.io.InputStream;

/** Bounded reads of entries a mod jar controls. */
public final class ModJarLimits {
    private ModJarLimits() {
    }

    /**
     * Reads at most {@code limit} bytes: a compressed entry can expand to gigabytes
     * (a zip bomb) and must fail as an unreadable mod, not as an out-of-memory crash.
     */
    public static byte[] read(InputStream in, int limit, String name) throws IOException {
        byte[] bytes = in.readNBytes(limit + 1);
        if (bytes.length > limit) {
            throw new IOException(name + " is larger than " + limit + " bytes");
        }
        return bytes;
    }
}
