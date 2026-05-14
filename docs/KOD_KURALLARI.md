# Kod kuralları ve mimari ilkeler

Bu belge, **Code Flow Visualizer** deposunda kod yazarken (insan veya AI) uyulması gereken standartları tanımlar. Amaç: sürdürülebilir, okunabilir, Java ekosistemine uygun ve modüler bir kod tabanı.

İlgili belgeler: [PROJE_BAGLAMI.md](PROJE_BAGLAMI.md), [MIGRATION_PLAN.md](MIGRATION_PLAN.md).

---

## 1. Genel ilkeler

- **Tek sorumluluk:** Her sınıf ve metot tek bir iş yapar; “god class” oluşturmayın.
- **Açık bağımlılık yönü:** Üst katman alt katmana bağımlıdır; tersi yok (`core` → `swing-ui` yasak).
- **Küçük ve odaklı diff:** Bir PR / değişiklik tek özellik veya tek hata düzeltmesi içermeli; gereksiz formatlama veya alakasız refactor eklemeyin.
- **Çalışan ana dal:** `mvn clean verify` kırılmadan birleştirin.
- **Yorum değil kod:** Davranışı açıklayan gereksiz yorum yazmayın; karmaşık iş kuralı veya kamu API’si için kısa Javadoc yeterli.

---

## 2. Maven modül sınırları

| Modül | İçerir | İçermez |
|--------|--------|---------|
| `codeflow-core` | Model, parser, dosya izleme | Swing, AWT, migration UI |
| `codeflow-migration-core` | Migration workspace, golden, test runner, karşılaştırma servisleri | Swing, örnek uygulama verisi |
| `codeflow-swing-ui` | Swing bileşenleri, renderer’lar, migration paneli | `main`, örnek `resources` |
| `codeflow-visualizer-app` | `App`, `MainFrame`, demo kaynakları | Yeniden kullanılabilir kütüphane mantığı (buraya taşımayın) |

**Kurallar:**

- Yeni özellik önce **hangi modüle ait** olduğuna karar verin; şüphede `core` veya `migration-core` tercih edin.
- Modüller arası döngüsel bağımlılık **yasak**.
- Harici kütüphane eklemeden önce: JDK / mevcut modül ile çözülebilir mi? Eklenirse yalnızca ihtiyaç duyan modülün `pom.xml`’ine yazın.
- `groupId` / `artifactId`: `com.codeflow` altında tutarlı kalın.

---

## 3. Depo ve dosya yapısı

Kod **“bulduğum yere”** değil, **modül + paket kuralına** göre konur. Kök dizinde `src/` **olmaz**; tüm Java kaynağı Maven modüllerinin altındadır.

### 3.1 Kök dizin (yalnızca bunlar)

```
CodeFlowVisualizer/
├── pom.xml                    # parent POM (modül listesi)
├── README.md
├── .gitignore
├── docs/                      # Yalnızca Markdown belgeler
├── examples/                  # Çalıştırılmayan örnek veri / workspace şablonları
├── codeflow-core/
├── codeflow-migration-core/
├── codeflow-swing-ui/
└── codeflow-visualizer-app/
```

| Kök altı | Ne konur | Ne konmaz |
|----------|----------|-----------|
| `docs/` | `*.md` belgeler | `.java`, `pom.xml`, derlenen kod |
| `examples/` | Örnek COBOL/JSON, workspace şablonu, kısa README | Uygulama sınıfları, unit test |
| Modül klasörleri | `pom.xml` + `src/` ağacı | Belgeler (modül README istisna: tek satırlık not) |

### 3.2 Standart Maven ağacı (her modül)

```
<codeflow-*>/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/              # Üretim kodu (paket = klasör yolu)
    │   └── resources/         # classpath kaynakları (yalnızca gerektiğinde)
    └── test/
        ├── java/              # JUnit testleri (paket aynası: com.codeflow…)
        └── resources/           # test fixture (golden örnek, küçük .java metinleri)
```

- **Tek public sınıf = tek dosya**; dosya adı sınıf adıyla aynı (`FlowComparisonService.java`).
- Test sınıfı: `FooTest.java`, paket `com.codeflow…` ile **aynı** veya `…test` alt paketi (modülde tutarlı tek stil seçin).

### 3.3 Modül başına Java paket haritası

**`codeflow-core`** — `src/main/java/com/codeflow/`

| Alt paket / dosya | İçerik |
|-------------------|--------|
| `CodeFlow.java` | Kütüphane kök işareti (prefs vb.) |
| `model/` | `CodeClass`, `FlowNode`, `DependencyGraph`, … |
| `parser/` | `JavaSourceParser`, `FileWatcher` |

**`codeflow-migration-core`** — `src/main/java/com/codeflow/migration/`

| Alt paket | İçerik |
|-----------|--------|
| `MigrationPaths.java` | Workspace yol sabitleri (paket kökünde tek util) |
| `model/` | Record/DTO: `GoldenCase`, `MigrationWorkspaceState`, … |
| `golden/` | Manifest/case okuma |
| `flow/` | Eski/yeni akış karşılaştırma servisleri |
| `workspace/` | Workspace tarama ve orkestrasyon |
| `test/` | `MavenTestRunner` gibi dış süreç çalıştırıcılar (JUnit değil) |

**`codeflow-swing-ui`** — `src/main/java/com/codeflow/`

| Alt paket | İçerik |
|-----------|--------|
| `ui/` | Genel Swing: `DiagramPanel`, `*Renderer`, editör, gezgin |
| `ui/migration/` | Yalnızca migration ekranları (`MigrationDashboardPanel`) |
| `util/` | Swing tercihleri (`AppPreferences`) |

**`codeflow-visualizer-app`** — `src/main/java/com/codeflow/app/`

| Alt paket | İçerik |
|-----------|--------|
| `App.java` | `main` giriş |
| `MainFrame.java` | Pencere orkestrasyonu (ince tutulur) |
| `sample/` | Demo yükleyici (`ExampleSources`) — örnek veri app’e özel |

`src/main/resources/` **yalnızca bu modülde:** paketlenmiş demo (`examples/ecommerce-cart.java`).

### 3.4 Yeni dosya nereye? (karar ağacı)

1. **Swing / pencere / buton / çizim?** → `codeflow-swing-ui` → `ui/` veya `ui/migration/`
2. **COBOL→Java workspace, golden, test koşumu?** → `codeflow-migration-core` → uygun alt paket
3. **Java parse, akış modeli, dosya izleme (genel)?** → `codeflow-core`
4. **Sadece uygulamayı açan / demo metin?** → `codeflow-visualizer-app`
5. **Belge veya örnek workspace şablonu?** → `docs/` veya `examples/` (`.java` üretim kodu değil)

Şüphe: **iş kuralı ve I/O → core veya migration-core**; **gösterme → swing-ui**.

### 3.5 `examples/` vs uygulama kaynakları

| Konum | Amaç |
|-------|------|
| `examples/migration-workspace-sample/` | Kullanıcının “Workspace aç” ile deneyeceği **harici** klasör şablonu (`legacy/`, `.codeflow/golden/`) |
| `codeflow-visualizer-app/.../resources/examples/` | JAR içine gömülü **editör demo** metni |
| `src/test/resources/` (ileride) | Otomatik test fixture’ları |

Örnek COBOL/JSON **üretim koduna taşınmaz**; kütüphane modülleri örnek dosyaya bağımlı olmaz.

### 3.6 Yasaklar (dosya yapısı)

- Kök `src/main/java/…` (eski tek modül kalıntısı) **açmayın**
- `com.codeflow.ui` sınıfını `codeflow-core` veya `app` modülüne koymayın
- Aynı sınıfın kopyasını iki modülde tutmayın — ortak kod **alt modüle** taşınır
- Renderer’ı `parser/` altına, parser’ı `ui/` altına **karıştırmayın**
- Geçici deneme dosyalarını (`Test.java`, `Deneme.java`) repoya commit etmeyin

### 3.7 İsimlendirme (dosya / klasör)

- Java paket klasörleri: **küçük harf**, çoğul isim tercih (`model`, `parser`, `golden`)
- UI sınıfları: `…Panel`, `…Dialog`, `…Renderer` soneki
- Servisler: `…Service`, `…Loader`, `…Runner`
- Migration workspace dış dünyası: [MIGRATION_PLAN.md](MIGRATION_PLAN.md) içindeki `legacy/`, `target/`, `.codeflow/` — bunları repo köküne karıştırmayın

---

## 4. Paket ve isimlendirme (Java konvansiyonları)

- Paketler **küçük harf**, anlamlı segment: `com.codeflow.migration.golden`, `com.codeflow.ui.migration`.
- Sınıflar **PascalCase**; metot ve alanlar **camelCase**; sabitler **UPPER_SNAKE_CASE**.
- Arayüzler sıfat veya `…Service`, `…Loader`, `…Renderer` ile; `I` öneki kullanmayın.
- Kısaltma patlamasından kaçının; sektörde yaygın olanlar kabul: `UI`, `API`, `JSON`, `COBOL`.
- Türkçe karakter **paket ve sınıf adlarında kullanmayın**; kullanıcıya görünen metinler Türkçe olabilir (mevcut ürün dili).

**Kayıt (record) ve model:**

- Salt veri taşıyan DTO’lar için Java **record** tercih edin (`GoldenCase`, `MigrationStepView`).
- Genişleyen domain varlıkları için sınıf + kontrollü erişim; gereksiz setter zinciri yazmayın.

### 4.1 Sabitler ve hardcoded değerler

Sınıf gövdesinde ve metot içinde **dağınık literal** (sihirli sayı, yol parçası, regex, renk kodu, kullanıcı mesajı) bırakmayın. Değer tekrar ediyorsa veya anlamı bağlam dışında anlaşılmıyorsa **adlandırılmış sabite** veya uygun **merkezi sınıfa** taşıyın.

| Değer türü | Nereye |
|------------|--------|
| Workspace / dosya yolu parçaları | `MigrationPaths`, modül kök util (`…Paths`) |
| İş kuralı sabitleri (uzantı, eşik, anahtar kelime listesi) | İlgili servis veya parser sınıfının üstünde `private static final` / `private static final Set<>` |
| UI metinleri (buton, başlık, hata) | `codeflow-swing-ui` içinde ortak `…Messages` / panel sabitleri; aynı metin iki panelde kopyalanmaz |
| Renk, font, boşluk (tema) | Ortak UI tema sınıfı; panel içinde rastgele `new Color(…)` çoğaltmayın |
| Demo / örnek kaynak metni | `src/main/resources/` veya `examples/` — üretim sınıfı içine gömülmez |
| Test fixture | `src/test/resources/` |

**İstisnalar (sınıf içinde kalabilir):** tek kullanımlık, bağlamı net yerel değişkenler; döngü indeksi; anında anlaşılan `0` / `1`; kısa `switch` kolu.

**Yasak / kaçının:**

- Aynı string veya sayının üçüncü kopyası
- Servis katmanında kullanıcıya dönük UI metni
- Renderer / panel içinde iş kuralı yolu (`"legacy/"`, `".codeflow/golden/"` vb.) — path util kullanın

### 4.2 `static` kullanımı

`static` **zorunlu olmadıkça** kullanmayın. Özellikle büyük koleksiyonlar, önbellek veya durum tutan alanlar uygulama ömrü boyunca bellekte kalır; masaüstü aracında **RAM maliyeti** göz önünde bulundurulur.

**Tercih edin:**

- Örnek alanlar ve davranış için **örnek (instance)** üyeler; bağımlılık **constructor** ile verilir
- Gerçek sabitler için `private static final` (değişmez, tek kopya; Bölüm 4.1 ile uyumlu)
- Saf yardımcı işlevler için küçük `…Util` sınıfları; yine de gereksiz `static` şişirmeyin

**Yalnızca şu durumlarda `static`:**

- `main` giriş noktası
- Değişmez sabit (`static final`) — literal’i merkezileştirmek için
- Fabrika / tek satırlık pure helper (`Objects.requireNonNull`, `List.of` benzeri) ve **instance tutmuyorsa**
- Enum sabitleri veya record/sınıf düzeyinde zorunlu JVM semantiği

**Kaçının:**

- Test edilebilirliği bozan **static mutable state** (`static` Map/List/önbellek, “global” servis referansı)
- “RAM tasarrufu” bahanesiyle her şeyi `static` yapmak (çoğu nesne kısa ömürlüdür; GC toplar; static kalıcıdır)
- Alt sınıflarda override edilmesi gereken davranışı `static` metotla vermek
- Aynı sınıfta hem static hem instance ile aynı verinin iki kopyası

**Bellek notu:** Binlerce satırlık `static` koleksiyon, parse sonucu veya UI verisi **static alanda tutulmaz**; tarama/parse çıktısı state, model veya panel örneğinde yaşar; kullanılmayan referanslar serbest bırakılabilir.


```
UI (swing-ui, app)
    → migration-core / core servisleri çağırır
    → iş mantığı UI içinde uzun metotlarla yazılmaz

Servis (…Service, …Loader, …Runner)
    → I/O, parse, karşılaştırma
    → Swing bileşenine referans tutmaz

Model (com.codeflow.model, com.codeflow.migration.model)
    → Swing’den bağımsız veri yapıları
```

- `MainFrame` yalnızca **orkestrasyon**: panel oluşturma, olay bağlama, servis sonucunu UI’ya yansıtma.
- Ağır iş **EDT dışında** (`SwingWorker`, arka plan thread); sonuç `SwingUtilities.invokeLater` ile UI’ya.

---

## 6. Swing ve masaüstü UX

- Uzun süren işlem (tarama, `mvn test`) UI thread’ini **bloke etmeyin**.
- Kullanıcı metinleri mevcut ürünle uyumlu Türkçe (ASCII alternatifleri kabul: `Klasor`, `kos` — mevcut stile uyum).
- Renk ve font: mevcut koyu tema ve `DiagramPanel` / `MigrationDashboardPanel` ile tutarlılık; rastgele yeni palet tanımlamayın.
- `null` bileşen kontrolleri: public API’de mümkünse `Objects.requireNonNull` veya erken dönüş.

---

## 7. Hata yönetimi ve günlükleme

- Beklenen I/O hataları (`IOException`): servis katmanında yakalanır veya üst katmana **anlamlı mesajla** iletilir.
- UI’da kullanıcıya `JOptionPane` veya panel içi mesaj; stack trace’i yalnızca geliştirici günlüğüne (`printStackTrace` yerine ileride SLF4J tercih edilebilir).
- `catch (Exception ignored)` yalnızca bilinçli ve yorumla gerekçelendirilmiş yerlerde; yeni kodda geniş `catch` kullanmayın.
- Migration workspace: eksik `legacy/`, `manifest.json` → **ERROR/WARNING** pipeline adımı; sessizce yutmayın.

---

## 8. Dosya sistemi ve migration workspace

- Yol sabitleri `MigrationPaths` içinde; string literal dağınıklığı yapmayın.
- Workspace dışına yazmadan önce kök yolun kullanıcı seçimi olduğundan emin olun.
- `.codeflow/` altı yapısı [MIGRATION_PLAN.md](MIGRATION_PLAN.md) ile uyumlu kalsın; format değişince manifest sürümü (`version`) artırın.

---

## 9. Parser ve görselleştirme

- `JavaSourceParser` regex tabanlıdır; bilinen sınırları aşan Java özellikleri için önce **yol haritası** maddesi açın, sonra genişletin.
- Renderer’lar (`FlowchartRenderer` vb.) yalnızca **çizim**; parse veya dosya I/O içermez.
- Performans: büyük dosya ağaçlarında gereksiz `Files.walk` tekrarından kaçının; tarama sonuçlarını state’te tutun.

---

## 10. Test ve kalite

- `codeflow-core` ve `codeflow-migration-core` için **birim test** yazılabilir alanlara öncelik verin (golden loader, metrik hesabı, path çözümleme).
- UI testleri zorunlu değil; kritik iş kuralları UI’dan bağımsız test edilmeli.
- Örnek veri: `examples/` ve `codeflow-visualizer-app/src/main/resources/`; test fixture’ları `src/test/resources` altında.
- Commit öncesi: `mvn clean verify` (en azından `compile`).

---

## 11. Bağımlılıklar ve lisans

- Varsayılan: **minimum bağımlılık** (şu an `FlatLaf` yalnızca uygulama modülünde).
- Yeni bağımlılık: gerekçe, modül, lisans notu (`README` veya `docs`).
- JSON için önce basit okuyucu / JDK; ağır kütüphane ancak gerçek ihtiyaçta.

---

## 12. AI / Cursor ile geliştirirken

- Bu dosyayı ve [PROJE_BAGLAMI.md](PROJE_BAGLAMI.md) bağlam olarak kullanın.
- Üretilen kod **Bölüm 3 paket haritasına** uysun; rastgele yeni kök paket (`com.example`) açmayın.
- Tek seferde çok modülü yeniden adlandırmayın.
- Migration özelliği eklerken **COBOL→Java MVP** kapsamını aşan senaryoları stub veya `Faz 2` notu ile bırakın.
- Kullanıcı istemedikçe ek markdown / README üretmeyin.

---

## 13. Git ve inceleme (öneri)

- Commit mesajı: ne ve neden (Türkçe veya İngilizce tutarlı bir dil).
- Büyük özellik: küçük mantıksal commit’lere bölün.
- İncelemede kontrol listesi:
  - Dosya doğru modül ve pakette mi? (Bölüm 3)
  - EDT’de bloklayıcı iş var mı?
  - Hardcoded literal / tekrar eden sabit var mı? (Bölüm 4.1)
  - Gereksiz `static` veya static mutable state var mı? (Bölüm 4.2)
  - Yeni public API dokümante mi?
  - Örnek workspace / doküman güncellemesi gerekli mi?

---

## 14. Yapılmaması gerekenler (anti-pattern)

- Kök dizinde veya yanlış modülde yeni `.java` dosyası açmak
- `codeflow-core` içine `import javax.swing.*`
- `MainFrame` içinde 200+ satırlık parse veya golden okuma mantığı
- UI metinlerini servis katmanına gömmek
- Sınıf içinde dağınık hardcoded yol, mesaj veya renk (Bölüm 4.1)
- Gereksiz `static` alan/metot veya static önbellek ile kalıcı bellek şişirme (Bölüm 4.2)
- Kopyala-yapıştır ile üçüncü renderer’da aynı layout kodunu çoğaltmak (ortak yardımcı çıkarın)
- Plan dışı “temizlik” refactor’u ile özellik diff’ini karıştırmak

---

*Son güncelleme: sabitler / hardcoded değerler ve static kullanımı (Bölüm 4.1–4.2).*
