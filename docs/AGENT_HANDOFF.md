# Kullanım ve geliştirme devri — 12 Ekim 2026

## Son teslim — 18 Kasım 2026, şarj durum regresyonu

Açık paketleme isteğiyle telefon debug1.1/kod2üretildi (AF314).
CHRGMODE_CHARGE tanınır; metinsel şarj durumu true/false/unknown ayrılır,
bilinmeyen değer geçerli modu false ile ezmez. Önceki gerçek güç varsa
dinamik gösterim,yoksa yalnız Şarj oluyor davranışı ve kilometreJSON dahildir.
163test/0hata;lint0hata/330uyarı;BUILD SUCCESSFUL26s;v2imza ve
APK1.1/kod2doğrulandı. `outputs/ToggCompanion-current-debug.apk`,4161411bayt,
SHA256 `939fbf1ce31f52b05e05fc1c6d987120047de8f85b9023398e5897315251edad`. Önceki paket/metadata/raporlar ve generatedduplicate
`obsolete/2026-11-18-charging-state-package/move-manifest.json` ile arşivlendi.
Kurulum/gerçekşarjyanıtı/Drive/Wearderlemesi/dijitalanahtar/pushyok.

## 18 Kasım 2026 — şarj durum regresyonu (kaynak)

AF312–AF313: CHRGMODE_CHARGE tanınmaması ve metinsel chargingStatus değerinin
Boolean.parseBoolean ile false olması düzeltildi. 163testgeçti; APK yok.
Gerçek yanıt/kök neden doğrulanmadı. [Ayrıntılar](CHARGING_POWER.md).

## Son teslim — 16 Kasım 2026, şarj gücü ve durum gösterimi

Açık kullanıcı isteğiyle telefon debug1.1/kod2paketlendi (AF311). AC11/DC120
varsayımları kaldırıldı. Ölçüm yoksa yalnız Şarj oluyor; varsa servisten okunan
güçle Şarj oluyor- 80kW biçimi kullanılır (80örnektir,sabitdeğildir).
Başarılı TVAyanıtında güvenli CHARGING alan/değer/zaman tanısı kaydedilir.
Önceki kilometre JSON paylaşımı ve sürüş modu düzeltmesi dahildir.
158test/0hata;lint0hata/330uyarı;BUILD SUCCESSFUL7s;v2imza ve APK
içinden1.1/kod2doğrulandı. `outputs/ToggCompanion-current-debug.apk`,4160591bayt,
SHA256 `10758aaf9b9a02a551ae018ace20e90120cb3b554b885009a16f6da891f9e239`. Önceki APK/raporlar ve generatedduplicate
`obsolete/2026-11-16-charging-status-package/move-manifest.json` ile arşivlendi.
Fiziksel AC/DC/Drive/sürüş modu doğrulaması ve cihaz kurulumu yapılmadı.
Wear kaynak düzeltmeleri bu telefon APK'sıyla saat APK'sına dönüşmez; Wear
derlenmedi. Dijital anahtar işlemi ve GitHub push yapılmadı.

## 16 Kasım 2026 — şarj durum metni ve gerçek yanıt tanısı

AF309–AF310: güç yoksa yalnız Şarj oluyor, varsa Şarj oluyor- 80kW. Gerçek
TVAyanıtından güvenli CHARGING alan/değer/zaman tanısı;158testgeçti. APK yok.
[Doğrulama adımları](CHARGING_POWER.md).

## 16 Kasım 2026 — şarj gücü kaynak düzeltmesi

AC/generic11kW ve DC120kW varsayımları, sabit AC11kW etiketi kaldırıldı.
Ölçüm varsa gerçek güç; yoksa Bilgi yok. Telefon/saat kaynaklarında bilinmeyen
-1 korunur. 156 JVM testi/telefon kaynak-XML kontrolü geçti; saat değişen
kaynakları Tile imza stub'ıyla kontrol edildi, tam Wear doğrulaması değildir.
APK üretilmedi; son teslim1.1/kod2bu düzeltmeyi içermez.
[Ayrıntılar ve fiziksel doğrulama sınırları](CHARGING_POWER.md), AF307–AF308.

## Son teslim — 16 Kasım 2026, kilometre JSON paylaşımı

Kullanıcının açık paketleme isteğiyle telefon debug APK'sı üretildi. Sürüm
1.1/kod2 APK içinden doğrulandı. 147 test/0 hata, lint0 hata/332 uyarı;
BUILD SUCCESSFUL in7s, v2 imza doğrulandı. Ayarlar → Kilometre paylaşımı ile
Drive sağlayıcısında tek JSON, kullanıcı başlangıç/aralık seçimi ve manuel
gönderim dahildir. Önceki sürüş modu Konfor varsayımı düzeltmesi de pakette.
`outputs/ToggCompanion-current-debug.apk`, 4257481 bayt, SHA256 `8fe6c8cc6da15b47ebd0e36f798ce8e631cada629c55225ce0cb383f1273917a`.
Önceki APK/raporlar ve üretilen APK kopyası
`obsolete/2026-11-16-odometer-export-package/move-manifest.json` ile arşivlendi.
Gerçek Drive bulut eşitlemesi/Gemini okuması ve fiziksel araç/sürüş modu
karşılaştırması henüz denenmedi. Kurulum, Wear derlemesi ve push yapılmadı.

## 16 Kasım 2026 — kilometre JSON dışa aktarımı (kaynak)

[ODOMETER_JSON_EXPORT.md](ODOMETER_JSON_EXPORT.md), C11/AF303–AF305/F121–F126:
Ayarlar → Kilometre paylaşımı, tek-belge SAF Drive hedefi, kullanıcı başlangıç
tarih/saati ve tekrar dakikası, persisted JobScheduler8204/manuel8205. Yalnız
odometre ve araç/sorgu zamanları; ayrı sunucu/OAuth sırrı yok. 147 JVM testi
ve Java/XML kontrolü geçti; APK üretilmedi. Drive sağlayıcısı/bulut/Gemini
okuması ve reboot/ekran kapalı zamanlama fiziksel telefonda doğrulanmalı.

## 16 Kasım 2026 — sürüş modu gösterimi (kaynak)

AraçEco/uygulamaKonfor bildirimi üzerine eksik modunComfort varsayımı parser/model/DB/UI boyunca kaldırıldı; strict metinsel enum gösterimi eklendi,unknowndeğerBilgi yok.141JVM testi ve kaynak/XML kontrolü geçti; APK üretilmedi. Kaynak/önbellek/bulut güncelliği sınırı:[Sürüş modu gösterimi](DRIVE_MODE_DISPLAY.md).


## Son teslim — 16 Kasım 2026,1.1yolculuk süresi düzeltmesi

Kullanıcının açık APK isteğiyle süre düzeltmesi paketlendi. Sürüm1.1/kod2korundu (APK aapt2 ile doğrulandı). BUILD SUCCESSFUL in24s;136test/0hata,lint0hata/328uyarı,v2imza doğrulaması başarılı. `outputs/ToggCompanion-current-debug.apk`,4146291bayt,SHA256 `dda68e98077feb5cb8781b6bf0a614ea3cf0a43a5c712e5b4597fe36f8a3922f`. Üç yolculuk sekmesinde10a-bölme hatası ve toplam dakikaya saat ekleme riski giderilmiştir. Önceki1.1özellikleri de dahil.

Önceki paket/metadata,ara rapor ve üretilen kopya `obsolete/2026-11-16-trip-duration-package/move-manifest.json` ile arşivlendi; dışarıda tek APK var. Yeni APK sonrası başarılı telemetri yenilemesi eski cache'deki küçültülmüş süreyi günceller. Bu tur cihaz kurulumu/canlı araç doğrulaması,Wear build,push yapılmadı.


## 16 Kasım 2026 — yolculuk süresi kaynak düzeltmesi

Dakikayı büyüklüğüne göre10'a bölen tahmin kaldırıldı; üç yolculuk sekmesi saat/dakika birimlerini korur.136JVM testi ve Java kaynak kontrolü geçti. APK üretilmedi; önceden cache'lenmiş süre yeni sorgudan sonra düzelir. Ayrıntı:[Yolculuk süresi](TRIP_DURATION.md).


## Güncel teslim — 16 Kasım 2026, sürüm1.1/kod2

Kullanıcının açık sürüm/paketleme isteği tamamlandı. app/build.gradle.kts versionName **1.1**,versionCode **2**. APK içinden aapt2 ile sürüm doğrulandı. Gradle testDebugUnitTest/lintDebug/assembleDebug **BUILD SUCCESSFUL in36s**; **133test/0hata**,lint **0hata/328uyarı**,APK **v2imza doğrulaması başarılı**.

`outputs/ToggCompanion-current-debug.apk`,4146203bayt,SHA256 `1aada508bb83cfcf55057842506053430bd6ea1fa0ea3a3f2c73ebd635c37019`. Widget map-only/VIN+konum önbelleği,Google URL iyileştirmeleri,gönderim kabul/doğrulama/belirsizlik ayrımı,ana sayfa ve Sürüş toplam araç kilometresi kartları,gerçek kaydedilen kart sırası ve tercih geçişi bu pakete dahildir. Önceki16Kasım kaynak-only notlar bu teslimden önceki aşamaları anlatır.

Önceki1.0APK/metadata, tamamlanmış ara kaynak raporları ve üretilen APK kopyası `obsolete/2026-11-16-v1.1-package/move-manifest.json` ile arşivlendi; dışarıda tek teslim APK var. Bu paket fiziksel telefona/emülatöre kurulmadı; canlı araç/Google örnek linki ve gerçek widget çizimi ayrıca denenmelidir. Wear build/push yok. Yeni source değişiklikleri için açık build kısıtı devam eder.


## 16 Kasım 2026 — toplam kilometre ve kart sıralaması (kaynak)

Ana sayfaya araç odometresinden ayrı Toplam Kilometre kartı eklendi; örnek14280km fallback kaldırıldı. Konum/ABRP dahil özet kartları artık kayıtlı sırada çizilir ve yeni kilometre/ABRP kartları özelleştirilebilir.133JVM testi, kaynak tip kontrolü ve XMLkontrolü geçti. APK üretilmedi. Birim, cache, bulut güncelliği, tercih geçişi ve sınırlar: [Ana sayfa ve kilometre](HOME_LAYOUT_AND_ODOMETER.md).


## 16 Kasım 2026 — gönderim kabulü ve geri okuma sonucu ayrıldı (kaynak)

Kullanıcı hedef araçta göründüğü halde “Hedef gönderilemedi veya gönderim doğrulanamadı” mesajını bildirdi. Kaynakta iki kayıp ayrım vardı: olumlu POST2xx sonrası GET doğrulama hatası onUncertain üretiyor; GezRouteBridge bu callback'i onError'a düşürüyor; VehicleRouteDialog bütün başarısız/belirsiz sonuçlara aynı başarısızlık metnini gösteriyordu. Kullanıcının son isteğinin canlı HTTP/gövde kaydı görülmedi; özel yanıtın false veya GET uyumsuzluğu olduğu varsayılmaz.

Yeni zincir dört durumu korur: VERIFIED(kayıt geri okundu),ACCEPTED(geçerli POST2xx kabulü,GET doğrulanmadı),UNCERTAIN(yanıt/false kayıt sonucu kesinleşmedi),FAILED(gerçek gönderim hatası). Kabul durumunda kullanıcıya **Hedef gönderim için kabul edildi. Araç ekranındaki hedefi kontrol edin.** gösterilir; GET ayrıntısı yalnız ROUTE/ACCEPTED uygulama günlüğündedir. VERIFIED ve ACCEPTED aynı hedefi tekrar gönderme düğmesini pasif tutar. UNCERTAIN hedef ulaşmış olabilir/araç ekranını kontrol edin mesajıyla aktarılır; “gönderilemedi” denmez. Otomatik DELETE/POST tekrarı eklenmedi.

POST false olsa da mevcut sıkı GET doğrulaması gönderilen istemci/koordinat/adres/durakları eşleştirirse kayıt VERIFIED sayılır; false+eşleşmeyen GET belirsiz kalır. GET eşleme kuralları gevşetilmedi. Hesap/araç değişiminde sonuç belirsiz kalır. Bulut kaydı veya kabulü fiziksel araç ekranına teslim kanıtı olarak sunulmaz.

Java kaynak tip kontrolü ve125JVM testi geçti;3yeni sonuç regresyonu (olumlu POST+GET hatası kabul, false+eşleşen GET doğrulanmış, false+eşleşmeyen GET belirsiz). APK/Android kaynak derlemesi,kurulum,canlı rota gönderimi,push yok. Mevcut2Kasım APK'sı yeni sonuç ayrımını içermez.


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


## 2 Kasım 2026 — güncel kaynak, henüz APK üretilmedi

ABRP artık ayrı bir sayfa: Senkronizasyon ve Ayarlar sekmeleri. Ana kart yalnız aktif/deaktif ve son başarılı gönderimi gösterir. Kullanıcı açınca `AbrpSyncService` hemen ve ardından 60 saniyelik aralıklarla yeni TVA isteği başlatır; `AbrpSyncEngine` yalnız bu isteğin canlı sonucunu ABRP'ye gönderir. Tüketim yalnız **Yolculuğum** kaydından gelir; eksik tüketim uydurulmaz. Demo/önbellek sonuçları gönderilmez. Araç sunucusunun yayınladığı verinin yaşı günlükte tutulur; yeni HTTP isteği fiziksel aracın yeni veri yayınladığını garanti etmez.

ABRP plan sorgusu, rota değişimi takibi, otomatik araca gönderim, bunların ayarları ve eski JobService kaldırıldı. Eski 8201/8202 işleri iptal edilir, rota tercihleri temizlenir. Kullanıcının çalıştığını doğruladığı **manuel harita/rota aktarımı korunur**; Ayarlar → Akıllı cihazıma aktar üzerinden veya paylaşım menüsünden açılır.

Kullanıcı tarafından başlatılan dataSync foreground servis, sürekli bildirim ve Durdur eylemi ile arka planda sürer. Kısmi wake lock ve oturum en fazla 6 saatle sınırlandırılmıştır. Android 15+ ayrıca 24 saatte toplam 6 saat arka plan dataSync kotası uygular; onTimeout servis ve anahtarı kapatır. Zorla durdurma/işletim sistemi kısıtları sonrası süresiz veya tam zamanlı çalışma garantisi verilmez. Kullanıcı sayfadan yeniden açabilir; otomatik boot/restart veya kota atlatma yoktur.

Teknik BLE/GATT/sertifika, rota ve ABRP hata bilgileri özel depolamada dönen `AppLog` günlüklerine yazılır. Ayarlar → Uygulama günlüğünü paylaş, FileProvider üzerinden salt okunur erişim verir. URL, Bearer, e-posta, UUID, VIN, uzun anahtar ve koordinat çiftleri maskelenir. Ham ağ gövdeleri, anahtarlar ve sertifikalar loglanmaz. Genel ayarlardaki manuel erişim tokenı/kullanıcı kimliği/demo kontrolleri ve ekran tanı dökümleri kaldırıldı; kurulum/onay gerektiren kullanıcı yönlendirmeleri korunur.

Kaynak Java tip kontrolü (geçici R sabitleri, SDK36 ve önbellekteki bağımlılıklar), XML/kaynak başvuruları ve JVM testleri ile kontrol edildi. Android kaynak derlemesi, DEX, imza, APK, telefon kurulumu ve GitHub push yapılmadı. Sonraki açık paketleme isteğinde gerçek Android build/lint ve cihazda ekran kapalı arka plan testi gerekir. Ayrıntılar: [ABRP senkronizasyonu](ABRP_SYNC.md).


## Güncel teslim — 25 Ekim rota paylaşımı APK

Kullanıcının açık derleme isteğiyle telefon debug derlendi: **BUILD SUCCESSFUL in22s**,33 görev/4 executed. APK outputs/ToggCompanion-current-debug.apk, 4135362 bayt, SHA256 `f30dadc60cd3296afe3335fa012e4e9f0702663fc526ac19893716b62814b8f1`. Companions paylaşım API/JSON düzeltmesini içerir; önceki kaynak-only notlar tarihseldir. Bu tur telefona kurulum, Wear build, uygulama testleri veya push yapılmadı. Yeni servisin gerçek yanıtı ve fiziksel araç teslimi hâlâ kullanıcı denemesini bekliyor. Önceki teslimler obsolete/2026-10-25/rota-paylasim-oncesi manifestiyle arşivlendi.


## 25 Ekim — rota paylaşımı kaynak düzeltmesi

HTTP200/araçta görünmeme bildirimi sonrası orijinal araç paylaşımı Companions DELETE→POST olarak bulundu. PlannedJourneys planlanan rota kaydıdır. Kaynak/JSON düzeltildi; APK derlenmedi.21 Ekim teslimi bu düzeltmeyi içermez. Ayrıntı: [Rota aktarımı](DIRECT_VEHICLE_ROUTE_TRANSFER.md). Kullanıcı aynı hedefin Trumore üzerinden araçta göründüğünü bildirdi.


## En son araç denemesi / teslim — 21 Ekim

Güncel telefon APK 4132786 bayt, SHA256 `f53a326a71ffea493f41006738c807a3d173d5f0d7870802669500b31901eb39`; kurulum başarılı. İki BLE bildirim kanalı ve sertifika parçalı aktarımı çalışıyor; SAM anahtarı aşamasında tekrar FIRST yanıtı nedeniyle imzalı oturum henüz kurulamadı. Kullanıcı araçtan ayrıldığı için canlı denemeler durduruldu. Güncel ayrıntı: [Paylaşılan anahtar](SMART_ACCESS_SHARED_ACCOUNT_PREPARATION.md). Eski teslim/hash kayıtları tarihseldir.


## Güncel teslim — 21 Ekim 2026

Paylaşılan davet kabulü ve sahip onayı sonrası guest sertifika canlı doğrulandı. BLE oturumu/komut doğrulaması bekliyor. Güncel telefon APK: 4131834 bayt, SHA256 `48c94d4fd884eeb648a7957564a0c593f3ab9747d61364286cac94e10ce6368c`; eski hash/eksik özellik kayıtları tarihseldir. Ayrıntı ve güvenli devam adımı: [Paylaşılan anahtar](SMART_ACCESS_SHARED_ACCOUNT_PREPARATION.md). Bu teslimde Wear/push yok.


## Başlangıç

Önce kök `AGENTS.md`, ardından `KB_INDEX.md` → `togg-companion/IDMAP.md` → `KG.adj` → ilgili `FACTS.jsonl` kayıtlarını okuyun. Tarihsel fact’leri değiştirmeyin; yeni düzeltme fact’i ekleyin. Güncel widget davranışı AF196/AF198/AF199 ile, ABRP davranışı AF185–AF193 ile belgelenir. En son teslim durumu `PROJECT_STATUS_2026_10_12.md` içindedir.

## Çalıştırma ve kullanım

- Telefon: native Android Java/XML, `app/`, minSDK26/compileSDK36/Java17. Paket `com.togg.companion`. Wear OS modülü `wear/`; saat telemetrisi telefonun Google Wearable Data Layer aktarımından gelir.
- Son telefon debug APK’sı GitHub Releases üzerinden ve yerel workspace `outputs/ToggCompanion-current-debug.apk` yolundan alınır. Sonraki açık kullanıcı isteğiyle saat debug APK’sı da derlenip yayımlandı; yerel güncel teslim `outputs/ToggCompanion-wear-current-debug.apk` dosyasıdır. Telefon paketi önceki GitHub Release üzerinden erişilebilir. Sürüm adı/kodu hâlen1.0/1; farklı bilgisayardaki debug imzası aynı olmayabilir. Güncelleme imza uyuşmazlığı verirse kullanıcı verilerini kaybetmeden önce imza/kurulum koşulunu inceleyin.
- Togg ID oturumunu açın; gerçek araç VIN ve hesap bağlamını doğrulayın. Sırları, tokenları veya sertifikaları repoya/loga eklemeyin. Demo verisi gerçek araç işlemi kanıtı değildir.
- Smart Access ekranında VIN/truId/sahiplik bilgisiyle “Sertifikayı al ve yetkilendir” akışını kullanın; Bluetooth/konum izinlerini cihaz koşullarına göre verin. Medya Bluetooth eşleşmesi BLE oturumu değildir. Sertifika, servis keşfi ve imzalı oturum tamamlanmadan komut hazır sayılmaz. Kilit/bagaj/READY hareketleri kullanıcı tarafından fiziksel araçta doğrulanmalıdır. Motor durdurma/READY’den aksesuar moduna dönüş komutu doğrulanmış kabul edilmez.
- Manuel hedef aktarımı Companion rota ekranından navigasyon bulut API’sine gönderilir. Kullanıcı bulut kabulünü bildirdi; aracın Gez ekranına teslim ayrı kontrol gerektirir. Trumore’a otomatik fallback yapılmaz.
- ABRP ayarlarına ilgili araç canlı veri tokenını kaydedin. Telemetri gönderimi ve otomatik rota aktarımı ayrı seçeneklerdir; ikisi yaklaşık4 dakikada bir çalışır. “Rotayı şimdi kontrol et” ile beklemeden kontrol isteyin. İlk kullanılabilir plan bir kez gönderilir; sonrasında rota imzası değişirse yeni hedefler gönderilir. “Takibi sıfırla” kabul edilmiş/belirsiz kayıtları temizleyerek yeniden göndermeye izin verir; belirsiz POST sonrasında bunu bilinçli kullanın.
- Widget’ı launcher’dan ekleyin, kompakt/geniş boyutlarda değerlendirin. Yenileme aracı sorgular; gösterilen saat verinin eskiliğini belirtir. Son park yeri canlı araç hareketi anlamına gelmez.

## Neyi, neden, nasıl değiştirdik?

| Alan | Gerekçe ve uygulama | Kaynak / ayrıntı |
| --- | --- | --- |
| BLE | Prototip komutların yerine sertifika/nonce/imza/MTU/parça/ACK akışı; araç hazır olmadan komut engeli | `VehicleBleManager`, `SmartAccessSession`, `SMART_ACCESS_IMPLEMENTATION.md` |
| Pasif kontroller | Kullanıcının aracında iklimlendirme ve desteklenmeyen uzaktan komutlar aktif değil; yerel başarı taklidi kaldırıldı | `MainActivity`, `OLED_GLASS_DESIGN.md` |
| HTTP301 | Eski host ana sayfaya yönlendiriyordu; yönlendirmeyi takip etmek yerine doğrulanmış navigasyon host’u kullanıldı | `TvaApiClient`, `HTTP301_ROUTE_FIX.md` |
| Rota | Gelecek şarj durağı origin diye atılmasın; ayrı upcoming hedef modeli ve aynı VIN’in konumu origin olarak kullanılır | `GezRouteBridge`, `TvaApiClient` |
| ABRP |4 dakika canlı veri ve değişen plan kontrolü; koordinat sırası/rota kimliği/geometri imzası, hesap+VIN+token kapsamı; aynı rotaya tekrar engeli | `AbrpClient`, `AbrpJobService`, `SecureTokenStorage`, `ABRP_AUTOMATIC_ROUTE_SYNC.md` |
| OLED | Telefon/saat gerçek siyah ve pastel vurgular; widget için ayrı canlı One UI paleti | `widget_colors.xml`, `WIDGET_ONE_UI_DESIGN.md` |
| Batarya | Eski eşikler geri getirildi: HV20/50/80 kırmızı/amber/mavi/yeşil,12V60/80 kırmızı/amber/yeşil | `ToggAppWidgetProvider` |
| Widget düzeltmesi | Kullanıcı görüntüsündeki siyah etiketler kaldırıldı.44dp buton35,2dp oldu;22dp beyaz vektör6,6dp eşit padding ile ortalanır; font baseline kayması giderildi | `widget_togg.xml`, `ic_widget_refresh.xml`, AF198 |

## Sınırlar ve sonraki çalışma

Cloud2xx, araç ekranına teslim veya fiziksel hareket kanıtı değildir. ABRP’nin kullanıcıya özel `get_latest_plan` JSON’u burada görülmedi; belirsiz alternatif/boş veya bozuk hedef gönderilmez. SoC tek başına rota değişikliği sayılmaz. ABRP SoC tahminlerini araç payload’ına ekleyen doğrulanmış alan henüz yok; araç hedeflerden kendi yolunu hesaplayabilir. Android JobScheduler/pil/ağ kısıtları nedeniyle4 dakika yaklaşık aralıktır.

Belirsiz POST sonucu otomatik tekrar edilmez; gönderim öncesi attempt ve kabul sonrası hash saklanır. Önceden başlamış POST ayar kapatılarak geri alınamaz. Canlı telemetri başarısızsa cache ABRP’ye gönderilmez. Eski motor komutları veya key/sertifika tahminleri eklemeyin. Orijinal APK/AOT kanıtı yerel `work/trumore-analysis/` içindedir ve üçüncü taraf decompile dosyaları repoya alınmaz.

## Derleme, teslim ve kontroller

Sadece açık kullanıcı derleme isteğiyle `./gradlew.bat :app:assembleDebug --offline --console=plain` çalıştırın; Windows’ta Android Studio JBR `JAVA_HOME`, SDK `ANDROID_HOME` gerekir. Offline cache yoksa bağımlılık erişimi ayrıca gerekir. Yeni APK’yi `outputs/` içine taşıyın; eski APK/teslim raporlarını göreli yollarını koruyan manifest ile `obsolete/` altına arşivleyin. APK’ler Git’e eklenmez; kullanıcı yayın isterse GitHub Release asset olarak yüklenir.

KB için `node scripts/validate-kb.cjs`, kaynak diff için `git diff --check` kullanın. Testleri kullanıcı istediğinde çalıştırın; eski110 test sonucu yeni kaynakların test edildiği anlamına gelmez. Son telefon ve saat debug derlemeleri başarılıdır; cihaz/emülatör/BLE/ABRP canlı testleri yapılmaz. Kaynak commit SHA, APK SHA256, boyut ve derleme günlüğünü teslimde kayıt altına alın. Git author mevcut kullanıcının kimliği olmalıdır; yeni yazar uydurmayın.

## Saat APK’sı kurulumu ve eşitleme

Saat paketi yalnız Wear OS cihaza kurulmalıdır; telefon APK’sı saatte, saat APK’sı telefonda kullanılmaz. Saat minSDK30/targetSDK34/compileSDK36; package ID telefonla aynı `com.togg.companion`, namespace `.wear`’dır. Data Layer için telefon ve saat uygulama kimliği ile imza eşleşmelidir; aynı makinede aynı debug anahtarıyla derlenmiştir. Saatte token girmeyin: telefon oturumu/araç ayarları ve telemetriyi yönetir.

Telefonda son sürümü kurup giriş/araç seçimini tamamlayın, telefonu saatle eşleyin; saatte yeni APK’yı kurup uygulamayı açın. Saat yenilemesi telefonun canlı veri isteğine gider. İstenirse Galaxy Watch Tile’ını ekleyin; “Araca yürü” için saat konum izni ve harita uygulaması gerekir. Veri gelmiyorsa Data Layer düğüm erişimini, iki taraftaki paket/imzayı, telefon arka plan erişimini ve veri zamanını kontrol edin. Eşitleme veya BLE komut çalışması derleme başarısından çıkarılamaz.

Saat APK’sı yayını telefon widget’ını veya ABRP zamanlamasını değiştirmez; ABRP4 dakikalık işler telefondadır. Depoda kaynak değişikliği gerektirmeyen paketleme yapıldı; bu tur yalnız dokümantasyon/KB teslim bilgisi değişti. Workspace tek aktif APK kuralı gereği telefonun yerel teslimi arşivlenir; önceki telefon release asset’i korunur.

## 15 Ekim sertifika tanı güncellemesi (kaynak)

Kullanıcının MTU247/TRANSPORT_READY ve sertifika HTTP500/Base64 hata bulguları üzerine parser hata ayrımı, request stage ve GATT callback tanısı eklendi. Bu düzeltme henüz APK’ye alınmadı; HTTP500 kök nedeni çözülmüş değildir. [Kanıt, gerekçe ve sonraki adım](SMART_ACCESS_CERTIFICATE_DIAGNOSTICS.md).

## 16 Ekim — ABRP son sürüş tüketimi

Kullanıcı seçimiyle ABRP consumption kaynağı currentTrip/son sürüş oldu; pozitif/sonlu gerçek mesafe-tüketim yoksa alan gönderilmez ve currentTrip örnek fallback değerleri kaldırıldı. Kaynak değişikliği henüz APK’de yok. ABRP referans tüketimi/kalibrasyonunu zorla ayarladığı doğrulanmış değildir; ayrıntı ABRP_AUTOMATIC_ROUTE_SYNC.md.

## Son kullanıcı düzeltmesi — ABRP kaynağı Yolculuğum

Önceki “Son sürüş/currentTrip” seçimi kullanıcı tarafından değiştirildi: ABRP consumption artık yalnız **Yolculuğum (`myTrip.avgConsumption`)** kaydından, mevcut normalize kWh/100km birimiyle alınır. Diğer sekmeler/legacy lastTrip fallback olarak kullanılmaz. myTrip eksik veya mesafe/tüketim sonlu ve pozitif değilse consumption gönderilmez. Canlı myTrip parser’ındaki örnek17,8 tüketim/odometre tabanlı mesafe fallback’i sıfırlandı.4 dakika aralık korunur. Kaynak güncellemesi; APK derlenmedi, canlı ABRP gönderimi ve push yapılmadı. ABRP’nin özel consumption alanını referans kalibrasyonunda kullandığı doğrulanmış değildir.

## Son zamanlama düzeltmesi —3 dakika canlı telemetri

Kullanıcı isteğiyle araç API’sinden yeniden telemetri alıp ABRP’ye gönderme işi180000ms/3 dakika aralığına çekildi. Her arka plan çalışması fetchTelemetry yapar; isLive/non-null/gerçek VIN/oturum koşulları olmadan, API hatasında veya cache fallback’inde ABRP’ye gönderim yapılmaz. Ana ekranın otomatik gönderimi de yalnız fetch callback’inin canlı non-null verisini kullanır. Telemetri3dk ve rota kontrolü4dk ayrı sabitlerdir; tüketim kaynağı Yolculuğum/myTrip olarak kalır. Zincirlenen JobScheduler/pil/ağ kısıtları nedeniyle3dk tam saat garantisi değildir; taze HTTP yanıtı aracın bulut zaman damgasının da o an olduğu anlamına gelmez. Kaynak düzeltmesi; APK/test/canlı gönderim/push yapılmadı.

## 19 Ekim 2026 — Güncel telefon paketi

Kullanıcının açık PAKETLE isteğiyle telefon debug APK’sı başarıyla derlendi. Paket bekleyen sertifika parser/tanı değişikliklerini, Yolculuğum/myTrip tüketimini,3 dakika canlı telemetri sorgusu/gönderimini ve e-Motor altındaki fazla rota butonunun kaldırılmasını içerir. Rota kontrolü4dk kalır. Yerel dosya `outputs/ToggCompanion-current-debug.apk`, boyut4111034 bayt, SHA256 `61d525deb1be63d53ba77a7ef7f53d76559eeceb667158ce76519f71c5a5b634`. Temel commit `73dcc2f4582234bd93c34ebb35773cae1fe07d8b` üzerine kaydedilmemiş kaynak değişiklikleri dahildir. Bu tur saat derlenmedi; eski saat teslimi tek aktif APK kuralıyla manifest kullanılarak obsolete/2026-10-19/paket-oncesi altında arşivlendi. GitHub push/release yapılmadı. Önceki kaynak-only notları bu yerel paket için aşılmıştır. HTTP500 kök nedeni ve ABRP özel consumption alanının kalibrasyon etkisi doğrulanmadı; cihaz testleri manuel.

## Son kaynak eklentisi — sertifika yanıt şeması

Yeni kullanıcı tanısı GET_CERTIFICATE/CERT_MISSING ve CREATE_CERTIFICATE/HTTP500 durumlarını ayırdı. Sırları içermeyen HTTP/JSON alan-tür tanısı kaynakta eklendi. Son derlenmiş telefon APK’sı bu yeni eklentiyi içermez; build/test/push yapılmadı. UpToDate şüphesi ve kesin sınırlar SMART_ACCESS_CERTIFICATE_DIAGNOSTICS.md içinde.

## Son19 Ekim paketi — güvenli sertifika şema tanısı

Yeni açık paketleme isteğiyle telefon assembleDebug başarılı. Güncel APK artık CERT_HTTP/CERT_SCHEMA tanısını da içerir. Dosya outputs/ToggCompanion-current-debug.apk;4112546 bayt; SHA256 `2aa7e6b17daf5c292092c5c052255b85a27664abf934bf5fcdd7c01970deec21`. Temel commit `73dcc2f4582234bd93c34ebb35773cae1fe07d8b` + bekleyen kaynak değişiklikleri. Önceki paket manifest ile obsolete/2026-10-19/sertifika-sema-paketi-oncesi altında arşivlendi. Test/canlı istek/araç doğrulaması/push yok. Sonraki kullanıcı denemesinde BLE bağlanıp bir kez Sertifikayı al ve yetkilendir sonrası tanı paylaşılmalıdır; sertifika HTTP500 kök nedeni henüz açık.

## 19 Ekim — Sahip kaydı / paylaşılan anahtar ayrımı

[Kaynak araştırması ve uygulama planı](SMART_ACCESS_ENROLLMENT_RESEARCH.md). Önemli düzeltme: ikinci CreateCertificate yolu0x2087a44 isOwnerCertificate=false gönderir; sessionId/vehicleName paylaşılan/saat anahtarı çağıranlarıyla bağlantılıdır. Önceki bu varyantın true olduğu notları aşılmıştır. Araç içi ilk kayıt ile davet kabulü ayrı süreçlerdir; mevcut500’ün kesin nedeni belirlenmedi. Bu araştırmada uygulama kodu/derleme/canlı istek yok.


## 20 Ekim — Emülatör denemesi

Orijinal Trumore kuruldu ve Togg ID formunda bir giriş gönderimi yapıldı. Türkçe ana ekran görüldü; hesap/dijital anahtar/sertifika doğrulanmadan Windows qemu üç kez çöktü. Ayrıntılar SMART_ACCESS_ENROLLMENT_RESEARCH.md / AF216. AVD Togg_Trumore_API35 ve SDK yerel kullanıcı dizininde. Kimlik bilgilerini dosyalara eklemeyin; bu tur Companion build/push yok.


## 20 Ekim — Gerçek telefon / Trumore2.1.26

AF217 ve SMART_ACCESS_ENROLLMENT_RESEARCH.md son bölüm: mevcut oturumda dijital anahtar/paylaşım UI incelendi. Davet KeySharing?seId&no&vin URL yapısını kullanıyor; CreateSession log adı gözlendi. Paylaşım gönderilmedi; sertifika akışı çözülmedi, araç uzakta. Kurulu2.1.26/663 kod splitleri obsolete altında, SHA256 manifesti work/trumore-analysis/analysis/device-2026-10-20/ içinde. Eski2.1.25 AOT adreslerini yeni sürüme taşımayın. Companion build/push yok.


## 20 Ekim —2.1.25 /2.1.26 karşılaştırması

F95: docs/TRUMORE_2_1_26_COMPARISON.md; AF218–AF220. Orijinal2.1.26/663 splitleri arşivde; karşılaştırma kanıtları work/trumore-analysis/analysis/version-diff-2.1.25-2.1.26/.58/10184 ortak girdi farklı; owner/shared bool ayrımı ham ARM64 ile yeni sürümde korundu. Yeni adres tablosu LOAD yeridir, fonksiyon başlangıcı değildir. Sertifika500 çözülmedi. Gelecek işler için kanıt/kabul koşulları F95 bölüm5; uygulama kodu/build/push değişmedi.


## 21 Ekim — Doğru BLE hedefi ve kaynak düzeltmesi

AF221–AF223 / SMART_ACCESS_ENROLLMENT_RESEARCH.md: BLEsmart seçimi147 verdi; taranan My T-SAS hedefi GATT0/MTU247/TRANSPORT_READY oldu, yetkili oturum açılmadan yaklaşık35sn sonra koptu. Fetch200/CERT_MISSING sürüyor. Kaynakta tarama ilk sıraya alındı, generic smart adaylığı kaldırıldı, servis kanıtı etiket/sıralama eklendi. Bunlar ve AF212 ek alan-adı tanısı mevcut APK içinde yok. Bu tur build/install/push/app test suite yok; yeni açık paketleme isteği gerekiyor. Mevcut-telefon araç onayı yeni Companion anahtarı kaydı sayılmadı.


## 21 Ekim — Son telefon APK kuruldu; Failure ayrıştırıldı

Kullanıcının açık Derle ve telefona kur isteğiyle telefon APK derlendi/güncellendi. Güncel çıktı outputs/ToggCompanion-current-debug.apk;4114798 bayt; SHA2562643bc7757558d147248b89fa7c83028bba7f3c8275965642a93876bb4500ece. AF212/AF223 ve güvenli resultEnum/resultIndex tanısı dahil; resultEnum non-Success sertifika olarak parse edilmeden reddedilir. Manuel sonuç PHONE_RELATION Updated/index2; GET_CERTIFICATE Failure/index1, ikisi HTTP200. Son APK açık CERT_SERVER_FAILURE mesajını gösterdi. Önceki source-only/build-yok notları bu teslim için aşılmıştır. Failure nedeni çözülmedi; yetkili Companion komutu yok. Wear/push/app test suite yok. Güncel araştırma ve sonraki adım SMART_ACCESS_ENROLLMENT_RESEARCH.md / SMART_ACCESS_CERTIFICATE_DIAGNOSTICS.md son bölümlerinde.


## 21 Ekim — Devam denemesi / yeni tanı yalnız kaynakta

AF227–AF229: kurulu APK ile tek create HTTP500, sonraki ayrı fetch CERT_SERVER_FAILURE. Sertifika yok; yeni BLE/araç komutu denemesi yok. Non2xx gövdesinin sınırlı/sabit alanlı CERT_ERROR_SCHEMA tanısı kaynakta hazır; mevcut APK’de yok. Yeniden build/install/push yapılmadı; APK SHA2562643bc7757558d147248b89fa7c83028bba7f3c8275965642a93876bb4500ece. Derleme açık kullanıcı isteği gerektirir. Orijinal timeToWaitBeforeFetch alanı yeniden bulundu, birimi/çağıran uygulaması tamamlanmadan bekleme tahmini eklenmedi; create500 nedeni çözülmedi. Ayrıntılar F93/F94 son bölümleri.


## 21 Ekim — Eksik ClaimCarToUser ve gerçek owner anahtarı çakışması

AF230–AF234 / F96 SMART_ACCESS_SHARED_ACCOUNT_PREPARATION.md: HTTP hata gövdesi tanısı açık kullanıcı izniyle derlendi/kuruldu; tek create500 yanıtı JSON nesnesi değil. Ardından orijinal2.1.25 ve2.1.26’da owner sertifikasından önce ClaimCarToUser bulundu. Query ownerPublicKey/vin/overwritePhoneRelation=false, POST boş JSON. Kaynağa bu adım eklendi; yalnız Success/Updated/RelationAlreadyExists sonrası create, OverwriteDetection sonrası OWNER_PHONE_CONFLICT ve durma. OwnerChange ve diğer bilinmeyen/başarısız sonuçlar create’e geçirmez. Gerçek UI denemesi OWNER_PHONE_CONFLICT verdi; bu yalnız server OverwriteDetection ile üretilir. Böylece eksik kayıt ve mevcut farklı owner anahtarı ayrıldı; create500 backend istisnası tam açıklanmadı.

Kullanıcı mevcut Trumore anahtarını koruyup ayrı hesaba paylaşım yolunun hazırlanmasını seçti; ayrı hesap henüz yok. Overwrite=true, anahtar silme veya otomatik kendi kendine davet yapılmadı. F96 guest davet endpointleri, nonce/imza zinciri, bağımsız hesap depolaması ve kalan uygulama/manuel doğrulama adımlarını içerir. Guest kabul entegrasyonu henüz uygulanmadı. Kullanıcıya ait ikinci hesap ve davet bağlantısı sonraki canlı aşama için gerekiyor.

Son owner kayıt düzeltmesi içeren APK kuruldu: outputs/ToggCompanion-current-debug.apk; 4116002 bayt; SHA256 0fb7a57d67041056667e207e331f9f4672ce4d5265c26f7c89ae6efc875a3dc6. Önceki source-only HTTP tanı notları bu paketle aşılmıştır. Tek aktif APK; önceki iterasyonlar manifestle obsolete altında. Wear/push/app test suite yok. BLE yetkili komut doğrulanmadı.


## 21 Ekim — Ayrı hesap giriş/önizleme APK

AF236–AF240/F96: kullanıcı ayrı hesabı ve daveti sağladı; değerleri repoya kopyalamayın. Ayrı şifreli guest OAuth saklama/refresh, PKCE state’li guest login ve yalnız GET sessionDetail önizleme eklendi. Ana telemetri hesabı değiştirilmez. Açık build/install isteği tamamlandı: APK4122834 bayt SHA256ba98940e6b2a7c0d0eabd84aeb520bf3acf9591492221d9b5d07924dd26a368d. İki yeni kurulum butonu/form manuel görüldü; kullanıcı cihazda giriş yapacak. Davet kabulü/PhoneKey/guest sertifika/BLE bağlamı henüz uygulanmadı; gerçek guest OAuth/önizleme sonucu henüz yok. F96 son bölüm protokol ve sınırları içerir.


## 25 Ekim 2026 — 442157 ekranı: scalar false ve aşama ayrımı

Kullanıcının ekranındaki "Paylaşım servisi işlemi onaylamadı" metni F08'de yalnız
literal `false` gövdesinden üretiliyordu. Önceki APK bu metinde DELETE/POST ve HTTP
kodunu sakladığı için ekran tek başına hangi aşamada durulduğunu kanıtlamaz.

Orijinal `companion-save-api.asm.resolved.txt` içindeki 0x1fe8198–0x1fe81ac,
DELETE sonucunu await edip değeri kullanmadan null döner. Dispatch
0x1fce4d8–0x1fce4fc de DELETE sonrası boolean kontrol etmeden POST'a geçer.
Bu nedenle F08 artık yalnız başarılı HTTP DELETE'in literal `false` yanıtında
POST'a devam eder. `false` değerinin sunucuda "kayıt yok" anlamına geldiği
kanıtlanmış değildir; bu anlam kullanıcıya iddia edilmez.

POST false hâlâ belirsiz sonuçtur; başarılı kayıt/teslim sayılmaz ve tekrar
edilmez. HTML, açık JSON hata nesneleri, HTTP hataları ve zaman aşımı engelleri
korunur. Yanıt doğrulama hatası artık temizleme/kaydetme, DELETE/POST ve HTTP
kodunu gösterir; ham gövde veya kişisel veri göstermez. DELETE engelinde
"Yeni rota gönderilmedi" denir. Fiziksel araç teslimi hâlâ doğrulanmadı.

Bu değişiklik kaynak kodundadır; yeni APK derlenmedi. Önceki çıktı APK'sı
AF256 ile belgelenen sürümdür. Uygulama testleri/canlı API çağrısı yapılmadı.


## 25 Ekim 2026 — false yanıt düzeltmesi paketlendi

Kullanıcının açık paketleme isteğiyle telefon APK derlemesi başarılı (3s).
AF257/AF258 düzeltmeleri outputs/ToggCompanion-current-debug.apk içinde.
Boyut: 4135638 bayt; SHA256: 5acb8ccb24cb574300d89436b49e18999a72ed4a5f8c7c57c1897f5cf50d4e0a.
Önceki teslimler obsolete/2026-10-25/rota-false-oncesi altında manifest ile arşivlendi.
Telefona kurulum, saat derlemesi, uygulama testleri ve push yapılmadı. Araç teslimi doğrulanmadı.


## 25 Ekim 2026 — kısa Maps rota bağlantısında ad/koordinat eşlemesi

Kullanıcının bildirdiği kısa bağlantı GET ile 302 sonrası 200 Maps directions
adresine açıldı. Yol başlangıcı koordinat, hedef kısa sokak adıydı; hedefin kesin
koordinatları aynı URL'nin data alanında ilgili !1m rota bloğundaydı. Eski F61,
data koordinatlarını okumadan önce yol adını Geocoder'a veriyordu ve bu nedenle
kesin koordinat URL'de bulunsa da çözümleme başarısız olabiliyordu.

F61 artık bilinen !4m/!4m directions zarfında !1m bloklarının wire token
sayılarını okuyup, blok sayısı yol noktası sayısıyla eşleşirse yalnız o bloğun
koordinatını aynı sıradaki adı olan noktaya bağlar. Başlangıç/ara durak/varış
sırası korunur. Başka noktanın koordinatı veya harita kamera merkezi hedefe
atanmaz. Eksik, belirsiz, çok koordinatlı veya tanınmayan bloklar eski Geocoder
yolunda kalır. Bilinen koordinat gereksiz geocoding gerektirmez; kısa adın +
boşluk ayırıcıları bu eşlemede düzenlenir.

F62'ye sentetik veriyle hedef koordinat eşlemesi ve başlangıç koordinatını
hedefe kopyalamama regresyonları eklendi. Uygulama testleri çalıştırılmadı ve
APK derlenmedi. Mevcut AF259 APK bu düzeltmeyi içermez. Canlı harita bağlantısı
salt okunur incelendi; buluta/araç ekranına gönderim yapılmadı. Kişisel bağlantı,
adres ve koordinatlar bu nota veya testlere alınmadı.


## 25 Ekim 2026 — Gez paylaşılan hedef yüklenmiyor: liste ve geri okuma

Kullanıcı442207/442211 karşılaştırmasında Companion sonrası Gez paylaşılan hedef
kartının yüklemede kaldığını, Gez ile paylaşımda adresin göründüğünü ve sonraki
paylaşımda üzerine yazma uyarısı çıktığını bildirdi. Bu gözlem kaydın varlığı ile
kaydın okunabilirliği/araç tesliminin farklı olduğunu gösterir; tek başına eksik
alanın hangisi olduğunu kanıtlamaz.

Somut statik fark: original dispatch1fce2ac boş growable waypoint listesi üretir,
1fce4c0 model field_23'e her zaman atar; a2e73c serializer null olmayan boş
listeyi de waypoints olarak yazar. F08 önceden sıfır ara durakta alanı atlıyordu;
artık her paylaşımda waypoints:[] veya dolu liste vardır. Bunun ekran hatasının
kesin nedeni olduğu henüz canlı karşılaştırmayla doğrulanmadı. transportationType,
hoursToExpire ve mesafe alanları normal original dispatch'te atanmadığı için
uydurma değer eklenmedi. device_Id kurulum kimliğidir; VIN yapılmadı.

Original tamamlanma1fce630 ->1fdf610 ->1fdf838 GET/c/api/Companions ile kaydı
JourneyResponse olarak geri okur; a2df30 arrivalPoint/startingPoint/waypoints ve
device_Id alanlarını ayrıştırır. F08 artık POST2xx sonrası GET ile aynı istemci
kimliğini, gönderilen nokta/durak koordinatlarını (~1m tolerans), adres label'ını
ve waypoint liste/sırasını doğrular. Okuma hatası, eksik alan veya farklı kayıt
başarı sayılmaz. POST literal false sonrası da salt okunur kontrol yapılıp
sonuç belirsiz kalır; false başarıya çevrilmez. GET hatası DELETE/POST tekrarı
başlatmaz; aynı oturum/VIN kontrolü ve gövde sınırı korunur. Ham yanıt, konum,
anahtar veya kimlik kullanıcıya/loga dökülmez. Geri okuma fiziksel araç teslimi
onayı değildir.

Kaynakta değişiklikler hazır; APK derlenmedi, JVM testleri çalıştırılmadı.
F65'e sentetik liste, adres eksikliği, farklı istemci ve değişmiş koordinat
regresyonları eklendi. ADB'de bağlı telefon bulunmadı; mevcut gerçek sunucu
kaydının Gez kaydıyla karşılaştırılması kullanıcı USB bağlantısını bekliyor.
Canlı hesap API isteği veya rota değişikliği yapılmadı. AF259 APK değişmedi.


## 25 Ekim 2026 — Maps ve paylaşılan hedef doğrulama paketi

Kullanıcının açık paketleme isteğiyle telefon APK derlemesi başarılı (4s).
AF260–AF262 kaynak değişiklikleri güncel APK içinde. Boyut 4138150 bayt;
SHA256 1c6817ea77281f7361d839927e19c0c5db29c9b14b0e7d7f923d804179ba83d7. Önceki çıktılar
obsolete/2026-10-25/rota-dogrulama-oncesi altında manifest ile arşivlendi.
Kurulum, saat derlemesi, JVM testleri veya push yapılmadı. Sunucu/araç doğrulaması
kullanıcının yeni denemesini bekliyor; Gez yükleme sorununun nedeni kesinleşmedi.
