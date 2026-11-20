package kiwi;

import java.nio.charset.StandardCharsets;

/** The CPython string-seed and randbelow rules used by the original patcher. */
final class PythonSeedRandom {
    private static final int N = 624;
    private final int[] state = new int[N];
    private int index = N;

    PythonSeedRandom(String seed) {
        // Python seeds MT19937 with the unsigned big-endian integer seed || SHA-512(seed).
        // init_by_array receives that integer as least-significant 32-bit word first.
        byte[] text = seed.getBytes(StandardCharsets.UTF_8);
        byte[] hash = HarvestMoon.digest("SHA-512", text);
        int[] words = new int[(text.length + hash.length + 3) / 4];
        for (int i = 0; i < text.length + hash.length; i++) {
            int reverse = text.length + hash.length - 1 - i;
            byte b = reverse < text.length ? text[reverse] : hash[reverse - text.length];
            words[i / 4] |= Byte.toUnsignedInt(b) << ((i % 4) * 8);
        }
        // Leading NULs in the Python integer don't contribute seed words.
        int size = words.length;
        while (size > 1 && words[size - 1] == 0) size--;
        initialize(words, size);
    }

    private void initialize(int[] words, int size) {
        state[0] = 19650218;
        for (int i = 1; i < N; i++) {
            int previous = state[i - 1];
            state[i] = 1812433253 * (previous ^ (previous >>> 30)) + i;
        }
        int i = 1;
        for (int j = 0, k = Math.max(N, size); k > 0; k--) {
            int previous = state[i - 1];
            state[i] = (state[i] ^ ((previous ^ (previous >>> 30)) * 1664525)) + words[j] + j;
            if (++i == N) { state[0] = state[N - 1]; i = 1; }
            if (++j == size) j = 0;
        }
        for (int k = N - 1; k > 0; k--) {
            int previous = state[i - 1];
            state[i] = (state[i] ^ ((previous ^ (previous >>> 30)) * 1566083941)) - i;
            if (++i == N) { state[0] = state[N - 1]; i = 1; }
        }
        state[0] = 0x80000000;
    }

    int oneTo(int max) {
        int bits = 32 - Integer.numberOfLeadingZeros(max);
        int value;
        do {
            value = nextInt() >>> (32 - bits); // Python getrandbits uses the high bits.
        } while (value >= max);
        return value + 1;
    }

    private int nextInt() {
        if (index == N) {
            for (int i = 0; i < N; i++) {
                int y = (state[i] & 0x80000000) | (state[(i + 1) % N] & 0x7fffffff);
                state[i] = state[(i + 397) % N] ^ (y >>> 1) ^ ((y & 1) == 0 ? 0 : 0x9908b0df);
            }
            index = 0;
        }
        int y = state[index++];
        y ^= y >>> 11;
        y ^= (y << 7) & 0x9d2c5680;
        y ^= (y << 15) & 0xefc60000;
        return y ^ (y >>> 18);
    }
}
