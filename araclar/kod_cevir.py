#!/usr/bin/env python3
"""Dışa aktarılmış ActionScript dosyalarına çeviri tablolarını uygular.

Kullanım: kod_cevir.py <betikler-klasoru> <ceviri-klasoru> <cikis-klasoru>

- adlar.json: WeaponClass, Armor ve Unit sınıflarındaki `NAME = "..."` satırları.
- kod.json:   dosya başına [eski, yeni] çiftleri (eski metin aynen aranır).

Çeviriden sonra bir denetim yapılır: adlar.json'daki bir eski ad kodun başka bir yerinde
hâlâ tırnak içinde geçiyorsa (ör. `if(this.NAME !== "Shogun Ghost")`), ad değiştiği için o
karşılaştırma bozulur; betik hata verir. Silah türü anahtarı da olan adlar
("Dagger" gibi) adlar.json'daki "_anahtar_adlar" listesine yazılarak denetimden çıkarılır.

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
    # Açıklama ve ayar satırları; "__Packages/..." gibi dosya yolları da alt çizgiyle başlar.
    for key in ("_aciklama", "_anahtar_adlar"):
        data.pop(key, None)
    return data


def check_names(scripts: Path, changed: dict, names: dict, allowed: set, errors: list) -> None:
    """Eski adın NAME satırı dışında kalan her kullanımını bildirir."""
    olds = {old for table in names.values() for old in table} - allowed
    for path in sorted(scripts.rglob("*.as")):
        rel = path.relative_to(scripts).as_posix()
        text = changed.get(rel)
        if text is None:
            text = path.read_text(encoding="utf-8")
        for old in olds:
            quoted = '"' + old.replace('"', '\\"') + '"'
            if quoted not in text:
                continue
            for line in text.splitlines():
                if quoted in line and not re.search(r'\bNAME = ' + re.escape(quoted) + ";", line):
                    errors.append(f"{rel}: çevrilen ad kodda başka yerde de geçiyor: {line.strip()[:90]}")


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
    with (tables / "adlar.json").open(encoding="utf-8") as f:
        allowed = set(json.load(f).get("_anahtar_adlar", []))
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

    if not errors:
        check_names(scripts, changed, names, allowed, errors)
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
