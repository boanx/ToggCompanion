# Mevcut Trumore anahtarını koruyan paylaşılan erişim hazırlığı

## 21 Ekim araç denemesi — güncel devir

Kullanıcı araçtan ayrılıp USB bağlantısını keseceğini bildirdi; canlı denemeler durduruldu. İmzalı BLE oturumu **henüz çalışmıyor**; araç kontrol komutu gönderilmedi. Önceki “sertifika doğrulandı” yalnız yerel sunucu sertifikası/binding denetimidir.

Tamamlanan düzeltmeler:
- Trumore bağlantı kurulumu2291d88 iki abonelik çağırır:2294cb0 (session receiver2294dfc) ve2293b24 (ikinci receiver2293d60). Companion2413 ve2415 bildirimlerini sırayla açar. Araçta her iki characteristic properties26 (READ/WRITE/NOTIFY); iki CCCD status0. Önce yalnız2415 dinlendiğinden RX0 kalıyordu.
- İlk protokol isteği gönderilir; ancak bağlantı sırasında araçtan gelmiş FIRST sertifika isteği kuyruktaysa ikinci başlangıç gönderilmez. Böylece başlangıç çakışması önlenir.
- Tam gönderilmiş aktif telefon sertifikası için araç PACK_COMPLETE/Iuu0/Juu0 yanıtı gözlendi. Yalnız bu değişimde toplam uzunluk veya0 kabul edilir; ACK yetki/komut başarısı değildir.

Son ayrıntılı tanı: GATT bağlı/MTU247, FIRST0/1 → USER_CERTIFICATE parçaları244 ve19 bayt → PRE236/2 → COMPLETE0/0 → telefon SAM anahtarı için REQUEST_USER_CERTIFICATE FIRST0/1 gönderiyor → araç yeniden FIRST0/1 gönderiyor → fail-closed SESSION_ERROR/disconnect. TX3/RX4. SAM public key, challenge, imzalı durum henüz alınmadı. “Sertifika araç tarafından kabul edildi” anlamı çıkarılmamalı; yalnız aktarım ACK'i gözlendi. Kör sertifika tekrar döngüsü eklenmedi.

Orijinal Trumore geçici kapatılarak çakışma olasılığı denendi. Ardından orijinal uygulamanın dijital anahtar ekranı açıldı; SmartAccess/Car - Connected olayı görüldü. Sahip anahtarı silinmedi/kopyalanmadı/değiştirilmedi. Son Companion tekrar girişimi başlatıldı, fakat araçtan ayrılma nedeniyle yeni sonuç doğrulanmadı.

Sonraki araştırma: orijinal2296184 SAM public key edinimi ile canlı tekrar FIRST yanıtının farkı; sertifika/protokol sürümü ve iki characteristic rolü kesinleştirilmeli. İmza, SAM pin, izin, nonce, hesap/VIN kontrolleri gevşetilmemeli. Süresi dolmuş guest oturumuyla kayıtlı anahtar butonu başlangıçta başlamadı; normal guest sertifika sorgusu refresh yapıp doğruladı. Kullanım kolaylığı için authenticate öncesi guest refresh ayrıca ele alınabilir.

Kanıt: work/trumore-analysis/analysis/vehicle-2026-10-21-guest/ altındaki dual-channel-diagnostics.txt, certificate-ack-diagnostics.txt ve disassembly. Ham sertifika, anahtar/token/davet verisi kaydedilmedi.

Güncel telefon APK: 4132786 bayt, SHA256 `f53a326a71ffea493f41006738c807a3d173d5f0d7870802669500b31901eb39`. Açık derle/kur/dene izniyle son düzeltme offline derlendi, adb install-r-t başarılı; veriler korundu. Önceki APK/loglar obsolete altında manifestle arşivlendi. Wear/push/app test suite yok.


## 21 Ekim 2026 — Paylaşılan sertifika doğrulandı (güncel)

Önceki yalnız önizleme/hazırlık kayıtları bu bölümle aşılmıştır. Kullanıcı ayrı hesap OAuth girişini tamamladı; sessionDetail önizlemesi ve ReplyToSession davet kabulü canlı başarılı oldu. Ardından sahip, orijinal Trumore'daki Tamamla ekranında süre ve erişim onayını seçip paylaşılan anahtarı kendisi oluşturdu. Bu sahip onayı öncesinde sertifika alınamaması, davet kabulünün tek başına yeterli olmadığını gösterdi.

Companion ayrı AndroidKeyStore PhoneKey kullanır; mevcut sahip anahtarını kopyalamaz/değiştirmez. İmza girdisi decoded sessionId || guest UUID16 || publicKey65 || decoded nonce; SHA256withECDSA raw64 imza yerelde doğrulanır. CheckUserPhoneRelation sonrası ReplyToSession çağrılır. Bekleyen/kabul edilmiş davet durumu şifreli saklanarak belirsiz ağ sonucunda otomatik tekrar engellenir. Davetteki vin referansı ilk4 SHA256(VIN UTF8) baytının paddingsiz Base64 gösterimidir; yetki kanıtı değildir.

GetCertificate isOwner=false yanıtındaki tam sertifika, gerçek VIN/guest UUID/PhoneKey ve geçerlilik kontrollerinden geçti. UI: “Paylaşılan sertifika doğrulandı. Araç yakınında BLE bağlantısı kurup kayıtlı anahtarla oturumu açın.” Sertifika bağımsız şifreli guest deposunda tutulur; ana telemetri oturumu korunur. VehicleBleManager guest_phone_key bağlamında yalnız bu PhoneKey ile imzalar; hesap/VIN değişimi oturum kapısını geçersiz kılar.

Tarih düzeltmesi: sertifika UTC altı bayt sırası saat, dakika, GÜN, AY, yıl LE16'dır. Eski ay/gün açıklaması ve Java kodu ters idi. Gerçek sertifika ve orijinal b98e4c → 9ea748 UTC constructor argüman eşlemesi hatayı doğruladı. Decode ve owner encode düzeltildi; geçerlilik/binding kontrolleri gevşetilmedi. Bu bulgu geçmiş owner HTTP500'ün tek nedeni olduğu anlamına gelmez; OverwriteDetection ayrı kanıttır.

Canlı BLE imzalı oturumu ve kilit/bagaj/READY komutları henüz doğrulanmadı. Araç hareketi üreten komutlar kullanıcı tarafından manuel denenmelidir. Yeniden davet kabulü veya owner overwrite yapılmamalı. Kullanıcı değerleri, davet, token, ham sertifika/özel anahtar raporlara kopyalanmadı.

Kanıt: workspace work/trumore-analysis/analysis/guest-invitation-2026-10-21/ altında accept-result.txt, certificate-date-diagnostic.txt, certificate-final-result.txt ve orijinal date/hash constructor disassembly. Telefon APK: 4131834 bayt; SHA256 48c94d4fd884eeb648a7957564a0c593f3ab9747d61364286cac94e10ce6368c. Offline assembleDebug ve adb install -r -t başarılı; veriler korundu. Wear, GitHub push ve uygulama test suite çalıştırılmadı.


21 Ekim 2026. Durum: protokol hazırlığı; uçtan uca davet kabul özelliği henüz uygulanmadı. Kullanıcı ayrı hesap/paylaşım yolunu seçti ve ayrı hesabının henüz olmadığını bildirdi. Sahip anahtarını değiştirme veya silme izni yok.

## Canlı engel ve tamamlanan düzeltme

Companion ayrı Android UID’sinde ayrı P256 anahtarı kullanıyor. Orijinal Trumore anahtarının bilgisinin bu uygulamada bulunması Companion’ı yetkilendirmez. Eksik owner kayıt adımı bulundu ve mevcut oluşturma akışına eklendi: PhoneRelation → ClaimCarToUser(overwritePhoneRelation=false) → yalnız kabul edilmiş sonuçtan sonra CreateCertificate. Gerçek telefon denemesinde OWNER_PHONE_CONFLICT UI mesajı çıktı. F70 bu mesajı yalnız sunucu resultEnum=OverwriteDetection olduğunda üretir. Bu bir sertifika değildir; CreateCertificate bu dalda çalıştırılmaz. HTTP500’ün tüm backend nedenleri veya istisnası açıklanmış sayılmaz; fakat mevcut owner ilişkisinin bu ayrı anahtarı kabul etmediği artık somut olarak ayrıldı.

Sahip anahtarı değiştirilmeyen yolda ayrı hesap için davet kabulü ve isOwner=false tam sertifika gerekir. Aynı owner hesabına kendini davet etmenin çalışacağı varsayılmaz. Orijinal SharedKey duplicate dalı farklı telefon anahtarını silen kod içeriyor; bu dal kullanılmayacak.

## Doğrulanan HTTP parçaları

Sabit servis kökü: https://tva-api.togg.cloud/smart-access/api/v1. Kimlik doğrulaması hesabın normal Bearer oturumudur; kimlik bilgileri/tokenlar bu belgeye konmaz.

| İşlem | İstek yerleşimi | Kanıt |
| --- | --- | --- |
| Sahip ilişkisi | POST /VehicleOwnerRelation/ClaimCarToUser; query ownerPublicKey (130 karakter hex), vin, overwritePhoneRelation=false; JSON boş nesne | 2.1.25 0x21f1728, 21f1854–21f18dc; 2.1.26 0x2272628, 2272754–22727dc; aynı query/body argüman konumları |
| Davet ayrıntısı | GET /ShareProcess/sessionDetail; query sessionId | 2.1.25 0x22870d0, 2287190–22871dc; GET descriptor dc10 |
| Davet yanıtı | POST /ShareProcess/ReplyToSession; JSON sessionId, signatureBase64, phonePublicKey, sharedName, friendName; bu çağrıda query haritası yok | 0x2288c04, 2288cec–2288dec; POST descriptor c418 |
| Telefon ilişkisi | POST CheckUserPhoneRelation; hex publicKey query ve Platform/Language/PushNotificationToken liste gövdesi | F77 mevcut doğrulanan sözleşme |
| Paylaşılan sertifika | GET /Certificates/GetCertificate; phonePublicKey, vin, isOwner=false | F70/F77 mevcut alma sözleşmesi; gerçek davet/sertifika ile ayrıca doğrulanmalı |

Davet ayrıntısı parser’ı0x19e0f40 sessionId, carColor, carModel, sharedName, ownerName, friendExpTimestamp okur. Yanıtın sessionId’si girilen davetle eşleştirilir; friendExpTimestamp original uygulamada tarih karşılaştırmasına gider. Süre birimi/kabul UI ayrıntıları henüz tam doğrulanmadı.

## Davet bağlantısı ve imza

Orijinal link okuyucu0x2287664: seId query değeri state.field33’e; no değeri Base64 decoder0xb97854 üzerinden state.field37’ye gider. no bir telefon numarası veya yetki token’ı olarak yorumlanmaz. Mevcut DigitalKeyManager seId’yi saklıyor, no alanını korumuyor; bu parser tek başına yeni akışı desteklemiyor.

Davet kabul çağırıcısı0x228877c → 0x22890e8 → 0x2288c04. İmza girdisi0x2289134–0x22891ec sırasıyla:

1. Base64.decode(sessionId / link seId)
2. kabul eden hesabın UUID’sinin16 bayt gösterimi (b96fc0/b96e24; tireleri kaldırıp hex byte dizisi)
3. kabul eden telefonun açık anahtar baytları
4. Base64.decode(link no) baytları

Liste Uint8List’e çevrilir;0x2289200 native signMsg ile imzalanır;0x2289230 Base64 encoder sonucunu verir. Native anahtar bu yolda truId + PhoneKey bağlamındadır (owner VIN anahtarından farklı). Aynı özel anahtar ve public key hem PhoneRelation/ReplyToSession hem sonra alınan paylaşılan sertifika için korunmalıdır. İmza yardımcısının r1 argümanı receiver; VIN’i imza sonuna eklemek doğru değildir. UUID bayt sırası, nonce uzunlukları, sessionId Base64 varyantları ve yeni2.1.26 imza çağırıcısı gerçek davet/ikinci hesapla ayrıca doğrulanmadan sözleşme tamamlandı kabul edilmez.

sharedName, yardımcı2288f24 üzerinden oturum ayrıntılarından alınır; friendName kabul eden hesap profilinden gelir. Bunlar mail adresiyle otomatik doldurulmaz. ReplyToSession Success kabulü, sunucu onaylı tam sertifikanın elde edildiği anlamına gelmez; owner tarafındaki sonraki imzalama/sertifika üretimi ve polling zinciri hâlâ tamamlanmalıdır.

## Companion uygulama planı

1. Ana telemetri/ABRP hesabından bağımsız, şifreli Smart Access guest oturumu: ayrı access/refresh token, hesap UUID ve kontrollü refresh. Mevcut ToggLoginActivity/SecureTokenStorage ana hesabı değiştirir; ikinci hesap için doğrudan kullanılmaz. Ayrı giriş oturumu tamamlanmadan ana hesap logout edilmez.
2. Davetin seId/no/vin değerlerini kayıpsız ve sınırlı parse et; değerleri loglama. Link yalnız bekleyen davet metadatasıdır. Sabit API hostu dışına token gönderme.
3. Guest kimliği için PhoneKey kapsamlı yerel anahtar oluştur/koru; PhoneRelation sonucu kontrolü, sessionDetail ve kullanıcıya owner/araç/süre önizlemesi.
4. Seçilen davete ait ispatı yerel anahtarla imzala ve ReplyToSession gönder. Aynı daveti belirsiz timeout sonrasında otomatik tekrar etme. Owner anahtarı veya başka guest anahtarı silme.
5. Owner tarafındaki onay/sertifika üretimini gerçek Trumore akışında izle. Guest GetCertificate isOwner=false; UUID/VIN/local key/tarih/sahiplik/izin doğrulaması korunur.
6. VehicleBleManager’ın kurulum/önbellek/trust/account-bound callback bağlamını guest oturumuna bağla; ana telemetri hesabının UUID’sini guest sertifikasıyla karşılaştırma. GATT hazır ve yetkili oturum ayrı kalır.
7. İlk gerçek kabul ve sertifika sonrası park hâlinde kullanıcıyla BLE bağlantısı ve tek kapı komutu manuel doğrulansın; eMotor/bagaj ayrı denenir.

## Kullanıcıdan gereken

Ayrı, kullanıcıya ait Trumore/Togg ID hesabı oluşturulması; sahip hesabından o hesaba dijital anahtar daveti hazırlanması ve davet bağlantısı. Parola sohbet veya dosyalara gerekmez; giriş cihazdaki normal ekrandan yapılmalı. Yeni hesabı bu tur oluşturmadık, davet göndermedik/kabul etmedik. Canlı owner anahtarına dokunmadan sonraki doğrulama bu girdileri bekliyor.

## Kanıt dizini / sınırlar

Workspace work/trumore-analysis/analysis/continuation-2026-10-21/: claim-ui-result.txt, error-body-diagnostics.txt,21f1728.asm.resolved.txt, claim-2.1.26.asm, claim-literals-2.1.26.json, shared-callers.json, invite-link-read.asm.resolved.txt, invite-reply-prepare.asm.resolved.txt, invite-signature.asm.resolved.txt, invite-uuid*.asm, session-detail-parser.asm.resolved.txt. Eski PP resolver yalnız2.1.25’e uygulandı. Yeni2.1.26 literal adresleri kendi pool exportundan ham ADD/LDR ile eşlendi. dae SDK profili UNVERIFIED; eski sürüm adresleri yeni binary için varsayılmadı. Hazırlık özelliğin çalıştığı iddiası değildir.


## 21 Ekim — İkinci hesap girişi ve yalnız okuma davet önizlemesi

Kullanıcı ayrı hesabın hazır olduğunu bildirdi ve davet bağlantısını paylaştı. E-posta/davet seId/no/vin değerleri bu belgeye veya kaynak dosyalara kopyalanmadı. Bu güncelleme önceki “ayrı hesap henüz yok” durumunu aşar; yeni hesapla cihazdaki OAuth girişi henüz kullanıcı tarafından tamamlanmalıdır.

F97 SmartAccessAccountStorage: ayrı EncryptedSharedPreferences, plaintext fallback yok. Ana SecureTokenStorage tokenları/VIN/ABRP tercihleri değişmez. Kaydetme, token sub UUID ve exp kontrolü yapar; ana telemetri hesabıyla aynı sub kabul edilmez. Refresh yalnız guest hesabı içindir; subject ve eski refresh karşılaştırılır, tüm instance’ların yazmaları ortak kilitte yapılır. JWT claim decode imza doğrulayıcı değildir; tokenlar normal HTTPS OAuth code exchange/refresh sonucundan gelir.

F12 guest login modu: ekstra ile seçilir; PKCE’ye rastgele state/prompt=login eklenir, callback scheme/host/path/state eşleşmesi aranır. Owner ile aynı hesap yanlışlıkla seçilirse ana hesabı değiştirmeden hata verir. F71 kurulumunda ayrı giriş ve davet inceleme butonları vardır. Mevcut normal giriş davranışı korunur.

F98 SmartAccessInvitationClient yalnız GET sessionDetail önizlemesidir. HTTPS/sabit official host/path, tek seId/no, sınırlı geçerli Base64 alanları kabul edilir; token yalnız sabit TVA API hostuna gönderilir, yönlendirme kapalıdır. Yanıt boyutu64KiB; sessionId echo ve güncel guest subject eşleşmesi gerekir. Owner/model/sharedName alanları ekranda sınırlı gösterilir; serbest yanıt/kimlik/token/davet loglanmaz. Link vin alanı yetki veya seçili gerçek VIN olarak alınmaz. Önizleme hesabı/daveti kabul etmez, anahtar oluşturmaz ve araç komutu göndermez.

Ek orijinal kanıt: link builder0x228b200, seId/no/vin yerleşimi; vin alanı ham şasi metni yerine0x19e1934 yardımcısından gelir. Yardımcı byte gösteriminden ilk4 baytı Base64’e çevirip padding’i kaldırır. Hash algoritması/araç eşleşmesi bu tur tamamlanmadı. Kısa vin parametresinin doğrudan17 haneli şasi olması beklenmez; geçersiz davet olduğu yalnız uzunluğundan söylenmez. Kanıtlar continuation-2026-10-21/shared-link-builder*.asm ve shared-link-vin-reference.asm.resolved.txt; helper algoritma ayrıntısı için shared-link-hash.asm.

Kullanıcının yeni açık derle/telefona kur isteğiyle güncelleme derlendi ve adb install-r-t başarılı. Kurulumda yeni iki buton ve guest login formu manuel görüldü, form iki giriş alanı gösterdi. Kullanıcıdan yeni hesapla cihaz ekranında giriş isteniyor. Gerçek guest OAuth tamamlanması/refresh/sessionDetail isteği bu kayıt anında doğrulanmadı. ReplyToSession, guest PhoneKey, tam sertifika ve BLE guest bağlamı entegrasyonu hâlâ eksik. Önizleme bunların tamamlandığını göstermez.

APK: outputs/ToggCompanion-current-debug.apk; 4122834 bayt, SHA256 ba98940e6b2a7c0d0eabd84aeb520bf3acf9591492221d9b5d07924dd26a368d. Önceki APK/rapor/loglar obsolete/2026-10-21/guest-hesap-oncesi/ manifestiyle arşivlendi. Wear/push/app test suite yok.


## Araç denemesi — ilk BLE isteği düzeltmesi (kaynak, henüz derlenmedi)

21 Ekim araç denemesinde doğru servis/GATT/MTU247 hazır olmasına rağmen AUTHENTICATING sırasında TX/RX0/0 kaldı ve30 saniyede zaman aşımı oldu. Kullanıcı araç Erişim Cihazı ekranında başarılı mesajını gördü; bu Companion oturumu doğrulaması değildir. Orijinal authentication_start0x2296184, REQUEST_USER_CERTIFICATE isteğini Iuu0/Juu1 ile aktif gönderir: SAM anahtarı yoksa FIRST, cache varsa COMPLETE. Companion start yalnız bekliyordu. F69 start ilk isteği gönderecek şekilde düzeltildi; başlangıç SAM key yanıtı telefon sertifikasının kabulünden önce gelebileceğinden yalnız beklenen-key/başlatılmış oturum koşulunda kabul edilir. SAM pin, nonce, imza, sertifika ve komut kapıları korunur. Aynı oturumda start tekrar göndermez. Yeni kaynak henüz APK içinde değil; yeni açık derleme isteği beklenir.


## 26 Ekim — GitHub kaynak araştırması

trumore-hardhat dev25a8022 araç pasaportu/ödeme prototipidir; BLE anahtar kodu bulunmadı. Exa/GitHub taramasında bağımsız Togg MyT-SAS uygulaması bulunmadı (indeks kapsamı sınırlıdır). Nordic Android-BLE-Library taşıma örneği, Tesla vehicle-command oturum mimarisi örneği, baka3k/digital_key NFC eğitim örneğidir; Togg uyumluluğu kanıtlanmadı. AF250 oturum engeli devam eder. Kanıt ve bağlantılar: work/trumore-analysis/analysis/github-digital-key-2026-10-26/RESEARCH.md (workspace). APK/komut/anahtar değişikliği yapılmadı.


## 26 Ekim — genişletilmiş araştırma düzeltmesi

AF265 sonrası ErgunUral/Togdas içinde aynı Togg BLE UUID/protokol ailesinin Swift uygulaması bulundu; README fiziksel BLE testini henüz doğrulamıyor. ErgunUral/Toggle APK_IOS_ANALYSIS.md, Togg Garajım1.0.13 Android paketinin com.ibrahim.togggaraj adıyla BLE/sertifika/komut analizini içeriyor. Orijinal APK/source ve bağımsız araç başarısı elde edilmedi. Sertifika frame boyutu ve sertifika sonrası tekrarlanan istek davranışı karşılaştırma hipotezleridir; kod değiştirilmedi. Ayrıntı ve sabit kaynak bağlantıları workspace work/trumore-analysis/analysis/github-digital-key-2026-10-26/EXPANDED_RESEARCH.md içinde. X/Telegram yalnız erişilebilir indeks ve tanıtım kapsamıyla incelendi; özel mesajlar okunmadı.
