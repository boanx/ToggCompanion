# Ana sayfa sırası ve toplam araç kilometresi — 16 Kasım 2026

## Güncel teslim — 16 Kasım 2026, sürüm1.1/kod2

Kullanıcının açık sürüm/paketleme isteği tamamlandı. app/build.gradle.kts versionName **1.1**,versionCode **2**. APK içinden aapt2 ile sürüm doğrulandı. Gradle testDebugUnitTest/lintDebug/assembleDebug **BUILD SUCCESSFUL in36s**; **133test/0hata**,lint **0hata/328uyarı**,APK **v2imza doğrulaması başarılı**.

`outputs/ToggCompanion-current-debug.apk`,4146203bayt,SHA256 `1aada508bb83cfcf55057842506053430bd6ea1fa0ea3a3f2c73ebd635c37019`. Widget map-only/VIN+konum önbelleği,Google URL iyileştirmeleri,gönderim kabul/doğrulama/belirsizlik ayrımı,ana sayfa ve Sürüş toplam araç kilometresi kartları,gerçek kaydedilen kart sırası ve tercih geçişi bu pakete dahildir. Önceki16Kasım kaynak-only notlar bu teslimden önceki aşamaları anlatır.

Önceki1.0APK/metadata, tamamlanmış ara kaynak raporları ve üretilen APK kopyası `obsolete/2026-11-16-v1.1-package/move-manifest.json` ile arşivlendi; dışarıda tek teslim APK var. Bu paket fiziksel telefona/emülatöre kurulmadı; canlı araç/Google örnek linki ve gerçek widget çizimi ayrıca denenmelidir. Wear build/push yok. Yeni source değişiklikleri için açık build kısıtı devam eder.


## Sürüş sekmesi — 16 Kasım 2026 kaynak düzenlemesi

Toplam Kilometre kartı Sürüş sayfasının en üstüne de eklendi. Ayrı eski kilometre satırı ve14280kmXMLörnek metni kaldırıldı; iki sayfa aynı buildOdometerCard ve aynı telemetry.odometerKm/veri zamanı biçimlendirmesini kullanır. Yolculuk sekmesi seçimi toplam kilometreyi değiştirmez. Java tip/XML/kaynak-ID kontrolü geçti; yalnız yerleşim değiştiği için yeni test eklenmedi. APK/kurulum yok.


## Güncel kaynak

Toplam kilometre, sürüş/yolculuk mesafesi üzerinden hesaplanmaz. VehicleInfo telemetry.odometerStatus.odometerStatusContent.odometer.odometerValue metre değeri1000'e bölünür ve tam kilometre aşağı yuvarlanır; örneğin16387900m→16387km. Açık odometerKm alanı75000gibi yüksek sayılarda da kilometre olarak korunur. Legacy düz odometer/odometerValue alanının önceki50000eşik uyumluluğu korunur; bu alias'ın birimi konusunda yeni canlı veri kanıtı yoktur. Eksik/geçersiz değer için sabit14280km üretimi kaldırıldı; içmodel0bilinmiyor kabul edilir, telefon ekranında — gösterilir ve ABRP'ye0odometer gönderilmez. Gerçek0km de mevcut modelde — olarak görünür; ayrı availability alanı eklenmedi.

Ana sayfada ayrı **Toplam Kilometre** kartı ve son araç verisi zamanı vardır. Sürüş sayfasındaki toplam kilometre satırı da aynı biçimlendiriciyi kullanır. Demo/örnek değer gerçek toplam kilometre gibi sunulmaz. Kaynak aracın bulut telemetrisidir; göstergeye doğrudan BLE/CAN okuma veya yeni bir araç uyandırma komutu eklenmedi. Bulut yayınlanmadığında gösterge panelinin en yeni değeri henüz sunucuda olmayabilir. Gerçek araç karşılaştırması yapılmadı.

## Kart sırası düzeltmesi

Önceki renderHomeCards konumu ayırıp her zaman gridin sonuna ekliyordu; ABRP ve yeni kilometre kartı da sabit konumdaydı. Ayarlar kaydetse de bu kartların istenen yeri uygulanmıyordu. HomeCardLayout artık ortak kart kataloğu, sıra normalizasyonu, görünürlük ve satır paketlemesini belirler. Konum/odometer/abrp tam genişlikte tek satırdır; bu kart gelince bekleyen grid satırı boşaltılır ve geniş kart kaydedilen pozisyonunda eklenir. Ardından grid aynı sıra üzerinden sürer. Tek/çift kolon ve büyük yazı ayarlarında düzleştirilmiş sıra aynıdır.

Ana Sayfayı Özelleştir listesinde odometer ve abrp de yer alır. Kaydet sıra+görünürlüğü tek preferences edit'inde yazar ve ana sayfayı yeniler; Varsayılan aynı yolu kullanır. Yeni varsayılan: odometer,battery,drive,tires,location,12v,abrp. home_cards_catalog_v2 tek-seferlik yükseltmesi mevcut bilinen kartların sırasını/gizlenmesini koruyup eksik yeni kartları ekler. Eski kontroller/climate/digital_key gibi desteklenmeyen özet anahtarları filtrelenir; araç kontrol bölümü özelleştirilebilir özet listesinden ayrıdır.

## Doğrulama ve teslim sınırı

133JVM testi geçti: toplam kilometre/sürüş ayrımı, metre→km aşağı yuvarlama, eksik kilometrede sahte değer olmaması, açık yüksek-km birimi, ABRP'ye bilinmeyen0göndermeme, geniş kartı arada/başta tutma, gizli kart koruma, eski tercihlerden geçiş ve tek/çift kolon sıra eşitliği. Java kaynak tip kontrolü ve XMLkontrolleri geçti. Android kaynak derlemesi/APK/kurulum/canlı API isteği/push yoktur. Güncel teslim APK2Kasım sürümü olup bu16Kasım kaynak düzenlemelerini içermez. İlk yeni telemetri sorgusuna kadar eski cache kaydı kalabilir.
