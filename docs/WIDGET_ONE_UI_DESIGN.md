# Widget: canlı One UI renkleri

## Güncel teslim — 16 Kasım 2026, sürüm1.1/kod2

Kullanıcının açık sürüm/paketleme isteği tamamlandı. app/build.gradle.kts versionName **1.1**,versionCode **2**. APK içinden aapt2 ile sürüm doğrulandı. Gradle testDebugUnitTest/lintDebug/assembleDebug **BUILD SUCCESSFUL in36s**; **133test/0hata**,lint **0hata/328uyarı**,APK **v2imza doğrulaması başarılı**.

`outputs/ToggCompanion-current-debug.apk`,4146203bayt,SHA256 `1aada508bb83cfcf55057842506053430bd6ea1fa0ea3a3f2c73ebd635c37019`. Widget map-only/VIN+konum önbelleği,Google URL iyileştirmeleri,gönderim kabul/doğrulama/belirsizlik ayrımı,ana sayfa ve Sürüş toplam araç kilometresi kartları,gerçek kaydedilen kart sırası ve tercih geçişi bu pakete dahildir. Önceki16Kasım kaynak-only notlar bu teslimden önceki aşamaları anlatır.

Önceki1.0APK/metadata, tamamlanmış ara kaynak raporları ve üretilen APK kopyası `obsolete/2026-11-16-v1.1-package/move-manifest.json` ile arşivlendi; dışarıda tek teslim APK var. Bu paket fiziksel telefona/emülatöre kurulmadı; canlı araç/Google örnek linki ve gerçek widget çizimi ayrıca denenmelidir. Wear build/push yok. Yeni source değişiklikleri için açık build kısıtı devam eder.


## 16 Kasım 2026 — widget harita kaydı ve Google bağlantıları (kaynak)

Widgetta başka uygulama sayfasının görünmesinin kaynak nedeni: MainActivity harita görünümünün pencere koordinatlarından PixelCopy alıyordu; gecikmiş istek farklı sekme/görünüm açıkken pencere piksellerini kaydedebiliyordu. Bu pencere kopyası kaldırıldı. Yalnız bağlı/görünür harita WebView'i, postVisualStateCallback sonrası kendi Canvas'ına çizilir. Yakalama en fazla640piksel genişlikte; görünüm ayrılmış/gizlenmişse veya araç değişmişse atlanır.

Yeni WidgetMapSnapshotStore atomik widget_map_v2.bin içinde sürüm imzası+VIN+enlem/boylam+PNG tutar. Widget yalnız kendi aracı ve aynı koordinatla eşleşen kaydı gösterir; eski widget_map_snapshot.png okunmaz. Uygun kayıt yoksa mevcut konum radarı gösterilir; uygulamanın harita WebView'i görüntülendiğinde yeni kayıt üretilir. Gerçek cihazda WebView render/görsel doğrulaması sonraki açık derleme isteğini bekler.

Google adres çözümleme: açık koordinatlar önce yerel olarak okunur (gereksiz finalURL HTTP isteği yok), encoded query/%21data koordinatları desteklenir. Google url/link/deep_link_id/continue/q iç içe bağlantıları sınırlı çözülür. HTTP yönlendirmeleri try/finally kapatılır, non2xx hata olur;200HTML yanıtta yalnız sınırlı canonical/og:url metadata değerlendirilir. Sayfadaki rastgele script sayıları koordinat kabul edilmez. Place/search/isimli query için @ veya ll görüntü merkezi hedef koordinatı sanılmaz; isim için geocoding gerekir. URL'deki +boşluk ile %2Bliteralplus ayrımı düzeltildi. Metadata ve dahili !data yapıları best-effort; Google'ın garanti ettiği resmi çıktı sözleşmesi olarak sunulmaz.

Kullanıcı şu anda bozuk örnek linki bulamadığını, tekrar gördüğünde paylaşacağını bildirdi. Dolayısıyla kullanıcının tam linki üzerinde canlı başarı iddiası yoktur. Sentetik encoded/wrapper/metadata/viewport testleri, loopback302 yönlendirme denemesi ve araç/konum önbellek ayrımı testleri dahil122JVM testi geçti. Telefon Java kaynak tip kontrolü ve XMLkontrolleri geçti. APK/Android kaynak derlemesi, kurulum veya push yapılmadı; teslim APK'sı2Kasım sürümünde kalır.

Resmi dayanaklar: [Google Maps URL parametreleri](https://developers.google.com/maps/documentation/urls/get-started), [PixelCopy pencere kopyası](https://developer.android.com/reference/android/view/PixelCopy), [WebView görsel durum callback'i](https://developer.android.com/reference/android/webkit/WebView.VisualStateCallback).


## Son teslim — 2 Kasım 2026, üçüncü satır düğme büyütme/ortalama

Kullanıcı ekran görüntüsünde ABRP düğmesinin konum bloğuna yapışık ve küçük olduğu görüldü. Düğme40→56dp(%40büyük),metin13→17sp yapıldı; dar genişlikte12–17sp otomatik sığdırma korunur. Yeni widget_abrp_row kalan dikey alanı layout_weight=1 ile doldurur ve düğmeyi gravity=center ile üçüncü satırın ortasında tutar;8dp üst/alt padding,72dp minimum satır yüksekliği ve28dp kapsül köşeleri vardır. Konum ikinci satırdadır; mevcut aktif/deaktif renkleri ve tek dokunuş davranışı değişmedi. Gizleme/gösterme artık düğme yerine satır kapsayıcısına uygulanır.

Açık paketleme isteği tamamlandı: BUILD SUCCESSFUL in26s,114test/0hata,lint0hata/327uyarı,APK v2imzası doğrulandı. `outputs/ToggCompanion-current-debug.apk`,4140327bayt,SHA256 `58e695925a55f57fdfc1287fbcedb6192bb1ef8ac218eeef0a0586d29478216c`. Önceki teslim/metadata/rapor ve üretim APK kopyası `obsolete/2026-11-02-widget-button-size-package/move-manifest.json` ile arşivlendi. Dışarıda tek APK var. Bu sürüm cihazda kurulmadı; gerçek launcher görünümü kullanıcı denemesi bekliyor. Wear build/push yok.


## Son teslim — 2 Kasım 2026, widget satır düzeltmesi paketlendi

Kullanıcının açık paketleme isteği tamamlandı: BUILD SUCCESSFUL in6s,114test/0hata,lint0hata/327uyarı ve APK v2imza doğrulaması başarılı. Araç konumu/harita ikinci veri satırında; **ABRP Senkronizasyonu** aç/kapat düğmesi üçüncü satırda. Önceki Yol Tarifi/ABRP değişiklikleri de pakette. `outputs/ToggCompanion-current-debug.apk`,4140107bayt,SHA256 `2a2ac597bd8b91fedc1a4b6ae1c2ca9484e9a6c7875b45bbeedb5529ea1ffa51`. Önceki paket,metadata,ara rapor ve oluşturulan APK kopyası `obsolete/2026-11-02-widget-row-package/move-manifest.json` ile arşivlendi; dışarıda tek teslim APK var. Bu paket cihazda kurulmadı veya fiziksel widgetta denenmedi; Wear/push yok. Önceki kaynak-only satır düzeltmesi notu bu teslimin öncesine aittir.


## 2 Kasım 2026 — widget satır sırası düzeltmesi (kaynak)

İkinci veri satırına araç haritası/adresi geri getirildi; ABRP düğmesi bunun altındaki üçüncü satırda. Yükseklik varyantları şimdi70dp kompakt,160dp konumlu,250dp konum+ABRP şeklindedir; API26–30 aynı eşikleri kullanır. Düğme metni ve erişilebilir açıklaması **ABRP Senkronizasyonu** olarak güncellendi. Mevcut aktif/deaktif renkleri ve tek dokunuşlu servis kontrolü değişmedi. Java kaynak tip kontrolü ve widget XML kontrolü geçti. APK üretilmedi; mevcut teslim APK'sı önceki satır sırasındadır.


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


## 2 Kasım 2026 — widget araç zamanı ve telemetri yenileme

- Üstteki Son Veri, yalnız gösterilen gerçek araç snapshot'ının `timestampMillis` değeridir; `getLastRefresh`, yerel saat veya farklı bir son senkronizasyon kaydı yerine kullanılmaz. Gün/saat gösterilir; araç zamanı yoksa `--`. Harita rozeti de aynı snapshot zamanını kullanır. Demo verisinin zamanı gerçek araç verisi gibi gösterilmez.
- Yenile okuna basılması uygulamayı açmaz. WidgetRefreshJobService tek seferlik yeni TVA VehicleInfo/izinli mevcut fallback-strateji sorgusu yapar. Android31+ expedited iş istenir, kota nedeniyle kabul edilmezse normal acil iş planlanır. Android zamanlamayı geciktirebilir. Aynı iş sürerken tekrar dokunmalar birleştirilir; işlem sırasında ikon soluklaşır.
- Başarıda yalnız canlı, doğru VIN'e ait ve geçerli oturum bağlamındaki snapshot kaydedilir; hata/demo/cache sonucu eski telemetriyi ve görünen araç zamanını değiştirmez. Başarısızlık kullanıcıya kısa mesajla, ayrıntısı paylaşılabilir WIDGET günlüğüyle bildirilir. JobService sonlandırılınca geç kalan callback işleme alınmaz.
- Bu bir sunucudan telemetri okuma isteğidir. Fiziksel aracı uyandıran veya yeni ölçüm yayınlamaya zorlayan doğrulanmış bir komut eklenmedi. Sunucu aynı araç snapshot'ını döndürürse Son Veri saati aynı kalır; bu beklenen davranıştır.
- Bozuk/elipsleşen çizgi-yay oku yerine ortalanmış dolu vektör yenile simgesi kullanılır. Düğme35,2dp, ikon22dp, eşit6,6dp padding korunur.

Kaynak tip kontrolü,46XML/kaynak kimliği kontrolü ve20TelemetryParser JVM testi geçti. İlk çalışmada eski test kilometre sayacını eksik Yolculuğum mesafesi sanıyordu; bu beklenti0olarak düzeltildi (üretim parser davranışı değiştirilmedi). APK derlenmedi/kurulmadı; One UI üzerinde fiziksel görünüm, Android iş başlatma ve gerçek araç/API denemesi bekleniyor.

Android yaşam döngüsü dayanağı: [Broadcast alıcısında uzun iş sınırları](https://developer.android.com/develop/background-work/background-tasks/broadcasts), [JobService](https://developer.android.com/reference/android/app/job/JobService).


12 Ekim 2026 kaynak güncellemesi. One UI 8.5 görünümüne uyum hedeflenir; Samsung tarafından onaylanmış bir tema veya resmi 8.5 paletinin birebir kopyası değildir.

[Samsung One UI renk rehberi](https://developer.samsung.com/one-ui/color/system.html), koyu temada #3E91FF mavisini, geniş sakin yüzeyler üzerinde canlı küçük vurguları ve okunabilirliği önerir.

- Ana yüzey: koyu mavi → gerçek siyah, 28dp yuvarlak köşeler ve ince renkli kenar.
- Yenileme: 35,2dp mavi kapsül (önceki 44dp’nin %80’i), ortalanmış 22dp beyaz vektör ikon ve erişilebilir Türkçe açıklama.
- Yüksek voltaj pil göstergesi: eski dört aralık geri getirildi: %0–19 kırmızı #EF4444, %20–49 amber #FFB800, %50–79 One UI mavisi #3E91FF, %80–100 yeşil #10B981.
- 12V: %0–59 kırmızı, %60–79 amber, %80–100 yeşil; eski uyarı eşikleri korunur. Yüzdeler ve kutup işaretleri beyazdır; altlarında ayrı siyah etiket zemini bulunmaz. Batarya doluluk rengi yazıların arkasında görünür.
- Menzil: lacivert yol, mavi kenarlar, sıcak yol çizgileri; şarj sırasında mint çerçeve.
- Lastikler: mint kenarlı koyu kart, mercan uyarı rengi ve renk dışında da görülen ! işareti.
- Konum: mavi pin/halo ve lacivert radar; harita arka planının desatürasyonu korunur.

Widget paleti ayrı kaynak dosyasındadır; telefon/saat pastel OLED paleti değişmez. RemoteViews uyumlu mevcut kontrol türleri, tıklama akışları ve farklı boyut yerleşimleri korunur. Gerçek arka plan bulanıklığı eklenmez.

## Teslim durumu

Kullanıcının açık derleme isteğiyle 12 Ekim 2026 tarihinde telefon debug APK’sı başarıyla üretildi. XML sözdizimi ve KB tutarlılığı kontrol edildi. Güncel teslim APK’sı bu tasarımı ve geri getirilen batarya renk aralıklarını içerir. Emülatör/fiziksel One UI ekran kontrolü yapılmadı; kompakt/geniş widget görünümü kullanıcı tarafından cihazda değerlendirilecektir.

## Kullanıcı ekran görüntüsü sonrası düzeltme

Batarya yüzdeleri ve akü kutup işaretlerinin siyah etiketleri kaldırıldı. Yenileme düğmesi 35,2dp oldu; 22dp ikon 6,6dp eşit kenar boşluğu ile ortalanır. Fontun baseline/yan boşluk kaymasını önlemek için metin glifi yerine vektör kullanılır. Bu düzeltme kullanıcının sonraki açık derleme/yayın isteğiyle güncel telefon APK’sına alınmıştır. Fiziksel cihaz görünümü kullanıcı tarafından doğrulanacaktır.
