# Kilometre JSON paylaşımı — 16 Kasım 2026

Uygulama: Ayarlar → Kilometre paylaşımı. 16 Kasım 2026 telefon debug
APK 1.1/kod2 bu özelliği içerir (AF306). 147 test ve lint0 hata ile
paketlendi; gerçek Drive eşitlemesi ve cihaz zamanlaması henüz denenmedi.

## Kullanım

1. Telefonda Google Drive yüklü ve istenen hesapta açık olmalı.
2. "Google Drive'da JSON dosyası oluştur" ile Android dosya seçicisini açın.
   Yan menüden Drive hesabını ve klasörü seçip `togg-kilometre.json` oluşturun.
   Seçici diğer belge sağlayıcılarına da izin verir; yerel klasör seçerseniz
   dosya yerelde kalır. Uygulama yerel dosyayı kendiliğinden Drive'a yüklemez.
3. İlk gönderimin tarih/saatini ve tekrar aralığını dakika olarak girin.
   60=saatlik, 1440=günlük; 15–525600 dakika arasında serbest seçim vardır.
4. Otomatik gönderimi açıp ayarları kaydedin. "Şimdi gönder" otomatik gönderim
   kapalıyken de kullanılabilir; form ayarlarını kaydeder.
5. Son dosya güncellemesini, sonucu ve planlanan zamanı aynı ekrandan izleyin.
   Yeni dosya seçmek otomatik gönderimi kapatır; yeniden kaydetmek gerekir.

## Dosya sözleşmesi

Tek dosyanın içeriği değiştirilir; geçmiş liste tutulmaz. Örnek **temsili**:

```json
{
  "odometer_km": 4499,
  "vehicle_data_at": "2026-11-15T22:00:00Z",
  "retrieved_at": "2026-11-16T22:00:00Z"
}
```

Tek araç ölçümü toplam odometredir; sürüş sayaçları, batarya, konum, VIN,
plaka ve hesap/token bilgileri dışarı verilmez. İki zaman ISO8601 UTC'dir.
`vehicle_data_at` Togg bulutunun araç yayın zamanını, `retrieved_at` yeni
HTTP yanıtının alınma zamanını gösterir. Son sorgu eski araç ölçümünü
döndürebilir; zamanlar eşitlenerek tazelik iddiası üretilmez. Mevcut modelde
0 km bilinmeyen sayılır: bilinmeyen kilometre/zaman, demo, cache/fallback,
araç/hesap değişimi ve ağ hatası dışarı aktarılmaz. Önceki dosyaya yalnız
gerçek, aynı araç ve hesap bağlamındaki yeni servis yanıtından yazılır.

## Neden Storage Access Framework?

Android ACTION_CREATE_DOCUMENT ve kalıcı tek-belge URI izni kullanılır.
Ayrı sunucu, Apps Script, OAuth istemci sırrı veya tüm Drive erişimi gerekmez.
Kullanıcı dosyayı seçer; Drive sağlayıcısı bulut eşitlemesini üstlenir.
"Dosya güncellendi" sağlayıcının yazma/kapama sonucudur; Drive sunucusunda
kalıcı kayıt veya Gemini'nin dosyayı okuyabildiği anlamına gelmez.
Ham JSON'un Gemini görevlerinde okunması/indekslenmesi bu değişiklikte
entegre veya doğrulanmış değildir. Diğer ajanlar Drive API ile dosyayı okuyabilir.

Kalıcı izin dosya taşınır/silinirse veya sağlayıcı hesaptan çıkarsa kaybolabilir;
dosyayı yeniden seçin. Sağlayıcı yazımı atomik olmayabilir: kesilen dosya yazımı
eski içeriği bozabilir; sonraki başarılı gönderim tam içeriği yeniden yazar.
Seçilen dosya ilk başarılı gönderime kadar boş olabilir. Eski dosyalar silinmez.

## Zamanlama ve bağlam

JobScheduler: 8204 otomatik, 8205 manuel. Otomatik tek seferlik iş ilk
başlangıca hizalı bir sonraki aralığa tekrar kurulur; bitişe göre kaymaz,
kaçan aralıklar topluca gönderilmez. Jobs ağ bekler; Android pil/Doze/force-stop
kısıtlamaları nedeniyle dakika hassasiyeti garanti edilmez. Persisted iş için
RECEIVE_BOOT_COMPLETED izni vardır; yeniden başlatmada JobScheduler işi saklar.
Ön plan servisi veya kesin alarm kullanılmaz. Telefon kapalıyken gönderilemez.
Aralık gerçek geçen dakikadır; başka saat dilimine geçmek takvimi otomatik
yeniden sabitlemez, kullanıcı tarih/saatini yeniden kaydedebilir.

Yapılandırma nesli ve SHA256(araç+JWT issuer/sub; çözülemez bağlamda güvenli
ret) ile eski işlerin farklı hesaba/araç bağlamına yazması engellenir.
Togg tokenları SecureTokenStorage'da kalır; export prefs yalnız ayarlar,
belge URI'si ve bağlam özeti tutar. Uçuşta yazma başladıktan sonra ayar
değişirse buluttaki önceki yazı geri alınamaz. Job stop/120sn timeout,
CancellationSignal ve açık descriptor kapatma ile durdurulmaya çalışılır.
ODOMETER olayları paylaşılabilir AppLog'a yazılır; dosya URI'si/ölçüm/token
loglanmaz. Yeni özellik ABRP'nin 1dk servisini değiştirmez.

## Doğrulama ve kalan cihaz kontrolleri

147 JVM testi (6 yeni payload/zamanlama testi), tüm telefon Java kaynakları
SDK36 tip kontrolü, XML parse ve KB doğrulaması geçti. Son paketleme: Gradle
test/lint/assemble başarılı,147 test,lint0 hata/332 uyarı,v2 imza doğrulandı.
Gerçek Drive sağlayıcısı, persistable izin/reboot, ekran kapalı zamanlama ve
bulutta dosyanın gerçekten güncellenmesi bağlı telefonda henüz denenmedi.

Manuel kabul: Drive'da dosya seç → Şimdi gönder → dosyayı Drive web üzerinden
oku → üç alanı ve araç veri zamanını kontrol et → 15dk plan/ekran kapalı dene →
kapatınca yeni gönderim olmadığını dene → yeniden başlat/hesap değişimi/ağ
kesilmesi/dosya silinmesi durumlarında yanlış veri veya başarı oluşmadığını dene.

Resmi sözleşmeler:
- https://developer.android.com/training/data-storage/shared/documents-files
- https://developer.android.com/reference/android/app/job/JobInfo.Builder

Kanıt: F121–F126, AF303–AF305. JVM ve kaynak kontrolü fiziksel Drive/araç
başarısının kanıtı değildir.
