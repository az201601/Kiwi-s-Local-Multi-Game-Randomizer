# Architecture: local multiworld, offline

## Shape of the system

```
            +-----------------------------+
            |  world definition (per game) |
            |  items / locations / rules   |
            +--------------+--------------+
                           |
        +------------------+------------------+
        |                                     |
  +-----v------+                       +------v------+
  |  solver    |  generates a seed     |  patcher    |
  | (rules)    |  and a placement      | (per game)  |
  +-----+------+                       +------+------+
        |                                     |
  +-----v-------------------------------------v------+
  |  offline "multiworld" file (one JSON per seed)    |
  +---------------------+----------------------------+
                        |
                +-------v--------+
                |  local client  |
                |  (no network)  |
                +----------------+
```

The Archipelago analogue is direct: **items** are placed into **locations**
belonging to any game in the world; a **solver** has to prove the placement is
completable under the rules; a **client** hands the player the items their game
received as they check locations.

Difference from Archipelago: instead of a server, the seed file *is* the
message bus. A local client watches each emulator's save state (or a patched
ROM's SRAM) and, when a location is checked, writes the corresponding item into
the receiving game. One process, no sockets, no ports.

## Why the Game Boy work comes first

Patching a retail ROM safely needs two things this repo now provides:

1. **A byte-exact rebuild** (`make verify`). Randomization means editing bytes;
   if we cannot rebuild the original image perfectly, we cannot tell our edits
   from our mistakes.
2. **Addressable structure.** Locations need addresses. The recursive-descent
   walk plus a curated codemap turns "somewhere in bank 5" into
   `sub_05_4a1c` with a name like `day_advance`.

That is also why the toolkit is game-agnostic from day one: game 2 gets the
same `tools/gb/` (or a sibling for its platform) and plugs into the same
world-definition interface.

## Neutral on game identity

The world definition schema (items, locations, rules) is deliberately
game-agnostic JSON. A game module only has to provide:

* `locations`: stable ids + human names + the address/flag that proves the
  location was reached,
* `items`: ids + which locations they may be placed in (or "anywhere"),
* `rules`: predicates over the set of items received so far.

Nothing in that schema knows about Harvest Moon, which is what lets the second
game reuse all of it.
