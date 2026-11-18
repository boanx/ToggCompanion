# Smart Access: araç onayı ve paylaşılan anahtar araştırması

19 Ekim 2026. Araştırma kapsamı: orijinal Trumore2.1.25 ARM64 AOT, Companion kaynakları ve Togg’un resmi T10F kılavuzu. Hesaba giriş, tokenlı API isteği, araç komutu ve anahtar silme yapılmadı. Uygulama kaynak davranışı bu tur değiştirilmedi; APK üretilmedi.

## 1. Sonuç

Sahip telefonunun ilk kaydı, paylaşılan anahtarın davetle kabulü ve mevcut sertifikayla BLE oturumu ayrı süreçlerdir. Companion şu anda üçüncüsünü ve sınırlı sahip sertifika alma/oluşturma isteğini içeriyor; ilk kayıt için araç içi eşleştirme rehberi/durum takibi ve davet kabulüne ait ShareProcess entegrasyonu bulunmuyor. Kullanıcının Trumore’da araçtan onay verdiği bilgisi eksik kapsamı ortaya çıkardı; bunun mevcut HTTP500’ün kesin nedeni olduğu kanıtlanmadı.

## 2. Resmi araç içi kayıt adımı

[T10F Ekim2026 kullanım kılavuzu](https://toggprodcdn.blob.core.windows.net/trumore/T10F_Kullanici_El_Kitabi_2026Ekim.pdf), bölüm3.1.7, basılı/PDF sayfa29–30: birincil anahtar kaydında sahiplik doğrulaması, araç içinde fiziksel anahtar, araç erişim menüsünden başlatılan telefon eşleştirmesi ve her iki uçta internet gerekiyor. Telefonun kablosuz şarj bölmesine konması ve eşleştirme koduyla devam edilmesi anlatılıyor. Bunlar normal GATT bağlantısının tek başına kayıt onayı olmadığına işaret ediyor.

Yerel PDF SHA256:1ee97063635d162936e60ce400fe98582d6c823ea9aea79d712bcf92deb4ead9. PDF212 sayfa. İlgili sayfalar Poppler ile render edilip29 görsel olarak kontrol edildi; JPEG2000 görsellerine ilişkin render uyarıları vardı, kayıt metni okunabildi. Bu PDF19 Ekim’de indirildi; web aracının boyut sınırı nedeniyle text extraction pypdf ile yerel yapıldı.

## 3. APK’de eşleştirme ekranı

`2290280.asm.resolved.txt`,0x2290320/0x2290474 çevresinde pairing-code başlık/açıklaması;0x22904fc çevresinde VIN’in son6 karakterini kullanma yönlendirmesi görülüyor. Bu APK metni bir kod giriş arayüzü olduğunu kanıtlar; her firmware’de aynı kod veya tüm araç onayının bu PIN’den ibaret olduğunu kanıtlamaz. Kılavuz ile APK farklı sürümlerdendir; adımların cihaz davranışı kullanıcıyla doğrulanmalıdır.

## 4. Önceki notta önemli düzeltme: ikinci CreateCertificate paylaşılan sertifikadır

| Kanıt | Bulgular |
| --- | --- |
|0x21f1f68 sahip yolu|0x21f230c NULL+0x20/true isOwnerCertificate değerini yükler; mevcut Companion sahibi oluşturma gövdesi bu yola dayanır.|
|0x2087a44 diğer yol|0x2087d78 NULL+0x30/false isOwnerCertificate değerini yükler;0x2087d80 vehicleName ve0x2087dbc sessionId alanları eklenir.|
|Çağıran0x2286134|0x2286674’te0x2087a44 çağrısı; aynı gövdede SharedKey duplicate/created kayıtları.|
|Çağıran0x2087624|0x2087938’de0x2087a44; My Watch ve shared-certificate hata metinleri.|
|Çağıran0x228e318|0x228e648’de0x2087a44; SharedKeyRecreation başarı/hata kayıtları.|

Eski `analysis/cert_audit/certificate-evidence.md` ve TRUMORE_2_1_25_APK_VERIFICATION.md içindeki ikinci yolun isOwnerCertificate=true olarak yazılması yanlıştı. Araştırma bunu false olarak düzeltir. Bu nedenle sessionId/vehicleName değerlerini mevcut owner POST’una eklemek kanıtlı bir çözüm değildir. sessionId, paylaşılan anahtar akışında anlamlıdır; aynı ismi taşıdığı için araçtan ilk onayın oturumu olduğu varsayılmaz.

## 5. Paylaşılan davet sözleşmesinin bulunan parçaları

| Giriş / adres | Sabit alanlar ve anlam sınırı |
| --- | --- |
|ShareProcess/CreateSession0x228b68c|vin,carModel,sharedName,carColor,ownerName haritası; yanıt sessionId okuması. E-posta bu alanlardan hangisine nasıl dönüşüyor henüz çözülmedi.|
|ShareProcess/sessionDetail0x22870d0|sessionId haritasıyla davet ayrıntısı isteği.|
|ShareProcess/ReplyToSession0x2288c04|sessionId,signatureBase64,phonePublicKey,sharedName,friendName haritası. Kabul yalnız mail/VIN yazmak değildir; telefon anahtarı ve imzalı yanıt içerir.|
|GetPendingInvites0x201a60c|Kullanıcı için davetleri alma endpoint’i string-xref ile bulundu.|
|GetAllSharedPubKeys0x201ab0c|Paylaşılan public-key listesi endpoint’i bulundu.|
|DeclineInvite0x2288474|Ayrı davet reddetme endpoint’i bulundu.|

Bu araştırmada ek endpointlerin tüm HTTP descriptor/verb/body/query yerleşimi, imzalanan byte dizisi ve mail→Tru.ID eşleme zinciri tamamlanmadı; executable API sözleşmesi olarak kullanılmamalıdır. 2286134 içindeki farklı telefona ait anahtarı silme ve duplicate branch’leri görülüyor. Mevcut Trumore anahtarını korumak için bu dallar otomatik uygulanmaz.

## 6. Companion’daki karşılaştırma

- SmartAccessKeyStore ayrı uygulama kapsamındaki gerçek P256 anahtarı üretir. Trumore anahtarının aynı telefonda olması Companion’ın public key’ini yetkilendirmez.
- SmartAccessCertificateClient telefon ilişkisi, GetCertificate ve yalnız isOwnerCertificate=true sahip POST’u yapar. ShareProcess endpointleri ve davet session/signature akışı kaynakta yok.
- VehicleBleManager servis/CCCD sonrasında TRANSPORT_READY der. Bu, sahiplik kaydı/araç onayı veya imzalı yetkilendirme başarısı değildir.
- Kaynak aramasında createBond/getBondState/ACTION_PAIRING ve araç içi kayıt menüsü/kod rehberi bulunmadı. Android’in kendiliğinden bond istemesi mümkün olduğundan bu, hiçbir eşleşmenin gerçekleşemeyeceği kanıtı değildir.
- Kullanıcı PHONE_RELATION200, GET_CERTIFICATE200/CERT_MISSING, CREATE_CERTIFICATE500 bildirdi. Eksik iki yanıt alanı mevcut yerel APK tanısında filtreli; son bekleyen kaynak düzeltmesi ad/türü gösterecek. Kök nedeni yalnız araç onayı eksikliğine atfetmek için yeterli veri yok.

## 7. Uygulama planı

1. **İlk sahip kaydı:** Kullanıcıya fiziksel anahtar/araç erişim menüsü/internet koşullarını anlatan adımlı kurulum; Android bond durumunun gözlemi; kod yönlendirmesi ve zaman aşımı; GATT hazır ile kayıt onayını ayrı gösterme. Gerçek araç onay sinyali/endpoint’i çözülmeden “araç onaylandı” durumunu üretmemek.
2. **Davet kabulü:** Önce pending invite/sessionDetail DTO’sunu ve ReplyToSession imza girdisini tamamlamak. Kullanıcının seçtiği davet için yerel anahtarla gerçek imza; sunucu sonucu ve tam sertifika kontrolü. Sahip telefonunda onay/sertifika üretme gereksinimi ayrıca doğrulanmalı. Mailin truId yerine geçmesi veya kendine davet çalışması varsayılmamalı.
3. **Yetkili BLE:** Tam server sertifikası yerel anahtar/VIN/kimlik/tarih doğrulamasını geçince mevcut imzalı oturuma bağlamak. Sertifika yokken komut açmamak; sahip anahtarını silme/iptal/yenileme otomatik yapılmamalı.

Bu plan doğrudan uygulanmış özellik listesi değildir. Öncelik yeni şema tanısından eksik alanları öğrenmek ve ilk kayıt yolunun native/bond callback→owner API sırasını tamamlamaktır. HTTP500’ü giderdiği iddia edilen bir patch veya yeni APK bu araştırmada yoktur.

## Kanıtlar ve tekrar üretme

Yerel kök: work/trumore-analysis/analysis/enrollment-2026-10-19/. `enrollment-string-xrefs.json`, `certificate-create-callers.json`, `pairing-ui-xrefs.json`, adresli asm/resolved dosyaları ve PDF/metin/renderler saklandı. libapp.so SHA256:086ac67c7f1edc261e2744d0ad17f3e6aa79081a7b4c2929f9d577d8b3c0ad78. dae0.1.13 disasm çıktısı kullanıldı; profil UNVERIFIED sınırlaması korunur. PP ofsetleri ham ADD/LDR’den resolve_aot_pp.py ile yeniden çözüldü; caller’lar BL immediate taramasıyla doğrulandı. Örnek komut: `dae disasm binary/libapp.so 0x2288c04 -o 2288c04.asm`. Araçla ilgili onay adımları resmi kılavuzdan, byte/endpoint sözleşmeleri APK’den; biri diğerinin canlı kanıtı sayılmaz.


## 20 Ekim 2026 — Orijinal uygulama ile emülatör denemesi (AF216)

Kullanıcının izniyle resmi Google Android CLI, API35 Google APIs x86_64 rev9 görüntüsü ve Togg_Trumore_API35 AVD kuruldu. Emulator37.1.11/WHPX, Pixel6 profili,3GB RAM. Cihaz x86_64 ve arm64-v8a ABI bildiriyor. Orijinal, değiştirilmemiş Trumore2.1.25+661 (com.togg.toggmobile) base+ARM64+en+xhdpi split kurulumu başarılı; uygulama ve gömülü Togg ID giriş formu açıldı, Türkiye/Türkçe seçildi.

Kullanıcının sağladığı bilgilerle resmi giriş formuna bir kez gönderim yapıldı. Sonraki açılışta Türkçe ana ekran/profil görseli ve konum izni istemi görüldü. Bu gözlem hesap kimliğinin, tokenın veya dijital anahtar durumunun doğrulandığı anlamına gelmez. Anahtar kurulum ekranına/sertifika sonucuna ulaşılamadı; mevcut anahtar silinmedi, yenisi kaydedilmedi, fiziksel araca komut gönderilmedi.

Windows Application Event1000 kayıtları qemu-system-x86_64.exe ve headless varyantında0xc0000005 çökmesini00:27:03,00:29:52,00:32:54 zamanlarında gösterdi. Penceresiz çalıştırma ve SwiftShader/-Vulkan denemeleri sorunu gidermedi. -no-accel son denemesi cihaz çevrimiçi olmadan çıktı. Kök neden yalnız bu kanıtla belirlenemez; Trumore HTTP500 sertifika sorununun kanıtı değildir. Uygulama logunda background Flutter plugin kaydı sırasında WorkManager.Companion NoSuchFieldError ve görüntü decode hataları da görüldü; host çökmesine neden oldukları doğrulanmadı.

Devam sınırı: fiziksel Android cihazda kullanıcı gözetiminde orijinal Trumore giriş/araç onayı/dijital anahtar ekranları gözlenmeli veya kararlı bir emülatör ortamında tekrarlanmalı. Emülatör denemesi fiziksel BLE veya araç onayını doğrulamaz. Companion kaynak kodu/APK değiştirilmedi, derleme/push yok. Giriş sırları rapora veya kaynak koda yazılmadı. Yerel kanıt: work/trumore-analysis/analysis/emulator-2026-10-20/; tamamlanan kurulum dosyaları ve geçici loglar manifestle obsolete/2026-10-20/emulator-setup altına taşındı. AVD ve SDK yerel kullanıcı dizininde kurulu kalır.


## 20 Ekim 2026 — Fiziksel telefonda orijinal Trumore (AF217)

Kullanıcının USB ile bağladığı SM-S938B/Android16 üzerinde mevcut oturumla Trumore2.1.26, versionCode663 incelendi. Bu sürüm önceki statik analizde kullanılan2.1.25/661'den farklıdır; önceki AOT adresleri2.1.26 için geçerli varsayılmaz. Uygulama özel verisine/AndroidKeyStore özel anahtarına erişilmedi; mevcut kurulu APK splitleri yalnız kod karşılaştırması için obsolete/2026-10-20/trumore-2.1.26-device-input/ altında arşivlendi. Hash/boyut kanıtı work/trumore-analysis/analysis/device-2026-10-20/installed-apk-provenance.json içinde.

Canlı UI gözlemleri:
- Kullanıcının aracı Akıllı Cihazım içinde görünüyor. Dijital Anahtar sekmesi e-Motor Başlat, Kilidi Aç, Bagaj kapalı, BLE Bağlan ve Paylaşılan Anahtarlar sunuyor. Sekme açılışı otomatik bağlantı araması yaptı; daha sonra Bağlan durumuna döndü. Kullanıcı aracın uzakta olduğunu bildirdi. Kontrol butonlarına basılmadı.
- Paylaşılan Anahtarlar listesi boş görünüyordu. Yeni Anahtar Oluştur paneli iki yol sunuyor: Davetiye Gönder veya Dijital Anahtar QR'ını tarayın.
- Davetiye Gönder düğmesi e-posta alanı açmak yerine Android metin paylaşım panelini açtı. Metindeki bağlantı yapısı https://superapp-api.channels.togg.cloud/KeySharing?seId=<redacted>&no=<redacted>&vin=<redacted>. Parametre adları görüldü; değerler araştırma dosyalarına alınmadı. Herhangi bir alıcı/uygulama seçilmedi, mesaj gönderilmedi, panel geri ile kapatıldı.
- Özgün uygulamanın sınırlı PID log aralığında CheckUserPhoneRelation, ShareProcess/GetAllSharedPubKeys ve davet paneli açılışından sonra ShareProcess/CreateSession istek adları görüldü. Davet hazırlama sunucuda bir oturum oluşturmuş olabilir; yalnız paylaşımın gönderilmediği doğrulanmıştır. Yöntem/gövde/yanıt sözleşmesi bu loglardan doğrulanmadı.
- Aynı log aralığında GetCertificate veya CreateCertificate adı görülmedi. Bu, uygulamanın genel olarak bu çağrıları yapmadığı veya sertifikasının bulunmadığı anlamına gelmez. Sertifika/HTTP500 kök nedeni, eşleştirme ve kabul akışı bu denemeyle çözülmedi.

Sonuç: statik araştırmadaki ShareProcess/CreateSession varlığı gerçek uygulama davranışıyla desteklendi; davet URL alanları ilk kez UI'dan doğrulandı. E-posta→TruID veya seId→ReplyToSession/signature eşlemesi halen eksik. Fiziksel araç yakınında orijinal uygulama bağlantısı ve araç onayı sırasında güvenli kayıt alınması gerekiyor. Bu tur Companion kodu/APK değişmedi; derleme/push yok. Ham log, davet tokenı, hesap tokenı ve kişisel paylaşım önerileri kalıcı araştırma kanıtına alınmadı.


## 21 Ekim 2026 — Araç içinde canlı deneme ve doğru hedef

Kullanıcı mevcut telefonun araç bağlantısını onayladığını açıkladı. Ardından orijinal Trumore2.1.26 UI mavi kontrol düğmeleri, HAZIR ve Bağlantıyı Kes gösterdi; Countly SmartAccess/Car - Connected olayı görüldü. Agent kontrol komutu göndermedi. Özgün log penceresinde GetCertificate/CreateCertificate adı gözlenmedi; bu genel çağrı yokluğu veya cache içeriği kanıtı değildir.

Companion: eşleşmiş BLEsmart hedefi GATT147 ile başarısız oldu. Taramada My T-SAS seçilince GATT0/state2, MTU247, GATT_SERVICES0 ve TRANSPORT_READY görüldü. Sistemce maskelenmiş hedef aliası Trumore ile aynı, BLEsmart ile farklıydı; bu tam MAC kimliği kanıtı değildir. My T-SAS gerçek servis/characteristic/CCCD kontrolünü geçti. Yaklaşık35sn sonra status8 ile koptu; sertifika yok, trafik0/0. Tek fetch denemesi My T-SAS seçiminden önce PHONE_RELATION200 timestamp+2 gizli alan, GET_CERTIFICATE200 vin/timestamp+2 gizli alan ve CERT_MISSING verdi. CREATE denenmedi; bu tur HTTP500 yeniden üretildi denemez.

Kullanıcı fotoğrafları440957/440958 Güvenlik→Dijital Anahtar→Erişim Cihazı düğmesini ve Bağlanma başarılı mesajını gösterdi. Kullanıcı yalnız düğmeye basınca doğrudan mesaj çıktığını, yeni telefon/kod/PIN görmediğini doğruladı. Yeni Companion anahtarı veya sertifikası kaydedildiği iddia edilmez; mevcut anahtar silinmedi.

Kaynak düzeltmesi AF223: generic smart ad ipucu kaldırıldı; T-SAS tanındı. Reklamda Smart Access UUID veya başarılı servis/CCCD kanıtı uygulama oturumu boyunca işaretlenip sıralamada öne alınıyor. Menüler bu keşif kanıtını etiketliyor, tarama ilk sıraya geliyor; seçim ofsetleri düzeltildi. connectToPairedTogg yalnız servis kanıtı bulunan cihazı otomatik seçiyor. Bu işaret sertifika/READY yetkisi sağlamaz. Diğer eşleşmiş cihazlar manuel menüde kalır.

Yeni hedef seçimi ve AF212 ek JSON alan adı tanısı mevcut APK içinde yok. Kaynak değişti; build/install/push ve uygulama test suite yok. Açık yeni paketleme isteği gerekiyor. Sonraki tanı adımı: gerçek iki JSON alan adını okuyup PhoneRelation/GetCertificate durumunu ayırmak. Mevcut anahtarları silerek yeniden kayıt denenmedi.

Kanıtlar: work/trumore-analysis/analysis/vehicle-2026-10-21/ altında original-ui-guide.json, original-sanitized-timeline.json, gatt-peer-comparison.json, corrected-target-gatt-events.json, companion-diagnostics.txt. Ham orijinal log, hesap tokenı, BLE payloadı veya özel anahtar saklanmadı. Fotoğraflar özgün kullanıcı eklerinde.


## 21 Ekim — Yeni APK, sonuç alanları ve gerçek Failure yanıtı

Kullanıcı açıkça Derle ve telefona kur yanıtıyla telefon build/install işlemini yetkilendirdi. Aynı tanı çalışmasında üç başarılı assembleDebug/yerinde adb install -r -t iterasyonu yapıldı: ilk paket AF212 alan adlarını, ikincisi orijinal pooldan bilinen enum adlarının güvenli değer tanısını, son paket uygulama sonucu reddini de içeriyor. Uygulama verileri korunarak güncellendi; eski APK ve loglar manifestlerle obsolete altında.

İlk yeni APK actual alanları gösterdi: PHONE_RELATION root timestamp/resultEnum/resultIndex; GET_CERTIFICATE root vin/timestamp/resultEnum/resultIndex. Sonuç değerleri için yalnız sabit allowlist adları ve0..11 tam sayıları kaydedildi; sunucunun keyfi metni/payloadı kayda alınmıyor. Sonraki gerçek kayıt: PHONE_RELATION HTTP200, resultEnum=Updated, resultIndex=2; GET_CERTIFICATE HTTP200, resultEnum=Failure, resultIndex=1. Sertifika verilmediği bu istekte backend uygulama sonucu ile doğrulandı. Failure kesin sebebi, yeni kayıt yetkisi veya doğru API parametrelerinin tümü bu sonuçtan çıkarılmaz.

ÖNEMLİ: original-result-enum-pool.json içindeki off_8 değerleri Dart enum sıra bilgisidir; ağ yanıtının resultIndex alanıyla eşlenmez. Örneğin eski pool Updated9/Failure2 iken gerçek ağ yanıtı Updated2/Failure1 oldu. Kararlar bilinen resultEnum metnine dayanır; ordinal-index varsayımı yapılmaz.

SmartAccessCertificate.parseFromServer, resultEnum mevcutsa Success dışındaki sonucu sertifika parse adımından önce açıkça reddeder. Failure için CERT_SERVER_FAILURE; UpToDate için CERT_NOT_MODIFIED; bilinmeyen/metin olmayan sonuç için genel güvenli hata verir. Sonuç alanı olmayan yerel cache kaydı önceki VIN/kullanıcı/public-key/tarih doğrulamasından geçmeye devam eder. Başarı/anahtar bağlama/imza şartı gevşetilmedi; UpToDate için geçersiz/olmayan cache kabul edilmedi. Telefonda son pakette CERT_SERVER_FAILURE mesajı tekrar üretildi.

Güncel APK: outputs/ToggCompanion-current-debug.apk;4114798 bayt; SHA2562643bc7757558d147248b89fa7c83028bba7f3c8275965642a93876bb4500ece. Version1.0/code1 debug, temel73dcc2f + bekleyen kaynak değişiklikleri. Son build2sn/33task/4executed, install Success. Wear build/push yok. Tek etkin workspace APK kuralı dosya taramasıyla doğrulandı. Kaynak test suite yok; manuel cihaz denemeleri ve KB doğrulaması yapıldı. Sertifika Failure kök nedeni ve Companion yetkili araç komutları hâlâ doğrulanmadı.

Yeni kanıtlar: companion-diagnostics-updated-apk.txt (alan adları), companion-diagnostics-result-enum.txt (güvenli enum sonuçları), original-result-enum-pool.json, final-apk-failure-message.txt. Ham token/özel anahtar saklanmadı.


## 21 Ekim — Devam denemesi: oluşturma500, sonraki sorguFailure

Kullanıcının “denemeye devam” isteğiyle USB bağlı telefonda, kurulu tanı APK’sının Smart Access kurulumundan bir kez “Yeni Smart Access sertifikası oluştur” çalıştırıldı. Mevcut yerel anahtar yeniden kullanıldı; sonuç CREATE_CERTIFICATE HTTP500. Ardından ayrı bir “Sertifikayı al ve yetkilendir” sorgusu çalıştırıldı; UI GET_CERTIFICATE / CERT_SERVER_FAILURE gösterdi. Gecikmeli sertifika teslimi bu sorguda da gözlenmedi. Bu tur BLE yeniden bağlanması veya araç kontrol komutu denenmedi. Kanıt: workspace work/trumore-analysis/analysis/continuation-2026-10-21/create-ui-result.txt ve fetch-after-create-ui-result.txt. Bunlar yalnız UI hata metinleri; HTTP500 yanıt gövdesi mevcut APK tarafından okunmuyor.

Statik karşılaştırmada daha önce çözümlenen transport descriptor kanıtı yeniden bulundu: SMART_ACCESS_IMPLEMENTATION.md HTTP bölümü, cert_audit/decode_http_functions.py ve flutter_audit/resolve_immutable_arrays.py. Telefon ilişkisi liste değerli JSON gövdesi ve sertifika query/body ayrımını değiştirecek yeni kanıt yok. Orijinal owner factory için119 bayt imza girdisi,64 bayt raw ECDSA imzası, UTC başlangıç ve365 gün geçerlilik mevcut Java biçimiyle uyumlu; bu yalnız statik karşılaştırmadır, sunucunun kabulünü kanıtlamaz. İlk cert_audit/certificate-evidence.md tablosunun shared çağrı true ifadesi sonraki AF214/AF219 ham boolean düzeltmesiyle aşılmıştır; shared false, owner true ayrımı korunur.

Orijinal2.1.25 owner CreateCertificate fonksiyonu0x21f1f68,0x21f244c–0x21f2540 aralığında timeToWaitBeforeFetch ve timestamp alanlarını okur. Companion başarılı oluşturmanın ardından hemen GET yapıyor. Bekleme alanının birimi/çağıran taraf uygulaması bu devam araştırmasında yeniden doğrulanmadı; tahmini bir bekleme eklenmedi. Bu fark, oluşturma aşamasındaki HTTP500’ün nedeni olarak gösterilemez.

Yeni kaynak tanısı F70/F93: non2xx yanıt gövdesi en çok8192 bayt okunarak yalnız sabit JSON alan adları/türleri ve önceden izin verilmiş sonuç enumları CERT_ERROR_SCHEMA içinde raporlanır; errors nesnesinde yalnız bilinen istek alanlarının türleri gösterilir. Bilinmeyen hata alan adları/değerleri, exception mesajları, traceId/detail/title değerleri kayda alınmaz. Boş/metin/limit üstü/okunamayan gövde ayrı sabit etiket alır; ana HTTP hata kodu korunur. Bu tanı henüz DERLENMEDİ/KURULMADI. Kurulu ve outputs içindeki APK SHA2562643bc7757558d147248b89fa7c83028bba7f3c8275965642a93876bb4500ece olarak kalır. Yeni açık derleme isteği gereklidir. Sunucu başarısızlığının nedeni ve ilk kayıt zinciri hâlâ açık.


## 21 Ekim — Eksik ClaimCarToUser ve gerçek owner anahtarı çakışması

AF230–AF234 / F96 SMART_ACCESS_SHARED_ACCOUNT_PREPARATION.md: HTTP hata gövdesi tanısı açık kullanıcı izniyle derlendi/kuruldu; tek create500 yanıtı JSON nesnesi değil. Ardından orijinal2.1.25 ve2.1.26’da owner sertifikasından önce ClaimCarToUser bulundu. Query ownerPublicKey/vin/overwritePhoneRelation=false, POST boş JSON. Kaynağa bu adım eklendi; yalnız Success/Updated/RelationAlreadyExists sonrası create, OverwriteDetection sonrası OWNER_PHONE_CONFLICT ve durma. OwnerChange ve diğer bilinmeyen/başarısız sonuçlar create’e geçirmez. Gerçek UI denemesi OWNER_PHONE_CONFLICT verdi; bu yalnız server OverwriteDetection ile üretilir. Böylece eksik kayıt ve mevcut farklı owner anahtarı ayrıldı; create500 backend istisnası tam açıklanmadı.

Kullanıcı mevcut Trumore anahtarını koruyup ayrı hesaba paylaşım yolunun hazırlanmasını seçti; ayrı hesap henüz yok. Overwrite=true, anahtar silme veya otomatik kendi kendine davet yapılmadı. F96 guest davet endpointleri, nonce/imza zinciri, bağımsız hesap depolaması ve kalan uygulama/manuel doğrulama adımlarını içerir. Guest kabul entegrasyonu henüz uygulanmadı. Kullanıcıya ait ikinci hesap ve davet bağlantısı sonraki canlı aşama için gerekiyor.

Son owner kayıt düzeltmesi içeren APK kuruldu: outputs/ToggCompanion-current-debug.apk; 4116002 bayt; SHA256 0fb7a57d67041056667e207e331f9f4672ce4d5265c26f7c89ae6efc875a3dc6. Önceki source-only HTTP tanı notları bu paketle aşılmıştır. Tek aktif APK; önceki iterasyonlar manifestle obsolete altında. Wear/push/app test suite yok. BLE yetkili komut doğrulanmadı.
