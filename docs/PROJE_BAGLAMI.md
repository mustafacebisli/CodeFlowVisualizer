# Code Flow Visualizer — Proje bağlamı (AI / hatırlatma)

Bu dosya, sohbetlerde tekrar tekrar açıklama gerektirmemesi için projenin ne olduğunu ve nasıl çalıştığını özetler.

## Ne bu?

**Code Flow Visualizer**, Java kaynak kodunu yazarken veya dışarıdan dosya değişince **canlı** olarak akış diyagramı / bağımlılık / genel bakış gösteren **bağımsız masaüstü** (Swing) uygulamasıdır. IDE eklentisi değildir; harici editörlerle birlikte **dosya izleme** ile çalışır.

## Teknoloji ve kısıtlar

- **Java 17+**, **Maven 3.6+**
- **FlatLaf** (tema); çekirdek `codeflow-core` Swing içermez
- Bağımsız uygulama girişi: `com.codeflow.app.App` (`codeflow-visualizer-app`)
- Dağıtım JAR: `codeflow-visualizer-app/target/codeflow-visualizer-app-1.0-SNAPSHOT.jar` (`mvn package`, shade)

## Çalıştırma

```bash
mvn clean package
java -jar codeflow-visualizer-app/target/codeflow-visualizer-app-1.0-SNAPSHOT.jar
```

Açılışta örnek e-ticaret sepet kodu yalnızca **uygulama** modülünde yüklenir: `codeflow-visualizer-app/src/main/resources/examples/ecommerce-cart.java`, sınıf `com.codeflow.app.sample.ExampleSources`. Kütüphane modülleri (`codeflow-core`, `codeflow-swing-ui`) örneğe bağımlı değildir.

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
| `docs/` | Proje bağlamı, yol haritası |
| `codeflow-core/` | Parser + model (Swing yok) |
| `codeflow-swing-ui/` | Diyagram / editör Swing bileşenleri |
| `codeflow-visualizer-app/` | `App`, `MainFrame`, örnek `resources/examples/` |

## Modül / dosya özeti

| Konum | Rol |
|-------|-----|
| `codeflow-core/.../CodeFlow.java` | `Preferences` düğüm kökü (işaret sınıfı) |
| `codeflow-core/.../parser/JavaSourceParser.java` | Regex tabanlı parse |
| `codeflow-core/.../parser/FileWatcher.java` | Klasör izleme |
| `codeflow-core/.../model/*` | `CodeClass`, `FlowNode`, bağımlılık modeli |
| `codeflow-swing-ui/.../ui/*` | `DiagramPanel`, renderer’lar, editör, gezgin |
| `codeflow-swing-ui/.../util/AppPreferences.java` | Kalıcı tercihler |
| `codeflow-visualizer-app/.../app/App.java` | Masaüstü giriş |
| `codeflow-visualizer-app/.../app/MainFrame.java` | Ana pencere, izleme, örnek yükleme |
| `codeflow-visualizer-app/.../app/sample/ExampleSources.java` | Yalnızca uygulama modülü örneği |

## Nasıl çalışıyor? (kısa)

1. **Parse:** `JavaSourceParser` kaynak metinden yapıları çıkarır.
2. **Ağaç:** Kontrol akışı `FlowNode` hiyerarşisine dönüşür (if/else true/false, döngülerde geri kenar).
3. **Çizim:** İlgili `*Renderer` sınıfları `Graphics2D` ile recursive / graf düzeni çizer.
4. **Canlılık:** Editör + dosya izleme, debounce ile gereksiz yeniden çizimi azaltır.

## Lisans

MIT (README ile uyumlu).

## Not

Arayüzde Türkçe etiketler kullanılıyor (ör. "Klasör İzle...", mod isimleri). Yeni özellik veya metin eklerken bu dil tutarlılığını korumak mantıklıdır.
