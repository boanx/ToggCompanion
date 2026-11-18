# Companion → Akıllı cihazıma aktar

## Güncel teslim — 16 Kasım 2026, sürüm1.1/kod2

Kullanıcının açık sürüm/paketleme isteği tamamlandı. app/build.gradle.kts versionName **1.1**,versionCode **2**. APK içinden aapt2 ile sürüm doğrulandı. Gradle testDebugUnitTest/lintDebug/assembleDebug **BUILD SUCCESSFUL in36s**; **133test/0hata**,lint **0hata/328uyarı**,APK **v2imza doğrulaması başarılı**.

`outputs/ToggCompanion-current-debug.apk`,4146203bayt,SHA256 `1aada508bb83cfcf55057842506053430bd6ea1fa0ea3a3f2c73ebd635c37019`. Widget map-only/VIN+konum önbelleği,Google URL iyileştirmeleri,gönderim kabul/doğrulama/belirsizlik ayrımı,ana sayfa ve Sürüş toplam araç kilometresi kartları,gerçek kaydedilen kart sırası ve tercih geçişi bu pakete dahildir. Önceki16Kasım kaynak-only notlar bu teslimden önceki aşamaları anlatır.

Önceki1.0APK/metadata, tamamlanmış ara kaynak raporları ve üretilen APK kopyası `obsolete/2026-11-16-v1.1-package/move-manifest.json` ile arşivlendi; dışarıda tek teslim APK var. Bu paket fiziksel telefona/emülatöre kurulmadı; canlı araç/Google örnek linki ve gerçek widget çizimi ayrıca denenmelidir. Wear build/push yok. Yeni source değişiklikleri için açık build kısıtı devam eder.


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


## Güncel teslim — 25 Ekim rota paylaşımı APK

Kullanıcının açık derleme isteğiyle telefon debug derlendi: **BUILD SUCCESSFUL in22s**,33 görev/4 executed. APK outputs/ToggCompanion-current-debug.apk, 4135362 bayt, SHA256 `f30dadc60cd3296afe3335fa012e4e9f0702663fc526ac19893716b62814b8f1`. Companions paylaşım API/JSON düzeltmesini içerir; önceki kaynak-only notlar tarihseldir. Bu tur telefona kurulum, Wear build, uygulama testleri veya push yapılmadı. Yeni servisin gerçek yanıtı ve fiziksel araç teslimi hâlâ kullanıcı denemesini bekliyor. Önceki teslimler obsolete/2026-10-25/rota-paylasim-oncesi manifestiyle arşivlendi.


## 25 Ekim 2026 — Araç paylaşım servisi düzeltmesi (kaynak)

Kullanıcı Companion HTTP200 sonucuna rağmen hedefin araçta görünmediğini, aynı hedefin Trumore Gez üzerinden araçta göründüğünü bildirdi. Araca fiziksel teslim bu kaynak düzeltmesiyle henüz denenmedi.

### Orijinal protokol kanıtı

Önceki PlannedJourneys araştırması planlanan rota kaydını doğruluyordu; gerçek araç paylaşım düğmesinin bu yolu kullandığını kanıtlamıyordu. Eski AF135/138 JSON kanıtı korunur; AF139/175 araç gönderimi yorumu bu kanıtla aşılır.

- Paylaşım UI0xa3f1ec içindeki PP0xa1560 closure0xa3fd6c toCar seçimini verir. Araç paylaşım UI0x22632dc →0x1fce1a0.
-0x1fce1a0 önce0x1fe80ac (eski paylaşımı kaldır), sonra0x1fe7ce4 (kaydet) çağırır. HTTP0x1fe8210 DELETE ve0x1fe7e54 POST yolu `/c/api/Companions`, Bearer oturumu. POST typed PlannedJourneyResponse ayrıştırıcısına gitmez.
-0xa2e5dc: source/device_Id/startingPoint/arrivalPoint/waypoints; isteğe bağlı totalTollCost/distance/hoursToExpire. Source marker0x1fce498: trumore-mobile-app.
- Nokta0xa2b844: **geolocation** (küçük l), placeName, address. Koordinat0xa2bbdc latitude/longitude; adres0xa2b950 label ve isteğe bağlı adres alanları.
- device_Id VIN değildir:0x20deb5c →0x20ad800 Countly DeviceIDModule getID → state copy0xa3d8e4. PP0x69788 named vwo state field_f; gönderim modeli field_17 bu değeri taşır. İlk araç kimliği varsayımı böylece düzeltilir.

### Değişiklik

F08 gönderimi `https://navigation-api.togg.cloud/c/api/Companions` DELETE→POST sırasına geçirir. Mevcut navigation prod host korunur; yeni endpoint bu tur canlı doğrulanmadı. Geçerli gövde hazırlanmadan DELETE başlamaz. Hesap/VIN her adımdan önce ve sonuç sonrası kontrol edilir; DELETE hatası/belirsizliği POST başlatmaz, belirsiz POST otomatik tekrarlanmaz. API hesaptaki önceki paylaşılan rotayı değiştirir; PlannedJourneys kayıtları silinmez. VIN gövdeye eklenmez; çok araçlı hesap eşlemesi manuel teyit gerektirir.

Nokta sırası/ara durak ayırımı mevcut builder üzerinden yeni JSON biçimine çevrilir. F06 bağımsız kalıcı yerel UUID kurulum kimliği üretir; VIN kullanılmaz, Trumore Countly kimliği kopyalanmaz. Protokol uyumu için orijinal source marker korunur. Ücret/mesafe/süre/SoC alanları uydurulmaz.

HTML, açık JSON error/errors/success=false veya hata statüsü2xx olsa da başarı sayılmaz; en fazla65536 karakter okunur. Ham gövde/token/konum loglanmaz. Orijinal void yanıt sözleşmesine uygun boş2xx kabul edilir. F85 sonuç düğmesi Paylaşım kaydedildi olur; fiziksel araç teslimi iddia edilmez.

Manuel ve ABRP gönderimleri aynı F08 yolunu kullanır. Eski accepted/pending fingerprint kayıtları otomatik silinmedi. Yeni manuel paylaşım doğrulandıktan sonra eski plan bastırılıyorsa kullanıcı Ayarlar → Takibi sıfırla ile yeni servise gönderime izin verebilir; belirsiz gönderimde kör tekrar yapılmamalı.

### Durum

Kaynak hazır; **APK derlenmedi/kurulmadı**. Çıktılardaki21 Ekim APK bu düzeltmeyi içermez. Uygulama testleri, canlı endpoint çağrısı veya GitHub push yapılmadı. KB metadata doğrulaması çalıştırıldı. Kullanıcı yeni build istediğinde aynı hedefin manuel araç teslimi denenmeli.

Kanıt: work/trumore-analysis/analysis/route-delivery-2026-10-25/ ham/resolved disassembly. dae SDK profili UNVERIFIED; yorumlar ham ADD/LDR/BL ve veri alanlarıyla karşılaştırıldı. Kullanıcının URL/konum/kimlik/token verileri rapora alınmadı.

## Tarihsel PlannedJourneys uygulaması


12 Ekim 2026 — İlk aşama: Companion içinden manuel hedef/rota gönderimi.

## Kullanım

1. `ToggCompanion-direct-route-debug.apk` dosyasını önceki debug uygulamasının üzerine kurun. OLED Glass teması ve BLE değişiklikleri bu pakette bulunur.
2. Ayarlar’dan Togg ID ile giriş yapın; gerçek VIN ve araç bilgisi seçili olsun. Uygulamayı açıp canlı telemetriyi yenileyin. Bilinen araç konumu tek hedef isteğinde başlangıç olarak kullanılır.
3. Ana ekrandaki **Akıllı cihazıma aktar** düğmesine dokunun. Bu düğme ABRP ayarı veya telemetri kartlarının yüklenmesi gerektirmez. Harita paylaşımında Companion’ı seçmek de aynı pencereyi açar.
4. Tek hedef için bir adres, Google Maps bağlantısı veya `enlem, boylam` girin; **Hedefi önizle** ile adı ve koordinatları kontrol edin. Paylaşım ile gelen hedef otomatik önizlenir; otomatik gönderilmez.
5. Çok noktalı rota için Google Maps rota bağlantısı veya her satırda bir koordinat/adres girilebilir. İlk nokta başlangıç, son nokta varış, aradakiler ara duraktır. **Tüm rota** veya yalnız bir noktanın hedef olarak gönderilmesini seçin. Bu aşamada ABRP’nin kapalı/kimlik gerektiren plan bağlantılarının çözümlenmesi garanti edilmez.
6. **Akıllı cihazıma aktar** düğmesine basın. Gönderim internet üzerinden yapılır; BLE bağlantısı gerekmez. Trumore uygulaması başarı veya hatada açılmaz.
7. Sonuç pencerede kalır. “Togg Cloud tarafından kabul edildi (HTTP …)” sonrası hedefin araç Gez ekranında görünüp görünmediğini kontrol edin.

İlk denemeyi tek hedefle yapın. Ardından bir başlangıç, bir ara durak ve varış içeren rota denenebilir. Çok durakların araç ekranında nasıl işlendiği fiziksel denemeyi bekler.

## Sonuçları paylaşırken

- Penceredeki sonuç metni ve HTTP kodu.
- Tek hedef mi, çok noktalı rota mı gönderildiği.
- Trumore’un açılıp açılmadığı (bu akışta açılmamalı).
- Hedefin araç ekranında görünmesi, hedef adı ve ara durakların durumu.
- Uygulamaya dönüldüğünde hedef/sonuç ekranının durumu.

Parola, access token, refresh token veya tam hesap bilgisi paylaşmanız gerekmez. Hedef ve konum bilgilerini ekran görüntüsünde isterseniz kapatabilirsiniz.

## Uygulanan davranış

- Eski hata→Trumore açma fallback’i ve `CLOUD|` / `TRUMORE|` durum karışıklığı kaldırıldı. Ayrı eski deep-link yardımcıları kaynakta kalır; manuel bulut gönderimi bunları çağırmaz.
- Gönderim ekranı OLED Glass temasını kullanır. Ad/koordinat önizlemesi, tüm rota veya tek hedef seçimi ve kopyalanabilir sonuç metni içerir.
- Hedef metni düzenlenirse önceki çözümleme geçersizleşir. Eski asenkron sonuçlar yeni hedefin yerine geçmez. Çok satırlı koordinat listesinde yalnız ilk noktayı alma hatası düzeltildi. Google Maps ekran merkezi (`@enlem,boylam,zoom`) ve veri işaretleri durak listesine eklenmez; paylaşım metnindeki bağlantılar URL olarak çözümlenir.
- Gönderim sırasında düzenleme ve ikinci istek engellenir. Süreç içindeki ABRP ve manuel rota istekleri aynı tek-istek sınırını kullanır. Başarılı hedef tekrar dokunmayla yeniden gönderilmez; yeni hedef/önizleme veya seçim kullanıcıdan gelir.
- Hata durumunda hedef korunur. Zaman aşımı veya ağ kopmasında otomatik POST tekrarı yapılmaz; yanıt alınmasa da hedef ulaşmış olabilir. Araç ekranını kontrol ederek yeniden denemek kullanıcıya bırakılır.
- HTTP 401’de açık izin reddi yoksa oturum en fazla bir kez yenilenir; HTTP 403/izin reddi için erişim açıklaması gösterilir. Başarısız sunucu yanıtlarının ham gövdesi/token bilgisi ekrana veya loga yazılmaz.
- Gönderim mevcut hesap ve VIN bağlamını kontrol eder. Hesap/araç değişirse sonuç o araç için başarılı sayılmaz; oturum yenileme sonucu başka hesaba yazılmaz.

## Orijinal uygulama ile eşleşen istek

`POST https://navigation-api.togg.cloud/j/api/PlannedJourneys`

- Bearer Togg ID oturumu, application/json.
- `startingPoint`, `arrivalPoint`, `waypoints`.
- Her nokta: `geoLocation: {latitude, longitude}`, `address: {label}`.
- İlk/son noktalar ara duraklarda tekrar edilmez. Tek hedefin başlangıcı yalnız aynı VIN’in geçerli kayıtlı konumundan alınır; bilinmeyen konum uydurulmaz.
- Orijinal JSON ve query’de VIN alanı bulunmadığından istek gövdesine VIN eklenmez. Companion’daki VIN kontrolü, API’ye belirli bir VIN seçimi yaptığını kanıtlamaz; bulutun hesap/aktif araç eşlemesi gerçek hesap ve araç üzerinde teyit edilmelidir.

Kanıt: orijinal APK AOT API entry `0x2256014`, request builder `0x1881a5c`, serializer `0xf53ac4`, point serializer `0xe8ef28` ve coordinate serializer `0xe8fcf4`; `docs/TRUMORE_2_1_25_APK_VERIFICATION.md` bölüm 3.2 ve KB AF135/AF138/AF139.

## Doğrulama sınırı ve sonraki aşama

Telefon debug APK’si derlendi; KB ve diff kontrolleri yapıldı. Bu aşamada yeni JVM testi eklenmedi/çalıştırılmadı. Kullanıcının istediği uygulama içi denemeler kendisi tarafından yapılacaktır. Bu ortamdan canlı API çağrısı veya araç gönderimi yapılmadı.

HTTP 2xx yalnız API kabulünü gösterir. Orijinal `PlannedJourneyResponse` içinden araç ekranına teslim ACK’i henüz doğrulanmış değildir. Başlangıç konumu olmayan isteğin kabulü, hesap/aktif araç seçimi ve araçtaki çok durak gösterimi manuel denemeyi bekler.

Bu paket yeni bir 7 dakikalık otomasyon kurmaz. Manuel aktarım teyit edildikten sonra canlı telemetri → ABRP → değişen rota ve SoC sonuçları → araç akışı geliştirilecektir. PlannedJourneyRequest içinde doğrulanmamış SoC alanları eklenmedi; ABRP SoC bilgisinin Gez/araç tarafından nasıl kabul edildiği ayrıca araştırılacaktır.

## 12 Ekim 2026 HTTP 301 düzeltmesi

Rota yolu için alan adı navigasyon API’sine düzeltildi. Kaynak kod güncel; mevcut APK yeniden derlenmedi ve bu düzeltmeyi içermiyor. Kanıt ve durum: [HTTP301_ROUTE_FIX.md](HTTP301_ROUTE_FIX.md).

## Ana sayfa sadeleştirmesi

Kullanıcı isteğiyle e-Motor bloğu altındaki bağımsız btn_send_route kaldırıldı. ABRP kartındaki Akıllı cihazıma aktar ve mevcut harita rota erişimi korunur. Bu kaynak düzenlemesi henüz APK’ye alınmadı.


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
