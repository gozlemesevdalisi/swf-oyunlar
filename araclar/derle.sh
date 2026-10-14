#!/usr/bin/env bash
# Bir oyunun Türkçe sürümünü orijinal SWF'den ve çeviri tablolarından yeniden üretir.
#
# Kullanım (depo kökünden):  araclar/derle.sh oyunlar/feudalism
#
# Gerekenler: Java 17+, Python 3, curl, unzip, sha256sum. FFDec (JPEXS Free Flash Decompiler, GPL-3.0)
# ilk çalıştırmada araclar/.ffdec klasörüne indirilir; depoya girmez.
set -euo pipefail

if [ $# -ne 1 ] || [ ! -f "$1/$(basename "$1")-orijinal.swf" ]; then
  echo "Kullanım: araclar/derle.sh oyunlar/<oyun>   (klasörde <oyun>-orijinal.swf olmalı)" >&2
  exit 2
fi

FFDEC_SURUM="24.1.1"
# İndirilen dosyanın bozulmadığını ya da değiştirilmediğini doğrulamak için SHA-256 özeti.
FFDEC_SHA256="dfa3a36575fd62bccc19750707a2f9d3a01bfc183763a061f07cb4f8cf1e38ed"
KOK="$(cd "$(dirname "$0")/.." && pwd)"
OYUN_KLASORU="$(cd "$1" && pwd)"
AD="$(basename "$OYUN_KLASORU")"
ORIJINAL="$OYUN_KLASORU/$AD-orijinal.swf"
CIKTI="$OYUN_KLASORU/$AD-tr.swf"
FFDEC="$KOK/araclar/.ffdec"

if [ ! -f "$FFDEC/.tamam" ]; then
  echo "FFDec $FFDEC_SURUM indiriliyor..."
  rm -rf "$FFDEC"
  mkdir -p "$FFDEC"
  curl -fsSL -o "$FFDEC/ffdec.zip" \
    "https://github.com/jindrapetrik/jpexs-decompiler/releases/download/version$FFDEC_SURUM/ffdec_$FFDEC_SURUM.zip"
  if ! echo "$FFDEC_SHA256  $FFDEC/ffdec.zip" | sha256sum -c --quiet -; then
    echo "HATA: indirilen FFDec dosyası beklenen dosya değil; derleme durduruldu." >&2
    rm -f "$FFDEC/ffdec.zip"
    exit 1
  fi
  unzip -q -o "$FFDEC/ffdec.zip" -d "$FFDEC"
  rm "$FFDEC/ffdec.zip"
  # Açma yarıda kalırsa bir sonraki çalıştırmada baştan indirilsin diye en son işaretlenir.
  touch "$FFDEC/.tamam"
fi

GECICI="$(mktemp -d)"
trap 'rm -rf "$GECICI"' EXIT
JAVA="java -Djava.awt.headless=true -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8"
sessiz() { grep -v -e "JAVA_TOOL_OPTIONS" -e "class is long" -e "generateTraits" -e "^Exported script" || true; }

echo "1/4 Kodlar dışa aktarılıyor..."
$JAVA -jar "$FFDEC/ffdec-cli.jar" -export script "$GECICI/betikler" "$ORIJINAL" 2>&1 | sessiz

echo "2/4 Koddaki metinler çevriliyor..."
python3 -I "$KOK/araclar/kod_cevir.py" "$GECICI/betikler/scripts" "$OYUN_KLASORU/ceviri" "$GECICI/cevrilen"

echo "3/4 Kodlar yeniden derleniyor..."
$JAVA -jar "$FFDEC/ffdec-cli.jar" -importScript "$ORIJINAL" "$GECICI/kod.swf" "$GECICI/cevrilen" 2>&1 | sessiz

echo "4/4 Yazılar ve yazı tipleri çevriliyor..."
$JAVA -cp "$FFDEC/lib/*" "$KOK/araclar/Cevir.java" "$GECICI/kod.swf" "$CIKTI" "$OYUN_KLASORU/ceviri" "$KOK" 2>&1 | sessiz

echo "Hazır: ${CIKTI#"$KOK"/}"
