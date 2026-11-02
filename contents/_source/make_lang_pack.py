"""Builds the Spanish resource pack for Diorama as a zip.

Usage: python -I make_lang_pack.py <pack_dir> <output_zip>
The pack keeps a single es_es.json; it is copied to every Spanish locale Minecraft offers, so players
with Spanish (Mexico), (Argentina), etc. get it too.
"""
import sys
import zipfile
from pathlib import Path

LOCALES = ["es_es", "es_mx", "es_ar", "es_cl", "es_ec", "es_uy", "es_ve"]
LANG = "assets/diorama/lang"


def main():
    pack, out = Path(sys.argv[1]), Path(sys.argv[2])
    lang = (pack / LANG / "es_es.json").read_bytes()
    out.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.write(pack / "pack.mcmeta", "pack.mcmeta")
        z.write(pack / "pack.png", "pack.png")
        z.write(pack / "diorama-LICENSE.txt", "diorama-LICENSE.txt")
        for loc in LOCALES:
            z.writestr(f"{LANG}/{loc}.json", lang)
    print(out)


if __name__ == "__main__":
    main()
