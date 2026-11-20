package nz.kiwi.hmrandom;

import java.util.List;

/** Applies {@link Patch}es to a {@link Rom}. */
public final class Patcher {

    private Patcher() {
    }

    public static void apply(Rom rom, List<Patch> patches) {
        for (Patch patch : patches) {
            rom.writeBytes(patch.offset(), patch.bytes());
        }
        // The header checksum covers the title area only, but recompute it so
        // emulators and flash carts do not complain.
        rom.fixHeaderChecksum();
    }
}
