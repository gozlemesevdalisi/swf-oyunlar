#!/usr/bin/env python3
"""Dışa aktarılmış ActionScript dosyalarına çeviri tablolarını uygular.

Kullanım: kod_cevir.py <betikler-klasoru> <ceviri-klasoru> <cikis-klasoru>

- adlar.json: WeaponClass, Armor ve Unit sınıflarındaki `NAME = "..."` satırları.
- kod.json:   dosya başına [eski, yeni] çiftleri (eski metin aynen aranır).

Yalnızca değişen dosyalar çıkış klasörüne aynı yol yapısıyla yazılır; FFDec'in
-importScript komutu bu klasördeki dosyaları oyuna geri derler. Tablodaki her
satır en az bir kez bulunmak zorundadır, bulunamazsa betik hata verir.
"""

import json
import re
import sys
from pathlib import Path

NAME_FILES = {
    "silahlar": "__Packages/WeaponClass.as",
    "zirhlar": "__Packages/Armor.as",
    "birlikler": "__Packages/Unit.as",
}


def load(path: Path) -> dict:
    with path.open(encoding="utf-8") as f:
        data = json.load(f)
    data.pop("_aciklama", None)
    return data


def apply_names(text: str, table: dict, rel: str, errors: list) -> str:
    for old, new in table.items():
        pattern = re.compile(r'(\bNAME = ")' + re.escape(old) + r'(";)')
        text, n = pattern.subn(lambda m: m.group(1) + new.replace('"', '\\"') + m.group(2), text)
        if n == 0:
            errors.append(f"{rel}: NAME bulunamadı: {old}")
    return text


def main() -> int:
    scripts, tables, out = (Path(a) for a in sys.argv[1:4])
    names = load(tables / "adlar.json")
    code = load(tables / "kod.json")
    errors: list[str] = []
    changed: dict[str, str] = {}

    def current(rel: str) -> str:
        if rel not in changed:
            with (scripts / rel).open(encoding="utf-8", newline="") as f:
                changed[rel] = f.read()
        return changed[rel]

    for group, rel in NAME_FILES.items():
        changed[rel] = apply_names(current(rel), names[group], rel, errors)

    for rel, pairs in code.items():
        text = current(rel)
        for old, new in pairs:
            if old not in text:
                errors.append(f"{rel}: bulunamadı: {old[:70]}")
                continue
            text = text.replace(old, new)
        changed[rel] = text

    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1

    for rel, text in changed.items():
        target = out / rel
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("w", encoding="utf-8", newline="") as f:
            f.write(text)
    print(f"{len(changed)} betik dosyası çevrildi.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
