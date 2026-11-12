#!/usr/bin/env python3
"""Decode the first confirmed Harvest Moon GBC compressed-stream format.

Format understanding is evidence-backed for the two bank-$12 streams used by
the CGB startup path. Other stream families and rare back-reference edge cases
must be tested before treating this as universal.
"""

from __future__ import annotations

import argparse
import hashlib
from dataclasses import dataclass
from pathlib import Path

BANK_SIZE = 0x4000


@dataclass(frozen=True)
class DecodeResult:
    data: bytes
    output_size: int
    consumed: int
    literals: int
    backreferences: int
    zero_prefix_bytes: int


def decode_stream(stream: bytes) -> DecodeResult:
    """Decode one length-prefixed LZSS stream.

    Header: 16-bit little-endian decoded output size.
    Flags: groups of eight, least-significant bit first; 1 means literal.
    Back-reference token: little-endian 16-bit value; high 12 bits are the
    backward distance and low 4 bits encode (copy length - 3).

    A reference before the beginning of decoded output yields zero bytes until
    it reaches the output base. The observed game routine implements this using
    the next 256-byte boundary and callers sampled so far use page-aligned
    destinations; unaligned output starts remain unverified. The known streams
    have no zero-distance references; those are rejected because the original
    routine would read pre-existing destination memory, which depends on caller.
    """
    if len(stream) < 2:
        raise ValueError("stream is too short for its 16-bit output-size header")
    output_size = stream[0] | (stream[1] << 8)
    cursor = 2
    output = bytearray()
    flag_byte = 0
    flag_bits_left = 0
    literals = 0
    backreferences = 0
    zero_prefix_bytes = 0

    def take_byte(what: str) -> int:
        nonlocal cursor
        if cursor >= len(stream):
            raise ValueError(f"truncated stream while reading {what} at source offset 0x{cursor:X}")
        value = stream[cursor]
        cursor += 1
        return value

    while len(output) < output_size:
        if flag_bits_left == 0:
            flag_byte = take_byte("flag byte")
            flag_bits_left = 8

        is_literal = flag_byte & 1
        flag_byte >>= 1
        flag_bits_left -= 1

        if is_literal:
            output.append(take_byte("literal"))
            literals += 1
            continue

        low = take_byte("back-reference low byte")
        high = take_byte("back-reference high byte")
        token = low | (high << 8)
        distance = token >> 4
        length = (token & 0x0F) + 3
        if distance == 0:
            raise ValueError(
                f"zero-distance back-reference at source offset 0x{cursor - 2:X} "
                "depends on destination memory before decode"
            )
        if length > output_size - len(output):
            raise ValueError(
                f"back-reference of {length} bytes at output offset 0x{len(output):X} "
                f"would exceed declared size 0x{output_size:X}"
            )

        backreferences += 1
        for _ in range(length):
            source = len(output) - distance
            if source < 0:
                output.append(0)
                zero_prefix_bytes += 1
            else:
                output.append(output[source])

    return DecodeResult(bytes(output), output_size, cursor, literals,
                        backreferences, zero_prefix_bytes)


def read_cpu_rom(rom: bytes, bank: int, address: int, size: int) -> bytes:
    """Read `size` bytes from a fixed/banked CPU ROM window."""
    result = bytearray()
    for index in range(size):
        current = address + index
        if current < 0x4000:
            offset = current
        elif current < 0x8000 and 1 <= bank < (len(rom) + BANK_SIZE - 1) // BANK_SIZE:
            offset = bank * BANK_SIZE + current - 0x4000
        else:
            raise ValueError(f"stream read leaves ROM window at CPU address 0x{current:04X}")
        if offset >= len(rom):
            raise ValueError(f"stream read leaves ROM file at offset 0x{offset:X}")
        result.append(rom[offset])
    return bytes(result)


def decode_rom_stream(rom: bytes, bank: int, address: int) -> DecodeResult:
    """Read a length-prefixed stream from a ROM bank and decode it."""
    header = read_cpu_rom(rom, bank, address, 2)
    output_size = int.from_bytes(header, "little")
    # Compressed data should not be larger than output plus simple control
    # overhead for these assets. Keep a hard boundary within the same bank.
    max_stream = 0x8000 - address
    stream = read_cpu_rom(rom, bank, address, max_stream)
    # Decode from a bounded view and report the consumed prefix; the ROM bank
    # remainder is allowed after the stream, so bytes after `consumed` are not
    # interpreted as part of the asset.
    result = decode_stream(stream)
    if result.output_size != output_size:
        raise AssertionError("internal output-size mismatch")
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path, help="local Game Boy ROM")
    parser.add_argument("--bank", type=int, required=True, help="source physical ROM bank")
    parser.add_argument("--address", type=lambda s: int(s, 0), required=True,
                        help="CPU address of the length-prefixed stream, e.g. 0x7A58")
    parser.add_argument("--output", type=Path, help="optional path for decoded bytes")
    args = parser.parse_args()

    rom = args.rom.read_bytes()
    result = decode_rom_stream(rom, args.bank, args.address)
    print(f"source: bank {args.bank} at ${args.address:04X}")
    print(f"decoded: {result.output_size} bytes; compressed bytes consumed: {result.consumed}")
    print(f"tokens: {result.literals} literals, {result.backreferences} back-references, "
          f"{result.zero_prefix_bytes} zero-prefix bytes")
    print(f"decoded SHA-256: {hashlib.sha256(result.data).hexdigest()}")
    print(f"first 32 bytes: {result.data[:32].hex(' ')}")
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes(result.data)
        print(f"decoded bytes written to {args.output}")


if __name__ == "__main__":
    main()
