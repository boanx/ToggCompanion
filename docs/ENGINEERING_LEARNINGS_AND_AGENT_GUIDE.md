# Togg Companion: mühendislik öğrenimleri ve agent rehberi

## 1. Giriş ve proje amacı

Togg Companion, Togg T10X ve T10F araçları için yerel Android telefon uygulaması ile Wear OS saat uygulamasından oluşan bir asistandır. Amaç; araç verilerine ve desteklenen araç işlevlerine doğrudan, anlaşılır ve güvenli yollar sunarken Trumore içindeki yavaş veya çok adımlı akışlara bağımlılığı azaltmaktır.

“Doğrudan bağlantı” tek bir taşıma katmanı anlamına gelmez. BLE Smart Access, Togg Cloud telematics ve Trumore app-to-app yönlendirmesi ayrı yeteneklerdir. İşlevi eklemeden önce hangi kanalın o işlevi gerçekten desteklediğini belirleyin.

## 2. Üç ayrı iletişim kanalı

### Kanal 1: BLE Smart Access (Bluetooth LE)

`VehicleBleManager`, kapı kilitleme/açma, bagaj ve immobilizer (motor çalıştırma yetkisi) gibi Smart Access komutlarını BLE GATT üzerinden gönderir. Uygulamadaki UUID'ler:

- Service: `f0002412-0451-4000-b000-000000000000`
- Command characteristic: `f0002413-0451-4000-b000-000000000000`

GATT oturumu `BluetoothDevice.TRANSPORT_LE` ile başlatılmalı; `CONNECTED` durumu servis keşfi ve yazılabilir komut characteristic'i bulunduğunda verilmelidir. `isGattReady()` canlı modda gerçek GATT yazıcısının hazır olduğunu doğrular. Android'in Classic Bluetooth A2DP/HFP eşleşmesi veya `ACTION_ACL_CONNECTED`, Smart Access bağlantısının kanıtı değildir.

**BLE Smart Access telemetri taşımaz ve navigasyon hedefi kabul etmez.** Batarya yüzdesi veya rota göndermek için BLE komut protokolünü kullanmaya çalışmayın. Komutun GATT'a gerçekten yazıldığı doğrulanmadan başarı bildirimi veya sahte telemetri üretmeyin. Demo modu, fiziksel araca komut göndermez ve yalnızca açıkça demo olarak sunulan davranışları çalıştırır.

### Kanal 2: Togg Cloud telematics API (eSIM / OTA araç bulutu)

Araçtaki telematik eSIM ile Togg TVA servisi arasındaki iletişim, araç bulutu üzerinden gerçekleşir. Uygulamadaki örnek endpoint'ler:

- Araç telemetrisi: `GET https://superapp-api.channels.togg.cloud/info/api/v2/VehicleInfo?vin=...`
- Yolculuk/hedef planlama: `POST https://superapp-api.channels.togg.cloud/j/api/PlannedJourneys`

İstekler Togg ID oturumundan alınan `Bearer` access token ile yetkilendirilir. Access token süresi dolduğunda `ToggAuthManager` üzerinden tek yenileme denemesi yapılır. HTTP 401 tek başına token süresinin dolduğunu kanıtlamaz: VIN/araç erişim izni (`FriendVehicleInfo` dahil) reddedilmiş olabilir. Yanıtın hata içeriğini ayrıştırın ve araç erişim izni hatasında yenileme döngüsü başlatmayın.

PlannedJourneys, hedefi araç bulutuna iletmek içindir; BLE GATT ile karıştırılmamalıdır. Çok duraklı Gez senaryosunda araç tarafına hedef gönderimi tek hedefle sınırlı kabul edilir; rotanın varış noktası ana hedef olarak iletilir. Birden fazla durak sunuluyorsa ara durakları ayrı kullanıcı aksiyonları olarak gösterin ve API payload'ında desteklenmeyen davranış varsaymayın.

### Kanal 3: Trumore Smart Bridge / app-to-app redirection

Trumore yönlendirmesinde kullanılan örnekler:

- Araç ekranı yönlendirmesi: `trumore-superapp://custom/redirection?path=/navigation/carDisplayDirections`
- Konum paylaşımı: `https://superapp-api.channels.togg.cloud/ShareLocation?...`

`GezRouteBridge`, ilgili derin bağlantıları ve HTTPS konum paylaşımını uygun Android `ACTION_VIEW` intent'leriyle açar. Bu yol Trumore'un yüklü/oturum açılmış olduğu durumlarda yerel köprü veya fallback olarak kullanılabilir. Uygulama/oturum mevcudiyeti ve çevrimdışı davranışı her cihazda doğrulanmış bir garanti saymayın; açma sonucunu kontrol edin ve kullanıcıya gerçek sonucu bildirin.

Salt `ShareLocation` bağlantısı Gez konum önizlemesini açabilir ve kullanıcıdan “Git” ile ardından “Konumu akıllı cihazıma aktar” adımlarını bekleyebilir. Tek tıkla bulut iletimi istendiğinde, uygulamadaki Togg Cloud PlannedJourneys akışını kullanın; Trumore yönlendirmesini desteklenebilir fallback olarak saklayın.

## 3. Geçmiş hatalar ve kaçınılacak anti-pattern'ler

### Tuzak 1: Classic Bluetooth medya bağlantısını BLE Smart Access sanmak

Android `ACTION_ACL_CONNECTED`, A2DP/HFP medya veya eller serbest eşleşmesini gösterebilir. Bu sinyali araç komutu için bağlantı başarısı olarak yorumlamayın. `connectGatt()` çağrısını `TRANSPORT_LE` ile yapın; bağlantı durumunu ancak servis keşfi ve komut characteristic'i doğrulandıktan sonra “Smart Access hazır” olarak gösterin. Arayüzde “BLE servisi aranıyor” ile “medya Bluetooth bağlantısı” durumlarını ayırın.

### Tuzak 2: Navigasyon veya konumu BLE ile göndermeye çalışmak

Smart Access GATT karakteristiği konum/navigasyon API'si değildir. Araç navigasyon hedeflerini BLE komutlarıyla taşımaya çalışmayın. Desteklenen akışta hedef PlannedJourneys üzerinden Togg Cloud'a gider; gerektiğinde Trumore deep link'i devreye girer.

### Tuzak 3: Trumore önizleme sayfasını doğrudan araç aktarımı sanmak

`ShareLocation` URL'sini açmak, otomatik olarak aracın hedefi aldığı anlamına gelmez; Gez önizleme ekranı ek kullanıcı adımları isteyebilir. Ürün gereksinimi buluta doğrudan iletim ise Togg Cloud API sonucunu bekleyin ve başarıyı yalnızca callback/yanıt başarılı olduğunda gösterin. Fallback açılması ile Cloud gönderim başarısını aynı durum olarak raporlamayın.

### Tuzak 4: Çok duraklı Gez rotasını tek seferde aktarılabilir varsaymak

Gez/araç hedef aktarımında tek hedef sınırını dikkate alın. Çok duraklı rotada varış hedefini ana hedef olarak kullanın; ara durakları listede ayrı `⚡ Araca İlet` aksiyonları olarak sunun. Harita navigasyonunun tüm rotayı açması, aracın tüm durakları bulut üzerinden aldığı anlamına gelmez.

### Tuzak 5: Her HTTP 401'de token'ı yenileyip sonsuz döngüye girmek

Token expiry ile VIN veya araç erişim izni (`FriendVehicleInfo`) sorununu ayırın. Yenilemeyi sınırlı sayıda deneyin; izin reddinde token yenilemeye tekrar tekrar dönmek yerine açıklayıcı bir hata gösterin.

### Tuzak 6: ABRP canlı takipte aynı durağı tekrar tekrar iletmek

15 dakikalık `get_latest_plan` kontrolünde son gönderilen durak anahtarını `SecureTokenStorage` içinde saklayın. Durak koordinatı anahtarı değişmediyse yeniden dispatch etmeyin. Başarılı dispatch ve kullanıcıya bildirim zamanını, başarısız deneme ile karıştırmayın.

### Tuzak 7: Android intent'i açıldı diye hedef iletildi varsaymak

Intent'in başka uygulama tarafından kabul edilmesi veya ekran açılması, araca hedef gönderildiğinin kanıtı değildir. Cloud dispatch callback sonucunu; Trumore intent açılışını ise ayrı durum olarak ele alın. “İletildi”, “Trumore açılıyor” ve “gönderilemedi” mesajlarını birbirinden ayırın.

## 4. ABRP ve telemetri entegrasyonu

ABRP entegrasyonunda iki bağımsız zamanlama bulunur:

- Canlı telemetri gönderimi: `https://api.iternio.com/1/tlm/send`, 7 dakikalık zincirlenen `JobScheduler` işi.
- Rota kontrolü: `https://api.iternio.com/1/tlm/get_latest_plan`, etkinleştirildiğinde 15 dakikalık ayrı `JobScheduler` işi.

`AbrpClient` ABRP iletişimini/parsing'i, `AbrpJobService` arka plan işlerini, `SecureTokenStorage` ise rota senkronizasyon tercihi ile son gönderilen durak bilgisini yönetir. Bir iş tamamlandığında sonraki tek seferlik işi planlama yaklaşımını koruyun; telemetri gönderim aralığını rota takibi için değiştirmeyin. Yeni durak araca gönderildiğinde Android bildirimi gösterilir. İşletim sistemi zamanlaması ve pil kısıtlamaları nedeniyle JobScheduler'ın tam dakika hassasiyetinde çalışacağını garanti etmeyin.

Rota takibini ayarlardan ayrı olarak açıp kapatın. Hedef değişimi koordinat anahtarı üzerinden karşılaştırılmalı; token/izin veya ağ hatasında bildirim ve fallback sonucu dürüstçe raporlanmalıdır.

## 5. Gelecekteki geliştiriciler ve agent'lar için çalışma kuralları

1. Önce `AGENTS.md`, `docs/KB_PROTOCOL.md` ve `docs/KB_INDEX.md` talimatlarını okuyun. Feature bilgisi gerekiyorsa indeksin yönlendirdiği en küçük KB kapsamını seçin; IDMAP, KG adjacency ve ilgili FACTS kayıtlarını kullanın. Kanıtlanmamış araç davranışı eklemeyin.
2. Dar kapsamlı değişiklik yapın. Kullanıcıya görünen başarı metnini yalnızca gerçek API/GATT sonucuna dayandırın. Secret/token değerlerini loglara veya dokümana yazmayın.
3. JVM testlerini çalıştırın: `./gradlew.bat testDebugUnitTest` (Windows'ta gerekirse Android Studio JBR için `JAVA_HOME` ayarlayın). BLE donanım davranışını JVM testi tek başına doğrulamaz; cihaz testi gerekiyorsa bunu ayrıca belirtin.
4. KB dosyaları değiştiğinde `node scripts/validate-kb.cjs` çalıştırın ve KB fact ID'lerini yeniden numaralandırmayın.
5. APK teslimi istenen bir görevde `./gradlew.bat assembleDebug` çalıştırın ve güncel APK'yı `C:\Users\aozyurt\Documents\AG\TOGG_Asistan.apk` konumuna kopyalayın. Kopyalamanın başarılı olduğunu dosya varlığı/boyutuyla doğrulayın. Bu kılavuz tek başına her kod değişikliğinde APK kopyalamayı zorunlu kılmaz; görev paketinin teslim koşulunu izleyin.
6. Git commit/rebase/push işlemlerinde önce branch ve working tree'yi inceleyin. Mevcut kullanıcı değişikliklerini koruyun, istenen kapsam dışında dosyaları eklemeyin ve son durumda branch durumunu raporlayın.

## 6. İlgili kaynaklar

- [Repository agent contract](../AGENTS.md)
- [Knowledge Base index](KB_INDEX.md)
- [Knowledge Base protocol](KB_PROTOCOL.md)
- [Togg Companion feature facts](togg-companion/FACTS.jsonl)

