# Togg Companion: mühendislik öğrenimleri ve agent rehberi

## 1. Giriş ve proje amacı

Togg Companion, Togg T10X ve T10F araçları için yerel Android telefon uygulaması ile Wear OS saat uygulamasından oluşan bir asistandır. Amaç; araç verilerine ve desteklenen araç işlevlerine doğrudan, anlaşılır ve güvenli yollar sunarken Trumore içindeki yavaş veya çok adımlı akışlara bağımlılığı azaltmaktır.

“Doğrudan bağlantı” tek bir taşıma katmanı anlamına gelmez. BLE Smart Access, Togg Cloud telematics ve Trumore app-to-app yönlendirmesi ayrı yeteneklerdir. İşlevi eklemeden önce hangi kanalın o işlevi gerçekten desteklediğini belirleyin.

## 2. Üç ayrı iletişim kanalı

### Kanal 1: BLE Smart Access (Bluetooth LE)

`VehicleBleManager`, Smart Access için BLE GATT keşfini yönetir. Trumore 2.1.25 incelemesinde bu UUID literal'leri bulundu; rollerin ve canlı komut frame'lerinin tam eşleşmesi henüz doğrulanmadı. Companion'da kullanılan değerler:

- Service: `f0002412-0451-4000-b000-000000000000`
- Command characteristic: `f0002413-0451-4000-b000-000000000000`

GATT oturumu `BluetoothDevice.TRANSPORT_LE` ile başlatılmalı; `CONNECTED` durumu servis keşfi, yazılabilir characteristic ve başarılı CCCD aboneliğinden sonra verilmelidir. `isGattReady()` taşıma bağlantısını gösterir; araç anahtarının doğrulandığını veya komut göndermeye yetkili bir oturum kurulduğunu göstermez. Android'in Classic Bluetooth A2DP/HFP eşleşmesi veya `ACTION_ACL_CONNECTED`, Smart Access bağlantısının kanıtı değildir.

Orijinal binary'de imzalı SAM araç durum mesajları vardır. Batarya telemetrisi ve navigasyon hedefi için Companion bulut kanallarını kullanır; bunları doğrulanmamış BLE frame'lerine koymayın. GATT'a başarılı yazma araç komutu başarısını kanıtlamaz. Durum mesajı imzası, sayaç, komut sonucu ve komutla eşleşme doğrulanmadan araç durumunu değiştirmeyin. Demo modu yalnızca açıkça simülasyon olarak sunulan davranışları çalıştırır.

Trumore, kullanıcı/VIN'e bağlı Android Keystore EC P-256 anahtarı ve ECDSA imzalama kullanır. Companion artık AndroidKeyStore, sertifika sağlama ve imzalı Smart Access frame/oturum akışını uygular. Kapı/bagaj/e-Motor başlatma yalnız geçerli yetki ve doğrulanmış oturumda gönderilir; sonuç daha yeni imzalı araç durumuyla eşleşmelidir. Fiziksel doğrulama manueldir. Eski A1/A2/A3 paketleri kullanılmaz; READY’den aksesuar moduna dönüş doğrulanmadığı için e-Motor durdurma pasiftir. Güncel kurulum: [Smart Access](SMART_ACCESS_IMPLEMENTATION.md). Davet linki/SE referansı okumak aktif araç anahtarı oluşturmaz. İklimlendirme, sıcaklık, buğu çözme, far ve sürüş modu kontrolleri de devre dışıdır. Ayrıntılı kaynak ve sınırlar: [Trumore APK doğrulaması](TRUMORE_2_1_25_APK_VERIFICATION.md).

### Kanal 2: Togg Cloud telematics API (eSIM / OTA araç bulutu)

Araçtaki telematik eSIM ile Togg TVA servisi arasındaki iletişim, araç bulutu üzerinden gerçekleşir. Uygulamadaki örnek endpoint'ler:

- Araç telemetrisi: `GET https://tva-api.togg.cloud/info/api/v2/VehicleInfo?vin=...`
- Yolculuk/hedef planlama: `POST https://navigation-api.togg.cloud/j/api/PlannedJourneys`

İstekler Togg ID oturumundan alınan `Bearer` access token ile yetkilendirilir. Access token süresi dolduğunda `ToggAuthManager` üzerinden tek yenileme denemesi yapılır. HTTP 401 tek başına token süresinin dolduğunu kanıtlamaz: VIN/araç erişim izni (`FriendVehicleInfo` dahil) reddedilmiş olabilir. Yanıtın hata içeriğini ayrıştırın ve araç erişim izni hatasında yenileme döngüsü başlatmayın.

PlannedJourneys POST çağrısı ve PlannedJourneyRequest serializer'ı orijinal AOT kodundan geri kazanıldı. Gövde `startingPoint`, `arrivalPoint`, `waypoints`; her nokta `geoLocation: {latitude, longitude}` ve `address: {label, ...}` içerir. Eski `vin/destination/stops` şeması kullanılmaz. İlk/son rota noktalarını ara durak listesinde tekrar etmeyin. Bilinmeyen başlangıç serializer gibi atlanır; backend kabulü ve aktif araca teslim henüz çalışma zamanında doğrulanmadı. HTTP 2xx sonucunu Cloud kabulü olarak bildirin.

### Kanal 3: Trumore Smart Bridge / app-to-app redirection

Trumore yönlendirmesinde kullanılan örnekler:

- Araç ekranı yönlendirmesi: `trumore-superapp://custom/redirection?path=/navigation/carDisplayDirections`
- Konum paylaşımı: `https://superapp-api.channels.togg.cloud/ShareLocation?...`

`GezRouteBridge`, ilgili derin bağlantıları ve HTTPS konum paylaşımını uygun Android `ACTION_VIEW` intent'leriyle açar. Bu yol Trumore'un yüklü/oturum açılmış olduğu durumlarda yerel köprü veya fallback olarak kullanılabilir. Uygulama/oturum mevcudiyeti ve çevrimdışı davranışı her cihazda doğrulanmış bir garanti saymayın; açma sonucunu kontrol edin ve kullanıcıya gerçek sonucu bildirin.

Salt `ShareLocation` bağlantısı Gez konum önizlemesini açabilir ve kullanıcıdan “Git” ile ardından “Konumu akıllı cihazıma aktar” adımlarını bekleyebilir. Companion içinden doğrudan aktarımda Togg Cloud PlannedJourneys akışını kullanın. Manuel gönderim başarısız olduğunda Trumore açılmaz; ayrı deep-link yardımcıları yalnız açık uygulama açma işlemlerinde kullanılmalıdır. Güncel akış `docs/DIRECT_VEHICLE_ROUTE_TRANSFER.md` içindedir.

## 3. Geçmiş hatalar ve kaçınılacak anti-pattern'ler

### Tuzak 1: Classic Bluetooth medya bağlantısını BLE Smart Access sanmak

Android `ACTION_ACL_CONNECTED`, A2DP/HFP medya veya eller serbest eşleşmesini gösterebilir. Bu sinyali araç komutu için bağlantı başarısı olarak yorumlamayın. `connectGatt()` çağrısını `TRANSPORT_LE` ile yapın; bağlantı durumunu ancak servis keşfi ve komut characteristic'i doğrulandıktan sonra “Smart Access hazır” olarak gösterin. Arayüzde “BLE servisi aranıyor” ile “medya Bluetooth bağlantısı” durumlarını ayırın.

### Tuzak 2: Navigasyon veya konumu BLE ile göndermeye çalışmak

Smart Access GATT karakteristiği konum/navigasyon API'si değildir. Araç navigasyon hedeflerini BLE komutlarıyla taşımaya çalışmayın. Desteklenen doğrudan akışta hedef PlannedJourneys üzerinden Togg Cloud'a gider; hata Companion içinde gösterilir ve otomatik deep link açılmaz.

### Tuzak 3: Trumore önizleme sayfasını doğrudan araç aktarımı sanmak

`ShareLocation` URL'sini açmak, otomatik olarak aracın hedefi aldığı anlamına gelmez; Gez önizleme ekranı ek kullanıcı adımları isteyebilir. Ürün gereksinimi buluta doğrudan iletim ise Togg Cloud API sonucunu bekleyin ve başarıyı yalnızca callback/yanıt başarılı olduğunda gösterin. Fallback açılması ile Cloud gönderim başarısını aynı durum olarak raporlamayın.

### Tuzak 4: Gez hedef aktarımı şemasını doğrulanmış varsaymak

PlannedJourneyRequest alanları AOT çağrı zinciriyle doğrulandı; çalışma zamanı teslim koşulları hâlâ açık. Harita navigasyonunun tüm rotayı açması veya Cloud HTTP 2xx yanıtı, aracın tüm durakları ekranında aldığı anlamına gelmez.

### Tuzak 5: Her HTTP 401'de token'ı yenileyip sonsuz döngüye girmek

Token expiry ile VIN veya araç erişim izni (`FriendVehicleInfo`) sorununu ayırın. Yenilemeyi sınırlı sayıda deneyin; izin reddinde token yenilemeye tekrar tekrar dönmek yerine açıklayıcı bir hata gösterin.

### Tuzak 6: ABRP canlı takipte aynı durağı tekrar tekrar iletmek

Yaklaşık 4 dakikalık `get_latest_plan` kontrolünde hesap/VIN/ABRP token kapsamındaki kabul edilmiş rota imzasını `SecureTokenStorage` içinde saklayın. Durak koordinatı anahtarı değişmediyse yeniden dispatch etmeyin. Başarılı dispatch ve kullanıcıya bildirim zamanını, başarısız deneme ile karıştırmayın.

### Tuzak 7: Android intent'i açıldı diye hedef iletildi varsaymak

Intent'in başka uygulama tarafından kabul edilmesi veya ekran açılması, araca hedef gönderildiğinin kanıtı değildir. Cloud dispatch callback sonucunu; Trumore intent açılışını ise ayrı durum olarak ele alın. “İletildi”, “Trumore açılıyor” ve “gönderilemedi” mesajlarını birbirinden ayırın.

## 4. ABRP ve telemetri entegrasyonu

ABRP entegrasyonunda iki bağımsız zamanlama bulunur:

- Canlı telemetri gönderimi: `https://api.iternio.com/1/tlm/send`, 4 dakikalık zincirlenen `JobScheduler` işi.
- Rota kontrolü: `https://api.iternio.com/1/tlm/get_latest_plan`, etkinleştirildiğinde 15 dakikalık ayrı `JobScheduler` işi.

`AbrpClient` ABRP iletişimini/parsing'i, `AbrpJobService` arka plan işlerini, `SecureTokenStorage` ise rota senkronizasyon tercihi ile son gönderilen durak bilgisini yönetir. Bir iş tamamlandığında sonraki tek seferlik işi planlama yaklaşımını koruyun; telemetri gönderim aralığını rota takibi için değiştirmeyin. Yeni durak araca gönderildiğinde Android bildirimi gösterilir. İşletim sistemi zamanlaması ve pil kısıtlamaları nedeniyle JobScheduler'ın tam dakika hassasiyetinde çalışacağını garanti etmeyin.

Rota takibini ayarlardan ayrı olarak açıp kapatın. Değişiklik tüm gelecek hedeflerin sırası ve varsa yayınlanmış rota kimliği/geometrisi üzerinden karşılaştırılır. Arka planda Trumore fallback’i açılmaz; belirsiz POST sonucu aynı rota için otomatik tekrarlanmaz. Güncel akış: [ABRP otomatik rota](ABRP_AUTOMATIC_ROUTE_SYNC.md).
Trumore fallback intent'inin açılması araç hedefinin iletildiğini kanıtlamaz; başarısız Togg Cloud gönderiminde durak anahtarını “gönderildi” olarak kaydetmeyin ve sonraki kontrolde yeniden deneyin.

## 5. Konum, saat eşitlemesi ve devre dışı kontroller

- Bilinmeyen araç koordinatları `0,0` olarak kalır. Demo veya eksik API yanıtları gerçek konum gibi Ankara/Kadıköy koordinatları üretmemelidir. Dashboard, widget, saat ve action shortcut navigasyonu yalnızca doğrulanmış/saklanmış araç koordinatıyla başlatır.
- Saat ilk telemetriyi almadan batarya, lastik ve araç konumu için örnek değer göstermemelidir. Saatteki yenileme isteği telefon üzerinden Togg API'den canlı telemetri ister; bağlantı/API hatasında önbellek kullanılabilir ve zaman damgası eski veriyi belli eder.
- Saat konum sağlayıcısı yalnızca ekran görünürken dinlenir; `onStop` aşamasında güncellemeler kaldırılır ve kullanıcı izin verince tekrar başlatılır.
- İklimlendirme, defrost, far ve sürüş modu yerel durumu değiştiren kontroller olarak sunulmaz. Yeni bir gerçek araç komut yolu eklendiğinde önce yetki/geri bildirim sonucu doğrulanmalıdır.

## 6. Gelecekteki geliştiriciler ve agent'lar için çalışma kuralları

1. Önce `AGENTS.md`, `docs/KB_PROTOCOL.md` ve `docs/KB_INDEX.md` talimatlarını okuyun. Feature bilgisi gerekiyorsa indeksin yönlendirdiği en küçük KB kapsamını seçin; IDMAP, KG adjacency ve ilgili FACTS kayıtlarını kullanın. Kanıtlanmamış araç davranışı eklemeyin.
2. Dar kapsamlı değişiklik yapın. Kullanıcıya görünen başarı metnini yalnızca gerçek API/GATT sonucuna dayandırın. Secret/token değerlerini loglara veya dokümana yazmayın.
3. JVM testlerini çalıştırın: `./gradlew.bat testDebugUnitTest` (Windows'ta gerekirse Android Studio JBR için `JAVA_HOME` ayarlayın). BLE donanım davranışını JVM testi tek başına doğrulamaz; cihaz testi gerekiyorsa bunu ayrıca belirtin.
4. KB dosyaları değiştiğinde `node scripts/validate-kb.cjs` çalıştırın ve KB fact ID'lerini yeniden numaralandırmayın.
5. APK teslimi istenen bir görevde `./gradlew.bat assembleDebug` çalıştırın ve güncel APK'yı `C:\Users\aozyurt\Documents\AG\TOGG_Asistan.apk` konumuna kopyalayın. Kopyalamanın başarılı olduğunu dosya varlığı/boyutuyla doğrulayın. Bu kılavuz tek başına her kod değişikliğinde APK kopyalamayı zorunlu kılmaz; görev paketinin teslim koşulunu izleyin.
6. Git commit/rebase/push işlemlerinde önce branch ve working tree'yi inceleyin. Mevcut kullanıcı değişikliklerini koruyun, istenen kapsam dışında dosyaları eklemeyin ve son durumda branch durumunu raporlayın.

## 7. İlgili kaynaklar

- [Repository agent contract](../AGENTS.md)
- [Knowledge Base index](KB_INDEX.md)
- [Knowledge Base protocol](KB_PROTOCOL.md)
- [Togg Companion feature facts](togg-companion/FACTS.jsonl)


## 12 Ekim 2026 HTTP 301 düzeltmesi

Rota yolu için alan adı navigasyon API’sine düzeltildi. Kaynak kod güncel; mevcut APK yeniden derlenmedi ve bu düzeltmeyi içermiyor. Kanıt ve durum: [HTTP301_ROUTE_FIX.md](HTTP301_ROUTE_FIX.md).
