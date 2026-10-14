# Geliştirme

## Türkçe sürümü yeniden üretmek

Depo kökünden:

```sh
araclar/derle.sh oyunlar/feudalism
araclar/derle.sh oyunlar/feudalism-2
```

Gerekenler: Java 17+, Python 3, curl, unzip. FFDec ilk çalıştırmada `araclar/.ffdec/` içine
indirilir (git'e girmez). Betik `oyunlar/feudalism/feudalism-tr.swf` dosyasını baştan yazar;
bu dosya elle düzenlenmez, her değişiklik çeviri tablolarından yapılır.

Betiğin adımları:

1. Orijinal SWF'deki ActionScript 2 kodları FFDec ile dışa aktarılır.
2. `araclar/kod_cevir.py` koddaki ekranda görünen metinleri çevirir.
3. Çevrilen kod dosyaları FFDec ile oyuna geri derlenir.
4. `araclar/Cevir.java` sabit yazıları ve ekran kutularını değiştirir, gömülü yazı tiplerine
   eksik Türkçe harfleri ekler, resim olarak çizilmiş yazıları yeniden çizer.

## Çeviri tabloları (`oyunlar/feudalism/ceviri/`)

| Dosya              | Ne içerir                                                                     |
| ------------------ | ----------------------------------------------------------------------------- |
| `adlar.json`       | Silah, zırh ve birlik adları (yalnızca `NAME = "..."` satırlarına uygulanır)  |
| `kod.json`         | Koddaki diğer metinler: dosya başına `[eski, yeni]` çiftleri                  |
| `sabit.json`       | Menü düğmeleri, ipuçları gibi sabit yazılar (kimlik numarasıyla)              |
| `kutular.json`     | Öğretici, diyaloglar, şehir başlıkları gibi ekran kutuları (kimlik numarasıyla) |
| `yazitipleri.json` | Eksik harflerin hangi benzer yazı tipinden alınacağı                          |
| `sekiller.json`    | Resim olarak çizilmiş yazılar ("You died..." → "Öldün...")                    |

Kurallar:

- `kod.json`'daki eski metin dosyada aynen bulunmalıdır; bulunamazsa derleme hata verir.
- Şehir adları ve silah türleri (`"Sword"`, `"Bow"` gibi) kodda anahtar olarak kullanılır;
  bunlar çevrilmez, yoksa oyun bozulur. Ekranda görünen adlar ayrı yerlerdedir.
- `sabit.json`'da her yazının parça sayısı orijinaldekiyle aynı olmalıdır. Sığmayan yazı için
  `"boyut": 0.9` gibi küçültme, ortalanması gereken için `"hiza": "orta"` kullanılır.
- `kutular.json`'da `\n` yeni paragraf demektir. Orijinali birkaç satırlık olan kutular yazıyı
  kendisi kaydırır; tek satırlık etiketler yazıya göre genişler.

- Kodun sonradan doldurduğu kutular için `kutular.json`'da `"kaydir": false` kaydırmayı kapatır.
- Kodun yazı yazdığı ve yazı tipi oyuna gömülü kutularda (Feudalism 2'deki şehir başlığı gibi)
  kullanılan yazı tipine, `yazitipleri.json`'da `"ekle"` ile Türkçe harfler önceden eklenir.

Bir metnin kimlik numarasını bulmak için orijinal SWF'yi FFDec ile açıp (`araclar/.ffdec/ffdec.sh`)
"texts" bölümüne bakılır; numara, metnin adındaki sayıdır.

## Oyunun kodunu değiştirmek

Oyunun kodu ActionScript 2'dir ve yaklaşık 12.800 satırdır. Önemli sınıflar:

| Sınıf          | Görevi                                        |
| -------------- | --------------------------------------------- |
| `WeaponClass`  | Silahlar: hasar, hız, kritik, fiyat           |
| `Armor`        | Zırhlar ve kalkanlar                          |
| `Unit`         | Toplanabilen birlikler ve düşmanlar           |
| `Town`         | Şehirler: ulus, satılan eşyalar, birlikler    |
| `Cities`       | Şehir ekranı: binalar, görevler, liman, ahır  |
| `Creature`, `Soldier`, `Player` | Savaştaki davranışlar        |
| `Quest`        | Görevler                                      |
| `WorldMap`     | Dünya haritası                                |

Kod değişikliği de çeviri gibi `kod.json` üzerinden yapılır: değiştirilecek satır "eski",
yeni hâli "yeni" olarak yazılır. Böylece her değişiklik orijinale göre kayıtlı kalır ve
Türkçe sürüm her zaman baştan üretilebilir.

Bilinen tuzak: FFDec bazı iç içe `switch`'leri `loop0:` etiketiyle yazar ama kendi derleyicisi
bu etiketi okuyamaz ("CURLY_CLOSE expected but COLON found"). Feudalism 2'nin `Skill.as`
dosyasında etiket kaldırılıp `break loop0;` yerine fonksiyonun son satırı yazıldı (`kod.json`).
Değiştirilmeyen dosyalar yeniden derlenmediği için başka yerde sorun çıkarmaz.

## Denemek

Depo kökünde `python3 -m http.server` çalıştırıp http://localhost:8000/oyunlar/feudalism/
adresini açın. Kayıtlar tarayıcıda SWF dosyasının adına göre saklanır; Türkçe ve orijinal
sürümlerin kayıtları ayrıdır.
