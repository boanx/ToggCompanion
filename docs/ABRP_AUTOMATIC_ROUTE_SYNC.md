# ABRP → otomatik araç rota aktarımı

12 Ekim 2026. Kullanıcı güncel telefon APK'sinin manuel hedefini buluta kabul ettirdiğini bildirdi; araç Gez ekranını ayrıca kontrol edecek. Aşağıdaki otomasyon kaynak kodda uygulanmıştır, henüz APK'ye alınmamıştır.

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
