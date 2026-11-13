# ROM drop folder (git-ignored)

Put your cartridges here. Nothing in this folder is committed - `.gitignore`
excludes everything except this README - so the repository stays free of
copyrighted data.

Expected layout (the name only has to match `ROM=` on the make line):

```
romfs/
  harvest_moon_gbc/
    hm.gbc          <- Harvest Moon GB (GBC), your own dump
```

Then:

```bash
make info          # header dump: title, MBC, CGB flag, checksums
make disasm        # writes out/hmgbc/  (banks/*.asm, main.asm, link.script)
make verify        # rebuilds out/hmgbc/ and compares to the ROM byte-for-byte
```

`make verify` printing `OK: rebuilt N bytes identical to original` is the
checkpoint that says the code/data split is trustworthy: every byte the
disassembler claimed was an instruction really is one, and everything else
came through as data unchanged.

**Why a dump of your own:** the analysis is a static read of the image; it
never needs the network. But distributing the image itself is not something
this repo does.
