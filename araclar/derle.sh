#!/usr/bin/env bash
# Bir oyunun Türkçe sürümünü orijinal SWF'den ve çeviri tablolarından yeniden üretir.
#
# Kullanım (depo kökünden):  araclar/derle.sh oyunlar/feudalism
#
# Gerekenler: Java 17+, Python 3, curl, unzip. FFDec (JPEXS Free Flash Decompiler, GPL-3.0)
# ilk çalıştırmada araclar/.ffdec klasörüne indirilir; depoya girmez.
set -euo pipefail

FFDEC_SURUM="24.1.1"
KOK="$(cd "$(dirname "$0")/.." && pwd)"
OYUN_KLASORU="$(cd "$1" && pwd)"
AD="$(basename "$OYUN_KLASORU")"
ORIJINAL="$OYUN_KLASORU/$AD-orijinal.swf"
CIKTI="$OYUN_KLASORU/$AD-tr.swf"
FFDEC="$KOK/araclar/.ffdec"

if [ ! -f "$FFDEC/ffdec-cli.jar" ]; then
  echo "FFDec $FFDEC_SURUM indiriliyor..."
  mkdir -p "$FFDEC"
  curl -sSL -o "$FFDEC/ffdec.zip" \
    "https://github.com/jindrapetrik/jpexs-decompiler/releases/download/version$FFDEC_SURUM/ffdec_$FFDEC_SURUM.zip"
  unzip -q -o "$FFDEC/ffdec.zip" -d "$FFDEC"
  rm "$FFDEC/ffdec.zip"
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
