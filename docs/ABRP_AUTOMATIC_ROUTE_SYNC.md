# Tarihsel ABRP otomatik rota tasarımı — kaldırıldı

2 Kasım 2026: aşağıdaki davranış güncel kaynakta yoktur. Kullanıcı isteğiyle tüm ABRP otomatik rota kodu kaldırıldı. Güncel sözleşme: [ABRP senkronizasyonu](ABRP_SYNC.md). Aşağıdaki notlar yalnız önceki sürümlerin tarihçesidir.

# ABRP → otomatik araç rota aktarımı

12 Ekim 2026. Kullanıcı güncel telefon APK'sinin manuel hedefini buluta kabul ettirdiğini bildirdi; araç Gez ekranını ayrıca kontrol edecek. Aşağıdaki otomasyon güncel telefon APK’sında yer alır; canlı ABRP/araç doğrulaması manueldir.

## Kullanım

1. ABRP'de ilgili araç için canlı veri tokenını alın; Companion'ın ABRP ayarına kaydedin. Togg ID oturumu ve gerçek VIN seçili olmalıdır. API anahtarının `get_latest_plan` erişimi de gereklidir.
2. **ABRP rota değişikliğini araca aktar (~4 dk kontrol)** seçeneğini açıp kaydedin. Bu seçim ayrı tutulur; telemetri gönderimi de isteniyorsa **4 dakikada bir ABRP'ye gönder** seçeneğini açın.
3. Kaydetme sonrası ilk kontrol istenir. Sonraki kontroller yaklaşık dört dakikada bir planlanır. Android arka plan/pil ve ağ kısıtları nedeniyle tam dakika veya anlık ABRP push garantisi yoktur.
4. İlk kullanılabilir ABRP planı başlangıç senkronizasyonu olarak bir kez gönderilir. Daha sonraki kontrollerde kabul edilmiş rota imzası değiştiyse yeni hedef/durak dizisi gönderilir; aynı rota tekrar gönderilmez.
5. **Rotayı şimdi kontrol et** beklemeden kontrol ister. Sonuç ABRP kartında ve izin verilmişse bildirimde görünür.

## Değişiklik algılama ve gönderim

- Eski yalnızca ilk durağın koordinatını karşılaştırma yerine sıralı gelecek hedeflerin koordinatları, varsa rota kimliği ve yayınlanan geometri imzası karşılaştırılır. SoC, süre, isim veya telemetri zamanı tek başına aynı hedef dizisini tekrar göndermeye neden olmaz.
- Seçili rotanın açık hedef/durak dizisi varsa son nokta varış, önceki gelecek noktalar ara durak olarak gönderilir. ABRP'nin ilk gelecek şarj durağı başlangıç diye atılmaz; başlangıç yalnız Companion'ın aynı VIN'e ait geçerli konumundan gelir. Rolü açık başlangıç/tamamlanmış duraklar hedef listesinden çıkarılır.
- ABRP yanıtı yalnız sıradaki hedefi veriyorsa yalnız bu hedef gönderilebilir; kart özeti bu sınırı belirtir. Seçimi belli olmayan birden fazla alternatif, bozuk/eksik koordinat ve boş rota gönderilmez. Üretimdeki kullanıcıya özel ABRP JSON şeması bu ortamda görülmedi; desteklenen alanların canlı yanıtla eşleşmesi manuel doğrulanacaktır.
- ABRP çizgisi/geometry, Togg payload'ına eklenmez. Araç API'sinin doğrulanmış sözleşmesi hedef/ara duraklar içindir; araç kendi yolunu hesaplayabilir. ABRP SoC sonucunu araç ekranına özel alan olarak gönderme desteği henüz doğrulanmadı.
- Kullanılan endpoint, manuel aktarımda bulut kabulü bildirilen navigasyon API'sidir. Trumore başarı veya hata durumunda arka plandan açılmaz.

## Kayıt, hata ve durdurma

- Başarılı rota imzası Togg hesabı, VIN ve ABRP tokenına bağlı hash kapsamıyla saklanır; farklı hesap/araç eski rota kaydıyla karıştırılmaz. Token/hash değeri ekrana veya loga yazılmaz.
- Bir kontrol veya gönderim sürerken ikinci otomatik kontrol/gönderim başlatılmaz. Ayar kapanır, hesap/VIN/token değişir veya iş durdurulursa henüz başlamamış gönderim iptal edilir. Daha önce gönderilmiş bir POST geri alınamaz.
- HTTP ile açıkça reddedilen gönderim başarılı sayılmaz; sonraki kontrolde denenebilir. Ağ kopması/zaman aşımı gibi sonuç belirsizliği kalıcı bekleyen rota kaydı bırakır; aynı rota otomatik tekrar gönderilmez. Bu kayıt süreç yeniden başlasa da korunur.
- **Rota gönderim kaydını sıfırla**, araç ekranı kontrol edildikten sonra aynı rotayı yeniden göndermeye izin verir. Açık kullanıcı işlemi olmadan belirsiz sonuç kaydı temizlenmez.
- Telemetri işi yalnız başarılı canlı TVA yanıtını ABRP'ye gönderir; API hatasında cache/demo veriyi canlı diye göndermez. ABRP'nin HTTP 200 içindeki uygulama `status` alanı da dikkate alınır.

## Kaynak ve doğrulama

Resmî [Iternio Telemetry API dokümanı](https://documenter.getpostman.com/view/7396339/SWTK5a8w), `get_latest_plan` işlevini, API key + kullanıcı tokenı gereksinimini ve `status/result` yanıtını anlatır. Yerel read-only kopya: `work/trumore-analysis/analysis/abrp-api/collection.json`. Bu yayımlanmış koleksiyonun mevcut request listesinde `get_latest_plan` örnek gövdesi bulunmadı; response alan uyumu kesinleşmiş gibi sunulmaz. Kod bu nedenle belirsiz alternatifleri veya eksik hedefleri göndermez.

12 Ekim 2026 tarihli sonraki açık derleme isteğiyle `ToggCompanion-current-debug.apk` güncellendi; yaklaşık 4 dakikalık telemetri/rota kontrolü ve otomatik rota kaynak kodu pakette yer alır. JVM testi eklenmedi/çalıştırılmadı, canlı ABRP tokenı okunmadı ve gerçek rota POST’u gönderilmedi. Kullanıcıya özgü ABRP yanıt şeması ve araçta otomatik teslim manuel doğrulama bekler.

## 16 Ekim 2026 — Son sürüş tüketimi (kaynak)

Kullanıcı tüketim kaynağını “Son sürüş” olarak seçti; myTrip/Yolculuğum ve son şarj kaydı kullanılmaz. AbrpClient önce currentTrip.avgConsumption değerini, yalnız currentTrip nesnesi yoksa eski lastTripAvgConsumption alanını kullanır. Değer parser tarafından kWh/100km’ye normalize edilmiştir; enerji sanılıp mesafeye tekrar bölünmez. İlgili mesafe ve tüketim sonlu/pozitif değilse consumption alanı atlanır. Gerçek currentTrip verisi eksikken parser artık3km/22kWh/100km örneği üretmez; eksik alanların fallback’i0’dır. Mevcut4 dakika ve manuel gönderim aynı buildTlmJson yolunu kullanır.

Bu mevcut consumption JSON alanının kaynağını değiştirir; ABRP referans tüketimini/otomatik kalibrasyonunu zorla ayarlayan ayrı bir API çağrısı değildir. Yerel resmi Iternio koleksiyonunun send açıklamasında consumption parametresi belgelenmiyor; kalibrasyon için anlık speed/power/is_charging ve en az10 saniyede bir örnek belirtiliyor.4 dakikalık sürüş ortalamasını anlık power gibi göndermiyoruz; ABRP’nin bu özel alanı nasıl kullandığı doğrulanmadı.

APK derlenmedi, test eklenmedi/çalıştırılmadı, canlı hesaba veri gönderilmedi, GitHub push yapılmadı. Önceki Smart Access kaynak düzeltmeleri korundu. Yeni paket sonrasında gelen gerçek telemetriyle alanın kullanıcı tarafında doğrulanması gerekir.

## Son kullanıcı düzeltmesi — ABRP kaynağı Yolculuğum

Önceki “Son sürüş/currentTrip” seçimi kullanıcı tarafından değiştirildi: ABRP consumption artık yalnız **Yolculuğum (`myTrip.avgConsumption`)** kaydından, mevcut normalize kWh/100km birimiyle alınır. Diğer sekmeler/legacy lastTrip fallback olarak kullanılmaz. myTrip eksik veya mesafe/tüketim sonlu ve pozitif değilse consumption gönderilmez. Canlı myTrip parser’ındaki örnek17,8 tüketim/odometre tabanlı mesafe fallback’i sıfırlandı.4 dakika aralık korunur. Kaynak güncellemesi; APK derlenmedi, canlı ABRP gönderimi ve push yapılmadı. ABRP’nin özel consumption alanını referans kalibrasyonunda kullandığı doğrulanmış değildir.

## Son zamanlama düzeltmesi —3 dakika canlı telemetri

Kullanıcı isteğiyle araç API’sinden yeniden telemetri alıp ABRP’ye gönderme işi180000ms/3 dakika aralığına çekildi. Her arka plan çalışması fetchTelemetry yapar; isLive/non-null/gerçek VIN/oturum koşulları olmadan, API hatasında veya cache fallback’inde ABRP’ye gönderim yapılmaz. Ana ekranın otomatik gönderimi de yalnız fetch callback’inin canlı non-null verisini kullanır. Telemetri3dk ve rota kontrolü4dk ayrı sabitlerdir; tüketim kaynağı Yolculuğum/myTrip olarak kalır. Zincirlenen JobScheduler/pil/ağ kısıtları nedeniyle3dk tam saat garantisi değildir; taze HTTP yanıtı aracın bulut zaman damgasının da o an olduğu anlamına gelmez. Kaynak düzeltmesi; APK/test/canlı gönderim/push yapılmadı.

## 19 Ekim 2026 — Güncel telefon paketi

Kullanıcının açık PAKETLE isteğiyle telefon debug APK’sı başarıyla derlendi. Paket bekleyen sertifika parser/tanı değişikliklerini, Yolculuğum/myTrip tüketimini,3 dakika canlı telemetri sorgusu/gönderimini ve e-Motor altındaki fazla rota butonunun kaldırılmasını içerir. Rota kontrolü4dk kalır. Yerel dosya `outputs/ToggCompanion-current-debug.apk`, boyut4111034 bayt, SHA256 `61d525deb1be63d53ba77a7ef7f53d76559eeceb667158ce76519f71c5a5b634`. Temel commit `73dcc2f4582234bd93c34ebb35773cae1fe07d8b` üzerine kaydedilmemiş kaynak değişiklikleri dahildir. Bu tur saat derlenmedi; eski saat teslimi tek aktif APK kuralıyla manifest kullanılarak obsolete/2026-10-19/paket-oncesi altında arşivlendi. GitHub push/release yapılmadı. Önceki kaynak-only notları bu yerel paket için aşılmıştır. HTTP500 kök nedeni ve ABRP özel consumption alanının kalibrasyon etkisi doğrulanmadı; cihaz testleri manuel.
