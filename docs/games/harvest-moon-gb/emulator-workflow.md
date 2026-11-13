# PyBoy headless workflow (reverse engineering + verification)

PyBoy 2.x used throughout. All scripts run with `games/harvest-moon-gb/tools/` on the path.

## Boot + known states (reproducible)

Deleting the `.rtc`/`.ram` sidecars first is required for these timings (stale sidecars
change boot length). `rtc_lock_experimental` is a **method call**, not a ctor kwarg.

```python
import os, pyboy
for ext in (".rtc", ".ram"):
    p = rom + ext
    if os.path.exists(p): os.remove(p)
pb = pyboy.PyBoy(rom, window="null")
pb.rtc_lock_experimental(True)
pb.tick(1680, True)                          # title appears ~frame 1500
pb.button_press("start"); pb.tick(10, True); pb.button_release("start")
pb.tick(240, True)                           # name-entry grid
```

Known frames (fresh RTC/RAM; work/*.png):

| File | State |
|---|---|
| `seq_*.png` | boot timeline: Victor copyright (~f180) → white (~f360) → Natsume logo → landscape/truck cutscene (~f420–1440) → white fade → **title ~f1500** |
| `pd0.png`/`pd1.png` | title screen (hand pointer animates) |
| `ff_*.png`, `flow2_*.png`, `pb_*.png` | name-entry grid, cursor states, confirm path |
| `tp4_*.png` | name confirmed → "You are a Male? Female?" gender-select screen |
| `flow_at_end_big.png` etc. | 6× upscales of cursor vs END tile |

Full name-entry semantics (cursor WRAM `$cd65/$cd66`, END tile = confirm): see name-entry.md.

## Reading the current bank at runtime

Every MBC3 bank in this ROM is **tagged with its absolute bank number as the first byte**
(see rom-identification.md). A mapping-mode read of `$4000` was observed to track the bank
early in boot, but it is **not reliable** as an MBC3 register read (later probes showed it
doing something else). For ground truth, monkey-patch the mapper (`MBC3.setitem`) and log
bank writes, or use `hook_register`/`register_file.PC`.

## Watchpoints / hooks

`hook_register(bank, addr, cb)` inserts a BRK and calls `cb(context)` on the next instruction
boundary — usable for farcall tracing but heavy; prefer SRAM/WRAM readout + bank reads.

```python
pb.register_memory_watchpoint(0xc0a7, value, ...)  # or use access watchpoints on right addresses
```

## RTC locking

`rtc_lock_experimental=True` freezes the MBC3 RTC so tests are deterministic
(`$b880` time shadow stays 0, scenes don't drift). For playthrough tests, let RTC run and
verify day/season rollover by ticking many frames.

## Practical recipes

```python
# screenshot
pb.screen.image.save("work/shot.png")
# hold a button for N frames
pb.button_press("start"); pb.tick(20); pb.button_release("start")
# read a text record pointer region
pb.memory[0cb53], pb.memory[0cb54]   # script cursor
# dump SRAM
bytes(pb.memory[0xa000:0xa000+0x2000])
```

## Limitations

- PyBoy does **not** do SGB multi-palette on `window="null"` for screenshot capture of the
  4-color layer, and the title screen is blank white in some shots — capture with
  `window="SDL2"`/enabled UI if you need SGB mode palettes.
- `$ff44` VBlank spins and busy-loops behave as on hardware; expect long tick loops for intros.
- CGB "double-speed" games usually need `tick(…, True)` (render=True) to sync SGB; harmless for our purposes.

## Verification harness (to build for the randomizer)

1. Boot ROM with seed P, autoplay N tasks via scripted input, screenshot at fixed points,
   assert no crash & expected dialogue branch taken.
2. For every patched dialogue record, cold-load and transcribe the text box, comparing to expected.
3. Season/day rollover test: fast-forward 40 in-game days (RTC time-accelerated in PyBoy?) or
   manipulate `$b880/$b881/$b882` + `$a140/$a135` in WRAM, tick frames, assert handlers ran.

## Saved states produced by this work (under `work/`, never commit)

| State | Meaning |
|---|---|
| `state_gender.pkl` | Just before the gender-select screen |
| `state_after_petname.pkl` | Pet-name END confirmed (full SRAM template written by now) |
| `state_farm_start.pkl` | Barn-door scene immediately after the intro cutscene |
| `state_first_control.pkl` | Farmhouse interior, Spring 1 6 AM — direction pad + SELECT work; SELECT toggles HUD |

`pb.frame_count` resets to 0 on `load_state`. SRAM/RTC snapshots are produced by
`pb.stop(save=True)` which flushes the `.ram`/`.rtc` files next to the ROM copy.
