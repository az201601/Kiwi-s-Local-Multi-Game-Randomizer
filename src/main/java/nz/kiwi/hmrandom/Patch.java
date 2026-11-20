package nz.kiwi.hmrandom;

/** A raw byte patch applied to the ROM. */
public final class Patch {

    private final int offset;
    private final byte[] bytes;
    private final String description;

    public Patch(int offset, byte[] bytes, String description) {
        this.offset = offset;
        this.bytes = bytes;
        this.description = description;
    }

    public int offset() {
        return offset;
    }

    public byte[] bytes() {
        return bytes;
    }

    public String description() {
        return description;
    }

    @Override
    public String toString() {
        return description + " @ " + Integer.toHexString(offset);
    }
}
