# Code Flow Visualizer — Yol haritası (plan takibi)

Bu dosya **ürün hedeflerini**, **tamamlanan** ve **yapılacak** işleri tek yerde tutar. Sohbet veya sprint planlamasında burayı güncelleyin.

### İşaretler (ikon)

| Anlam | İkon |
|--------|------|
| **Tamamlandı** (kodda var) | ✅ |
| **Yapılmadı** / plan dışı | ❌ |
| **Kısmen** | 🟨 |

> GitHub, Cursor ve çoğu Markdown önizleyicide yukarıdakiler renkli emoji olarak görünür.

---

## 1. Ürün vizyonu (hedef durum)

1. **Anlama:** Java (ve ileride benzer dil) **sözdizimini** güvenilir şekilde çözümleyip kontrol akışını, çağrıları ve yapıları modele dökmek.
2. **Görselleştirme:** Modeli akış diyagramı, bağımlılık, genel bakış ve sınıf-içi çağrı gibi görünümlere **canlı** yansıtmak.
3. **Ters yön (uzun vade):** Görsel ekranda **manuel** olarak düğüm eklemek (ör. `if` bloğu), **bağlantı çizmek**, diyagramı düzenlemek ve bunun **kaynak koda** güvenli biçimde yansıması (iki yönlü senkron veya “koddan diyagrama” tek doğruluk kaynağı seçimi).

Şu an (v1) ağırlık **2** üzerindedir; **3** için mimari ve parser seviyesi büyük iş gerektirir.

---

## 2. Mevcut teknik çerçeve (kısa)

| Alan | Şu an | Risk |
|------|--------|------|
| Parse | Satır / regex tabanlı `JavaSourceParser` | Karmaşık Java yapılarında hata / atlama |
| UI | Swing, tek metin editörü, `Graphics2D` çizim | Görsel düzenleyici için yeniden etkileşim modeli gerekir |

İleride **AST** (ör. JavaParser, Eclipse JDT) geçişi birçok maddenin ön koşuludur.

---

## 3. Tamamlananlar

### 3.1 Çekirdek uygulama
- ✅ Maven proje, Java 17, `com.codeflow.App` girişi
- ✅ Yerleşik editör + klasör izleme (`FileWatcher`, birleşik buffer + `// === dosya ===` ayırıcıları)
- ✅ Canlı yeniden parse (debounce)

### 3.2 Görünümler
- ✅ Akış diyagramı (metot seçimi)
- ✅ `(Tüm metotlar)` ile **aynı sınıfta tüm metotların** üst üste akışı
- ✅ Sınıf bağımlılık grafiği (`DependencyGraph` + çağrı çözümlemesi)
- ✅ Genel bakış (sınıf / alan / metot kartları)
- ✅ **Sınıf içi metot çağrıları** modu (`IntraClassCallRenderer`)
- ✅ Çözüm gezgini (`ProjectExplorerPanel`) — dosya / tip ağacı, izleme modunda göreli yol

### 3.3 Parse / model (mevcut kapsam)
- ✅ `if` / `else if` / `else` dalları → `FlowNode` ağacı
- ✅ `for` / `while` / `do-while` (satır sezgisi; `}` `while` aynı satır; süslü parantez sonraki satırda vb.)
- ✅ `switch` / `case` / `default` → `SWITCH` + kollar
- ✅ `return`, `throw`, atama / çağrı / genel ifade düğümleri (birleşik “işlem” şekli)
- ✅ Enum sabit listesi + genel bakışta **CONSTANTS**
- ✅ `CodeClass` kaynak dosya adı, `rawBody` (DI taraması için)
- ✅ Constructor parametreleri + `@Autowired` / `@Inject` alanları → bağımlılık kenarı (sezgisel)
- ✅ `this.metot()` → aynı sınıf çözümü (iç çağrı grafiği için)

### 3.4 Editör / diyagram UX
- ✅ Akış panelinde **zoom** (+/−, Cmd/Ctrl/Alt + teker, `JScrollPane` üzerinde `consume`)
- ✅ Akışta **birleşik** görsel dil (karar/döngü/switch: elmas; işlemler: tek renk yuvarlak dikdörtgen)

---

## 4. Yapılacaklar — sözdizimi anlama ve görselleştirme

Aşağıdakiler **regex tabanında kısmen** mümkün olsa da üretim kalitesi için çoğunda **AST** önerilir.

### 4.1 Kontrol akışı
- ✅ `try` / `catch` / `finally` ve çoklu `catch` (satır/heuristic; try-with-resources ve iç içe blok sınırlı)
- ✅ `switch` ifadesi: `case … ->` tek satır / blok açılışı; `yield` ayrı düğüm; desen eşleşmesi tam AST değil
- ✅ `break` / `continue` ve `break etiket` / `continue etiket` (etiket metin olarak)
- ✅ `synchronized` blokları (`sync (lock)` elması + gövde)
- ✅ `assert` koşulu (elmas düğümü)

### 4.2 Döngü ve yineleme
- 🟨 `for` / `for-each` — çok satırlı koşul parantezi + `:` ile enhanced for; iç içe `<>` / tam güvenilirlik için AST gerekir (`JavaSourceParser.splitStatements`)
- ✅ `while` koşulunun çok satırlı parantez bloğu (aynı `for` parantez tarayıcısı)
- 🟨 `.forEach(...)` — akışta `forEach` döngü düğümü; zincir satırı / lambda gövdesi sınırlı

### 4.3 Nesne ve çağrı
- 🟨 Noktasız çağrı `metot(` — satır başı ve `;` `}` `)` sonrası sezgi; aynı sınıfta metot adıyla çözüm (`resolveMethodCalls`, boş `targetExpression`)
- ✅ Zincir `a.b().c()` — ardışık `hedef.metot(` eşlemeleri (`extractMethodCalls` noktalı desen)
- 🟨 `super.metot()` ve `BuyukHarfSinif.statik(` — `super` + üst tip; `Type.method` sezgisel (`extractMethodCalls` + `resolveMethodCalls`)
- ❌ Generik / iç sınıf / anonim sınıf sınırları

### 4.4 Diğer yapılar
- ❌ `record` compact constructor / bileşen erişimi ayrıntısı
- ❌ `enum` gövdesinde metot / iç enum (şu an sabit + kalan gövde ayrımı sınırlı)
- ❌ Modül / paket düzeyi özet

---

## 5. Yapılacaklar — görsel düzenleyici ve kod senkronu

Hedef: diyagramda **manuel** `if` ekleme, **ok çekme**, sonra **kod üretimi veya mevcut kodu güvenli güncelleme**.

- ❌ Düğüm paleti (Başla/Bitiş, Karar, İşlem, Döngü, …) ve tuval üzerinde **sürükle-bırak**
- ❌ **Bağlantı çizimi** (kenar seçimi, silme, birleşim noktası)
- ❌ Diyagram ↔ metin **iki yönlü** senkron (çakışma çözümü: ya “kod tek kaynak” ya “diyagram tek kaynak”)
- ❌ Kod üretimi şablonları (ör. yeni `if` gövdesi için blok iskeleti)
- ❌ Geri al / yinele (undo/redo)
- ❌ Çok dosya / sekme editörü (gezgin ile birebir dosya açma)
- ❌ Diyagram **dışa aktarma** (PNG, SVG, PlantUML, Mermaid)

---

## 6. Yapılacaklar — editör ve masaüstü UX

- ✅ Yakınlaştırma (akış görünümü) — *mevcut*
- ❌ **Pinch-to-zoom** (trackpad; Swing’de yerel jest — genelde JNI / platform API veya harici kütüphane)
- ❌ **Sürükle-bırak** ile dosya / klasör açma (`.java` veya proje kökü)
- ❌ Satır içi hata / uyarı işaretleme (parse hatalarında)
- 🟨 Sözdizimi vurgulama — *kısmen: tek renk metin; tam lexer / renklendirme yok*
- ❌ Tam **syntax highlighting** (Lexer tabanlı veya editör bileşeni değişimi)
- ✅ Satır numaraları — *mevcut*
- ❌ Arama / değiştir, çoklu imleç

---

## 7. Önerilen ekler (mantıklı sırayla)

1. **AST geçişi** (JavaParser veya JDT) — tüm “syntax listesi” maddelerinin temeli.
2. **Parse hata raporu** — hangi satırda model üretilemedi (kullanıcı güveni).
3. **Otomatik test** — altın örnek `.java` dosyaları + beklenen düğüm sayısı / türü.
4. **Ayarlar dosyası** — zoom, son izlenen klasör, pencere boyutu.
5. **CLI / headless** — `java -jar … --input src --export out.png` (CI / dokümantasyon).
6. **Lisans / üçüncü parti** — AST kütüphanesi eklenirse bağımlılık ve dağıtım notu.

---

## 8. Bu dosyayı nasıl kullanmalı?

- Yeni özellik konuşulunca önce burada **madde açın**; bitince `❌` → `✅` (veya kısmen ise `🟨`) yapın ve mümkünse **ilgili sınıf / dosya** adını parantez içinde not edin.
- `PROJE_BAGLAMI.md` “ne bu uygulama” özeti için; **bu dosya** “nereye gidiyoruz ve ne kaldı” için.

---

*Son güncelleme: 2026-05-03 — 4.2 / 4.3 kısmi parser iyileştirmeleri eklendi.*
