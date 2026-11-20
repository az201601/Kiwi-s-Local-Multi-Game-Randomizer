# Harvest Moon GB/GBC: Starting money

The optional starting-money roll is **1 to 500G inclusive**. Leaving the option off preserves the original 500G. The roll uses a separate seed stream from Grandpa’s Rainy Day Money, so toggling it never changes the drawer payout for a given seed. Start a new game to see the new starting balance.

## Emulator verification

Tested on the [supported USA ROM](../rainy-day-money/README.md#rom-and-references) in PyBoy 2.9.0. The new-game balance was read from cartridge RAM and checked again after opening the drawer:

| Mode | Starting balance | After the drawer |
| --- | --- | --- |
| Game Boy Color, `kiwi-demo`, option on | [377G](377g-start.png) | [1,322G](1322g-after-drawer.png) |
| Game Boy Color, `kiwi-demo`, option off | 500G | 1,445G |
| Game Boy monochrome, forced 1G boundary test | 1G | 946G |

All three runs collected the same **945G** drawer reward. Press **Select** after the farm tour to check the new-game balance.

On this ROM, each new-game initialization path writes a three-byte balance at cartridge RAM `$B8EF–$B8F1`. The patch updates the low/high immediate bytes at file offsets `0x752C4` / `0x752C9` and `0xF527F` / `0xF5284`. The game still clears the third byte itself. The patch does not edit save files.
