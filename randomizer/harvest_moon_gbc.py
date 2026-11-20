"""Patch Grandpa's Rainy Day Money in the supported Harvest Moon GB ROM."""

import argparse
import hashlib
from pathlib import Path
import random
import secrets

from . import seed_shop

SUPPORTED_SHA256 = "54fe95af5ded33631db84b6ba4ecc4a86f9f7528c9c56ca678150a3246d49623"
MIN_GOLD = 1
MAX_GOLD = 1000
VANILLA_STARTING_MONEY = 500

# File offsets, not CPU addresses. This ROM contains two copies of both the
# drawer handler (LD BC, $03E8 immediately before the add) and the receipt.
PAYOUT_OFFSETS = (0x99D7, 0x89944)
STARTING_MONEY_OFFSETS = ((0x752C4, 0x752C9), (0xF527F, 0xF5284))
RECEIPT_OFFSETS = (0x1C718, 0x9C6B5)
RECEIPT_GLYPHS = {**{str(n): 0x34 + n for n in range(10)}, "G": 0x06, "!": 0x40, " ": 0xEF}
GLOBAL_CHECKSUM = slice(0x14E, 0x150)


def roll_rainy_day_money(seed: str) -> int:
    """Roll one amount per seed, independent of gameplay or save state."""
    return random.Random(seed).randint(MIN_GOLD, MAX_GOLD)


def roll_starting_money(seed: str) -> int:
    """Use a separate seed stream so the drawer reward never changes."""
    return random.Random(f"harvest-moon-gbc/starting-money:{seed}").randint(1, VANILLA_STARTING_MONEY)


def patch_rainy_day_money(rom: bytes, amount: int, starting_money: int = VANILLA_STARTING_MONEY,
                         *, seed: str = "", randomize_seed_stock: bool = False,
                         shuffle_seed_slots: bool = False) -> bytes:
    """Return a patched copy of the verified input ROM."""
    if type(amount) is not int or not MIN_GOLD <= amount <= MAX_GOLD:
        raise ValueError(f"amount must be an integer from {MIN_GOLD} to {MAX_GOLD}G")
    if type(starting_money) is not int or not 1 <= starting_money <= VANILLA_STARTING_MONEY:
        raise ValueError("starting money must be an integer from 1 to 500G")
    if hashlib.sha256(rom).hexdigest() != SUPPORTED_SHA256:
        raise ValueError("unsupported ROM; expected an unmodified Harvest Moon GB (USA) (SGB Enhanced) ROM")

    patched = bytearray(rom)
    payout = amount.to_bytes(2, "little")
    # Keep the original field width and text control bytes in place.
    receipt = bytes(RECEIPT_GLYPHS[c] for c in f"{amount}G!!".ljust(7))
    for offset in PAYOUT_OFFSETS:
        patched[offset : offset + len(payout)] = payout
    for offset in RECEIPT_OFFSETS:
        patched[offset : offset + len(receipt)] = receipt

    for low, high in STARTING_MONEY_OFFSETS:
        patched[low], patched[high] = starting_money.to_bytes(2, "little")

    seed_shop.patch(patched, seed, randomize_seed_stock, shuffle_seed_slots)
    checksum = (sum(patched) - sum(patched[GLOBAL_CHECKSUM])) & 0xFFFF
    patched[GLOBAL_CHECKSUM] = checksum.to_bytes(2, "big")
    return bytes(patched)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("rom", type=Path, help="unmodified input ROM")
    parser.add_argument("output", type=Path, help="new ROM path; must not already exist")
    parser.add_argument("--seed", help="reproduce a previous roll; defaults to a fresh seed")
    parser.add_argument("--randomize-rainy-day-money", action=argparse.BooleanOptionalAction, default=True,
                        help="roll 1 to 1,000G for the drawer (default: enabled)")
    parser.add_argument("--randomize-starting-money", action="store_true",
                        help="roll 1 to 500G for new games instead of the default 500G")
    parser.add_argument("--randomize-seed-stock", action="store_true", help="redistribute crop seeds across seasons")
    parser.add_argument("--shuffle-seed-slots", action="store_true", help="shuffle the seed counter order each season")
    args = parser.parse_args(argv)

    seed = args.seed if args.seed is not None else secrets.token_hex(16)
    amount = roll_rainy_day_money(seed) if args.randomize_rainy_day_money else MAX_GOLD
    starting_money = roll_starting_money(seed) if args.randomize_starting_money else VANILLA_STARTING_MONEY
    try:
        patched = patch_rainy_day_money(args.rom.read_bytes(), amount, starting_money, seed=seed,
                                        randomize_seed_stock=args.randomize_seed_stock,
                                        shuffle_seed_slots=args.shuffle_seed_slots)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        with args.output.open("xb") as output_file:
            output_file.write(patched)
    except (OSError, ValueError) as exc:
        parser.exit(1, f"Error: {exc}\n")

    print(f"Wrote: {args.output}")
    print(f"Seed: {seed!r}")
    print(f"Grandpa's Rainy Day Money: {amount}G")
    print(f"Starting money: {starting_money}G")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
