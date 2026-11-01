#!/usr/bin/env python3
"""Fails the build if an APK would not run on devices with 16 KB memory pages.

Checks, for every 64-bit native library (arm64-v8a / x86_64) in the APK:
  * every ELF PT_LOAD segment is aligned to at least 16 KB (0x4000), and
  * uncompressed (STORED) .so files start on a 16 KB boundary inside the zip.
An APK without native libraries passes trivially.
"""
import struct
import sys
import zipfile

PAGE = 16384
ABIS64 = ("lib/arm64-v8a/", "lib/x86_64/")


def elf_min_load_align(data: bytes):
    if data[:4] != b"\x7fELF" or data[4] != 2:  # 64-bit only
        return None
    little = data[5] == 1
    e = "<" if little else ">"
    phoff = struct.unpack_from(e + "Q", data, 0x20)[0]
    phentsize, phnum = struct.unpack_from(e + "HH", data, 0x36)
    aligns = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type = struct.unpack_from(e + "I", data, off)[0]
        if p_type == 1:  # PT_LOAD
            aligns.append(struct.unpack_from(e + "Q", data, off + 0x30)[0])
    return min(aligns) if aligns else None


def check(apk_path: str) -> bool:
    ok = True
    found = 0
    with zipfile.ZipFile(apk_path) as z, open(apk_path, "rb") as raw:
        for info in z.infolist():
            if not (info.filename.endswith(".so") and info.filename.startswith(ABIS64)):
                continue
            found += 1
            align = elf_min_load_align(z.read(info))
            if align is None or align < PAGE:
                print(f"FAIL  {info.filename}: ELF segment alignment {align} < {PAGE}")
                ok = False
            if info.compress_type == 0:  # STORED
                raw.seek(info.header_offset + 26)
                n, m = struct.unpack("<HH", raw.read(4))
                data_start = info.header_offset + 30 + n + m
                if data_start % PAGE:
                    print(f"FAIL  {info.filename}: stored at offset {data_start}, not 16 KB aligned")
                    ok = False
    if found == 0:
        print(f"{apk_path}: no 64-bit native libraries - nothing to align, OK")
    elif ok:
        print(f"{apk_path}: {found} native libraries, all 16 KB compatible")
    return ok


if __name__ == "__main__":
    results = [check(a) for a in sys.argv[1:]]
    sys.exit(0 if results and all(results) else 1)
