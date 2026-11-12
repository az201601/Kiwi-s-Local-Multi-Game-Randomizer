# Harvest Moon GBC — full offline reverse-engineering

## Scope

This is a full-ROM investigation of the supplied `Harvest Moon GB (U) [C][!].gbc`, not a feature-only study for the randomizer. The goal is to account for and understand the entire cartridge: executable code, bank-switching, interrupts, memory and save layouts, all data formats, graphics, maps, text, audio, game rules, schedules, events, menus, and any unused or unknown regions. Findings that are not yet understood stay explicitly marked unknown; they are not silently skipped or guessed.

No online research is being used. Analysis is based on the supplied ROM and locally available tooling. The original ROM is an input artifact and must not be edited. Record its hash before any analysis so all findings remain tied to the exact dump.

## What “full” means for this project

- Every ROM bank and byte range gets a ledger entry, including padding-looking, duplicate, and apparently unused ranges.
- Executable bytes are disassembled with correct Game Boy CPU instruction boundaries and bank context. Data is identified and decoded using evidence rather than blindly interpreted as opcodes.
- Control-flow targets, interrupt vectors, bank-selection writes, indirect dispatches, and code/data references are cross-referenced. Unresolved targets are tracked.
- Structured data is catalogued and decoded: graphics and compression, maps, tile attributes, palettes, text, item and shop tables, NPC/event data, music and sound, and any other formats found.
- RAM, VRAM, OAM, HRAM, cartridge RAM, and save data are mapped as far as the ROM permits; field meanings are tied to read/write sites and observed logic.
- Mechanics and content are documented from code and data: calendar/time, crops, tools, stamina, inventory, shops, money, animals, NPCs, events, progression, menus, and edge cases—not just randomizer-relevant features.
- Each conclusion is labelled as directly observed in code/data, strongly inferred, or still unverified. Contradictions and gaps remain visible.

A linear “disassemble every byte as an instruction” dump would be misleading because ROMs mix code, tables, text, and compressed assets. The deliverable is a complete annotated ROM map plus bank-aware disassembly and decoded data, with every range accounted for.

## Evidence and progress

See `initial-profile.md` for the first ROM-header and bootstrap inspection, and `runtime-observations.md` for PyBoy cross-checks. The profile is only a starting point; it does not claim that bank contents or mechanics are understood yet.

Current verified progress:

- Exact input identity, header checks, and hashes for all 64 banks are recorded.
- A dependency-free LR35902 decoder covers all base and CB-prefixed opcode encodings; its opcode-length and bank-mapping tests pass.
- A **candidate** fixed-bank control-flow sweep exists at `disassembly/bank00-entry-candidates.asm`. It is deliberately not treated as ground truth: inline data, dynamic dispatch, and mapper state still require analysis.
- PyBoy 2.9.0 confirms the reset entry, boot-mode branch behavior, HRAM DMA routine copy, VBlank-safe LCD disable, and an inline-table far dispatch. These runtime observations are reproducible with the optional probe script and use no saved game data.
- All 63 switchable banks begin with a byte equal to their bank number. This matches the bank-call trampoline's read from `$4000` when saving/restoring the active ROM bank.
- The fixed-bank routine at `$31A0` is confirmed as a length-prefixed LZSS-style decoder for 19 opening-path decompressor calls (16 distinct source streams) sampled in forced CGB and DMG PyBoy runs. All offline decoded lengths, compressed-byte counts, and output hashes match their runtime captures; a 2bpp tile preview visibly contains Natsume/copyright patterns, but exact map usage is unresolved. See `data/compression.md`, `disassembly/bank00-compression.asm`, and `observations/compression-trace-600-*.json`. This classifies only the observed stream family, not all ROM compression or the meanings of those assets.
- The byte-range classification of the ROM and the gameplay/mechanics inventory are still largely unstarted. No candidate sweep or partial data-format result is being treated as a full disassembly.

Planned working artifacts:

- `rom-map.md` — bank/range ledger, ownership, classification, confidence, and unresolved bytes.
- `disassembly/` — annotated bank-aware code listing and symbols.
- `memory-map.md` — hardware, RAM, save data, and game-state fields with references.
- `data/` — decoded tables/assets and format notes.
- `mechanics/` — evidence-backed descriptions of all systems and content.
- `questions.md` — unresolved hypotheses and the next evidence needed.

## Method

1. Freeze and verify the input identity; audit header, bank count, checksums, and per-bank hashes.
2. Trace reset and interrupt entry points, establish cartridge banking and hardware initialization, and build a symbol/reference ledger.
3. Recursively disassemble reachable code per bank, then investigate indirect dispatches and any unvisited code candidates.
4. Classify every non-code region and identify its encoding before decoding it.
5. Trace each mechanic from entry points through state reads/writes and relevant data tables; document complete paths and edge cases.
6. Run consistency checks so mapped ranges cover the ROM without overlaps or unexplained gaps, and so all claims point back to ROM offsets and banks.

Static analysis alone cannot prove every runtime behavior. Anything that needs execution or a controlled game-state experiment will be marked accordingly rather than presented as certain.
