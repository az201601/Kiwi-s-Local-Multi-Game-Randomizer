# Runtime observations

These observations are tied to the input ROM SHA-256 in `initial-profile.md`. They were collected locally with PyBoy 2.9.0; no gameplay guide, online disassembly, or game database was consulted. PyBoy was used only as an emulator/debugging aid. Runs used a fresh in-memory cartridge-RAM buffer and `stop(save=False)`, so they did not create or modify a save file or the ROM.

Reproduce the boot/mode/LCD hooks with `python -m pip install -r tools/requirements-pyboy.txt`, then run:

```sh
.venv/bin/python tools/pyboy_probe.py 'Harvest Moon GB (U) [C][!].gbc' --mode cgb --frames 120
.venv/bin/python tools/pyboy_probe.py 'Harvest Moon GB (U) [C][!].gbc' --mode dmg --frames 120
.venv/bin/python tools/pyboy_dispatch_trace.py 'Harvest Moon GB (U) [C][!].gbc' --mode cgb --frames 600
.venv/bin/python tools/pyboy_dispatch_trace.py 'Harvest Moon GB (U) [C][!].gbc' --mode dmg --frames 600
.venv/bin/python tools/pyboy_compression_trace.py 'Harvest Moon GB (U) [C][!].gbc' --mode cgb --frames 600 --json reverse-engineering/observations/compression-trace-600-cgb.json
.venv/bin/python tools/pyboy_compression_trace.py 'Harvest Moon GB (U) [C][!].gbc' --mode dmg --frames 600 --json reverse-engineering/observations/compression-trace-600-dmg.json
```

PyBoy is an optional runtime tool; the static ROM scripts and unit tests do not depend on it.

## Boot and opening screen

- PyBoy's startup reaches the cartridge reset entry at CPU `$0150` around emulator frame 64. This is an emulator observation, not a claim about real-time timing.
- A fresh no-input run at frame 600 shows the farm scene and a sign reading “WELCOME” (capture: `observations/boot-600-frames.png`).
- In a separate fresh run, pressing Start from the opening scene leads to the name-entry screen (capture: `observations/after-name-start.png`). The name-entry screen shows a character grid. Its navigation/confirmation behavior has not yet been fully mapped.
- One exploratory input sequence returned from name entry to a logo/`START` screen after Down, stayed there after Right, and returned to name entry after A. Captures are `observations/name-entry-after-down.png`, `observations/name-entry-after-right.png`, and `observations/name-entry-after-a.png`; this is a single-run observation, not yet a complete input-state map.

## Boot-time hardware-mode branch

PyBoy's CGB mode was forced both ways so the same cartridge startup path could be compared. Hooks were installed at fixed-bank addresses `$0150`, `$01E6`, and `$0202`; the hook observations are before the instruction at each address executes. The cartridge bytes were not patched.

| Forced mode | At `$0150`, A | At `$01E6`, `$DFF7` | At `$0202`, `$C0BB` | At `$0202`, `$C0BC` |
|---|---:|---:|---:|---:|
| CGB | `$11` | `$11` | `$01` | `$00` |
| DMG | `$01` | `$01` | `$00` | `$2A` |

The bytes at `$0154–$0159` save the post-boot A value at `$DFF7`; the startup code later reads it and compares it with `$11` before choosing the two writes to `$C0BB/$C0BC`. PyBoy confirms both paths execute and produce the table above. This is strong evidence of a CGB-versus-DMG boot-mode distinction. The *meaning* of `$C0BB` and `$C0BC` is still unknown; they remain provisional state fields, not named gameplay variables.

## Startup memory helpers

Hooks at `$2282`, `$2242`, and `$0178` confirmed the early startup path:

- At the first `$2282` call, registers are `HL=$FF80`, `BC=$007F`, `SP=$DFED`. At the next call, `HL=$DD00`, `BC=$00FF`. The routine at `$2282` XORs A to zero, stores through `[HL+]`, decrements BC, and loops until BC is zero.
- `$2242` copies the ten bytes at `$2250` to `$FF80–$FF89`. At `$0178`, immediately after it returns, HRAM contains `3E C0 E0 46 3E 28 3D 20 FD C9`.
- Those bytes decode as `LD A,$C0; LDH ($46),A; LD A,$28; DEC A; JR NZ,-3; RET`: a routine in HRAM that starts OAM DMA from `$C000` and waits before returning. This matches the code at `$2242/$2250` and is runtime-confirmed by the copied bytes.
- The routine at `$225A` is a safe-LCD-disable helper. PyBoy observed entry with `LY=$1C, LCDC=$91`; the loop exits at `$226D` with `LY=$91`; by `$2272`, `LCDC=$11` (bit 7 cleared). This confirms it waits for line 145/VBlank before disabling the LCD. It saves IE, clears IE bit 0 while waiting, and restores IE before returning.
- Later hooks show `$2282` and `$2242` are called again outside the first reset sequence, with different HL destinations. Therefore `$2282` is a generic zero-fill helper; the two initial clear ranges are not its only uses.

## Banked far-dispatch cross-check

A separate hook run confirmed the RST `$08` dispatcher for the startup path. At `$0065`, A is `$20`; after the RST, the handler at `$216C` sees `$0066` as its inline-table base. The selected three-byte entry at `$00C6` is `DF 3F 00`, producing HL=`$3FDF` and target bank `$00` at `$217F`. At `$2194`, the prior switchable bank read from `$4000` is `$01`, and execution reaches `$3FDF`. This confirms the inline far-pointer path and supports the per-bank ID prefix observation in `rom-map.md`. Other runtime hooks show the same trampoline also dispatches into switchable banks (e.g. bank `$1D`); those destinations still need full trace/meaning.

## RST dispatch sample (600 no-input frames)

`tools/pyboy_dispatch_trace.py` hooks the RST `$00`/`$08` handlers and reads the selected inline table entries from the unchanged ROM. Full machine-readable samples are in `observations/rst-dispatch-600-cgb.json` and `observations/rst-dispatch-600-dmg.json`. Their embedded `mode` fields were checked against the filenames: the CGB file contains the bank `$1D` paths below, and the DMG file contains the bank `$3D` paths; attribution follows the saved JSON metadata.

- RST `$08` at `$0065` uses indices `$10` and `$20` in both modes, selecting `$3FCA` and `$3FDF` in fixed bank 0.
- In CGB mode, the site `$0296` selected bank `$1D:$4001` at index `$10` and `$1D:$56EC` at `$20`; site `$0575` repeatedly selected `$1D:$40A4` and `$1D:$57A5`.
- In DMG mode, those sites instead used indices `$3A/$4A` and bank `$3D`, selecting `$3D:$4001`, `$3D:$56A7`, `$3D:$4097`, and `$3D:$5753` respectively.
- No RST `$00` dispatch was observed in this 600-frame no-input sample. That is not evidence that the dispatcher is unused.

This demonstrates that the boot/opening sequence follows different far-dispatch paths in forced CGB and DMG emulation. The meanings of the selected routines/assets are still unknown; do not label these as palette or screen routines until their code and data are traced.

## Compressed-stream runtime sample

`tools/pyboy_compression_trace.py` hooks the shared fixed-bank routine at `$31A0`, its point after switching to the source bank, and its return. It captured 11 completed calls in the forced-CGB no-input sample and 8 in the forced-DMG sample. All returned to their caller's prior ROM bank. `observations/compression-trace-600-cgb.json` and `observations/compression-trace-600-dmg.json` contain caller/source/destination, frame, length, consumed-input count, and output hash for each call.

The 19 runtime outputs were each decoded directly from the unchanged ROM by `tools/gb_lzss.py`; all output sizes, compressed-byte consumption counts, and SHA-256 values matched. The tested outputs span source banks `$12`, `$1D`, `$20`, `$32`, and `$3D` and destination ranges `$8000–$9FFF`. This confirms one compression format across these sampled paths, but does not establish that there are no other formats. See `data/compression.md` for the exact format and call ledger.

For the CGB trace, VRAM-bank `$FF4F` reads were recorded. DMG hardware does not expose that selector; the tracer records `null` for DMG rather than interpreting an unmapped/open-bus value.

## Caveats and follow-up

A PyBoy hook replaces the instruction at its address with a temporary debugger breakpoint during that run. Hooks were placed only at confirmed instruction addresses, and the ROM file itself was not changed. Hook observations establish that those paths execute under PyBoy; they do not prove every hardware-specific behavior on original Game Boy hardware.

Next runtime work: preserve controlled save states, map the name-entry controls, reach ordinary play, and correlate frame-by-frame behavior with the candidate code and RAM writes. Every new field or mechanic will remain unnamed until the ROM references and gameplay observation support the interpretation.
