package nz.kiwi.hmrandom;

import java.util.List;
import java.util.Map;

/**
 * A game world in the local multiworld.
 *
 * This is the Archipelago-shaped seam: a world owns an item pool and a set of
 * checks, and turns an assignment of items to checks into raw ROM patches.
 * Adding a second game later means adding another implementation of this
 * interface and letting {@link Multiworld} pool the items together.
 */
public interface World {

    String name();

    /** Every item that can be placed for this world. */
    List<Item> items();

    /** Every check that can receive an item for this world. */
    List<Location> locations();

    /**
     * @param assignment maps a location id to the item placed there
     * @return the raw ROM patches that realise the assignment
     */
    List<Patch> createPatches(Map<String, Item> assignment);
}
