"""Check the mixed-shop prototype in PyBoy using two town-entry save states."""

import argparse
from pathlib import Path

from . import shop_art_prototype as art


def verify(original: bytes, patched: Path, states: tuple[Path, Path]) -> None:
    from pyboy import PyBoy

    # Each case supplies year, day, season, Milk flag, and the stock-mask input.
    cases = (
        ('eve', (0, 0, 0, 0, 0), (art.ORANGE, art.TURNIP, art.POTATO, art.GRAPE, art.TEA)),
        ('eve', (1, 14, 1, 1, 0), (art.ORANGE, art.GRAPE, art.TEA)),
        ('nina', (1, 13, 0, 0, 0), (art.GRASS,)),
        ('nina', (1, 14, 0, 0, 0), (art.APPLE, art.GRASS)),
        ('nina', (1, 13, 0, 1, 0), (art.MILK, art.GRASS)),
        ('nina', (1, 14, 0, 1, 0), (art.APPLE, art.MILK, art.GRASS)),
        ('nina', (1, 14, 1, 1, 0), (art.APPLE, art.MILK, art.TOMATO, art.CORN, art.GRASS)),
        ('nina', (1, 14, 2, 1, 0), (art.APPLE, art.MILK, art.EGGPLANT, art.GRASS)),
        ('nina', (1, 14, 2, 1, 0x1A), (art.APPLE, art.MILK, art.PEANUT, art.GRASS)),
        ('nina', (1, 14, 3, 1, 0), (art.APPLE, art.MILK, art.CARROT)),
        ('nina', (1, 14, 3, 1, 0x34), (art.APPLE, art.MILK, art.BROCCOLI)),
    )
    for cgb, mode, state in zip((True, False), art.MODES, states):
        records, atlas = art._catalog(original, mode)
        pyboy = PyBoy(str(patched), window='null', sound_emulated=False, cgb=cgb)
        pyboy.set_emulation_speed(0)
        try:
            def press(key: str, delay: int = 115, times: int = 1) -> None:
                for _ in range(times):
                    pyboy.button(key, 8)
                    pyboy.tick(delay, True, False)

            def open_counter(shop: str, fields: tuple[int, ...]) -> None:
                with state.open('rb') as source:
                    pyboy.load_state(source)
                if shop == 'eve':
                    press('left')
                mem = pyboy.memory
                mem[0, 0xB882] = 0x12 if shop == 'eve' else 9
                for addr, value in zip((0xB885, 0xB883, 0xB884, 0xB942, 0xB8F4), fields):
                    mem[0, addr] = value
                press('a', times=2)

            mem = pyboy.memory
            for shop, fields, items in cases:
                open_counter(shop, fields)
                assert mem[0xCD33] == len(items), (cgb, shop, fields, tuple(mem[0xCD33:0xCD4D]))
                for i, item in enumerate(items):
                    row, tile = 0xCD34 + i * 5, 0x50 + i * 4
                    addr = 0x9500 + i * 64
                    assert bytes(mem[row:row + 5]) == records[item * 9 + 4:item * 9 + 8] + bytes((tile,))
                    assert bytes(mem[0, addr:addr + 64]) == atlas[item * 64:(item + 1) * 64]
                    assert bytes(mem[0, 0x9943 + i * 3:0x9945 + i * 3]) == bytes((tile, tile + 1))
                    assert bytes(mem[0, 0x9963 + i * 3:0x9965 + i * 3]) == bytes((tile + 2, tile + 3))
                for i in range(len(items), 5):
                    assert bytes(mem[0, 0x9943 + i * 3:0x9945 + i * 3]) == bytes((0x7C, 0x7D))

            def money() -> int:
                return int.from_bytes(bytes(mem[0, 0xB8EF:0xB8F2]), 'little')

            # Audit three purchase paths with controlled starting balances.
            open_counter('nina', (1, 14, 0, 1, 0))
            mem[0, 0xB8EF:0xB8F2] = (1000).to_bytes(3, 'little')
            mem[0, 0xB8ED:0xB8EF] = bytes((10, 100))
            press('a', 80, 4)
            assert (money(), mem[0, 0xB8ED]) == (1000, 10)
            press('a', 90, 3)
            press('b', 104)
            assert (money(), mem[0, 0xB8ED]) == (500, 100)

            open_counter('nina', (1, 14, 0, 1, 0))
            mem[0, 0xB8EF:0xB8F2] = (1000).to_bytes(3, 'little')
            mem[0, 0xB8CC] = 0
            press('right', 94)
            press('a', 99, 5)
            assert (money(), mem[0, 0xB8CC]) == (900, 10)

            open_counter('eve', (0, 0, 0, 0, 0))
            mem[0, 0xB8EF:0xB8F2] = (1000).to_bytes(3, 'little')
            mem[0, 0xB8D2] = 0
            press('right', 94)
            press('a', 99, 5)
            press('left', 99)
            press('a', 124, 2)
            assert (money(), mem[0, 0xB8D2]) == (880, 1)
        finally:
            pyboy.stop(save=False)
    print(f'{len(cases) * 2} stock and artwork checks; six cross-counter purchases passed (CGB + DMG).')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original_rom', type=Path)
    parser.add_argument('prototype_rom', type=Path)
    parser.add_argument('cgb_state', type=Path, help='CGB town selection with Nina’s Flower Shop highlighted')
    parser.add_argument('dmg_state', type=Path, help='DMG town selection with Nina’s Flower Shop highlighted')
    args = parser.parse_args()
    verify(args.original_rom.read_bytes(), args.prototype_rom, (args.cgb_state, args.dmg_state))


if __name__ == '__main__':
    main()
