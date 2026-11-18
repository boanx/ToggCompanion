# Trumore 2.1.25 → 2.1.26: karşılaştırma ve geliştirme hazırlığı

Tarih: 20 Ekim 2026. Kapsam: orijinal Android paketlerinin çevrimdışı kod/varlık/manifest karşılaştırması. Companion davranışı değiştirilmedi; APK derlenmedi ve GitHub push yapılmadı.

## 1. Kaynak ve bütünlük

- Eski sürüm: kullanıcı tarafından sağlanan2.1.25/661; base + arm64 split, `obsolete/2026-10-12/work/trumore-analysis/input/`.
- Yeni sürüm: kullanıcının USB ile bağladığı telefondan alınan2.1.26/663; dört kurulu split `obsolete/2026-10-20/trumore-2.1.26-device-input/`. Kurulu sürüm/provenans/hashler `analysis/device-2026-10-20/installed-apk-provenance.json`.
- Her iki base APK `apksigner verify --print-certs` ile başarıyla doğrulandı; signer SHA256 aynı: `c5fa69cd6966a9f76933affc4559c3c43460e995426d9c3505de0f46dcde1974`. Bu eşitlik paketlerin aynı signer ile imzalandığını gösterir; yayın kanalının bağımsız doğrulaması değildir.
- Ortak kod karşılaştırması yalnız base+arm64 üzerinde yapıldı. Eski en/xhdpi ve yeni tr/xxhdpi dil/yoğunluk farkları uygulama değişikliği sayılmadı. ZIP boyutu farkı doğrudan kod büyümesi değildir; paketleme/sıkıştırma da değişebilir.
- Eski libapp.so:36.766.608 bayt, SHA256 `086ac67c7f1edc261e2744d0ad17f3e6aa79081a7b4c2929f9d577d8b3c0ad78`.
- Yeni libapp.so:37.356.432 bayt, SHA256 `e4c0df818cbea4891c3072c1fad05a4e52ac86c0db697052571d73139f018f31`; artış589.824 bayt.

## 2. Ölçülen farklar

| Alan | Gözlem | Anlam sınırı |
| --- | --- | --- |
| ZIP girdileri |10.184 ortak girdinin10.126'sı birebir aynı,58'i değişmiş; eklenen/silinen girdi yok | Dosya adlarının sabitliği içeriğin tamamının sabitliği değildir |
| Native kütüphaneler |20 kütüphane aynı; yalnız libapp.so ve librjsniffer-lib.so farklı | Native yardımcıların sabitliği Dart BLE çağrı akışının sabitliğini kanıtlamaz |
| Flutter, HERE, signer |libflutter.so, libheresdk.so, libsigner.so aynı | Motor/navigasyon/kriptografi yardımcı binary değişimi saptanmadı |
| Android izinleri |45 benzersiz izin iki sürümde de aynı; minSDK26, target/compileSDK36 aynı | Manifestte izin sıralaması ve yinelenen satır temizliği var |
| HTTP manifest ayarı |usesCleartextTraffic true → false | Manifest düzeyinde değişim; networkSecurityConfig istisnaları ayrıca incelenmeden tüm uygulamanın HTTPS davranışı hakkında kesin sonuç çıkarılmaz |
| Manifest receiver |ly.count.android.sdk.ReferrerReceiver / INSTALL_REFERRER kaldırılmış | Anahtar yetkilendirme düzeltmesi olduğuna kanıt yok |
| Ortam varlıkları |dev/preprod/prod/staging/test/test2 .env dosyaları hash olarak aynı | Gizli yapılandırma değerleri rapora aktarılmadı; Dart içindeki host seçimi ayrıca değişmiş olabilir |
| Android DEX |98.225 →98.247 class descriptor;55 eklenen/33 kaldırılan descriptor | Obfuscation yeniden adlandırması bu sayılara dahildir;22 yeni ürün özelliği demek değildir |
| Widget |Yeni h sınıfı Kotlin metadata'da HomeWidgetFonts.kt, measureTextBounds/measureSize, font bitmap çizimi referansları içeriyor | home_widget entegrasyonunun değiştiğine kanıt; Companion'a doğrudan Flutter kodu aktarılmamalı |
| Saat |Yeni literal: Received refresh request from watch, triggering device refresh | Yeni metin görüldü; yenilemenin hangi şartlarda canlı araç verisi aldığı henüz izlenmedi |

Değişen varlıklar arasında baseline.prof/profm ve MaterialIcons-Regular.otf var. Ortak ortam dosyaları ve diğer Flutter asset girdileri aynı. DEX kaynaklarını tek bir class adına göre karşılaştırmak yanıltıcıdır: eski h=HomeWidgetGlanceDataStore, yeni h=HomeWidgetFonts.

## 3. Dijital anahtar ve sertifika

İki sürümde de pool/string ve ham ARM64 literal yüklemelerinde şu yollar bulundu: CheckUserPhoneRelation; Certificates/GetCertificate; Certificates/CreateCertificate; ShareProcess/CreateSession, sessionDetail, ReplyToSession, GetPendingInvites, GetAllSharedPubKeys. Endpoint adının varlığı HTTP yöntemi/gövde/yanıt eşitliği değildir.

`isOwnerCertificate` çevresindeki ham instructionlar iki sürümde de ayrımı koruyor:

| Yol |2.1.25 bool ADD adresi |2.1.26 bool ADD adresi | Ham word / sonuç |
| --- | --- | --- | --- |
| Paylaşılan |0x2087d78 |0x2101af8 |0x9100c2d0; x22(NULL)+0x30 → false |
| Sahip |0x21f230c |0x227320c |0x910082d0; x22(NULL)+0x20 → true |

Kaynak: `owner-boolean-windows.json`. Önceki AF214 düzeltmesi yeni sürümde de destekleniyor. sessionId/vehicleName alanlarını owner isteğine eklemek için yeni bir kanıt yok. GetCertificate HTTP200/CERT_MISSING ve CreateCertificate HTTP500 kök nedeni bu karşılaştırmayla çözülmedi. Yeni sürümde sertifika almanın çalıştığı iddia edilmez.

İki sürümün odağa alınmış pool literal farkında SmartAccessKeychainAccessibility migration mesajı kaldırılmış. Bu tek metin, Android anahtar depolama/sertifika sözleşmesinin değiştiğinin kanıtı değildir.

## 4. Gelecek inceleme için adres haritası

Aşağıdaki adresler ham ARM64 literal LOAD yeridir; fonksiyon giriş adresi değildir. Kaynak: her sürümün `protocol-xrefs.json` dosyası.

| Literal |2.1.25 LOAD |2.1.26 LOAD |
| --- | --- | --- |
| CreateCertificate / paylaşılan |0x2087e14 |0x2101b94 |
| CreateCertificate / sahip |0x21f2330 |0x2273230 |
| GetCertificate |0x21f34b8 |0x22743c8 |
| CheckUserPhoneRelation |0x21f41dc |0x22750fc |
| PlannedJourneys |0x2256190 /0x2256364 |0x22d8f90 /0x22d9164 |
| sessionDetail |0x22871bc |0x230a4fc |
| ReplyToSession |0x2288dcc |0x230c138 |
| CreateSession |0x228b7d4 |0x230eb74 |

Eski AOT adresleri yeni binary'ye taşınmamalı. Yeni dae instruction-table mahalleleri bazı işlemleri aynı geniş aralığa atıyor; bunları gerçek fonksiyon başlangıcı olarak kullanmayın. Tahmin edilen0x2272f68 adresini dae disasm reddetti; bu adres bir fonksiyon olarak belgelenmedi.

## 5. Rota ve telemetri

PlannedJourneys yolu ve odaktaki sertifika/tüketim/telemetri alan literalleri açısından yeni bir endpoint değişimi saptanmadı. Bu, serializer veya hesaplama kodlarının eşit olduğu anlamına gelmez. Mevcut Companion için yeni host, alternatif HTTP500 fallback'i veya yeni tüketim dönüşümü eklenmedi.

Eklenecek işler için kabul koşulları:

| Öncelik | Hazırlanacak özellik | Kodlamadan önce gereken kanıt / kabul koşulu |
| --- | --- | --- |
|1 | Companion ilk dijital anahtar yetkilendirme | Araç yakınında mevcut Trumore sahibi kaydının sırası, certificate status/alanları, cihaz public key bağlamı; GATT ile bulut yetkisi ayrı gösterilmeli; var olan anahtar değiştirilmemeli |
|2 | Davet/QR kabulü | Gerçek UI'da görülen KeySharing?seId&no&vin yapısını ayrıştırma; sessionDetail/ReplyToSession HTTP sözleşmesi ve imzalanacak byte dizisi çözülmeli; davet URL'si tek başına yetki kabul edilmemeli |
|3 | Saatten güncel veri isteği | Yeni watch-refresh literalinin çağıranları; Companion Wear istek→telefon fetch→taze yanıt→Wear akışı, cache ayrımı ve kısa aralıklı istek birleştirme |
|4 | ABRP rota güncellemesi→araç | Var olan rota imzası/tekrar önleme korunmalı; bulut kabulü ile araç ekranına teslim ayrılmalı; SOC/durak serializer'ı2.1.26 kodunda ayrıca doğrulanmalı |
|5 | Widget tipografi/ölçekleme | HomeWidgetFonts yaklaşımı bir araştırma referansı; Companion RemoteViews için kendi boyut/dpi/yazı ölçümü; mevcut tema ve refresh butonu değişiklikleri korunmalı |

## 6. Kapsam dışındaki ürün değişiklikleri

Yeni literal yollar ağırlıkla dijital abonelik/ödeme ekranlarında: digitalProductSubscription ve subscriptionDetails; Package/{country}/subscriptions v1→v2; getSubscriptions/getSubscriptionDetail/getPlanPurchaseInfo/cancelSubscription; payment-methods ve magnet-notification-consent. Tam liste `endpoint-literal-diff.json`. Bunlar keşfedilmiş literallerdir; doğrulanmış API sözleşmesi veya kullanıma açılmış özellik ilanı değildir. Hesapta abonelik/ödeme çağrısı yapılmadı.

## 7. Tekrar üretme, araçlar ve sınırlamalar

Kanıt kökü: `work/trumore-analysis/analysis/version-diff-2.1.25-2.1.26/`. ZIP/decompressed SHA256 karşılaştırması ve DEX descriptor sayımı `compare_apk_versions.py` ile yeniden üretilebilir. Literal locator `locate_protocol_literals.py` ELF executable segmentlerde ham ADD/LDR tarar. İki script salt okunur girişlerden araştırma çıktısı üretir; APK oluşturmaz, cihaza bağlanmaz.

Örnek:

```text
python work/trumore-analysis/compare_apk_versions.py OLD_BASE OLD_ARM NEW_BASE NEW_ARM OUT
python work/trumore-analysis/locate_protocol_literals.py ELF PP_TEXT FUNCTIONS_TEXT STUBS_TEXT OUT_JSON
```

dae0.1.13 yeni binary'de Dart3.12.2 version-hash eşleşmesi buldu; SDK profili UNVERIFIED. Eski ve yeni pool değerleri export/text/pp.txt formatından alındı; query pp TSV çıktısı locator girdisi değildir. Ham adres/word ve ZIP hash bulguları ile decompiler çıkarımları ayrı tutuldu. Yeni export yalnız UnsupportedError class filtresiyle nesne indexlerini üretmiştir. Kullanılmayan export dosyaları (tek class içeren functions.txt dahil) manifestle obsolete/2026-10-20/version-comparison-unused altına taşındı; etkin kanıt dizininde yalnız export/text/pp.txt tutuldu. Genel fonksiyon listesi için üst dizindeki query functions.txt/stubs.txt kullanılır. Nearest-entry sonuçları gerçek sınır kanıtı değildir.

JADX1.5.6 yeni widget h sınıfını dosyaya çıkardı; APK içindeki model assetlerinin iç arşivlerinin taranması hata verdi ve işlem2 hata ile exit1 döndü. Tam/sorunsuz decompilation iddiası yok. Metadata ve okunabilen yöntemler araştırma ipucu olarak kullanıldı; hata veren/pseudocode bölgeler çalışma kanıtı sayılmaz.

Bu tur cihazda yeni işlem yapılmadı; önceki turdaki2.1.26 UI gözlemleri AF217/F94'tedir. Gerçek BLE/sertifika/rota/saat denemesi veya uygulama test suite'i çalıştırılmadı. Repo KB doğrulaması ayrıca yapıldı. Orijinal split APK'lar obsolete altında kalır; çıktıda yalnız araştırma dokümanı bulunur.
