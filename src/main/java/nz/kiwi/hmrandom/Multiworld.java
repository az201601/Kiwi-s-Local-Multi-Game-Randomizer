package nz.kiwi.hmrandom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Local multiworld generator.
 *
 * One seed drives every player's world. The item pools of all worlds are
 * shuffled together and dealt out to the checks, so an item from one game can
 * end up in another game's check - the Archipelago model, but generated and
 * played entirely on one machine.
 */
public final class Multiworld {

    private final long seed;
    private final List<String> players;
    private final List<World> worlds;

    public Multiworld(long seed, List<String> players, List<World> worlds) {
        this.seed = seed;
        this.players = new ArrayList<String>(players);
        this.worlds = new ArrayList<World>(worlds);
    }

    public long seed() {
        return seed;
    }

    public List<String> players() {
        return players;
    }

    /** The combined item pool for the whole multiworld. */
    public List<Item> pooledItems() {
        List<Item> pool = new ArrayList<Item>();
        for (World world : worlds) {
            pool.addAll(world.items());
        }
        return pool;
    }

    /**
     * Deals the pooled items out to every check in every world.
     *
     * @return maps a location id to the item placed there
     */
    public Map<String, Item> generate() {
        List<Item> pool = pooledItems();
        Collections.shuffle(pool, new Random(seed));

        List<Location> checks = new ArrayList<Location>();
        for (World world : worlds) {
            checks.addAll(world.locations());
        }

        Map<String, Item> assignment = new LinkedHashMap<String, Item>();
        int itemIndex = 0;
        for (Location location : checks) {
            if (itemIndex >= pool.size()) {
                break;
            }
            assignment.put(location.id(), pool.get(itemIndex));
            itemIndex++;
        }
        return assignment;
    }

    /** All raw patches needed to realise the assignment. */
    public List<Patch> patches(Map<String, Item> assignment) {
        List<Patch> patches = new ArrayList<Patch>();
        for (World world : worlds) {
            patches.addAll(world.createPatches(assignment));
        }
        return patches;
    }

    /** Human-readable spoiler log. */
    public String spoiler(Map<String, Item> assignment) {
        StringBuilder sb = new StringBuilder();
        sb.append("Seed: ");
        sb.append(Long.toString(seed));
        sb.append('\n');
        sb.append("Players: ");
        sb.append(players.toString());
        sb.append('\n');
        for (World world : worlds) {
            for (Location location : world.locations()) {
                Item item = assignment.get(location.id());
                String itemName = item == null ? "(nothing)" : item.name();
                String line = String.format("  %-28s <- %s%n",
                    world.name() + " / " + location.name(), itemName);
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
