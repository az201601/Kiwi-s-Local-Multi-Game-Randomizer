# Compressed stream format 1 — opening-path graphics

## Evidence and status

The fixed-bank routine at bank `$00:$31A0` is a length-prefixed, flag-byte LZSS-style decompressor. It was hooked during separate 600-frame, no-input PyBoy runs in forced CGB and DMG modes. The ROM is the unchanged supplied dump; each run used fresh in-memory cartridge RAM and `stop(save=False)`.

- CGB sample: `observations/compression-trace-600-cgb.json`, 11 completed decompressions.
- DMG sample: `observations/compression-trace-600-dmg.json`, 8 completed decompressions.
- Trace command: `.venv/bin/python tools/pyboy_compression_trace.py 'Harvest Moon GB (U) [C][!].gbc' --mode cgb --frames 600 --json reverse-engineering/observations/compression-trace-600-cgb.json` (replace `cgb` with `dmg` for the other run).
- For every recorded call, `tools/gb_lzss.py`'s offline output matches the runtime output length, compressed-byte consumption, and SHA-256. The 19 comparisons run in `test_every_recorded_opening_trace_output_matches_offline_decoder`.

This proves the format for these streams only; it is **not yet established as the ROM's only compression format**. The zero-distance/back-reference case is not in the sampled data and is rejected by the Python decoder because its result may depend on pre-existing destination memory.

## Observed calls

Banks and addresses below are hexadecimal. Output end addresses are exclusive. Runtime caller sites identify the 3-byte `CALL $31A0`; full frame counts, output hashes, bank restoration, and first output bytes are in the JSON traces.

| Mode | Caller | Source stream | Destination | Output bytes | Compressed bytes consumed |
|---|---:|---:|---:|---:|---:|
| CGB | `$20:$6D55` | `$20:$4C14` | `$9800` | `$0400` | 127 |
| CGB | `$1D:$570A` | `$12:$7A58` | `$9000` | `$0800` | 1,153 |
| CGB | `$1D:$5715` | `$12:$7ED9` | `$9800` | `$0400` | 227 |
| CGB | `$1D:$5720` | `$12:$7ED9` | `$9C00` | `$0400` | 227 |
| CGB | `$20:$6D29` | `$20:$49B7` | `$9800` | `$0400` | 145 |
| CGB | `$20:$6D34` | `$20:$49B7` | `$9C00` | `$0400` | 145 |
| CGB | `$1D:$401F` | `$1D:$5FE3` | `$8800` | `$0800` | 729 |
| CGB | `$1D:$402A` | `$1D:$5A94` | `$9000` | `$0800` | 1,359 |
| CGB | `$1D:$4035` | `$1D:$62BC` | `$8000` | `$0300` | 234 |
| CGB | `$1D:$4040` | `$1D:$63A6` | `$9800` | `$0400` | 377 |
| CGB | `$1D:$404B` | `$1D:$651F` | `$9C00` | `$0400` | 346 |
| DMG | `$3D:$56B8` | `$32:$7A68` | `$9000` | `$0800` | 1,153 |
| DMG | `$3D:$56C3` | `$32:$7EE9` | `$9800` | `$0400` | 229 |
| DMG | `$3D:$56CE` | `$32:$7EE9` | `$9C00` | `$0400` | 229 |
| DMG | `$3D:$4012` | `$3D:$5E7E` | `$8800` | `$0800` | 729 |
| DMG | `$3D:$401D` | `$3D:$58D0` | `$9000` | `$0800` | 1,454 |
| DMG | `$3D:$4028` | `$3D:$6157` | `$8000` | `$0300` | 223 |
| DMG | `$3D:$4033` | `$3D:$6236` | `$9800` | `$0400` | 377 |
| DMG | `$3D:$403E` | `$3D:$63AF` | `$9C00` | `$0400` | 346 |

The CGB callers in bank `$20` were observed writing to VRAM bank 1; callers in bank `$1D` wrote to VRAM bank 0, as read from `$FF4F`. DMG does not have a usable VRAM-bank selector, so its trace leaves that field null rather than interpreting an unmapped `$FF4F` read. The grayscale 2bpp preview `observations/cgb-opening-tiles-9000-grayscale.png` renders the 128 tiles decoded from `$12:$7A58`; visible patterns include the Natsume wordmark and copyright lettering. This is a visual classification of tile patterns only—the exact tilemap references and screen timing remain unverified.

The decoded stream extents also establish contiguous compressed-data runs (all ranges are half-open):

- Bank `$1D`: `$5A94–$6679`, five adjacent streams loaded by the CGB opening path.
- Bank `$3D`: `$58D0–$6509`, five adjacent streams loaded by the DMG opening path.
- Bank `$12`: `$7A58–$7FBC`, three CGB calls over two adjacent streams (`$7A58` ends exactly where `$7ED9` begins; the second is reused for two destinations).
- Bank `$32`: `$7A68–$7FD2`, three DMG calls over two adjacent streams (`$7A68` ends exactly where `$7EE9` begins; the second is reused twice).
- Bank `$20`: two separate CGB streams, `$49B7–$4A48` and `$4C14–$4C93`; gaps between them are not yet classified.

The CGB routine at `$1D:$56EC` makes three calls using source bank `$12`: `$7A58` to `$9000`, then the same `$7ED9` stream to `$9800` and `$9C00`. It saves the currently selected bank as read at `$4000`, selects the source bank via the MBC3 write at `$2100`, and restores the prior bank before returning; PyBoy observed restoration to `$1D`. The DMG variant uses bank `$3D` code, bank `$32` streams `$7A68/$7EE9`, and restores `$3D`.

## Byte format

1. The first two bytes are the decoded size, little-endian.
2. The rest is organized as flag byte, then up to eight tokens. Flag bits are consumed least-significant bit first.
3. Flag bit `1`: the next source byte is a literal output byte.
4. Flag bit `0`: read a little-endian 16-bit token:
   - `distance = token >> 4` (12 bits, backward distance)
   - `length = (token & 0x0F) + 3` (3–18 bytes)
   - Copy `length` bytes from the already decoded output at `current_output_length - distance`, allowing overlapping copies.
5. In the observed streams, a back-reference that reaches before the start of output supplies zero bytes until the reference reaches decoded data. This behavior was compared against each runtime output hash. The game implements it via a 256-byte boundary and every sampled destination starts at `$xx00`; behavior with an unaligned output start is unverified.
6. The game stops at the declared output length and restores the previous ROM bank.

`tools/gb_lzss.py` implements the confirmed subset. It reports compressed-byte consumption, literal/back-reference counts, and a SHA-256 without writing an asset unless `--output` is explicitly supplied. `tools/pyboy_compression_trace.py` records the runtime caller, source bank/address, destination, bank transitions, and output digest. `tools/gb_tile_preview.py 'Harvest Moon GB (U) [C][!].gbc' --bank 0x12 --address 0x7A58 --output reverse-engineering/observations/cgb-opening-tiles-9000-grayscale.png` reproduces the grayscale, tile-only image. The preview intentionally does not apply map data or CGB palettes. These tools do not write to the ROM or save data.

## Remaining questions

- Are other banks/call sites using this token format, or are there additional formats?
- Are zero-distance tokens used, and if so, what destination-memory initialization do callers guarantee?
- What do the decoded graphics, tiles, maps, and VRAM-bank variants depict?
- Which additional decompressor call paths become active after input/gameplay, and which streams are loaded dynamically from tables rather than direct call arguments?
