# Name entry screen ("Name?") — verified 2026-11-13

The first screen after the title-screen Start press. Verified end-to-end headless with
PyBoy (boot → title → grid → type 4 chars → confirm → gender-pick screen).

## Screen layout

- Prompt `Name?` + name preview at the top (4 glyph slots).
- Grid of selectable glyphs, 6 rows × up to 13 columns:
  - Row 0: `A–M` (13)
  - Row 1: `N–Z` + `Σ` (Sigma, U+03A3-like glyph)
  - Row 2: `a–m`
  - Row 3: `n–z`
  - Row 4: `0–9 ! ? ♥`
  - Row 5: `[ ] … _ ↑ ← ↓ → — ~ SP` (11 symbols) + **END tile** (green/tan boxed
    glyph, bottom-right, spans ~2 cells) — this is the confirm button.
- Name length limit: **4 characters** (further A presses after 4 are swallowed).
- Grid is navigated with the D-pad; the game wraps in both axes:
  - Down from row 5 wraps to row 0 (6th Down from row 0).
  - Left/Right wrap within a row; Right from col 11 wraps to col 0 and Left from
    col 0 wraps to col 11 on row 5.

## Cursor mechanics (WRAM)

| Address | Role | Notes |
|---|---|---|
| `$cd65` | grid column (0–12) | authoritative for input handling; END = col **11** on row 5 |
| `$cd66` | grid row (0–5) | |
| `$c000` | derived cursor Y = `$29 + $10 * row` | recomputed by the game on input; writing it directly gets overwritten |
| `$c001` | derived cursor X = `$23 + $8 * col` | same |

Verified: `cd65=11, cd66=5` + A → confirm. `cd65=12` is out of range for the symbol row;
A there deletes the last typed character (one-char backspace). `A` on the `[` cell
(row 5 col 0) is a no-op.

The on-screen cursor is a dark `▽` drawn **above** the targeted tile (not on it); its
screen position matches `$c000`/`$c001`.

## Buttons

| Button | Behavior |
|---|---|
| A | type highlighted glyph; on END tile (row 5 col 11) → **confirm name, advance** to the "You are a Male? Female?" gender-selection screen (default `Male`, `▶` pointer) |
| B | no-op |
| Start | no-op |
| Select | untested |
| D-pad | move cursor (wraps) |

Pure-button path to confirm (verified twice, deterministic):
`Down ×5` (row 0 → row 5 col 0), `Left` (wrap → END), `A` → gender screen.

## Boot-to-grid recipe (fresh state)

```python
import os, pyboy
for ext in (".rtc", ".ram"):
    p = rom + ext
    if os.path.exists(p): os.remove(p)     # stale saves change boot length
pb = pyboy.PyBoy(rom, window="null")
pb.rtc_lock_experimental(True)              # method, not ctor kwarg
pb.tick(1680, True)                        # title screen appears ~frame 1500
pb.button_press("start"); pb.tick(10, True); pb.button_release("start")
pb.tick(240, True)                         # name grid
```

Input timing used for verification: 4-frame press + ~48-frame gap per D-pad step.

Screen flow around it:
`Victor copyright → Natsume logo → landscape/truck-gate cutscene → white fade → title`
(~frame 1500) `→ Start → white flash → Name grid` (~240–300 ticks after Start)
`→ END + A → "You are a Male? Female?"`.
Start during the intro is swallowed (intro is not skippable).

## Name storage

Typed-name WRAM buffer not yet located (candidates in `$d310–$d3a0` churn but no stable
4/10-byte buffer found; animation traffic dominates that region). SRAM hero name at
`$a100-$a10f` (10 chars + padding) matches the JP build; the name is presumably committed
to SRAM later in the opening flow, not on this screen.

## Glyph codes written to the name buffer

Rows 0–1 store bold A–Z (`$00-$19`), rows 2–3 regular A–Z (`$1A-$33`), row 4 digits
(`$34-$3D`; `!`=`$40`, `?`=`$41`, `♥`=`$42`), row 5 symbols (`[`=`$3E` `]`=`$3F` `…`=`$43`
`_`=`$46` `↑`=`$47` `←`=`$48` `↓`=`$49` `→`=`$4A` `—`=`$44` `~`=`$45`; the `SP` cell is
**not typable**). Empty slot sentinel = `$D8`. Stored bytes == VM font indices, and the
intro renders name bytes by direct font lookup (name bytes bypass the VM opcode layer).

`SP` cell (row 5 col 10) does nothing when A is pressed. `A` on out-of-range col 12
backspaces one character (and see the flow-breakage note in new-game-flow.md).

Untyped slots (trailing) are filled with `$D8`, drawn as an underscore-style blank in the
name box / grid header. Names may be fewer than 4 characters; the END confirm accepts them
and the SRAM slot keeps `$D8` padding.

**Transition swallow**: the gender-pick and pet-preference screens swallow the first `A`
issued while they are still sliding in (~48 f is not enough spacing from the previous
screen's confirm). Budget two `A` presses per screen or wait ≥100 f; otherwise the harness
silently desyncs into typing at whatever cell the cursor is on.

## Notes for the randomizer

- The `Σ` glyph in row 1 is encodable with the text VM (`text-encoding.md`).
- If the randomizer ever patches name-entry behavior (e.g. default names), the three
  WRAM bytes `$cd65/$cd66` + the END-tile A path are the entire confirm surface.
- Screenshots of the verified flow: `work/pb_*.png` (button path), `work/tp4_*.png`
  (memory-poke path), `work/tp2_*.png` (END = backspace observation at col 12).
