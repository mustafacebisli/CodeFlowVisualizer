# COBOL → Java Migration Takip — Uygulama planı (Faz 1)

Bu belge, Code Flow Visualizer’ın **lokal masaüstü migration konsolu** olarak evrilmesinin mimari ve teslimat planıdır. Dönüşüm kodunu **harici LLM (Cursor vb.)** üretir; bu ürün **okur, karşılaştırır, test sonuçlarını gösterir ve görselleştirir**.

## Kilitlenen kararlar

| Konu | Karar |
|------|--------|
| Senaryo | Yalnızca **COBOL → Java** (Faz 1) |
| Dağıtım | **Lokal Swing**; sunucu yok |
| Pilot kapsam | **Alt sistem** |
| Doğrulama | **İş akışı karşılaştırması** + **golden test geçiş oranı** |
| Test modu | **Golden replay** (`.codeflow/golden/`) |
| Metrik | `iş doğruluğu % = geçen_test / toplam_test` |
| BMS / CICS UI | MVP dışı |
| Onay iş akışı | Ürün dışı |
| Kalıcılık | Dosya: `.codeflow/migration.json` |

## Maven modülleri

```
codeflow-parent
├── codeflow-core              # Java parse, model, FileWatcher
├── codeflow-migration-core    # Migration workspace, golden, test runner (YENİ)
├── codeflow-swing-ui          # Diyagram + MigrationDashboardPanel
└── codeflow-visualizer-app    # App, MainFrame, örnek demo
```

## Workspace dizin standardı

Kullanıcı bir **migration workspace** kök klasörü seçer:

```
workspace/
├── legacy/                 # COBOL kaynak (alt sistem)
├── target/                 # Java hedef (Maven projesi önerilir)
└── .codeflow/
    ├── migration.json      # legacyPath, targetPath, son metrikler
    └── golden/
        ├── manifest.json   # test case listesi
        └── cases/
            └── TC001-.../
                ├── case.json
                ├── input.json
                └── expected.json
```

JUnit testleri `target/src/test/java/...` altında üretilir (LLM işi).

## Görsel pipeline (Migration sekmesi)

1. Eski kaynak bağlandı  
2. Yeni kaynak bağlandı  
3. Analiz  
4. Akış karşılaştırması  
5. Golden test seti  
6. Test koşumu  
7. Sonuç özeti (% geçiş)

## Faz 1 teslimatları (bu build)

- [x] `codeflow-migration-core` modül iskeleti  
- [x] `GoldenManifestLoader`, `MigrationWorkspaceService`  
- [x] `MavenTestRunner` (lokal `mvn test`)  
- [x] `FlowComparisonService` (Java parse + COBOL dosya sayımı — ilk sürüm)  
- [x] `MigrationDashboardPanel` + MainFrame sekmesi  
- [ ] Tam COBOL AST / akış diff (Faz 1.1)  
- [ ] Surefire XML → case bazlı pass/fail eşlemesi (Faz 1.1)  

## Çalıştırma

```bash
mvn clean package
java -jar codeflow-visualizer-app/target/codeflow-visualizer-app-1.0-SNAPSHOT.jar
```

Uygulamada **Migration COBOL→Java** sekmesi → **Workspace ac** → `legacy/`, `target/`, `.codeflow/golden/` olan klasör.
