# Harvest Moon GB/GBC: Nina’s Flower Shop

Two independent, seeded checkboxes (both off by default):

- **Randomize seasonal seed stock** puts the eight crop seed types into four pairs. Each crop seed appears in exactly one season; the player’s name no longer filters randomized stock. Grass is included in Spring, Summer, and Fall, never Winter.
- **Shuffle seed shop slots** changes the counter order separately for each season, including the position of Grass where sold. On its own, it preserves the original seasonal inventory and the name-based Fall/Winter filters.

These options change **what Nina sells**, not the prices, planting seasons, or crop growth rules. Seeds can be bought out of season to save for later. With both options off, the shop remains untouched.

## Screenshot verification

Tested on the [supported USA ROM](../rainy-day-money/README.md#rom-and-references) in PyBoy 2.9.0, using seed **`nina-2`**, both shop options enabled, and the player name `AAA`. Each link shows the selected counter slot and its unmodified price. The season field was set before opening the shop from the same emulator state to avoid playing a full year. Stock, prices, and purchase logic were not edited in emulator memory.

| Season | Slot 1 | Slot 2 | Slot 3 |
| --- | --- | --- | --- |
| Spring | [Corn](spring-slot-1.png) | [Eggplant](spring-slot-2.png) | [Grass](spring-slot-3.png) |
| Summer | [Turnip](summer-slot-1.png) | [Grass](summer-slot-2.png) | [Peanut](summer-slot-3.png) |
| Fall | [Grass](fall-slot-1.png) | [Potato](fall-slot-2.png) | [Broccoli](fall-slot-3.png) |
| Winter | [Tomato](winter-slot-1.png) | [Carrot](winter-slot-2.png) | — |

- **Purchase check:** Bought [one bag of Corn in Spring for 300G](spring-corn-purchase.png). The actual balance fell from 500G to 200G, and Corn inventory increased from zero to one bag.
- **Game Boy mode:** All four inventories matched in monochrome mode, including [Winter’s two slots](winter-game-boy.png).
- **Options in isolation:** In a separate Fall check, slots-only gave Grass before Eggplant with `AAA` and Grass before Peanut with `aAA`. Stock-only offered Potato and Broccoli followed by Grass for both names.

## ROM notes

The patch replaces the Flower Shop’s nine unrolled record calls with a dispatcher at file offsets `0x2491D` and `0xA4900`. It looks up the season (`$B884`) in a four-row inventory table, then feeds the selected records to the original shop builder. The item icons, names, prices, and transaction scripts still come from the original records. The shop’s active item count and five-byte records are visible from `$CD33` in WRAM. Only stock randomization relaxes the crop records’ original season/name filters; Grass retains its vanilla filter.
