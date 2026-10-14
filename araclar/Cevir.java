import com.eclipsesource.json.Json;
import com.eclipsesource.json.JsonObject;
import com.eclipsesource.json.JsonValue;
import com.jpexs.decompiler.flash.SWF;
import com.jpexs.decompiler.flash.tags.DefineEditTextTag;
import com.jpexs.decompiler.flash.tags.base.FontTag;
import com.jpexs.decompiler.flash.tags.base.MissingCharacterHandler;
import com.jpexs.decompiler.flash.tags.base.StaticTextTag;
import com.jpexs.decompiler.flash.tags.base.TextTag;
import com.jpexs.decompiler.flash.types.GLYPHENTRY;
import com.jpexs.decompiler.flash.types.TEXTRECORD;
import java.awt.Font;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SWF dosyasındaki sabit yazıları (DefineText) ve ekran kutularını (DefineEditText) çeviri
 * tablolarına göre değiştirir. Gömülü yazı tiplerinde olmayan harfleri benzer ücretsiz yazı
 * tiplerinden ekler.
 *
 * <p>Kullanım: java -cp "ffdec/lib/*" Cevir.java girdi.swf cikti.swf ceviri-klasoru depo-koku
 */
public class Cevir {

  private static final Map<Integer, Font> SOURCE_FONTS = new HashMap<>();
  private static final List<String> ERRORS = new ArrayList<>();
  private static final Map<String, Map<Integer, Font>> DEVICE_FONTS = new HashMap<>();

  public static void main(String[] args) throws Exception {
    File input = new File(args[0]);
    File output = new File(args[1]);
    File tables = new File(args[2]);
    File root = new File(args[3]);

    SWF swf;
    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(input))) {
      swf = new SWF(in, false);
    }

    JsonObject fonts = read(new File(tables, "yazitipleri.json"));
    for (JsonObject.Member m : fonts) {
      if (m.getName().startsWith("_")) continue;
      File file = new File(root, m.getValue().asObject().getString("kaynak", ""));
      Font font = Font.createFont(Font.TRUETYPE_FONT, file);
      // FFDec bazı yerlerde yazı tipini adıyla arar; adın bulunabilmesi için kaydedilir.
      java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
      SOURCE_FONTS.put(Integer.parseInt(m.getName()), font);
    }

    // Gerekli harfler önceden eklenir; metin değiştirilirken eksik harf kalmamalıdır.
    String[][] device = {
      {"sans", "Sans"}, {"serif", "Serif"},
    };
    String[] styles = {"Regular", "Bold", "Italic", "BoldItalic"};
    for (String[] d : device) {
      Map<Integer, Font> byStyle = new HashMap<>();
      for (int st = 0; st < 4; st++) {
        File f = new File(root, "yazitipleri/Liberation" + d[1] + "-" + styles[st] + ".ttf");
        byStyle.put(st, Font.createFont(Font.TRUETYPE_FONT, f));
      }
      DEVICE_FONTS.put(d[0], byStyle);
    }

    MissingCharacterHandler addGlyph = new MissingCharacterHandler() {
      @Override
      public boolean handle(TextTag text, FontTag font, char c) {
        ERRORS.add("yazı tipi " + font.getCharacterId() + " içinde '" + c + "' harfi yok");
        return false;
      }
    };

    JsonObject statik = read(new File(tables, "sabit.json"));
    JsonObject kutular = read(new File(tables, "kutular.json"));
    prepareFonts(swf, statik, kutular, fonts);

    int statics = 0;
    for (JsonObject.Member m : statik) {
      if (m.getName().startsWith("_")) continue;
      int id = Integer.parseInt(m.getName());
      JsonObject entry = m.getValue().asObject();
      List<String> parts = new ArrayList<>();
      for (JsonValue v : entry.get("parcalar").asArray()) parts.add(v.asString());
      boolean center = "orta".equals(entry.getString("hiza", ""));
      double scale = entry.getDouble("boyut", 1);
      if (replaceStatic(swf, id, parts, center, scale, addGlyph)) statics++;
    }

    int edits = 0;
    for (JsonObject.Member m : kutular) {
      if (m.getName().startsWith("_")) continue;
      int id = Integer.parseInt(m.getName());
      JsonValue v = m.getValue();
      String text = v.isString() ? v.asString() : v.asObject().getString("metin", "");
      double scale = v.isString() ? 1 : v.asObject().getDouble("boyut", 1);
      JsonValue wrap = v.isString() ? null : v.asObject().get("kaydir");
      int originalXmax = swf.getCharacter(id) instanceof DefineEditTextTag t ? t.bounds.Xmax : 0;
      if (replaceEdit(swf, id, text)) {
        if (wrap != null) {
          // Kodun doldurduğu kutularda kaydırma kararı elle verilir; kutu orijinal genişliğinde kalır.
          DefineEditTextTag tag = (DefineEditTextTag) swf.getCharacter(id);
          tag.wordWrap = wrap.asBoolean();
          tag.bounds.Xmax = originalXmax;
        }
        if (scale != 1) {
          DefineEditTextTag tag = (DefineEditTextTag) swf.getCharacter(id);
          tag.fontHeight = (int) Math.round(tag.fontHeight * scale);
        }
        edits++;
      }
    }

    if (!ERRORS.isEmpty()) {
      ERRORS.forEach(e -> System.err.println("HATA: " + e));
      System.exit(1);
    }
    try (OutputStream out = new FileOutputStream(output)) {
      swf.saveTo(out);
    }
    System.out.println(statics + " sabit yazı ve " + edits + " ekran kutusu çevrildi.");
  }

  /**
   * Çevirilerde geçen ve gömülü yazı tipinde olmayan harfleri benzer yazı tipinden ekler, sonra
   * bütün harflerin genişliklerini o yazı tipinden yazar. Orijinal yazı tiplerinde genişlik
   * tablosu yoktur; tablo olmadan yeni metnin harf aralıkları tutmaz.
   */
  private static void prepareFonts(SWF swf, JsonObject statik, JsonObject kutular, JsonObject fonts) {
    Map<Integer, StringBuilder> needed = new HashMap<>();
    // Kodun yazı yazdığı, yazı tipi gömülü kutular için yazitipleri.json'daki "ekle" harfleri.
    for (JsonObject.Member m : fonts) {
      if (m.getName().startsWith("_")) continue;
      String extra = m.getValue().asObject().getString("ekle", "");
      if (!extra.isEmpty()) {
        needed.computeIfAbsent(Integer.parseInt(m.getName()), k -> new StringBuilder()).append(extra);
      }
    }
    // Yazı tipi gömülü ekran kutularının çevirileri.
    for (JsonObject.Member m : kutular) {
      if (m.getName().startsWith("_")) continue;
      if (!(swf.getCharacter(Integer.parseInt(m.getName())) instanceof DefineEditTextTag tag)) continue;
      if (!tag.useOutlines) continue;
      JsonValue v = m.getValue();
      String text = v.isString() ? v.asString() : v.asObject().getString("metin", "");
      needed.computeIfAbsent(tag.fontId, k -> new StringBuilder()).append(text.replaceAll("<[^>]+>", ""));
    }
    for (JsonObject.Member m : statik) {
      if (m.getName().startsWith("_")) continue;
      if (!(swf.getCharacter(Integer.parseInt(m.getName())) instanceof StaticTextTag tag)) continue;
      StringBuilder chars = new StringBuilder();
      for (JsonValue v : m.getValue().asObject().get("parcalar").asArray()) chars.append(v.asString());
      for (int fontId : tag.getFontIds()) {
        needed.computeIfAbsent(fontId, k -> new StringBuilder()).append(chars);
      }
    }
    for (Map.Entry<Integer, StringBuilder> e : needed.entrySet()) {
      FontTag font = (FontTag) swf.getCharacter(e.getKey());
      Font source = SOURCE_FONTS.get(e.getKey());
      if (source == null) {
        ERRORS.add("yazı tipi " + e.getKey() + " için yazitipleri.json'da kaynak yok");
        continue;
      }
      // Kaynak dosya zaten kalın/italik olduğu için Java'nın yapay kalınlaştırması kapatılır.
      boolean bold = font.isBold();
      boolean italic = font.isItalic();
      boolean hadLayout = font.hasLayout();
      font.setBold(false);
      font.setItalic(false);
      e.getValue().chars().distinct().forEach(ch -> {
        char c = (char) ch;
        if (font.containsChar(c)) return;
        if (!source.canDisplay(c) || !font.addCharacter(c, source)) {
          ERRORS.add("yazı tipi " + e.getKey() + " için '" + c + "' harfi eklenemedi");
        }
      });
      // Genişlik tablosu olan yazı tiplerinde eski harflerin genişlikleri korunur; yeni harflerinki
      // eklenirken yazılır.
      if (!hadLayout) {
        font.setAdvanceValues(source);
        font.setHasLayout(true);
      }
      font.setBold(bold);
      font.setItalic(italic);
      font.setModified(true);
    }
  }

  private static JsonObject read(File file) throws IOException {
    try (Reader r = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      return Json.parse(r).asObject();
    }
  }

  /** Sabit yazının her parçasını sırayla yenisiyle değiştirir; biçim bilgisini korur. */
  private static boolean replaceStatic(
      SWF swf, int id, List<String> parts, boolean center, double scale,
      MissingCharacterHandler handler)
      throws Exception {
    if (!(swf.getCharacter(id) instanceof StaticTextTag tag)) {
      ERRORS.add(id + " sabit yazı değil");
      return false;
    }
    List<double[]> oldLines = center ? lineExtents(swf, tag) : null;

    String formatted = tag.getFormattedText(false).text;
    StringBuilder result = new StringBuilder();
    int part = 0;
    int i = 0;
    while (i < formatted.length()) {
      char c = formatted.charAt(i);
      if (c == '[') {
        int end = blockEnd(formatted, i);
        result.append(cleanBlock(formatted.substring(i, end + 1), scale));
        i = end + 1;
        continue;
      }
      int end = i;
      while (end < formatted.length() && formatted.charAt(end) != '[') {
        end += formatted.charAt(end) == '\\' ? 2 : 1;
      }
      if (part >= parts.size()) {
        ERRORS.add(id + ": orijinalde " + parts.size() + " parçadan fazlası var");
        return false;
      }
      result.append(escape(parts.get(part++)));
      i = end;
    }
    if (part != parts.size()) {
      ERRORS.add(id + ": orijinalde " + part + " parça var, çeviride " + parts.size());
      return false;
    }
    if (!tag.setFormattedText(handler, result.toString(), null)) {
      ERRORS.add(id + ": yazı ayarlanamadı");
      return false;
    }
    if (center) centerLines(swf, tag, oldLines);
    tag.updateTextBounds();
    tag.setModified(true);
    return true;
  }

  /** Köşeli parantez bloğunun sonunu bulur; tırnak içindeki ']' karakterlerini atlar. */
  private static int blockEnd(String s, int start) {
    boolean quoted = false;
    for (int i = start + 1; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\') {
        i++;
      } else if (c == '"') {
        quoted = !quoted;
      } else if (c == ']' && !quoted) {
        return i;
      }
    }
    return s.length() - 1;
  }

  /**
   * Eski harflere ait harf aralığı ayarlarını çıkarır (yeni metinde anlamları yoktur) ve istenirse
   * yazı boyunu küçültür.
   */
  private static String cleanBlock(String block, double scale) {
    StringBuilder b = new StringBuilder();
    for (String line : block.split("\n", -1)) {
      if (line.startsWith("spacing ") || line.startsWith("spacingpair ")) continue;
      if (scale != 1 && line.startsWith("height ")) {
        line = "height " + Math.round(Integer.parseInt(line.substring(7).trim()) * scale);
      }
      if (b.length() > 0) b.append('\n');
      b.append(line);
    }
    return b.toString();
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
  }

  /** Her satırın başlangıç x'ini ve genişliğini (twip) döndürür. */
  private static List<double[]> lineExtents(SWF swf, StaticTextTag tag) {
    List<double[]> lines = new ArrayList<>();
    int x = 0;
    for (TEXTRECORD r : tag.textRecords) {
      if (r.styleFlagsHasXOffset) x = r.xOffset;
      double width = 0;
      for (GLYPHENTRY g : r.glyphEntries) width += g.glyphAdvance;
      if (!r.glyphEntries.isEmpty()) lines.add(new double[] {x, width});
      x += (int) width;
    }
    return lines;
  }

  /** Yeni satırları, eski satırların orta noktasına göre yerleştirir. */
  private static void centerLines(SWF swf, StaticTextTag tag, List<double[]> oldLines) {
    int line = 0;
    for (TEXTRECORD r : tag.textRecords) {
      if (r.glyphEntries.isEmpty() || line >= oldLines.size()) continue;
      double width = 0;
      for (GLYPHENTRY g : r.glyphEntries) width += g.glyphAdvance;
      double[] old = oldLines.get(line++);
      r.styleFlagsHasXOffset = true;
      r.xOffset = (int) Math.round(old[0] + old[1] / 2 - width / 2);
    }
  }

  /**
   * Kutunun genişliğini orijinal metnin en uzun satırına indirir. Orijinal metin elle kısa
   * satırlara bölündüğü için kutular çoğu zaman görünen balondan geniştir; kelime kaydırma
   * açılınca yazının balondan taşmaması için bu gerekir. Ölçüm, oynatıcının kullandığı
   * Liberation yazı tipleriyle yapılır.
   */
  private static void fitWidth(SWF swf, DefineEditTextTag tag, String original, boolean bold) {
    FontTag font = (FontTag) swf.getCharacter(tag.fontId);
    String name = font == null ? "" : font.getFontNameIntag();
    boolean serif = name.contains("serif") || name.contains("Times") || name.contains("Book");
    // Oynatıcı düz kutularda yazı tipinin kalın bayrağını kullanmaz; kalınlık yalnızca HTML'den gelir.
    int style = bold ? Font.BOLD : Font.PLAIN;
    Font measure = DEVICE_FONTS.get(serif ? "serif" : "sans").get(style).deriveFont(tag.fontHeight / 20f);
    java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
    double widest = 0;
    for (String line : original.split("[\\r\\n]+")) {
      widest = Math.max(widest, measure.getStringBounds(line.stripTrailing(), frc).getWidth());
    }
    // Flash kutusunda her iki yanda 2 piksel boşluk vardır; ölçü farkları için biraz pay bırakılır.
    int width = (int) Math.ceil((widest * 1.03 + 6) * 20);
    if (widest > 0 && tag.bounds.Xmin + width < tag.bounds.Xmax) {
      tag.bounds.Xmax = tag.bounds.Xmin + width;
    }
  }

  /** Ekran kutusunun yazısını değiştirir; çok satırlı kutularda kelime kaydırmayı açar. */
  private static boolean replaceEdit(SWF swf, int id, String value) {
    if (!(swf.getCharacter(id) instanceof DefineEditTextTag tag)) {
      ERRORS.add(id + " ekran kutusu değil");
      return false;
    }
    if (value.startsWith("html:")) {
      String original = tag.initialText == null ? "" : tag.initialText;
      tag.html = true;
      tag.initialText = value.substring(5);
      // Elle satır kırılmış HTML kutuları kaydırılır; tek satırlıklar olduğu gibi kalır.
      if (original.contains("<sbr")) {
        fitWidth(swf, tag, original.replaceAll("<sbr\\s*/>", "\n").replaceAll("<[^>]+>", ""), original.contains("<b>"));
        tag.wordWrap = true;
      }
    } else {
      String text = value.replace("\n", "\r");
      if (tag.html) {
        ERRORS.add(id + ": HTML kutusuna düz metin verilmiş");
        return false;
      }
      // Orijinalinde elle satır kırılan kutular yazıyı kendisi kaydırır; tek satırlık etiketler
      // ise yazıya göre genişler (Türkçe karşılık çoğu zaman daha uzundur).
      String original = tag.initialText == null ? "" : tag.initialText.strip();
      boolean paragraph = original.contains("\r") || original.contains("\n") || text.contains("\r");
      tag.initialText = text;
      if (tag.wordWrap) {
        // Zaten kendisi kaydıran kutu olduğu gibi kalır.
      } else if (paragraph && tag.multiline) {
        fitWidth(swf, tag, original, false);
        tag.wordWrap = true;
      } else {
        tag.wordWrap = false;
        tag.autoSize = true;
      }
    }
    tag.hasText = true;
    tag.setModified(true);
    return true;
  }
}
