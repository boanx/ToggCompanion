# Smart Access sertifika hataları — 15 Ekim 2026

## Kullanıcı denemelerinin gösterdiği

Paylaşılan tanıda MTU247 ve TRANSPORT_READY görülüyor: en az iki denemede GATT, servis/characteristic keşfi ve CCCD aboneliği tamamlanmış. Son ekrandaki DISCONNECTED/MTU23 değerleri kopma sonrası sıfırlanmış durumdur; geçmiş başarılı bağlantıyı geçersiz kılmaz. Trumore’da kapı kilitleri kullanıcı tarafından çalıştırılmıştır; Companion’da henüz imzalı oturum/komut başarı kanıtı yoktur.

Son kurulum görüntülerinde “Smart Access sertifika HTTP500” ile “Sertifika base64 verisi geçersiz” ayrı denemelerde görülmüştür. İlk mesaj telefon ilişkisi değil sertifika endpoint’indendir; eski log alma/oluşturmayı ayıramaz. İkinci mesaj, eski parser’ın alan bulunamadığında da decode hatasında da aynı catch’e girmesi nedeniyle bozuk Base64 kanıtı değildir. Sunucu ham yanıtı paylaşılmadığından kök neden kesinleşmemiştir. Kullanıcı VIN/truId/token/sertifika değerleri bu belgeye kopyalanmaz.

## Orijinal kanıtla karşılaştırma

Yerel `work/trumore-analysis/analysis/cert_audit/certificate_response_parser.asm.resolved.txt` içindeki0x15e2eb4 parser’ı vin/certificateBase64/timestamp alanlarını okur,0xb97854 decoder’ına gider. Mevcut field isimleri bu kanıtla uyumludur. GET/POST/query/header sözleşmesinin önceki tamamlayıcı kanıtı SMART_ACCESS_IMPLEMENTATION.md’de kayıtlıdır. Host veya alan isimlerini bu hata üzerinden tahmin ederek değiştirmedik. Bu tur orijinal decoder’ın tüm kabul ettiği kodlama çeşitleri yeniden çözümlenmedi.

## Kaynak değişiklikleri ve gerekçe

- `SmartAccessCertificate`: data zarfı türü, vin türü, eksik/null/metin-olmayan/boş/büyük certificateBase64, gerçek Base64 decode hatası, sertifika formatı ve hesap/araç/telefon anahtarı/tarih bağı ayrı hata kodlarıyla ayrılır. Standart Base64’te yalnız ASCII boşluk/tab/CR/LF normalize edilir; keyfi karakter veya belirsiz JSON alanı kabul edilmez. URL-safe/percent-escaped Base64 için kanıtsız otomatik fallback eklenmez.
- `SmartAccessCertificateClient`: PHONE_RELATION, GET_CERTIFICATE ve CREATE_CERTIFICATE etiketleri HTTP/ağ/JSON yanıt hatalarında gösterilir; GET sertifika parser hatası da GET_CERTIFICATE olarak etiketlenir. Ağ/TLS ve timeout ham exception yerine sabit açıklama taşır. HTTP500 yeni oluşturma isteğiyle otomatik tekrar edilmez.
- `VehicleBleManager`: paylaşılabilir log yalnız sabit stage/error etiketlerini ve HTTP kodunu kaydeder. GATT bağlantı callback status/state ve servis keşfi status değerleri eklenir. Query, VIN, truId, public/private key, token veya ham yanıt loglanmaz.
- `SmartAccessSetupDialog`: aynı hata metninin iki kez gösterilmesi kaldırılır.

Örnek yeni tanı: `CERTIFICATE_REJECTED HTTP=500 GET_CERTIFICATE` veya `CERTIFICATE_REJECTED GET_CERTIFICATE CERT_NULL`. Bunlar örnektir; yeni cihaz denemesinden alınmış sonuç değildir.

## Değişmeyen doğrulamalar ve sınırlar

Tam sürüm1/247 bayt biçimi, kimlik/VIN/telefon anahtarı/geçerlilik ve sahiplik doğrulaması korunur. HTTP2xx veya çözülen Base64 tek başına komut yetkisi sağlamaz. İşlem sonucu belirsizken otomatik sertifika oluşturma veya başka uygulamadan anahtar kopyalama yapılmaz. HTTP500’ün request veya backend kök nedeni henüz çözülmüş sayılmaz; gerçek response şeması görülmedi.

## Teslim / sonraki deneme

Kaynak düzenlemesi; APK derlenmedi, test eklenmedi/çalıştırılmadı, canlı sertifika isteği/araç komutu gönderilmedi, GitHub push yapılmadı. KB ve whitespace statik kontrolleri yapılır. Son dağıtılmış telefon/saat APK’ları bu düzeltmeleri içermez. Kullanıcı açıkça yeni telefon paketi istediğinde bu kod alınır; aynı BLE cihazında bir sertifika alma denemesi sonrası yeni stage/error koduyla ilerlenir. Sertifikayı yeniden oluşturma, mevcut Trumore erişimine etkisi doğrulanmadan otomatik çözüm olarak sunulmaz.

## 19 Ekim 2026 — Güncel telefon paketi

Kullanıcının açık PAKETLE isteğiyle telefon debug APK’sı başarıyla derlendi. Paket bekleyen sertifika parser/tanı değişikliklerini, Yolculuğum/myTrip tüketimini,3 dakika canlı telemetri sorgusu/gönderimini ve e-Motor altındaki fazla rota butonunun kaldırılmasını içerir. Rota kontrolü4dk kalır. Yerel dosya `outputs/ToggCompanion-current-debug.apk`, boyut4111034 bayt, SHA256 `61d525deb1be63d53ba77a7ef7f53d76559eeceb667158ce76519f71c5a5b634`. Temel commit `73dcc2f4582234bd93c34ebb35773cae1fe07d8b` üzerine kaydedilmemiş kaynak değişiklikleri dahildir. Bu tur saat derlenmedi; eski saat teslimi tek aktif APK kuralıyla manifest kullanılarak obsolete/2026-10-19/paket-oncesi altında arşivlendi. GitHub push/release yapılmadı. Önceki kaynak-only notları bu yerel paket için aşılmıştır. HTTP500 kök nedeni ve ABRP özel consumption alanının kalibrasyon etkisi doğrulanmadı; cihaz testleri manuel.

## 19 Ekim — Yeni cihaz tanısı ve güvenli yanıt şeması (kaynak)

Kullanıcı yeni pakette iki başarılı MTU247/GATT_SERVICES status0/TRANSPORT_READY olayı paylaştı. Son durumda GATT CONNECTED, oturum ERROR. GetCertificate üçten fazla kez CERT_MISSING, CreateCertificate iki denemede HTTP500 döndürdü. Parser akışına göre CERT_MISSING anında seçilen JSON nesnesinin vin alanı önceki kontrolleri geçti; certificateBase64 alanı bulunmadı. Bu, API’de bu telefon için hiç sertifika olmadığı veya yanlış JSON zarfı olmadığı yönünde kesin sonuç vermez. Önceki status147 ve status8 kopmaları sertifika hatasından ayrı olaylardır; sayısal GATT kodlarının nedenini bu kayıt tek başına açıklamaz.

Orijinal21f32e4 akışı0x21f3540–0x21f3594 aralığında UpToDate ve Success ayrımı yapar; tam parser yalnız Success yolunda çağrılır, UpToDate mevcut önbelleğin timestamp’ini günceller. Güncel yanıtta bu statünün gerçekten bulunduğu henüz kanıtlanmadı. lastCheck göndermeyen mevcut çağrı ve doğrulanmış tam sertifika kontrolleri korundu; UpToDate diye varsayılıp eksik sertifika kabul edilmez.

Yeni kaynak tanısında callback üzerinden CERT_HTTP operasyon/status ve CERT_SCHEMA kaydı eklenir. Şema yalnız sabit allowlist alanların türlerini, ek alan sayısını, root/data/result/response nesnelerinde bir seviye özetini ve status/statusCode içindeki bilinen Success/UpToDate/NotFound/Error/Pending kelimelerini içerir. Ham bilinmeyen alan adları, değerler, certificateBase64 içeriği, VIN/truId/anahtar/token/gövde loglanmaz. Bu alt nesneler tanı için incelenir; yeni zarf veya alternatif sertifika alanı kabulüne dönüşmez. Eski kurulum callback’inden gelen tanı hesap/VIN bağlamı değiştiyse kaydedilmez.

HTTP500 hata gövdesi bu değişiklikte okunup paylaşılmaz; create isteğinin kesin backend gerekçesi halen yoktur. Yeni kod sertifika üretim sorununu çözdü iddiası taşımaz; sonraki açık derleme isteğiyle alınacak pakette yanıt şemasını ayırır. Bu kaynak eklentisi19 Ekim’de daha önce verilen4111034 bayt APK’de yok; yeni derleme/test/canlı istek/push yapılmadı.

## Son19 Ekim paketi — güvenli sertifika şema tanısı

Yeni açık paketleme isteğiyle telefon assembleDebug başarılı. Güncel APK artık CERT_HTTP/CERT_SCHEMA tanısını da içerir. Dosya outputs/ToggCompanion-current-debug.apk;4112546 bayt; SHA256 `2aa7e6b17daf5c292092c5c052255b85a27664abf934bf5fcdd7c01970deec21`. Temel commit `73dcc2f4582234bd93c34ebb35773cae1fe07d8b` + bekleyen kaynak değişiklikleri. Önceki paket manifest ile obsolete/2026-10-19/sertifika-sema-paketi-oncesi altında arşivlendi. Test/canlı istek/araç doğrulaması/push yok. Sonraki kullanıcı denemesinde BLE bağlanıp bir kez Sertifikayı al ve yetkilendir sonrası tanı paylaşılmalıdır; sertifika HTTP500 kök nedeni henüz açık.

## Yeni runtime sonucu — gizlenen iki alan

Son kullanıcı kaydında PHONE_RELATION HTTP200 ve root timestamp:string +otherFields2; GET_CERTIFICATE HTTP200 ve root vin:string,timestamp:string +otherFields2 görülüyor. CREATE_CERTIFICATE HTTP500. Servis bağlantısı/MTU247 tamamlanmış, daha sonra GATT status8 ile kopmuş. Telefon kaydının HTTP2xx olması tek başına payload’ın tüm iş kurallarının kabul edildiği anlamına gelmez. GetCertificate’nin HTTP2xx nesnesi beklenen tam sertifika sözleşmesine uymuyor; iki ek alanın adı önceki allowlist nedeniyle görünmüyor. Bunun UpToDate, yeni alan adı veya backend hata/sonuç zarfı olduğu kanıtlanmadı.

Önceki tanı filtrelemesi gerekli alan adlarını da sakladığı için kaynakta düzeltildi: bilinen alanların yanında en fazla16 ek alanın yalnız adı/türü alfabetik sırayla yazılır. Ek adlar en fazla48 karakter ASCII identifier olabilir; VIN biçimli ve16–48 hex karakterli dinamik adlar gizlenir. Alan değerleri, key/query/token/certificate içerikleri yazılmaz; tanınmayan uzun/dinamik alanların yalnız sayısı unlistedFields olarak kalır. Bu, bilinmeyen alternatif sertifika alanını kabul etmez ve yeni gerçek yanıtın ne olduğunu henüz kanıtlamaz.

Kaynak-only eklentidir: güncel4112546 bayt APK bu yeni isim görünürlüğünü içermez. Derleme/test/canlı request/GitHub push yapılmadı. Tekrarlayan create500 denemesi çözüm olarak önerilmez; bir sonraki açık paket isteğiyle şema adları görünür hale gelir.

## 19 Ekim — Sahip kaydı / paylaşılan anahtar ayrımı

[Kaynak araştırması ve uygulama planı](SMART_ACCESS_ENROLLMENT_RESEARCH.md). Önemli düzeltme: ikinci CreateCertificate yolu0x2087a44 isOwnerCertificate=false gönderir; sessionId/vehicleName paylaşılan/saat anahtarı çağıranlarıyla bağlantılıdır. Önceki bu varyantın true olduğu notları aşılmıştır. Araç içi ilk kayıt ile davet kabulü ayrı süreçlerdir; mevcut500’ün kesin nedeni belirlenmedi. Bu araştırmada uygulama kodu/derleme/canlı istek yok.


## 21 Ekim — Doğru BLE hedefi ve kaynak düzeltmesi

AF221–AF223 / SMART_ACCESS_ENROLLMENT_RESEARCH.md: BLEsmart seçimi147 verdi; taranan My T-SAS hedefi GATT0/MTU247/TRANSPORT_READY oldu, yetkili oturum açılmadan yaklaşık35sn sonra koptu. Fetch200/CERT_MISSING sürüyor. Kaynakta tarama ilk sıraya alındı, generic smart adaylığı kaldırıldı, servis kanıtı etiket/sıralama eklendi. Bunlar ve AF212 ek alan-adı tanısı mevcut APK içinde yok. Bu tur build/install/push/app test suite yok; yeni açık paketleme isteği gerekiyor. Mevcut-telefon araç onayı yeni Companion anahtarı kaydı sayılmadı.


## 21 Ekim — Yeni APK, sonuç alanları ve gerçek Failure yanıtı

Kullanıcı açıkça Derle ve telefona kur yanıtıyla telefon build/install işlemini yetkilendirdi. Aynı tanı çalışmasında üç başarılı assembleDebug/yerinde adb install -r -t iterasyonu yapıldı: ilk paket AF212 alan adlarını, ikincisi orijinal pooldan bilinen enum adlarının güvenli değer tanısını, son paket uygulama sonucu reddini de içeriyor. Uygulama verileri korunarak güncellendi; eski APK ve loglar manifestlerle obsolete altında.

İlk yeni APK actual alanları gösterdi: PHONE_RELATION root timestamp/resultEnum/resultIndex; GET_CERTIFICATE root vin/timestamp/resultEnum/resultIndex. Sonuç değerleri için yalnız sabit allowlist adları ve0..11 tam sayıları kaydedildi; sunucunun keyfi metni/payloadı kayda alınmıyor. Sonraki gerçek kayıt: PHONE_RELATION HTTP200, resultEnum=Updated, resultIndex=2; GET_CERTIFICATE HTTP200, resultEnum=Failure, resultIndex=1. Sertifika verilmediği bu istekte backend uygulama sonucu ile doğrulandı. Failure kesin sebebi, yeni kayıt yetkisi veya doğru API parametrelerinin tümü bu sonuçtan çıkarılmaz.

ÖNEMLİ: original-result-enum-pool.json içindeki off_8 değerleri Dart enum sıra bilgisidir; ağ yanıtının resultIndex alanıyla eşlenmez. Örneğin eski pool Updated9/Failure2 iken gerçek ağ yanıtı Updated2/Failure1 oldu. Kararlar bilinen resultEnum metnine dayanır; ordinal-index varsayımı yapılmaz.

SmartAccessCertificate.parseFromServer, resultEnum mevcutsa Success dışındaki sonucu sertifika parse adımından önce açıkça reddeder. Failure için CERT_SERVER_FAILURE; UpToDate için CERT_NOT_MODIFIED; bilinmeyen/metin olmayan sonuç için genel güvenli hata verir. Sonuç alanı olmayan yerel cache kaydı önceki VIN/kullanıcı/public-key/tarih doğrulamasından geçmeye devam eder. Başarı/anahtar bağlama/imza şartı gevşetilmedi; UpToDate için geçersiz/olmayan cache kabul edilmedi. Telefonda son pakette CERT_SERVER_FAILURE mesajı tekrar üretildi.

Güncel APK: outputs/ToggCompanion-current-debug.apk;4114798 bayt; SHA2562643bc7757558d147248b89fa7c83028bba7f3c8275965642a93876bb4500ece. Version1.0/code1 debug, temel73dcc2f + bekleyen kaynak değişiklikleri. Son build2sn/33task/4executed, install Success. Wear build/push yok. Tek etkin workspace APK kuralı dosya taramasıyla doğrulandı. Kaynak test suite yok; manuel cihaz denemeleri ve KB doğrulaması yapıldı. Sertifika Failure kök nedeni ve Companion yetkili araç komutları hâlâ doğrulanmadı.

Yeni kanıtlar: companion-diagnostics-updated-apk.txt (alan adları), companion-diagnostics-result-enum.txt (güvenli enum sonuçları), original-result-enum-pool.json, final-apk-failure-message.txt. Ham token/özel anahtar saklanmadı.


## 21 Ekim — HTTP hata gövdesi için kaynak tanısı (APK’de yok)

Devam denemesinde mevcut APK üzerinden bir CreateCertificate HTTP500 ve sonra ayrı GetCertificate CERT_SERVER_FAILURE UI sonucu doğrulandı; sertifika hâlâ alınamadı (AF227). F94 son bölümde kanıt konumları var.

SmartAccessCertificateClient non2xx dalına CERT_ERROR_SCHEMA eklenir. Hata gövdesi8192 baytla sınırlı okunur. JSON’da yalnız sabit alan adları/türleri, bilinen sonuç enumları ve0..11 resultIndex; errors nesnesinde yalnız sabit istek alanlarının türleri görünür. Bilinmeyen alan adları ve bütün serbest değerler gizlenir. title/detail/traceId/instance/message/error içerikleri, anahtarlar ve request echo’su paylaşılmaz. Boş, nesne JSON olmayan, limit üstü ve okunamayan yanıtlar sabit etiket taşır. Hata gövdesinin okunamaması HTTP durumunu gölgelemez. Normal yanıt tanısına timeToWaitBeforeFetch alanının türü de eklenir; bekleme/yeniden deneme/sertifika kabul davranışı değişmez.

Kaynak değişikliği; yeni build/install/push veya uygulama test suite yok. Mevcut APK aynı kalır. Tanı kaydı henüz gerçek HTTP500 gövdesinde çalıştırılmadı; sunucu gerekçesinin bu şemayla kesin bulunacağı iddia edilmez. İleriki açık derleme sonrası bir create denemesinde hata formatı/validation alanlarını ayırmak için kullanılabilir. AF228–AF229.


## 21 Ekim — Eksik ClaimCarToUser ve gerçek owner anahtarı çakışması

AF230–AF234 / F96 SMART_ACCESS_SHARED_ACCOUNT_PREPARATION.md: HTTP hata gövdesi tanısı açık kullanıcı izniyle derlendi/kuruldu; tek create500 yanıtı JSON nesnesi değil. Ardından orijinal2.1.25 ve2.1.26’da owner sertifikasından önce ClaimCarToUser bulundu. Query ownerPublicKey/vin/overwritePhoneRelation=false, POST boş JSON. Kaynağa bu adım eklendi; yalnız Success/Updated/RelationAlreadyExists sonrası create, OverwriteDetection sonrası OWNER_PHONE_CONFLICT ve durma. OwnerChange ve diğer bilinmeyen/başarısız sonuçlar create’e geçirmez. Gerçek UI denemesi OWNER_PHONE_CONFLICT verdi; bu yalnız server OverwriteDetection ile üretilir. Böylece eksik kayıt ve mevcut farklı owner anahtarı ayrıldı; create500 backend istisnası tam açıklanmadı.

Kullanıcı mevcut Trumore anahtarını koruyup ayrı hesaba paylaşım yolunun hazırlanmasını seçti; ayrı hesap henüz yok. Overwrite=true, anahtar silme veya otomatik kendi kendine davet yapılmadı. F96 guest davet endpointleri, nonce/imza zinciri, bağımsız hesap depolaması ve kalan uygulama/manuel doğrulama adımlarını içerir. Guest kabul entegrasyonu henüz uygulanmadı. Kullanıcıya ait ikinci hesap ve davet bağlantısı sonraki canlı aşama için gerekiyor.

Son owner kayıt düzeltmesi içeren APK kuruldu: outputs/ToggCompanion-current-debug.apk; 4116002 bayt; SHA256 0fb7a57d67041056667e207e331f9f4672ce4d5265c26f7c89ae6efc875a3dc6. Önceki source-only HTTP tanı notları bu paketle aşılmıştır. Tek aktif APK; önceki iterasyonlar manifestle obsolete altında. Wear/push/app test suite yok. BLE yetkili komut doğrulanmadı.
