# Proje durumu — 12 Ekim 2026

## Güncel kaynak

- **Smart Access:** AndroidKeyStore P-256 anahtarları, hesap/telefon ilişkisi, sertifika oluşturma/alma, imzalı BLE oturumu, parçalı GATT aktarımı ve doğrulanmış sonuç geri bildirimi uygulanmıştır. Kapı kilitleme/açma, bagaj açma/kapama ve e-Motor READY başlatma akışı vardır. Fiziksel araç doğrulaması kullanıcı denemelerini bekler.
- **Pasif özellikler:** Klima, buğu çözme, far ve desteklenmeyen uzaktan kontroller pasiftir. READY → aksesuar dönüş komutu doğrulanmadığından e-Motor durdurma gönderilmez.
- **Doğrudan rota:** “Akıllı cihazıma aktar” ekranında hedef/rota önizlenir, tüm rota veya tek hedef seçilir ve PlannedJourneys üzerinden gönderilir. Hatalarda Trumore açılmaz. Eski hedef/asenkron sonuç karışıklığı ve tekrar gönderim önlemleri eklendi.
- **HTTP 301:** Adres `https://navigation-api.togg.cloud/j/api/PlannedJourneys` olarak düzeltildi. Önceki Superapp adresi `/home` sayfasına yönlendiriyordu. Yeni adrese kimliksiz HEAD `405, Allow: POST` döndürdü; gerçek oturumla POST ve araç teslimi henüz teyit edilmedi.
- **Pastel OLED Glass:** Gerçek `#000000` zemin; mint, buz mavisi ve lavanta vurguları; cam kartlar, çizgi ikonlar ve dar ekran/büyük yazı için uyarlanabilir düzen. Telefon, saat, Tile ve widget paletleri güncellendi.

## APK ve doğrulama durumu

**Kullanıcı açıkça istemeden APK compile/assemble/package/rebuild yapılmayacak. GitHub commit/push isteği derleme izni sayılmaz.**

Son yerel telefon APK’sı workspace `outputs/ToggCompanion-current-debug.apk` dosyasıdır. Güncel derleme SHA256:

`79cae1b71d8d2969ba572325210207ab69ba6fdcb75f42def2e252d7a5075786`

Bu APK HTTP301 navigasyon host düzeltmesini, pastel OLED telefon temasını, One UI widget’ını, siyah batarya etiketlerinin kaldırılmasını,35,2dp ortalanmış yenileme düğmesini ve4 dakika ABRP telemetri/otomatik rota kaynak kodunu içerir. Telefon `:app:assembleDebug --offline --console=plain` derlemesi başarılıdır. Saat APK’sı bu teslimde üretilmedi. APK Git’e eklenmez; yayın isteği GitHub Release asset ile karşılanır.

Önceki110 JVM testi tarihsel sonuçtur; bu tur test çalıştırılmadı. Fiziksel BLE/araç Gez teslimi, kullanıcıya özgü ABRP yanıtı ve yeni widget görüntüsü manuel doğrulama bekler. Kullanıcı önceki pakette manuel hedefin bulutta kabul edildiğini bildirdi; bu, fiziksel araç teslimi kanıtı değildir.

## Sıradaki işler

1. Kullanıcının yeni widget’ı kompakt/geniş boyutlarda denemesi.
2. Tek/çok hedefin araç Gez ekranına teslimini ve Smart Access hareketlerini manuel doğrulamak.
3. Canlı ABRP JSON uyumunu doğrulamak; ABRP SoC tahminlerinin araç payload’ına aktarımı için doğrulanmış alan halen yoktur.4 dakikalık zamanlayıcı tam dakika garantisi vermez.

## Belgeler

- [Smart Access kurulumu ve sınırlar](SMART_ACCESS_IMPLEMENTATION.md)
- [Doğrudan rota ve manuel deneme](DIRECT_VEHICLE_ROUTE_TRANSFER.md)
- [HTTP 301 kök nedeni ve kanıt](HTTP301_ROUTE_FIX.md)
- [OLED Glass ve pastel tema](OLED_GLASS_DESIGN.md)
- [Orijinal Trumore APK incelemesi](TRUMORE_2_1_25_APK_VERIFICATION.md)
- [KB yönlendiricisi](KB_INDEX.md)

Orijinal APK, hesap sırları, sertifika/token cache'i, decompile edilmiş üçüncü taraf paketler ve yerel derleme çıktıları repository'ye eklenmez.

## Sonraki kaynak güncellemesi: ABRP otomatik rota

Kullanıcı paketlenmiş güncel APK ile manuel hedefin bulutta kabul edildiğini bildirdi; araç ekranı denemesi pending. Yaklaşık 7 dakikalık ABRP değişiklik kontrolü, kapsamlı hedef dizisi ve tekrar önleme kaynakta eklendi. Bu yeni kod henüz APK’ye alınmadı. Ayrıntılar: [ABRP otomatik rota](ABRP_AUTOMATIC_ROUTE_SYNC.md).

## Aralık güncellemesi

Kullanıcı isteğiyle araç SoC/telemetri gönderimi ve ABRP rota kontrolü yaklaşık 4 dakikalık aralığa düşürüldü. Bu kaynak güncellemesi henüz APK’ye alınmadı.

## Widget renk güncellemesi

Widget canlı One UI mavi/mint/lavanta vurguları ve gerçek siyah yüzeyle güncellendi. Kaynak değişikliği henüz APK’ye alınmadı. [Widget tasarım notları](WIDGET_ONE_UI_DESIGN.md).

## Son teslim: widget renkleri ve ABRP otomasyonu

Kullanıcının açık derleme isteğiyle telefon debug APK’sı yeniden başarıyla derlendi. Son APK; One UI widget tasarımını, eski HV %20/%50/%80 ve 12V %60/%80 renk eşiklerini, 4 dakikalık ABRP telemetri/rota kontrolünü ve değişen rotayı otomatik buluta gönderme kaynak kodunu içerir. Önceki “kaynakta, APK’de yok” notları tarihsel durumdur. Araç teslimi, ABRP yanıt uyumu ve One UI cihaz görüntüsü kullanıcı tarafından manuel doğrulanacaktır. Bir sonraki derleme için yeniden açık istek gerekir.

## Widget ekran görüntüsü düzeltmesi (kaynak)

Batarya/akü siyah etiket zeminleri kaldırıldı. Yenileme butonu %80 boyuta (35,2dp) küçültüldü; 22dp beyaz vektör tam merkezde. Bu sonraki değişiklik için derleme/push yapılmadı; mevcut teslim APK’sı değişmedi.

## Son teslim düzeltmesi

Yukarıdaki güncel APK bilgisi geçerlidir. Aşağı/önceki tarihsel kaynak-only kayıtları son paketle aşılmıştır. Kullanım ve geliştirme başlangıcı: [Agent devir rehberi](AGENT_HANDOFF.md).
