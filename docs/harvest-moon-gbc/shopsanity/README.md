# Shopsanity research: Juice Bar + Flower Shop

**Scope:** Shuffle drinks and seeds across Eve’s Juice Bar and Nina’s Flower Shop. Purchases stay repeatable and grant the actual item or its normal effect. This is not a one-time location check. The standalone prototype below is not integrated into the main seeded randomizer.

## Working artwork prototype

The prototype makes a fixed swap: **Turnip and Potato seeds go to Eve; Apple Juice and Milk go to Nina.** All other items stay at their original shops. Turnip and Potato remain Spring stock, Apple still needs Year 2, Spring 15, and Milk still needs the vanilla Milk unlock flag. The conditional items only use counter space once their own gates pass. The two shops’ opening hours stay unchanged.

| | Juice Bar | Flower Shop |
| --- | --- | --- |
| Game Boy Color | [Turnip Seeds selected](mixed-eve-turnip-cgb.png) | [Milk selected](mixed-nina-milk-cgb.png) |
| Game Boy | [Turnip Seeds selected](mixed-eve-turnip-dmg.png) | [Apple Juice selected](mixed-nina-apple-dmg.png) |

The prototype extracts the original shop artwork from the ROM for each hardware mode. After stock checks, it loads four tiles for each visible item into that item’s counter slot and updates the menu record before the original renderer runs. This lets both shops reuse the native cursor, selection highlighting, and purchase scripts without assuming that the same tile number represents the same item at both shops. No new artwork files are needed to build the ROM.

### Try it locally

Download the [Java 8+ mixed-shop test JAR](../../../downloads/kiwi-shop-art-test-java8.jar). Open it, pick the original USA ROM, and save a new output ROM. The JAR is separate from the regular randomizer and does not require Python. You can also launch it with `java -jar kiwi-shop-art-test-java8.jar` and optionally pass the input and output paths.

Alternatively, from the repository root:

```sh
python -m tools.shop_art_prototype \
  'Harvest Moon GB (USA) (SGB Enhanced).gbc' mixed-shop.gbc
```

Open `mixed-shop.gbc` in your emulator. For the early visual test, visit Eve at 6 PM in Spring to see two seed bags among the drinks. Nina’s imported drinks only appear once individually unlocked. Use a separate emulator save for this prototype; the builder refuses to overwrite any existing output.

The PyBoy verifier checked 22 menu states across both modes, including the Apple date boundary, Milk flag both ways, seed season and stock masks, the drawn tile indices, and the exact VRAM artwork. In both modes a Turnip bag cost 120G at Eve’s; at Nina’s, Apple Juice cost 500G and restored stamina from 10 to 100, and Milk cost 100G and stocked ten servings. To rerun, provide a PyBoy 2.9 town-selection state in each hardware mode with Nina’s shop highlighted:

```sh
python -m tools.verify_shop_art \
  'Harvest Moon GB (USA) (SGB Enhanced).gbc' mixed-shop.gbc cgb.state dmg.state
```

This does not enable seeded cross-shop randomization. A future integration still needs to allocate patch space alongside the crop graphics overlay, keep stock gates attached to each item, and feed any reassigned seed seasons into the farm growth and artwork patches.

### Rebuild the test JAR

Use JDK 17 or newer to target Java 8. Build into a fresh classes directory so the JAR contains only the intended classes and embedded patch.

```sh
javac --release 8 -encoding UTF-8 -d build/shop-art-test/classes java/src/kiwi/*.java
python -m tools.shop_art_jar_resource \
  'Harvest Moon GB (USA) (SGB Enhanced).gbc' build/shop-art-test/classes
jar --create --file build/kiwi-shop-art-test-java8.jar --main-class kiwi.ShopArtTestApp \
  -C build/shop-art-test/classes .
```

## Vanilla Juice Bar

The [town guide](https://gamefaqs.gamespot.com/gbc/197527-harvest-moon-gbc/faqs/81605/town-and-shops) lists Eve’s hours as 6 PM to midnight. I entered at 6 PM and compared the supported USA ROM in PyBoy 2.9.0. The opening counter contained Orange Juice, Wild Grape Juice and Green Tea in both Game Boy and Game Boy Color modes.

| Item | Price | Observed availability and purchase behavior |
| --- | ---: | --- |
| Orange Juice | 300G | Opening stock. No grocery counter added. With max stamina 100, purchasing and dismissing the dialogue moved stamina from 10 to 60. |
| Apple Juice | 500G | Date gate: menu first includes it on **Year 2, Spring 15**. At max stamina 100, purchasing and dismissing the dialogue moved stamina from 10 to 100. |
| Wild Grape Juice | 300G | Available in the Year 1 Spring 1 fixture. Purchase added 10 to the Wild Grape Juice counter. The guide lists a harvest-event requirement, but the ROM’s counter initializer has no such check for this item. |
| Green Tea | 200G | Opening stock. Purchase added 10 to the Green Tea counter. |
| Milk | 100G | Menu requires `$B942` nonzero. With the flag set in the fixture, purchasing added 10 Milk. The guide attributes the flag to shipping milk; the natural flag-setting event was not replayed. |

[Orange Juice on the opening counter](vanilla-orange.png) · [Wild Grape Juice on the opening counter](vanilla-grape.png) · [All five counter slots with date and Milk flag set](vanilla-full-counter.png)

## Cross-counter transaction probes

In Game Boy Color mode I replaced only the first active item record in emulator RAM, after the counter was displayed. Then I used the shop’s normal purchase prompts; I did not edit the wallet, purchase quantity or item grant to make either transaction succeed.

| Probe | What happened |
| --- | --- |
| [Turnip seed at Eve’s](probe-turnip-at-eve.png) | The seed quantity prompt ran. One bag cost 120G (500G → 380G) and the Turnip bag counter went 0 → 1. The counter still showed the old Orange Juice icon. |
| [Green Tea at Nina’s](probe-tea-at-nina.png) | The ten-cup purchase prompt ran. It cost 200G (500G → 300G) and the Green Tea counter went 10 → 20. The counter still showed the old seed-bag icon. |

This demonstrates that the existing transaction scripts can cross counters for these two item types. It does **not** verify mixed-menu icons, immediate drinks bought at Nina’s, or a complete cross-shop shuffle. The screenshots intentionally show the original icons because the probe left the artwork untouched.

## ROM trace and next step

- Both counters use a visible count at `$CD33` and five-byte active entries at `$CD34` (two-byte item name pointer, two-byte purchase script pointer, one-byte icon tile number). Thus a common stock dispatcher can route an item by its record rather than implementing every purchase twice.
- Eve’s initializer is at file offset `0x24D7D` (CGB) / `0xA4D5C` (DMG); Nina’s is `0x2491D` / `0xA4900`. Eve copies the Orange Juice entry first, optionally copies Apple Juice, and uses the shared append helper for the pantry drinks. Apple’s date check uses `120 × year + 30 × season + day` (zero-based) and checks for a value of at least 134. The Milk flag was verified by toggling `$B942` and reopening the menu.
- Pantry counters: `$B8CA` Wild Grape Juice, `$B8CB` Green Tea, `$B8CC` Milk. Current/max stamina: `$B8ED/$B8EE`. Wallet: little-endian `$B8EF–$B8F1`. Turnip bag counter: `$B8D2`. Entered with the clock set to 18:00 (`$B882 = 0x12`); for Apple and Milk visibility the fixture set date/flag fields, not shop code.
- A full shuffle should retain each item’s native purchase price and effect and test insufficient funds and repeat purchases across counters. If crop seasons are reassigned, use the assigned season for availability, growth, and field graphics regardless of counter. Grass stays at Nina’s in this prototype.
