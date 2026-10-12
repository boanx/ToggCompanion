# Proje durumu — 12 Ekim 2026

## Güncel kaynak

- **Smart Access:** AndroidKeyStore P-256 anahtarları, hesap/telefon ilişkisi, sertifika oluşturma/alma, imzalı BLE oturumu, parçalı GATT aktarımı ve doğrulanmış sonuç geri bildirimi uygulanmıştır. Kapı kilitleme/açma, bagaj açma/kapama ve e-Motor READY başlatma akışı vardır. Fiziksel araç doğrulaması kullanıcı denemelerini bekler.
- **Pasif özellikler:** Klima, buğu çözme, far ve desteklenmeyen uzaktan kontroller pasiftir. READY → aksesuar dönüş komutu doğrulanmadığından e-Motor durdurma gönderilmez.
- **Doğrudan rota:** “Akıllı cihazıma aktar” ekranında hedef/rota önizlenir, tüm rota veya tek hedef seçilir ve PlannedJourneys üzerinden gönderilir. Hatalarda Trumore açılmaz. Eski hedef/asenkron sonuç karışıklığı ve tekrar gönderim önlemleri eklendi.
- **HTTP 301:** Adres `https://navigation-api.togg.cloud/j/api/PlannedJourneys` olarak düzeltildi. Önceki Superapp adresi `/home` sayfasına yönlendiriyordu. Yeni adrese kimliksiz HEAD `405, Allow: POST` döndürdü; gerçek oturumla POST ve araç teslimi henüz teyit edilmedi.
- **Pastel OLED Glass:** Gerçek `#000000` zemin; mint, buz mavisi ve lavanta vurguları; cam kartlar, çizgi ikonlar ve dar ekran/büyük yazı için uyarlanabilir düzen. Telefon, saat, Tile ve widget paletleri güncellendi.

## APK ve doğrulama durumu

**Kullanıcı açıkça istemeden APK compile/assemble/package/rebuild yapılmayacak. GitHub commit/push isteği derleme izni sayılmaz.**

Son yerel telefon APK'si `ToggCompanion-direct-route-debug.apk`, SHA256:

`82a37cdba2434e3f465ddcbd91392eb05a1e84e47db366cdaa59d77c22cc43b8`

Bu APK, sonraki **HTTP 301 adres düzeltmesini ve pastel temayı içermez**. Kaynak kod APK'den ileridedir. APK'ler Git tarafından ignore edilir; bu güncelleme GitHub release/APK yayını değildir. Eski paketler ve geçici dosyalar yerel `obsolete/` arşivindedir.

Önceki Smart Access aşamasında 110 JVM testi başarılı olmuş ve telefon/saat debug paketleri derlenmiştir. Son doğrudan rota aşamasında telefon APK'si derlenmiştir. HTTP 301 ve pastel kaynak güncellemelerinden sonra APK/JVM testi çalıştırılmamıştır. Son statik XML, KB ve diff kontrolleri başarılıdır; önceki test sonucu son kaynak ağacının test edilmiş olduğu anlamına gelmez.

Gerçek araç komutu veya tokenlı rota POST'u gönderilmedi. HTTP 301 teşhisinde yalnız kimliksiz HEAD istekleri yapıldı.

## Sıradaki işler

1. Kullanıcı derleme istediğinde kaynak düzeltmelerini APK'ye almak.
2. Tek hedefin API kabulünü ve araç Gez ekranına teslimini manuel doğrulamak; sonra çok durak davranışını incelemek.
3. Canlı telemetri → ABRP → değişen rota/duraklar ve SoC → araç akışını geliştirmek. İstenen yeni 7 dakikalık akış henüz tamamlanmış değildir; mevcut iş zamanlayıcısı tam dakika hassasiyeti garantisi vermez.

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
