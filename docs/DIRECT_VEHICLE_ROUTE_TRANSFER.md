# Companion → Akıllı cihazıma aktar

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
