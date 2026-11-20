# Kiwi’s Local Multi-Game Randomizer

The first supported check is **Grandpa’s Rainy Day Money** in Harvest Moon GB/GBC. When randomized, the drawer awards a seeded amount from **1 to 1,000G**, and the in-game receipt shows the same amount. It still pays only once, regardless of house expansion. Starting money can also be randomized from **1 to 500G** for new games, and Nina’s Flower Shop supports seasonal seed stock and counter slot shuffles. This is a local ROM patcher, not yet a multiworld client.

## Java desktop GUI

For a prebuilt app, open [`kiwi-randomizer-java8.jar`](downloads/kiwi-randomizer-java8.jar) on GitHub and click **Download raw file**. The app targets **Java 8 or newer**. Double-click the JAR or run `java -jar kiwi-randomizer-java8.jar` in a desktop session.

To build from source with JDK 17 or later, run from the repository root:

```sh
javac --release 8 -encoding UTF-8 -d build/classes java/src/kiwi/HarvestMoon.java java/src/kiwi/PythonSeedRandom.java java/src/kiwi/RandomizerApp.java java/src/kiwi/SeedShop.java
java -cp build/classes kiwi.RandomizerApp
```

1. Choose the original ROM and a new `.gbc` output path. The app preselects the supported ROM if it finds it in the current directory.
2. Leave **Seed** blank for a fresh roll or enter an existing seed. Choose the options independently:
   - **Randomize Grandpa’s Rainy Day Money (1–1,000G)** is checked by default. Uncheck it for the original **1,000G** drawer reward.
   - **Randomize starting money (1–500G)** is unchecked by default. Unchecked keeps the original **500G** balance for new games.
   - **Randomize seasonal seed stock** redistributes the eight crop seeds across seasons, two per season. Each crop grows in the season Nina sells it. Grass stays in Spring, Summer, and Fall.
   - **Shuffle seed shop slots** changes the counter order separately each season. On its own it does not change what Nina sells. Both shop options are unchecked by default. [See the verified inventories](docs/harvest-moon-gbc/seed-shop/README.md) and [overnight growth screenshots](docs/harvest-moon-gbc/seed-shop/README.md#overnight-growth-verification).
   - With both money options checked, `kiwi-demo` gives **377G** at the start and **945G** from the drawer. With all options unchecked, the output is an unchanged copy of the original ROM.
3. Click **Generate patched ROM**. The app displays the amount, the seed, and where it saved the new ROM. Open that ROM in your emulator.

The desktop app does not need Python. It verifies the ROM revision and never overwrites an existing file. [See the GUI](docs/harvest-moon-gbc/desktop-gui.png).

To create a runnable JAR after compiling (keep `--release 8` for every distributed build):

```sh
jar --create --file build/kiwi-randomizer-java8.jar --main-class kiwi.RandomizerApp -C build/classes .
java -jar build/kiwi-randomizer-java8.jar
```

Run the Java patch and Swing form smoke tests after compiling the app:

```sh
javac --release 8 -encoding UTF-8 -cp build/classes -d build/classes java/test/kiwi/RandomizerTest.java
java -Djava.awt.headless=true -cp build/classes kiwi.RandomizerTest
```

## Generate a randomized ROM (Python CLI)

With Python 3.10 or later, run from the repository root:

```sh
python -m randomizer.harvest_moon_gbc \
  'Harvest Moon GB (USA) (SGB Enhanced).gbc' \
  output/harvest-moon-randomized.gbc \
  --seed kiwi-demo
```

Load the new ROM in your emulator and start a new game. After the opening tour, face the drawer on the right of the farmhouse bed and press **A**. An existing save in which you collected the stash will not get another payout.

Add `--no-randomize-rainy-day-money` to retain the original drawer reward, or `--randomize-starting-money` to roll the same starting balance as the Java GUI. The starting-money option only affects a **new game**. See the [emulator verification](docs/harvest-moon-gbc/starting-money/README.md).

Use `--randomize-seed-stock` and/or `--shuffle-seed-slots` to enable the Flower Shop options.

Omit `--seed` for a fresh roll. The command prints the seed and amount. Reuse the seed with the original ROM and a new output path to reproduce that roll. It never overwrites an existing file. Only the exact [verified ROM revision](docs/harvest-moon-gbc/rainy-day-money/README.md#rom-and-references) is supported.

Run the tests with `python -m unittest discover -s tests`. See the [research and screenshots](docs/harvest-moon-gbc/rainy-day-money/README.md) for the vanilla location and house-expansion checks.
