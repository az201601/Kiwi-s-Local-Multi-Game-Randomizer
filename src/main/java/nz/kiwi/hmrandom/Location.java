package nz.kiwi.hmrandom;

/** A check the player can reach. */
public final class Location {

    private final String id;
    private final String name;
    private final String region;

    public Location(String id, String name, String region) {
        this.id = id;
        this.name = name;
        this.region = region;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String region() {
        return region;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location)) return false;
        return id.equals(((Location) o).id);
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
