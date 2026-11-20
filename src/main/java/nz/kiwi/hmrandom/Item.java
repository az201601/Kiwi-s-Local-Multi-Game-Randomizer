package nz.kiwi.hmrandom;

/** A thing that can be placed at a check. */
public final class Item {

    public enum Kind {
        /** A bag of G added directly to the player's money. */
        MONEY,
        /** An entry in the player's item inventory. */
        ITEM
    }

    private final String id;
    private final String name;
    private final Kind kind;
    private final int value;

    public Item(String id, String name, Kind kind, int value) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.value = value;
    }

    public static Item money(String id, String name, int amount) {
        return new Item(id, name, Kind.MONEY, amount);
    }

    public static Item item(String id, String name, int itemId) {
        return new Item(id, name, Kind.ITEM, itemId);
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public Kind kind() {
        return kind;
    }

    /** Money amount for MONEY items, inventory item ID for ITEM items. */
    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Item)) return false;
        Item other = (Item) o;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
