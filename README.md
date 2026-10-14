# SWF Oyunları

Eski Flash (SWF) oyunlarım. Flash 2020'de kapandığı için oyunlar
[Ruffle](https://ruffle.rs) ile, Flash olmadan çalışır.

| Oyun                                  | Durum                                   |
| ------------------------------------- | --------------------------------------- |
| [Feudalism](oyunlar/feudalism/) 1.041k | Türkçe çeviri hazır; orijinali de duruyor |
| [Feudalism 2](oyunlar/feudalism-2/) 1.02 | Türkçe çeviri hazır; orijinali de duruyor |

## Nasıl oynanır

**Windows'ta (en kolayı):**

1. https://ruffle.rs/downloads adresinden Ruffle'ın Windows sürümünü indirip açın.
2. Bu depodan `oyunlar/feudalism/feudalism-tr.swf` (ya da `oyunlar/feudalism-2/feudalism-2-tr.swf`) dosyasını indirin
   (dosyaya tıklayın, sağ üstteki indirme düğmesine basın).
3. Dosyayı Ruffle ile açın (ya da Ruffle penceresine sürükleyin).

**Tarayıcıda:** depo GitHub Pages ile yayınlanırsa ana sayfa oyun listesini gösterir; oyun
sayfasında Türkçe ile orijinal arasında geçiş düğmesi vardır. Bilgisayarda denemek için
depo kökünde `python3 -m http.server` çalıştırıp http://localhost:8000 adresini açmak yeterlidir
(Ruffle dosyayı doğrudan çift tıklayarak açılan sayfada çalışmaz).

Kontroller: WASD ya da yön tuşları ile hareket, Boşluk ile saldırı, 1-6 iksir ve parşömenler.
Kayıtlar oyunu açan programda (tarayıcı ya da Ruffle) saklanır.

## Klasörler

```
oyunlar/<oyun>/
  <oyun>-orijinal.swf   oyunun dokunulmamış hâli
  <oyun>-tr.swf         Türkçe sürüm (araclar/derle.sh üretir, elle düzenlenmez)
  ceviri/               çeviri tabloları (bkz. GELISTIRME.md)
  index.html            oyun sayfası
ruffle/                 Ruffle 0.7.1, tarayıcıda SWF oynatıcı (MIT / Apache-2.0)
yazitipleri/            Liberation yazı tipleri (SIL OFL 1.1)
araclar/                Türkçe sürümü üreten araçlar
```

Çeviriyi değiştirmek ya da oyunu geliştirmek için: [GELISTIRME.md](GELISTIRME.md).

## Haklar ve teşekkür


- Türkçe sürümlerde internete bağlanan reklam (MochiAds) ve istatistik (MochiBot) çağrıları
  kapatıldı; Feudalism 2'de özel karakter oluşturma, bilgisayardan açıldığı gibi her yerde açıktır.
- **Ruffle**: MIT ya da Apache-2.0 (`ruffle/LICENSE_MIT`, `ruffle/LICENSE_APACHE`).
- **Liberation yazı tipleri**: SIL Open Font License 1.1 (`yazitipleri/OFL-1.1.txt`). Oyundaki
  Arial ve Times New Roman ile aynı ölçülerdedir; ekrandaki yazıların sığması için kullanılır.
- **TeX Gyre yazı tipleri** (Pagella, Bonum, Chorus, Adventor): GUST Font License
  (`araclar/yazitipleri/GUST-Font-License.txt`). Oyuna gömülü yazı tiplerinde olmayan Türkçe
  harfler bunlardan alınır.
- **FFDec** (JPEXS Free Flash Decompiler, GPL-3.0): derleme sırasında indirilir, depoda yoktur.
