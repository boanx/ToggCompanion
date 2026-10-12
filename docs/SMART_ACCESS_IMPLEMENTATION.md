# Smart Access uygulaması ve manuel deneme

12 Ekim 2026. Bu belge, önceki protokol inceleme derlemesinden sonra eklenen gerçek sertifika/BLE uygulamasını anlatır. Fiziksel araçta çalışma henüz teyit edilmedi. Klima, buğu çözme ve desteklenmeyen uzaktan kontroller pasiftir. READY'den aksesuar moduna dönüş için doğrulanmış komut bulunmadığından motor durdurma gönderilmez.

## Uygulamada deneme

1. Yeni telefon APK'sını kurun ve gerçek Togg ID hesabınızla giriş yapın. Demo modunu kullanmayın.
2. **Ayarlar → Dijital anahtar → Smart Access kurulumu ve yetkilendirme** ekranını açın. Aynı ekran BLE bağlantı listesindeki **Smart Access kurulumu** seçeneğinden de açılır.
3. Aracın gerçek 17 haneli VIN'ini kontrol edin. Kullanıcı UUID'si Togg ID oturumundan doldurulur; elle değiştirmek normalde gerekmez. Araç sahibiyseniz **Aracın sahibiyim** seçili olmalıdır. Paylaşılan araçta bu seçeneği kapatın.
4. **Sertifikayı al ve yetkilendir** düğmesine basın. Telefonun açık anahtarı önce Togg ID hesabıyla ilişkilendirilir. İlk kurulumda bu uygulamanın ayrı Android anahtarına ait sertifika bulunmayabilir. **Sertifika isteğinde** HTTP 404 alırsanız araç sahibi olarak **Yeni Smart Access sertifikası oluştur** düğmesini kullanın. Oluşturma kabul edilmiş ancak sertifika henüz hazır değilse biraz sonra yalnızca **Sertifikayı al ve yetkilendir** düğmesine tekrar basın.
5. Araç yakınında BLE listesinden **Çevredeki BLE Cihazlarını Tara** ile Smart Access cihazını seçin. Medya Bluetooth eşleşmesi yeterli değildir. Araçta dijital anahtar/erişim cihazları eşleştirme ekranı gerekiyorsa açın.
6. **Kayıtlı anahtarla oturumu aç** düğmesine basın. Sertifika BLE bağlıyken alınırsa bu adım otomatik başlar. **Smart Access oturumu hazır** mesajını bekleyin. Sadece **BLE bağlı** yazması komut yetkisi anlamına gelmez.
7. Araç park halindeyken sırayla **kilitle**, **kilit aç**, **bagaj aç**, **bagaj kapat** ve **e-Motor başlat** işlemlerini uygulamadan deneyin. Her işlemden sonra sonucu bekleyin; motor başlatma fiziksel READY durumu ile eşleştirilir.
8. BLE listesindeki **Tanı bilgisini görüntüle / paylaş** seçeneğiyle metni alın. Hata sonrası bağlantı kapanırsa yeniden tarayıp bağlanın ve kayıtlı anahtarla oturumu açın.

### Geri bildirim biçimi

Şu bilgileri birlikte paylaşın:

- Telefon modeli ve Android sürümü; araç modeli ve yazılım sürümü.
- Sertifika alma/oluşturma sonucu, varsa HTTP kodu ve ekrandaki son oturum aşaması.
- Her komut için uygulama mesajı ve araçtaki fiziksel sonuç.
- Tanı metni; gerekiyorsa hata ekranı görüntüsü.

Tanı metni token, özel anahtar, sertifika, UUID, VIN, challenge veya imza baytlarını içermez. Ekran görüntüsündeki kişisel bilgileri paylaşmanız gerekmez.

## Uygulanan akış

| Katman | Davranış |
| --- | --- |
| Kimlik | Orijinal Trumore akışında erişim token'ının UUID `sub` alanı `trumore_user_id` olarak kaydedilir ve sertifika başlığına gider. Companion aynı doğrulanmış bağlantıyı kullanır; UUID ve VIN biçimini kontrol eder. |
| Telefon anahtarı | Kullanıcı/VIN başına AndroidKeyStore P-256 anahtarı, SHA256withECDSA ve 64 bayt ham R/S imza. Özel anahtar dışarı aktarılmaz. |
| Telefon hesabı ilişkisi | Önce `POST …/UserPhoneRelation/CheckUserPhoneRelation?publicKey=…` çalışır. JSON `Platform:["android"]`, cihazın dil kodu için `Language:[…]` ve `PushNotificationToken:[""]` taşır. Trumore push token'ı kullanılmaz; boş değer orijinal nullable-token fallback'idir. |
| Sertifika servisi | Sabit HTTPS TVA host, Bearer kimlik doğrulaması ve `x-app-build-number: 661`. Query içindeki telefon public key'i 65 bayt `04 || X || Y` verisinin 130 karakterlik hex biçimidir. |
| Alma | `GET https://tva-api.togg.cloud/smart-access/api/v1/Certificates/GetCertificate?phonePublicKey=…&vin=…&isOwner=true/false`. Her seferinde tam sertifika istenir; `lastCheck` gönderilmez. |
| Oluşturma | `POST …/CreateCertificate?phonePublicKey=…`; JSON gövdesi `vin`, `shortCertificateBase64`, `isOwnerCertificate:true`. Sahip kısa sertifikası gerçek yerel anahtarla imzalanır. HTTP kabulü kendi başına etkin anahtar sayılmaz; tam sertifika ayrıca alınır. |
| Sertifika kabulü | Sürüm 1, 247 bayt; UUID/VIN/telefon public key/geçerlilik/sahiplik eşleşmesi; kapı/bagaj/sürüş izinleri yalnızca alınan sertifikadan okunur. Şifreli önbellek açılamazsa kurulum hata verir. |
| BLE oturumu | SAM sertifika isteği → telefon sertifikası → SAM public key edinimi veya kayıtlı pin → telefonun SAM nonce'ını imzalaması → SAM'ın telefon nonce'ını imzalaması → taze imzalı durum. İlk SAM anahtarı seçilen bağlantıda kullanıcı/VIN'e sabitlenir; sonraki değişiklik reddedilir. |
| Komut | Yeni 10 bayt nonce istenir; 8 bayt komut/tarih + nonce imzalanır, 64 bayt imza eklenir, 82 bayt yük `0x23` olarak parçalanır. |
| Sonuç | GATT yazma onayı veya komut ACK'i başarı değildir. Komut ACK'inden sonraki daha yeni, geçerli imzalı SAM durumunun hedef kilit/bagaj/READY haliyle eşleşmesi gerekir. Sekiz saniyede doğrulanamayan sonuç başarısızdır ve oturum kapanır. |
| Saat/kısayol | Telefondaki aynı oturum ve sertifika izinleri kullanılır. Saatte gösterilen doğrulanmış sertifika metadatası özel anahtar değildir; saat komutu telefona iletir. Eski etkinleştirilmemiş davet kaydı canlı erişimi engellemez veya yetki vermez. |

Kapı kapama/açma `0x01/0x02`, bagaj açma/kapama `0x04/0x05`, motor başlatma `0x0e` kullanılır. Komut tarihi UTC yıl BE16, ay, gün, saat, dakika, saniyedir. Sertifika tarih biçimi bundan farklıdır: saat, dakika, ay, gün, yıl LE16. Sertifika izinleri başlığa göre byte 47=kapı, 48=bagaj, 49=sürüş; tam sertifikada 48/49/50'dir.

Request ACK `PRE` alanları alınan toplam payload uzunluğu ve sonraki blok numarasıdır; `COMPLETE` toplam uzunluk ve sıfırdır. Parçalar sıra/uzunluk/blok sayısı ile doğrulanır. Android GATT yazmaları sıraya alınır; paket kapasitesi gerçek MTU'ya göre belirlenir. En küçük MTU 23 için 20 bayt paket/12 bayt payload kullanımı test edilmiştir.

85 bayt SAM durumunun ilk 15 baytı durum, sonraki 6 baytı LE sayaç, son 64 baytı imzadır. İmza ilk 21 bayt üzerinden doğrulanır; sayaç kesin artmalıdır. Alanlar başlatılabilirlik, menzil, kilit, beş kapı/bagaj açıklığı, dört cam, bagaj/cam menzili ve güç durumudur. `On=3` aksesuar; orijinal READY tüketicilerinin kullandığı `Driving=4` READY kabul edilir. BLE durum overlay'i bulut batarya/konum değerlerini veya bulut zaman damgasını taze göstermez.

## Orijinal APK kanıtları

Girdi ve hash bilgileri `TRUMORE_2_1_25_APK_VERIFICATION.md` içindedir. Yeni ham disassembly kanıtları çalışma alanındaki `../trumore-analysis/analysis/cert_audit/` ve `../trumore-analysis/flutter_audit/` dizinlerinde korunur.

- Kimlik zinciri: `1ff4f5c` token decoder `1fe6a0c` ile `sub` okur ve `1ff505c–1ff50b0` içinde kullanıcı/erişim token'ını kaydeder; `1ff51dc → 21f003c → 21f1f68 → 15e2b44` sertifika kimliğine taşır.
- Host: `b99af0` constructor `a7a0c8` TVA getter sonucuna `/smart-access/api/v1` ekleyip `field_7` yazar. `a7a0c8 → a7a12c → a7a164` zinciri `TVA_HOST_URL` okur; APK `prod.env` değeri `https://tva-api.togg.cloud`'dur. Bu instance `nVe` constructor'ında `field_33` olarak tutulur ve sertifika çağrılarına geçirilir.
- HTTP: `a77e50` API nesnesinde `pp+cac8` Create/POST bağlaması, `pp+cad0` Get/GET bağlamasıdır. Function cluster 37.427 kayıt/4 pointer ile `3c4f9b` sınırına tam oturur. `a78cec` POST, `a78ab0` GET sabitlerini yükler. Create descriptor `77098`: path pos1, `pJg` header pos2, `Yfg` query pos3, `Ech` gövde pos4, `ULu` pos5; Get descriptor `dc10`: path1, headers2, query3, flag4. `1ff7d08 → 1ff7fb8 → a78ab0 → 1fe0104 → a3b554 → a3bb38` query'yi request `field_4b`'ye taşır; `a79e8c` bunu `Request Query` olarak loglar, header `field_b` ve body `field_57`'den ayrıdır.
- Header: `1ff6ec8` ortak `x-app-build-number` alanı, `1ff6d78` `authorization`/`Bearer ` alanını kurar. Flutter header adı widget Retrofit'in `x-app-version` alanından farklıdır.
- Public key: `b95474` byte'ları closure `pp+2be90 → b95524` ile radix 16/two-digit padding'e dönüştürüp boş ayırıcıyla birleştirir.
- Telefon ilişkisi: `21f3bfc → 21f3ebc`, anahtarı hazırlayıp hex `publicKey` query'si ve liste değerli Platform/Language/PushNotificationToken gövdesiyle `/UserPhoneRelation/CheckUserPhoneRelation` çağrısını yapar (`21f4204`, aynı POST wrapper/descriptor `1ff5c8c/77098`). `21f40b4–21f40c8` push token bulunmadığında boş string seçer. Bu adım sertifika alma/oluşturmadan önce eklenmiştir; tam sertifika kabulü yine ayrıca gereklidir.
- Sertifika: `15e31f0`, `b98e4c`, `b95de8`, `b988e0`, `b95c14`; sahip başlık varsayılanları `15e2b44`. Üretimde UTC şimdi/365 gün, owner true, mode 1, üç izin true ve üç reserved alan sıfır ile **istek** oluşturulur. Etkin yetki sunucu/SAM sonucundan gelir.
- Oturum: request handler `2295404`, SAM key `2296efc`, SAM proof `2296fcc`, status `2294454`, raw status `19f81ac/19f8528`, READY tüketicileri `22a6424/22d72a4`.

Araç public key'inin dış sunucu köküne dayalı ayrı doğrulaması bu sürümde gösterilmez; ilk seçilen bağlantının anahtarı pin edilir ve karşılıklı nonce/imza kontrolü yapılır. Tam sertifikanın son 64 baytı SAM anahtarı sayılmaz. Desteklenmeyen sürüm 0 sertifika, izin yazma, kalibrasyon, eşleştirme silme veya motor durdurma komutları sentezlenmez.

Analiz aracı [dae v0.1.13 kaynak kodu](https://github.com/ejfkdev/dae/tree/v0.1.13) ve resmi Dart snapshot biçimi, yalnızca snapshot kayıtlarını okumak için kullanıldı. SDK profilinin durumu hâlâ **unverified**; sıfır uyarı tüm profili doğrulanmış yapmaz. Kontrat sonuçları ham ARM64, bağımsız Java köprüsü ve tam sınır kontrolü olan dar kapsamlı decoder'lara dayanır.

## Doğrulama sınırı

JVM testleri gerçek JCE P-256 imzaları kullanan sahte SAM ile oturum/parça/ACK/komut/durum akışını denetler. AndroidKeyStore'ın gerçek cihazda çalışması, canlı HTTPS servisinin kabulü, firmware'in aynı akışı izlemesi, kapı/bagajın fiziksel hareketi ve READY geçişi manuel denemeyle teyit edilmelidir. Yerel inceleme/derleme hiçbir sertifika API isteği veya fiziksel araç komutu göndermedi.
