# Fixed-bank RST and far-dispatch machinery

This is the first nontrivial control-flow/data-format discovery. It explains why a naive recursive disassembly drifted into apparent code at `$0066` and why bank state must be tracked.

All addresses below are CPU addresses in fixed ROM bank 0. Byte offsets and the exact ROM hash are in `initial-profile.md` / `rom-profile.json`.

## RST vectors

| Vector | Bytes | Current decode / role | Confidence |
|---:|---|---|---|
| `$0000` | `C3 60 21` | `JP $2160`; near inline-table dispatcher | High from code flow |
| `$0008` | `C3 6C 21` | `JP $216C`; far inline-table dispatcher | High; runtime observed |
| `$0010` | `C9` | `RET` | Direct decode |
| `$0018` | `C9` | `RET` | Direct decode |
| `$0020` | `C9` | `RET` | Direct decode |
| `$0028` | `C9` | `RET` | Direct decode |
| `$0030` | `C9` | `RET` | Direct decode |
| `$0038` | `85 6F 3E 00 8C 67 C9` | `ADD A,L; LD L,A; LD A,0; ADC A,H; LD H,A; RET` — add 8-bit A to HL | High from direct decode; callers still to find |

The spaces after each short vector are mostly `$FF` fill, but each byte still belongs in the bank ledger.

## RST `$00`: 16-bit inline jump table

Bytes at `$2160` decode as:

```asm
    ld   l, a
    ld   h, $00
    add  hl, hl          ; offset = 2 * index
    ld   e, l
    ld   d, h
    pop  hl              ; consume RST's return address: inline-table base
    add  hl, de
    ld   a, [hli]        ; target low
    ld   h, [hl]         ; target high
    ld   l, a
    jp   hl
```

The `POP HL` consumes the address after the RST instruction. The handler treats bytes immediately after the RST as a table of little-endian 16-bit destinations, uses A as the entry index, then jumps to the selected target. It does **not** return to execute the inline table. A target that returns uses the caller's older return address. This is a computed tail-dispatch, not an ordinary RST call.

## RST `$08`: 24-bit bank/address inline call table

Bytes at `$216C` decode as:

```asm
    ld   e, a
    ld   d, $00
    ld   l, a
    ld   h, $00
    add  hl, hl          ; 2 * index
    add  hl, de          ; 3 * index
    ld   e, l
    ld   d, h
    pop  hl              ; consume RST return address: inline-table base
    add  hl, de
    ld   e, [hl]         ; target low
    inc  hl
    ld   d, [hl]         ; target high
    inc  hl
    ld   a, [hl]         ; target ROM bank
    ld   l, e
    ld   h, d
    ; falls through to the bank-call trampoline at $217F
```

This uses A as an index into three-byte entries laid out as `[address low, address high, ROM bank]`. The routine consumes the RST return address as the inline-data pointer, then falls through to `$217F`; it does not execute the table after dispatch.

## `$217F` bank-call trampoline

At `$217F`, the code saves BC, preserves the requested bank, reads a byte from `$4000`, switches the MBC3 bank through `$2100`, then calls `$2194` (`JP (HL)`). After the selected routine returns, it restores the old bank and returns. `$2100` lies inside the MBC3 ROM-bank-select write range `$2000–$3FFF`.

Every physical bank 1–63 begins with its own bank number (`bank n` begins with byte `n`). This gives the trampoline a way to recover the currently selected bank by reading `$4000`; `rom-profile.json` now records and verifies the prefix for every bank. `$2194` is the shared `JP (HL)` indirection point.

Combined behavior: RST `$08` selects a 3-byte table entry, sets A to its bank and HL to its address, calls it through the trampoline, restores the prior ROM bank, then returns to the RST caller's caller. It is effectively an inline-table far tail-call.

## Example call site: `$0061`

```asm
$0061: LD A,[$C0A7]
$0064: OR A
$0065: RST $08
$0066: ; inline 3-byte entries begin here
```

The startup code writes `$20` to `$C0A7` at `$0202–$0206`. The inline bytes at `$0066 + 3*$20 = $00C6` are `DF 3F 00`, i.e. target `$3FDF`, bank `$00`. At `$3FDF`, the bytes are `3E 08 CD C8 25 C9` (`LD A,$08; CALL $25C8; RET`). The target's gameplay meaning is not yet known.

A contiguous run of 42 plausible triples is present from `$0066` through `$00E3`; `$00E4–$00FF` is `$FF` fill. The 42-entry span is a current table-boundary inference based on the 3-byte decoder and fill transition; all possible indices and callers still need verification.

## Runtime cross-check

PyBoy hooks confirm the example in a fresh CGB-mode run:

- At `$0065`, A is `$20`; the pre-existing caller return address `$0269` is on the stack.
- At `$216C`, after the RST pushes its return address, A remains `$20` and `$0066` is at the top of the stack; the handler then consumes it as the inline-table base.
- At `$217F`, A is `$00` and HL is `$3FDF`; at `$2194`, A is `$01`, the previous bank read from `$4000`.
- Execution reaches `$3FDF`.

The same run also observes other far calls to banked addresses (for example HL `$56EC` with bank `$1D`), confirming that this is active banked code, not merely an unused pattern. The complete table set, all dispatch call sites, and all target routines remain to be enumerated.

## Disassembler consequence

A code walker must treat these RST vectors specially. The bytes after an RST `$00`/`$08` are inline pointer data, not ordinary fall-through instructions; target addresses depend on A, and far dispatch changes MBC3 bank state. Until that modeling exists, output from `gb_disasm.py` is explicitly candidate-only and may decode table bytes as false instructions.
