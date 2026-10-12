# Trumore 2.1.25 APK doğrulaması

İlk inceleme: 10 Ekim 2026. Güncelleme: 12 Ekim 2026. Bulut telemetri/token yenileme çağrıları, PlannedJourneys istek şeması ve BLE komut yükünün önemli bölümü orijinal APK üzerinden doğrulandı. Companion'ın sertifika sağlama, imzalı oturum ve araç durumunu doğrulama entegrasyonu tamamlanmadı. Önceden kullanılan iki baytlık BLE paketleri orijinal komut zinciriyle uyuşmuyor.

> Bu belge tarihsel bulguları ve düzeltme eklerini korur. Güncel entegrasyon/derleme durumunda [Proje Durumu](PROJECT_STATUS_2026_10_12.md) ve [Smart Access](SMART_ACCESS_IMPLEMENTATION.md) belgelerini esas alın.

## 1. Kaynak ve yöntem

Kullanıcının sağladığı dosya: `C:/Users/aozyurt/Documents/CX/tools/decompiled_sources/Trumore_2.1.25.apk`.

Dosya uzantısı APK olsa da içeriği base APK ve mimari/dil/ekran yoğunluğu parçaları içeren XAPK paketidir. Base APK manifesti `com.togg.toggmobile`, sürüm `2.1.25`, versionCode `661` bilgisini doğrular.

| Kaynak | SHA-256 |
| --- | --- |
| Sağlanan dış paket | `36df19718b9842a517fdd0997cdb3cae51b9f898fe20c774fab97ea540741d09` |
| `com.togg.toggmobile.apk` | `cf5c3b9b12dbdc2fee690328d428f13ae4808ce8f15ff2253eb1b9578dd50ee0` |
| `config.arm64_v8a.apk` | `34972db5fc32167d97df88971961e093ba8db00428a6f4ebe1e30e3646a052b6` |
| `lib/arm64-v8a/libapp.so` | `086ac67c7f1edc261e2744d0ad17f3e6aa79081a7b4c2929f9d577d8b3c0ad78` |

12 DEX dosyası jadx 1.5.6 ile işlendi. Decompiler 44.583 sınıflık iş listesi sonunda 444 hata bildirdi; tüm yöntemlerin eksiksiz Java olarak geri kazanıldığı iddia edilmiyor. Aşağıda kullanılan Smart Access kripto yöntemleri ve Retrofit arayüzleri okunabilir durumdadır. Flutter iş mantığı `libapp.so` içindeki Dart AOT kodundadır. String/byte offset envanterinden sonra [dae](https://github.com/ejfkdev/dae) v0.1.13 ile nesne havuzu ve ARM64 kodu çıkarıldı. Araç Dart 3.12.2 snapshot hash eşleşmesi, sıkıştırılmış pointer'lar ve sıfır parser uyarısı bildirdi; profilin kendi durumu `unverified` olduğundan pseudocode tek başına kanıt sayılmadı. Bu binary için dae'nin birden çok talimatla yüklenen PP offset yorumlarında hata görüldü; gerçek ADD/LDR talimatları `aot_pool_xrefs.py` ve `resolve_aot_pp.py` ile ayrı kontrol edildi.

Çalışma çıktıları repo'nun kardeşi `../trumore-analysis/` dizinindedir: `input/`, `jadx/sources/`, `jadx/resources/`, `binary/`, `inventory.json`, `jadx.log`. Aşağıdaki Java satırları bu jadx çıktısını, native offset'ler 36.766.608 baytlık `binary/libapp.so` dosyasındaki byte konumunu belirtir. Bunlar işlem belleği adresi değildir.

`adb devices` bağlı cihaz göstermedi. APK kurulmadı/çalıştırılmadı; canlı API çağrısı veya fiziksel araç komutu gönderilmedi. Bu rapor statik protokol incelemesidir.

## 2. Kaynak çağrı zinciriyle doğrulananlar

| İşlev | APK kanıtı | Companion karşılaştırması |
| --- | --- | --- |
| Araç telemetrisi | `com/togg/toggmobile/b.java:37,40`, `U4/a.java:17-19`: `https://tva-api.togg.cloud/info/api/v2/VehicleInfo`, GET, `vin` | Host/yol ve query alanı eşleşiyor. |
| Paylaşılan araç telemetrisi | `b.java:46`, `U4/a.java:13-15`, `V4/a.java:75,83`, `.../tva_home_widget/data/remote/e.java:346-389`: `FriendVehicleInfo`, `vin`, `ownerId` | Owner ID varsa arkadaş endpoint'i seçimi eşleşiyor. |
| Telemetri başlıkları | `.../data/remote/e.java:433`: `Authorization: Bearer <token>`, `User-Agent: Trumore Home Widget`, `x-app-version: 661` | Başlıklar eşleşiyor. |
| Token yenileme | `b.java:43,49`, `U4/b.java:17-20`, `.../data/remote/k.java:126-133` | `https://toggid.togg.cloud/auth/realms/toggid/protocol/openid-connect/token`, form POST `client_id=super-app`, `grant_type=refresh_token`, `refresh_token` eşleşiyor. İlk girişin bütün OAuth/PKCE ayarları bu kanıtla doğrulanmaz. |
| Uygulamaya yönlendirme zarfı | `jadx/resources/AndroidManifest.xml:218-228,285-291` | `trumore-superapp*://custom/redirection` ve SmartDevice/SmartAccess/EvPlanner/ShareLocation HTTPS yönleri mevcut. Her query parametresinin işlevi ayrıca teyit edilmelidir. |
| Smart Access native köprüsü | `p187v4/d.java:57,82-92,147-158,167-194` | `smart_access_ble_package`; `generateKeystore(truId,vin)`, `getPublicKey`, `signMsg`, `verifySignature` yöntemleri var. Companion'da bu araç anahtarı kurulumu/imzalama akışı yok. |
| Araç/kullanıcıya bağlı özel anahtar | `defpackage/C3297c.java:50-57,101-111` | AndroidKeyStore'da kullanıcı kimliği + VIN ile alias; EC `secp256r1` anahtar çifti üretiliyor. Companion'ın EncryptedSharedPreferences kullanması aynı işlev değildir. |
| Mesaj imzalama/doğrulama | `C3297c.java:84-98,144-184` | `SHA256withECDSA`, DER imzadan 64 bayt R/S dönüşümü ve P-256 doğrulaması var. Companion'ın ham komutları bunları içermiyordu. |

Orijinal widget'in 401 yanıtında token yenileyerek üç istek sınırı kullanması ile Companion'ın tek yenileme kullanması farklıdır. Bu, tek başına bir protokol hatası değildir. Araç erişim reddini token expiry'den ayırmak yine gereklidir.

## 3. Native kanıt ve doğrulama sınırı

| `libapp.so` byte offset | Bulunan literal | Çıkarılabilen sonuç |
| --- | --- | --- |
| `0x15245d` / `0x233209` / `0x21f2f1` | `f0002412...` / `f0002413...` / `f0002415...` | Companion UUID değerleri orijinal binary'de var. Service/write/notify rollerinin eşleştirmesi string varlığından kesinleşmez. |
| `0x10de6a` / `0x12504f` | `REQUEST_USER_CERTIFICATE` / `REQUEST_SIGNED_CHALLENGE` | Sertifika/challenge mesaj türleri mevcut; frame düzeni ve sırası çıkarılamaz. |
| `0xd0661` / `0x13a04a` | `RAND_CHALLENGE_MSG` / `SIGNED_CHALLENGE_MSG` | Rastgele ve imzalı challenge kavramları var. |
| `0x226282` | SAM durum imzasının doğrulanamaması veya sayacın artmaması hata metni | Orijinal kodda durum imzası/sayaç doğrulamasına ilişkin hata yolu var. Her notification'ı başarılı araç sonucu saymak için kanıt yok. |
| `0x1f1ff7` / `0x1b1a59` | `Accessory` / `Ready` | Modlar orijinal uygulamada ayrı adlandırılıyor. |
| `0x142210` / `0xea3f4` / `0x279476` | `engineStarted` / `engineStopped` / `canDrive` | e-Motor durum/yetki alanları var; komut opcode/bit değerleri teyit edilmedi. |
| `0x1daa94` | Motor başlatırken telefonun şarj yuvasına yerleştirilmesi gerektiğini anlatan metin | Orijinal uygulama bir ek başlatma önkoşulu bildiriyor. Hangi araç/modlarda geçerli olduğu çağrı zinciriyle doğrulanmadı. |
| `0x21cf5c` / `0x236a8f` / `0x142d7f` | `canTrunkOpen` / `Trunk Opened` / `Trunk Closed` | Bagaj açma yetkisi ve açık/kapalı durumları var. Kapatma komutu veya toggle semantiği teyit edilmedi. |
| `0x13a252` / `0xb02c2` | `/j/api/PlannedJourneys` / `PlannedJourneyRequest` | Endpoint/model adı mevcut. `{vin,destination,stops}` isteğinin şeması, HTTP yöntemi ve araç teslimi bu string'lerle doğrulanmaz. |
| `0x1908b5` / `0xba2bc` | `/navigation/carDisplayDirections` / `/carDisplayDirections` | Route adları mevcut. Companion'daki koordinat/ad query bağlaması teyit edilmedi. |

### 3.1. AOT kodundan geri kazanılan BLE komutları

Aşağıdaki değerler yalnızca enum sıra numaraları değildir: `0x19fa42c` encoder'ı enum nesnesindeki sayısal değeri `0x19fa454` adresinde yükler ve sekiz baytlık yükün ilk alanına `0x19fa644` adresinde koyar. `dae-export/text/objs.txt:130498–130556` aynı ad/değer ilişkisini verir. Adresler ELF sanal kod adresleridir; yukarıdaki string byte offset'leriyle karıştırılmamalıdır.

| İşlev / orijinal enum | Yükteki komut değeri | Ek kod kanıtı |
| --- | --- | --- |
| Kapı kilitleme — `CMD_DOOR_CLOSE` | `0x01` | `0x22a72c4` seçiminde PP `0x107e50` |
| Kapı kilidi açma — `CMD_DOOR_OPEN` | `0x02` | Aynı seçimde PP `0x107e48` |
| Elektrikli bagaj açma — `CMD_ETG_OPEN` | `0x04` | `0x22a803c` seçiminde PP `0x107d88` |
| Elektrikli bagaj kapatma — `CMD_ETG_CLOSE` | `0x05` | Aynı seçimde PP `0x107d90` |
| Araç başlatma — `CMD_CAR_START` | `0x0e` | PP `0xd50a8`, `0x19fa42c` encoder |

Yük düzeni: `[command, year >> 8, year & 0xff, month, day, hour, minute, second]`. `0x19fa718` oluşturucusu o anki zamanı alır ve UTC dönüşümüne gider. Encoder alanları ve liste yazımları `flutter_audit/car_payload_encoder.asm` ve `car_message_constructor.asm` dosyalarında saklandı.

Kapı/bagaj seçimi → `0x22a6a34` → `0x1a57430` komut kuyruğu. Challenge aşaması `0x2296758` → `0x2296c3c`; sekiz baytlık yük challenge ile birleştirilir, `0x207d5a0` → `0x207d648` üzerinden native `signMsg(data,truId,vin)` çağrılır. İmza eklenir ve `APP_USER_CMD_MSG` (`0x23`) zarfı `0x19f63e0` ile oluşturularak `0x2292724` gönderim yoluna verilir. Ham ARM64 kanıtları `flutter_audit/serialize_car_command.asm`, `signed_command_stage.asm` ve `signMsg_method_channel.asm` içinde korunur.

Sertifika/challenge türlerinin ayrı sayısal alanları da geri kazanıldı: REQUEST_USER_CERTIFICATE `0x10`, REQUEST_SIGNED_CHALLENGE `0x11`, REQUEST_APP_USER_CMD `0x12`, REQUEST_PERMISSION_CMD `0x13`, REQUEST_SAM_APP_STATUS `0x14`; USER_CERTIFICATE_MSG `0x21`, SIGNED_CHALLENGE_MSG `0x22`, APP_USER_CMD_MSG `0x23`, APP_USER_DELETEPAIR_MSG `0x24`, APP_USER_CALIBRATE `0x25`, SAM_APP_STATUS_MSG `0x26`, RAND_CHALLENGE_MSG `0x27` (`objs.txt:130592–130734`). Bu tablo bütün parçalama, tekrar gönderme ve oturum sırasının uygulandığını göstermez.

Companion'ın eski `A1/A2/A3` paketleri bu gözlenen imzalı komut zinciriyle uyuşmaz. Bagaj kapatma için ayrı komut vardır; araca/SAM sürümüne göre destek ve fiziksel kapanma ayrıca denenmelidir. e-Motor'u durdurma veya READY'den aksesuar moduna geçirme komutu henüz geri kazanılmadı. GATT yazma onayı araç işlemi başarısı değildir.

Ek çözümleme dış paket yapısını da geri kazandı. Veri paketi `MJc` (`0x1f0bf30`) sekiz bayt başlık kullanır: tür, güncel blok, LE16 parça uzunluğu, LE16 toplam yük uzunluğu, LE16 toplam blok sayısı; ardından parça yükü gelir. `0x19f63e0` builder'ında varsayılan toplam paket kapasitesi 247, yük kapasitesi 239 bayttır. İstek/ACK `NJc` (`0x1f0c23c`) sekiz bayttır: tür, önceki mesaj durumu, LE16 Iuu, LE32 Juu. Son iki alanın ACK/sayaç anlamları henüz tam izlenmedi. Sanal serializer çağrıları resmi Dart formatına göre snapshot dispatch tablosu çözülerek bulundu; raw encoder/decoder düzenleri karşılaştırıldı. Ayrıntı: `flutter_audit/BLE_AOT_EVIDENCE.md`.

SAM durum handler'ı `0x2294454`, `SAM_APP_STATUS_MSG` ve tamamlanmış 85 bayt yük bekler. `[0,15)` durum, `[15,21)` altı bayt LE sayaç, `[21,85)` 64 bayt imzadır. Sayaç önceki değerden büyük olmalı ve `[0,21)` verisinin araç public key'iyle imza doğrulaması true dönmelidir; ancak bundan sonra durum/sayaç güncellenir (`0x22946e0–0x2294730`). Bu kurallar teyit edildi; 15 bayt durumun bit alanları, izin payload'ı ve tek bir komutun tamamlanma eşleştirmesi henüz çözülmedi.

### 3.2. PlannedJourneys çağrı zinciri ve JSON şeması

`0x2255df4` → `0x1881a5c` builder'ı `Gec` modelini oluşturur (`AllocationStub_Gec:0x20c80f4`) ve `0x2255e04` adresinde API `0x2256014` çağrısına verir. Bu yöntem POST (`0x2256140`), application/json (`0x2256160`), Bearer/Authorization metadata (`0x22560a0/0x22560b8`) ve `/j/api/PlannedJourneys` (`0x225618c`) kullanır. Query haritası boştur; bu çağrı modelinde VIN eklenmez.

Gec `toJson` (`0xf53c3c`) → serializer `0xf53ac4`: `startingPoint`, `arrivalPoint`, `waypoints`. Nokta serializer'ı `0xe8ef28`: `geoLocation` ve `address`; `0xe8fcf4`: sayısal `latitude`/`longitude`; `0xe8efec`: `label` ve nullable adres alanları. Örnek yapı:

```json
{
  "startingPoint": {"geoLocation": {"latitude": 40.0, "longitude": 28.0}, "address": {"label": "Başlangıç"}},
  "arrivalPoint": {"geoLocation": {"latitude": 41.0, "longitude": 29.0}, "address": {"label": "Varış"}},
  "waypoints": [{"geoLocation": {"latitude": 40.5, "longitude": 28.5}, "address": {"label": "Ara durak"}}]
}
```

Örnekteki koordinatlar açıklama içindir. Serializer null alanları atlar; başlangıç olmadan isteğin backend tarafından kabul edilmesi henüz denenmedi. Bu API'nin hesap/aktif araç seçimi ve araç ekranına teslimi çalışma zamanında teyit edilmelidir. HTTP 2xx yalnızca Cloud kabulünü ifade eder. Kaynak zinciri `../trumore-analysis/CLOUD_AOT_EVIDENCE.md` ve `planned-*.annotated.asm` dosyalarında saklandı.

### 3.3. OAuth yapılandırması

`assets/flutter_assets/assets/environment/prod.env` içindeki public client ID, redirect ve token URL değerleri Companion ile eşleşir. Lowercase authLink `/auth/realms/toggid/...` kullanırken uppercase AUTH_LINK `/realms/toggid/...` kullanır. AOT getter `0x17bdd04`, AUTH_LINK'i okur; auth URL builder `0x17bdb14` bu getter'ı kullanır. İki yolun varlığı Companion'daki yolun bozuk olduğunu kanıtlamaz; çalışma zamanı giriş denemesi yapılmadı. Bulunan `scope=openid` + S256 akışları e-posta/parola güncellemesine aittir. İlk girişin scope listesini bunlardan türetmedim.

### 3.4. Sertifika sağlama ve yanıt doğrulama

`0x21f1f68` ve `0x2087a44`, `/Certificates/CreateCertificate` için `phonePublicKey` içeren ayrı harita ile `vin`, `shortCertificateBase64`, `isOwnerCertificate` alanlarını kurar; ikinci varyant `vehicleName`/`sessionId` ekler. Native keystore/getPublicKey bağlantıları sırasıyla `0x21f1a50` ve `0x201b088` adreslerinde doğrudan izlenmiştir. Kısa sertifika encoder'ı `0x1d89f34`, native imzalama `0x2087ffc` ve 64 bayt imza kontrolü üzerinden base64 üretimine gider. İç sertifika alanlarının tam anlamı ve HTTP haritalarının yerleşimi teyit edilmedi.

`0x21f32e4`, `/Certificates/GetCertificate` için `phonePublicKey`, `vin`, `isOwner`, isteğe bağlı `lastCheck` kurar. Yanıt parser'ı `0x15e2eb4`, `vin`, `certificateBase64`, `timestamp` okur ve VIN uyuşmazlığını reddeder. Kabul dalı sertifikadaki public key'i yerel anahtarla karşılaştırır (`0x21f35e0`) ve ancak sonra kaydeder. Bu karşılaştırma tek başına sertifika ECDSA imzasının doğrulandığını kanıtlamaz. Reflected/obfuscated HTTP wrapper'ları nedeniyle exact HTTP fiili, host, header/query/body yerleşimi ve sunucu yetkilendirme sözleşmesi hâlâ eksiktir; çalıştırılabilir bir Create/Get isteği tahmin edilerek eklenmedi. Kanıt: `analysis/cert_audit/certificate-evidence.md`.

## 4. İnceleme sonucu yapılan düzeltmeler

- `VehicleBleManager`: doğrulanmamış canlı frame gönderimi, 2,5 saniyelik yazma-onayı başarı fallback'i ve rastgele notification'dan araç durumu üretimi kaldırıldı. Canlı komutlar açıklayıcı bir başarısız sonuç döndürür; yalnızca açık demo bağlantısı simülasyon çalıştırır. GATT bağlantısı araç anahtar yetkilendirmesi sayılmaz.
- GATT keşfi: geri bildirim characteristic'i ve CCCD aboneliği başarılı olmadan CONNECTED verilmez; notification/indication özelliği dikkate alınır.
- `DigitalKeyManager`/`DigitalKey`: davet/referans/JSON metni yalnızca etkinleştirilmemiş kayıt olarak saklanır. Rastgele metinden veya SE referansından yetkili token üretilmez; eksik izinler true olmaz ve bilinmeyen geçerlilik süresi 30 gün olarak uydurulmaz. Gelecekte başlayan anahtar geçerli sayılmaz. Kullanıcıya aktivasyon beklendiği gösterilir.
- READY: `NOT_READY`, `INACTIVE`, `IGNITION_OFF`, `ACCESSORY` alt dizgelerinden yanlış READY üretilmez. Açık false bayrağı önceliklidir; eski yolculuk ortalama hızı READY göstermez; yeni false telemetri eski true durumu temizleyebilir.
- Telemetri: canlı oturum/ağ/parse hatasında demo veri oluşturulmaz; varsa kayıtlı telemetri kullanılır. Tek bir eksik koordinat varsa tüm konum bilinmiyor sayılır; geçerli güney enlemi korunur. Araç izin hatası Owner ID boşken de token expiry olarak sınıflandırılmaz.
- Derlemede bulunan önceki telefon-saat lambda ve widget değişken çakışmaları giderildi.
- Eksik davet VIN/plakasına `T10F-DEV-001`/`34 TOGG 10` atama kaldırıldı; telefon ve saat araç bilgisinin bilinmediğini gösterir. İç içe davet yönlendirmesi dört adımla sınırlanır; target query parametresi iki kez URL decode edilmez.
- PlannedJourneys eski `{vin,destination,stops}` gövdesi yukarıdaki `startingPoint/arrivalPoint/waypoints` ve iç içe nokta yapısıyla değiştirildi. Çok durakta ilk nokta başlangıç, son nokta varış, aradakiler waypoint olur. Tek hedefte yalnızca aynı VIN'e ait geçerli kayıtlı konum başlangıç olarak kullanılabilir; bilinmeyen konum uydurulmaz. API, telefon ve ABRP bildirimlerindeki başarı metni Cloud kabulünü bildirir; araç ekranına teslim edildiği söylenmez.

Bu düzeltmeler gerçek Smart Access entegrasyonunu tamamlamaz. Komut sayıları ve imzalı yükün bir bölümü artık biliniyor; Companion'da kullanıcı/VIN anahtar kaydı, sertifika sağlama, tam challenge/session ve imzalı SAM durum çözümleme hâlâ entegre edilmelidir. Bagaj kapatma yeteneği araç/SAM sürümüyle ayrıca doğrulanmalıdır. Aksesuar/READY geçişinin durdurma yönü henüz teyit edilmedi.

## 5. Doğrulama ve açık noktalar

Regresyon testleri davetlerin yetki üretmemesini, olumsuz READY durumlarını, eksik koordinatları, bozuk telemetrinin demo verisine dönüşmemesini, GATT hazır olmanın canlı komut yetkisi sağlamamasını ve geri kazanılan rota JSON şemasını kapsar. `gradlew.bat --offline --no-daemon :app:testDebugUnitTest :app:assembleDebug :wear:assembleDebug` başarıyla tamamlandı: 62 test, 0 hata, 0 başarısızlık; telefon ve saat debug APK'leri derlendi. Bu testler fiziksel araç protokolünü veya orijinal APK'nin çalışma zamanı akışını doğrulamaz. KB doğrulaması teslim raporunda kaydedilir.

Önceki inceleme derlemesinin açık noktaları: BLE ACK alan anlamları, izin payload'ı, tam handshake ve 15 bayt SAM durum/komut sonucu decoder entegrasyonu; kısa/tam sertifika şeması ve sertifika API transport sözleşmesi; yetkili sertifika sağlama entegrasyonu; e-Motor durdurma/aksesuar geçişi; bagaj kapatma destek koşulları; başlangıçsız PlannedJourneys backend kabulü, aktif araç seçimi ve teslim sonucu; ilk OAuth scope listesi; fiziksel araçta uçtan uca deneme.

CONFLICT: Eski AF059/AF061 yalnızca Companion kodunu kaynak göstererek orijinal APK protokolünü doğrulanmış gibi aktarıyordu. Yeni düzeltici KB kayıtları gerçek APK kaynağı ve doğrulama sınırını bu belge üzerinden belirtir; eski kayıtlar geçmiş olarak korunur.

## 6. Sonraki uygulama derlemesi: 12 Ekim 2026

Yukarıdaki 4–5. bölümler önceki protokol inceleme derlemesinin durumunu kaydeder. Sonraki çalışmada ACK alanları, durum haritası, sertifika şeması, Togg ID kullanıcı kaynağı, host/HTTP/query/gövde/header zinciri çözüldü; Companion'a AndroidKeyStore, gerçek sertifika istemcisi, karşılıklı imzalı BLE oturumu ve imzalı komut/durum akışı eklendi. Canlı komutlar gerekli doğrulanmış oturumdan sonra artık gönderilebilir. Güncel kaynak kanıtları, sınırlamalar ve manuel deneme adımları [SMART_ACCESS_IMPLEMENTATION.md](SMART_ACCESS_IMPLEMENTATION.md) içindedir. GATT onayından başarı üretimi geri getirilmedi.

MISSING (güncel): fiziksel araçta uçtan uca deneme; e-Motor READY→aksesuar komutu; sürüm 0 sertifika ve paylaşılan yeni telefon aktivasyonunun ayrıntıları; PIN'in dış kök/kimlik üzerinden doğrulanması; bagaj kapatma firmware desteği; PlannedJourneys fiziksel teslimi ve ilk OAuth scope listesi.

## HTTP 301 rota host düzeltmesi — 12 Ekim 2026

Önceki şema/yol kanıtı API alan adını belirlemiyordu. Güncel public config, runtime NAVIGATION_API_URL getter’ı/client initialization ve kimliksiz HEAD karşılaştırması navigasyon API alan adına geçişi destekler. Superapp yolunun /home yönlendirmesi takip edilmemelidir. Ayrıntılar: [HTTP301_ROUTE_FIX.md](HTTP301_ROUTE_FIX.md). Kaynak düzeltmesi APK’ye alınmadı.
