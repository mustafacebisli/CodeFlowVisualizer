# Code Flow Visualizer — Proje bağlamı (AI / hatırlatma)

Bu dosya, sohbetlerde tekrar tekrar açıklama gerektirmemesi için projenin ne olduğunu ve nasıl çalıştığını özetler.

## Ne bu?

**Code Flow Visualizer**, Java kaynak kodunu yazarken veya dışarıdan dosya değişince **canlı** olarak akış diyagramı / bağımlılık / genel bakış gösteren **bağımsız masaüstü** (Swing) uygulamasıdır. IDE eklentisi değildir; harici editörlerle birlikte **dosya izleme** ile çalışır.

## Teknoloji ve kısıtlar

- **Java 17+**, **Maven 3.6+**
- **Sıfır harici kütüphane** — yalnızca Java Swing, `Graphics2D`
- Ana sınıf: `com.codeflow.App`
- Artifact: `code-flow-visualizer-1.0-SNAPSHOT` (JAR `mvn package` ile)

## Çalıştırma

```bash
mvn compile exec:java -Dexec.mainClass="com.codeflow.App"
```

veya paketlenmiş JAR:

```bash
mvn package
java -jar target/code-flow-visualizer-1.0-SNAPSHOT.jar
```

Açılışta örnek e-ticaret sepet kodu (CartManager, DiscountService, StockService, OrderProcessor) editörde yüklü gelir; kaynak metin `src/main/resources/examples/ecommerce-cart.java`, yükleme `com.codeflow.examples.ExampleSources`.

## Kullanım modları

1. **Yerleşik editör** — Sağ panelde kod; sol panelde diyagram ~500 ms debounce ile güncellenir (`DocumentListener`).
2. **Klasör izleme** — Araç çubuğunda **"Klasör İzle..."** ile `.java` içeren klasör seçilir; IDE’de kayıt edilince `WatchService` / `FileWatcher` ile güncellenir.

## Diyagram modları (sol panel, Mod)

| Mod | Açıklama |
|-----|----------|
| Akış diyagramı | Metot seviyesinde dallanma; if/else elmas, döngülerde geri oklar |
| Sınıf bağımlılıkları | Sınıfların birbirine referans grafiği |
| Genel bakış | Tüm sınıflar, alanlar, metotlar |

Navigasyon: solda sınıf/metot dropdown’ları (akış için), sağda sınıfa atlamak için dropdown.

## Depo klasörleri (özet)

| Klasör | İçerik |
|--------|--------|
| `docs/` | Proje bağlamı, yol haritası (`PROJE_BAGLAMI.md`, `YOL_HARITASI.md`) |
| `src/main/java/com/codeflow/` | Uygulama kaynağı (`App`, `model`, `parser`, `ui`, `examples` yükleyici) |
| `src/main/resources/examples/` | Paketlenmiş örnek Java metni (derlenmez; editörde gösterilir) |

## Paket / dosya yapısı (`src/main/java/com/codeflow/`)

| Dosya | Rol |
|-------|-----|
| `App.java` | Giriş |
| `examples/ExampleSources.java` | Classpath’ten örnek dosya okuma |
| `parser/JavaSourceParser.java` | Regex tabanlı Java parse (sınıf, alan, metot, kontrol akışı) |
| `parser/FileWatcher.java` | Dosya sistemi izleme |
| `model/CodeClass.java`, `CodeMethod.java` | Model |
| `model/FlowNode.java` | Akış ağacı (if/else dalları, döngü gövdesi + loop-back) |
| `model/MethodCall.java` | Metot çağrısı referansı |
| `model/DependencyGraph.java` | Sınıf bağımlılık grafiği üretimi |
| `ui/MainFrame.java` | Ana pencere, split |
| `ui/CodeEditorPanel.java` | Kod editörü (sözdizimi, satır numarası, navigasyon) |
| `ui/DiagramPanel.java` | Diyagram alanı |
| `ui/FlowchartRenderer.java` | Akış çizimi (dal genişlikleri, birleşim noktaları) |
| `ui/DependencyRenderer.java` | Bağımlılık çizimi |
| `ui/OverviewRenderer.java` | Mimari genel bakış çizimi |

## Nasıl çalışıyor? (kısa)

1. **Parse:** `JavaSourceParser` kaynak metinden yapıları çıkarır.
2. **Ağaç:** Kontrol akışı `FlowNode` hiyerarşisine dönüşür (if/else true/false, döngülerde geri kenar).
3. **Çizim:** İlgili `*Renderer` sınıfları `Graphics2D` ile recursive / graf düzeni çizer.
4. **Canlılık:** Editör + dosya izleme, debounce ile gereksiz yeniden çizimi azaltır.

## Lisans

MIT (README ile uyumlu).

## Not

Arayüzde Türkçe etiketler kullanılıyor (ör. "Klasör İzle...", mod isimleri). Yeni özellik veya metin eklerken bu dil tutarlılığını korumak mantıklıdır.
