#!/usr/bin/env python3
"""Wraps the compiler's output zip into a runnable JAR with a Main-Class manifest."""
import sys
import zipfile

src, dst, main_class = sys.argv[1], sys.argv[2], sys.argv[3]
manifest = f"Manifest-Version: 1.0\r\nMain-Class: {main_class}\r\n\r\n"

with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.writestr("META-INF/MANIFEST.MF", manifest)

print(f"wrote {dst}")
