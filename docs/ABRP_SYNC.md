# ABRP senkronizasyonu

## Güncel paket — 2 Kasım 2026, Yol Tarifi sekmesi + widget ABRP

Kullanıcının açık paketle isteğiyle son kaynaklar telefon APK'sına alındı: **BUILD SUCCESSFUL in8s**, **114test/0hata**, lint **0hata/327uyarı**, apksigner v2imzası doğrulandı. `outputs/ToggCompanion-current-debug.apk`, 4140095bayt, SHA256 `96f6d9c41737dd75920ee6b0e58e60c4c66825d791562c167f03da5df81b4ee6`. Bu paket diğerlerinin yanındaki **Yol Tarifi** sekmesini, aynı sayfadaki ABRP senkronizasyon/ayarlarını ve manuel rota aktarımını; genişletilmiş widgetta aydınlık aktif/koyu deaktif ABRP aç/kapat düğmesini içerir.

Önceki APK/metadata ve tamamlanmış ara raporlar `obsolete/2026-11-02-yol-tarifi-package/move-manifest.json` ile korunur. APK üretim kopyası da arşivde, dışarıda tek teslim APK var. Bu build için telefon/emülatör kurulumu veya gerçek araç/widget aç-kapat denemesi yapılmadı. Önceki kaynak-only notları bu teslimden önceki aşamayı anlatır. Wear build ve push yok.


## 2 Kasım 2026 — Yol Tarifi sekmesi ve widget ABRP anahtarı (kaynak)

Kullanıcı düzeltmesi: ayrı Activity açmak, mevcut sekmelerin yanına bir sayfa eklemek isteğini karşılamıyordu. MainActivity'ye Konum'dan sonra sekizinci **Yol Tarifi** sekmesi/page_directions eklendi. **Rotayı araca aktar** ve ABRP entegrasyonunun **Senkronizasyon / Ayarlar** bölümleri DirectionsPage içinde birlikte yer alır. Ana ABRP kartı ve genel Ayarlar → Yol Tarifi aynı sekmeyi seçer. Genel ayarlardaki ikinci rota düğmesi kaldırıldı. Eski AbrpActivity yalnız bildirim uyumluluğu için bu sekmeye yönlendirir; yeni bildirim/eksik kurulum widget girişi doğrudan MainActivity open_directions ekini kullanır.

Widgetın yükseklikleri de responsive varyantlara eklendi (önceden yalnız genişlik varyantları vardı). 70dp kompakt,160dp batarya satırları+ABRP düğmesi,250dp harita+ABRP düğmesi;170/240/310dp genişliklerle9yerleşim. API26–30 aynı eşikleri widget seçeneklerinden uygular. ABRP düğmesi aktif serviste açık mint/koyu metin, deaktifken koyu yüzey/açık metindir. Erişilebilir açıklaması açık durumu ve dokunma eylemini bildirir.

Yapılandırılmış hesapta tek dokunuş mevcut60sn dataSync servisini başlatır/durdurur. Exported=false AbrpWidgetToggleReceiver yalnız uygulamanın oluşturduğu PendingIntent üzerinden çağrılır. Kurulum eksikse düğme Yol Tarifi sayfasını açar; geçerli araç/ABRP bilgisi olmadan servis başlatılmaz. Servis açılış/kapanışı ve ABRP ayar kaydı widgetları günceller. Android servis başlangıcını reddederse durum deaktif kalır ve kısa mesaj gösterilir. Mevcut dataSync süre sınırları devam eder; limit atlatma yoktur.

Kaynak tip kontrolü,48XML/yerel kaynak başvurusu ve114JVM testi geçti. Bu tur Android kaynak derlemesi, APK üretimi veya cihaz kurulumu yapılmadı. Mevcut2Kasım teslim APK'sı bu son sekme/widget anahtarı düzenlemesini içermez; fiziksel widget dokunma/renk/yükseklik ve Yol Tarifi ekranı sonraki açık derleme/kurulumda doğrulanmalıdır.

Android dayanağı: widget kullanıcı etkileşimi, [arka plandan foreground servis başlatma istisnaları](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) arasındadır. Sistem başlangıç reddi yine güvenli biçimde ele alınır.


## Güncel teslim — 2 Kasım 2026, doğrulanmış telefon APK

Kullanıcının açık doğrulama/derleme isteği tamamlandı: `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug`, **BUILD SUCCESSFUL in9s**. **114test/0hata**, lint **0hata/325uyarı**. Debug APK `outputs/ToggCompanion-current-debug.apk`, 4354275bayt, SHA256 `6ed8ce9d68d1c16ad212a5222e9dcacbeece7463122e435feae69cab9aa1915d`; APK v2imzası apksigner ile doğrulandı. Önceki APK, metadata ve ara source-only raporlar `obsolete/2026-11-02-abrp-widget-package/move-manifest.json` ile arşivlendi; oluşturulan APK kopyası da arşivde. Dışarıda tek teslim APK var.

Paket; ABRP ayrı sayfa/60sn arka plan telemetri, otomatik ABRP rota kodunun kaldırılması, paylaşılabilir günlükler ve widgetın gerçek araç zamanı/yeni telemetri sorgusu/düzgün yenile oku değişikliklerini içerir. Lint sırasında API26URIencode/RemoteViews/style, Bluetooth izin iptali ve durum sabitleri, konum izin çifti ve API33+geri hareketi uyumluluğu düzeltildi. Eski BLE test fixture'ları doğru gün/ay sırası ve aktif ilk istek akışına güncellendi; güvenlik kontrolleri kaldırılmadı.

Emülatöre güncelleme kurulumu ve MainActivity soğuk açılışı geçti; crash kaydı yok. Widget broadcast→JobService akışı REFRESH_REQUESTED/SESSION_UNAVAILABLE ile hesap olmadan güvenli durdu. Emülatörde canlı hesap/araç yoktur; gerçek telemetri,1dk ekran-kapalı ABRP, BLEkomutları ve fiziksel OneUIwidget görünümü doğrulanmadı. Fiziksel telefona kurulum, Wear build ve GitHub push yapılmadı. Önceki kaynak-only notlar bu teslimin öncesine aittir.


## 2 Kasım 2026 — güncel kaynak, henüz APK üretilmedi

ABRP artık ayrı bir sayfa: Senkronizasyon ve Ayarlar sekmeleri. Ana kart yalnız aktif/deaktif ve son başarılı gönderimi gösterir. Kullanıcı açınca `AbrpSyncService` hemen ve ardından 60 saniyelik aralıklarla yeni TVA isteği başlatır; `AbrpSyncEngine` yalnız bu isteğin canlı sonucunu ABRP'ye gönderir. Tüketim yalnız **Yolculuğum** kaydından gelir; eksik tüketim uydurulmaz. Demo/önbellek sonuçları gönderilmez. Araç sunucusunun yayınladığı verinin yaşı günlükte tutulur; yeni HTTP isteği fiziksel aracın yeni veri yayınladığını garanti etmez.

ABRP plan sorgusu, rota değişimi takibi, otomatik araca gönderim, bunların ayarları ve eski JobService kaldırıldı. Eski 8201/8202 işleri iptal edilir, rota tercihleri temizlenir. Kullanıcının çalıştığını doğruladığı **manuel harita/rota aktarımı korunur**; Ayarlar → Akıllı cihazıma aktar üzerinden veya paylaşım menüsünden açılır.

Kullanıcı tarafından başlatılan dataSync foreground servis, sürekli bildirim ve Durdur eylemi ile arka planda sürer. Kısmi wake lock ve oturum en fazla 6 saatle sınırlandırılmıştır. Android 15+ ayrıca 24 saatte toplam 6 saat arka plan dataSync kotası uygular; onTimeout servis ve anahtarı kapatır. Zorla durdurma/işletim sistemi kısıtları sonrası süresiz veya tam zamanlı çalışma garantisi verilmez. Kullanıcı sayfadan yeniden açabilir; otomatik boot/restart veya kota atlatma yoktur.

Teknik BLE/GATT/sertifika, rota ve ABRP hata bilgileri özel depolamada dönen `AppLog` günlüklerine yazılır. Ayarlar → Uygulama günlüğünü paylaş, FileProvider üzerinden salt okunur erişim verir. URL, Bearer, e-posta, UUID, VIN, uzun anahtar ve koordinat çiftleri maskelenir. Ham ağ gövdeleri, anahtarlar ve sertifikalar loglanmaz. Genel ayarlardaki manuel erişim tokenı/kullanıcı kimliği/demo kontrolleri ve ekran tanı dökümleri kaldırıldı; kurulum/onay gerektiren kullanıcı yönlendirmeleri korunur.

Kaynak Java tip kontrolü (geçici R sabitleri, SDK36 ve önbellekteki bağımlılıklar), XML/kaynak başvuruları ve JVM testleri ile kontrol edildi. Android kaynak derlemesi, DEX, imza, APK, telefon kurulumu ve GitHub push yapılmadı. Sonraki açık paketleme isteğinde gerçek Android build/lint ve cihazda ekran kapalı arka plan testi gerekir. Ayrıntılar: [ABRP senkronizasyonu](ABRP_SYNC.md).

## Dosya haritası ve doğrulama

- `AbrpActivity`: bağımsız senkronizasyon/ayar sekmeleri, aç/kapat, taze manuel gönderim, kapasite ve gizli ABRP bağlantı anahtarı.
- `AbrpSyncService`: tek zamanlayıcı, görünür bildirim, oturum durdurma, wake lock ve Android zaman aşımı. START_NOT_STICKY; süresiz otomatik yeniden başlatma yoktur.
- `AbrpSyncEngine`: manuel ve otomatik çağrılar arasında tek işlem; hesap/VIN/token/kapasite değişince önceki işlemi göndermeme; başarıdan sonra son gönderim saati.
- `AbrpClient`: yalnız telemetri endpointi; yönlendirme izlenmez, yanıt 64KiB ile sınırlandırılır. HTTP durumları günlüklenir, token veya istek URL'si kaydedilmez.
- `AppLog`: yaklaşık 192KiB + önceki dosya; tek olay 8KiB; paylaşım cache/shared-logs altında, FileProvider yalnız bu dizini açar.

Manuel kabul: giriş yap, ABRP anahtarını kaydet, aç; bildirim görünmeli, ana kart son başarılı zamanı güncellemelidir. Ekranı kapatıp 3+ dakika sonra günlükte FRESH_FETCH_START/UPLOAD_OK aralıklarını kontrol et. Ağı kesince eski zaman korunmalı; kapatınca yeni gönderim başlamamalı. Ayar/hesap değişimi sırasında eski bağlam gönderilmemeli. ABRP rota kontrolü veya otomatik araç hedefi olmamalı. Manuel harita hedef aktarımı ayrıca denenmelidir.

Android resmi sınırlar: https://developer.android.com/develop/background-work/services/fgs/timeout ve https://developer.android.com/about/versions/15/behavior-changes-15 .
