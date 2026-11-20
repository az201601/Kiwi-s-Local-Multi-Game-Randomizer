"""Generate the small ROM delta embedded in the standalone Java test app."""

import argparse
from pathlib import Path
import struct

from .shop_art_prototype import build


def delta(original: bytes) -> bytes:
    patched = build(original)
    # Each record is a big-endian offset, length, and replacement bytes.
    records = []
    start = None
    for offset, (old, new) in enumerate(zip(original, patched)):
        if old != new:
            if start is None:
                start = offset
        elif start is not None:
            records.append(struct.pack('>IH', start, offset - start) + patched[start:offset])
            start = None
    if start is not None:
        records.append(struct.pack('>IH', start, len(patched) - start) + patched[start:])
    return struct.pack('>H', len(records)) + b''.join(records)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original_rom', type=Path)
    parser.add_argument('classes_dir', type=Path)
    args = parser.parse_args()
    out = args.classes_dir / 'kiwi' / 'shop-art.dat'
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(delta(args.original_rom.read_bytes()))


if __name__ == '__main__':
    main()
