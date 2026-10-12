# Kullanım ve geliştirme devri — 12 Ekim 2026

## Başlangıç

Önce kök `AGENTS.md`, ardından `KB_INDEX.md` → `togg-companion/IDMAP.md` → `KG.adj` → ilgili `FACTS.jsonl` kayıtlarını okuyun. Tarihsel fact’leri değiştirmeyin; yeni düzeltme fact’i ekleyin. Güncel widget davranışı AF196/AF198/AF199 ile, ABRP davranışı AF185–AF193 ile belgelenir. En son teslim durumu `PROJECT_STATUS_2026_10_12.md` içindedir.

## Çalıştırma ve kullanım

- Telefon: native Android Java/XML, `app/`, minSDK26/compileSDK36/Java17. Paket `com.togg.companion`. Wear OS modülü `wear/`; saat telemetrisi telefonun Google Wearable Data Layer aktarımından gelir.
- Son telefon debug APK’sı GitHub Releases üzerinden ve yerel workspace `outputs/ToggCompanion-current-debug.apk` yolundan alınır. Sonraki açık kullanıcı isteğiyle saat debug APK’sı da derlenip yayımlandı; yerel güncel teslim `outputs/ToggCompanion-wear-current-debug.apk` dosyasıdır. Telefon paketi önceki GitHub Release üzerinden erişilebilir. Sürüm adı/kodu hâlen1.0/1; farklı bilgisayardaki debug imzası aynı olmayabilir. Güncelleme imza uyuşmazlığı verirse kullanıcı verilerini kaybetmeden önce imza/kurulum koşulunu inceleyin.
- Togg ID oturumunu açın; gerçek araç VIN ve hesap bağlamını doğrulayın. Sırları, tokenları veya sertifikaları repoya/loga eklemeyin. Demo verisi gerçek araç işlemi kanıtı değildir.
- Smart Access ekranında VIN/truId/sahiplik bilgisiyle “Sertifikayı al ve yetkilendir” akışını kullanın; Bluetooth/konum izinlerini cihaz koşullarına göre verin. Medya Bluetooth eşleşmesi BLE oturumu değildir. Sertifika, servis keşfi ve imzalı oturum tamamlanmadan komut hazır sayılmaz. Kilit/bagaj/READY hareketleri kullanıcı tarafından fiziksel araçta doğrulanmalıdır. Motor durdurma/READY’den aksesuar moduna dönüş komutu doğrulanmış kabul edilmez.
- Manuel hedef aktarımı Companion rota ekranından navigasyon bulut API’sine gönderilir. Kullanıcı bulut kabulünü bildirdi; aracın Gez ekranına teslim ayrı kontrol gerektirir. Trumore’a otomatik fallback yapılmaz.
- ABRP ayarlarına ilgili araç canlı veri tokenını kaydedin. Telemetri gönderimi ve otomatik rota aktarımı ayrı seçeneklerdir; ikisi yaklaşık4 dakikada bir çalışır. “Rotayı şimdi kontrol et” ile beklemeden kontrol isteyin. İlk kullanılabilir plan bir kez gönderilir; sonrasında rota imzası değişirse yeni hedefler gönderilir. “Takibi sıfırla” kabul edilmiş/belirsiz kayıtları temizleyerek yeniden göndermeye izin verir; belirsiz POST sonrasında bunu bilinçli kullanın.
- Widget’ı launcher’dan ekleyin, kompakt/geniş boyutlarda değerlendirin. Yenileme aracı sorgular; gösterilen saat verinin eskiliğini belirtir. Son park yeri canlı araç hareketi anlamına gelmez.

## Neyi, neden, nasıl değiştirdik?

| Alan | Gerekçe ve uygulama | Kaynak / ayrıntı |
| --- | --- | --- |
| BLE | Prototip komutların yerine sertifika/nonce/imza/MTU/parça/ACK akışı; araç hazır olmadan komut engeli | `VehicleBleManager`, `SmartAccessSession`, `SMART_ACCESS_IMPLEMENTATION.md` |
| Pasif kontroller | Kullanıcının aracında iklimlendirme ve desteklenmeyen uzaktan komutlar aktif değil; yerel başarı taklidi kaldırıldı | `MainActivity`, `OLED_GLASS_DESIGN.md` |
| HTTP301 | Eski host ana sayfaya yönlendiriyordu; yönlendirmeyi takip etmek yerine doğrulanmış navigasyon host’u kullanıldı | `TvaApiClient`, `HTTP301_ROUTE_FIX.md` |
| Rota | Gelecek şarj durağı origin diye atılmasın; ayrı upcoming hedef modeli ve aynı VIN’in konumu origin olarak kullanılır | `GezRouteBridge`, `TvaApiClient` |
| ABRP |4 dakika canlı veri ve değişen plan kontrolü; koordinat sırası/rota kimliği/geometri imzası, hesap+VIN+token kapsamı; aynı rotaya tekrar engeli | `AbrpClient`, `AbrpJobService`, `SecureTokenStorage`, `ABRP_AUTOMATIC_ROUTE_SYNC.md` |
| OLED | Telefon/saat gerçek siyah ve pastel vurgular; widget için ayrı canlı One UI paleti | `widget_colors.xml`, `WIDGET_ONE_UI_DESIGN.md` |
| Batarya | Eski eşikler geri getirildi: HV20/50/80 kırmızı/amber/mavi/yeşil,12V60/80 kırmızı/amber/yeşil | `ToggAppWidgetProvider` |
| Widget düzeltmesi | Kullanıcı görüntüsündeki siyah etiketler kaldırıldı.44dp buton35,2dp oldu;22dp beyaz vektör6,6dp eşit padding ile ortalanır; font baseline kayması giderildi | `widget_togg.xml`, `ic_widget_refresh.xml`, AF198 |

## Sınırlar ve sonraki çalışma

Cloud2xx, araç ekranına teslim veya fiziksel hareket kanıtı değildir. ABRP’nin kullanıcıya özel `get_latest_plan` JSON’u burada görülmedi; belirsiz alternatif/boş veya bozuk hedef gönderilmez. SoC tek başına rota değişikliği sayılmaz. ABRP SoC tahminlerini araç payload’ına ekleyen doğrulanmış alan henüz yok; araç hedeflerden kendi yolunu hesaplayabilir. Android JobScheduler/pil/ağ kısıtları nedeniyle4 dakika yaklaşık aralıktır.

Belirsiz POST sonucu otomatik tekrar edilmez; gönderim öncesi attempt ve kabul sonrası hash saklanır. Önceden başlamış POST ayar kapatılarak geri alınamaz. Canlı telemetri başarısızsa cache ABRP’ye gönderilmez. Eski motor komutları veya key/sertifika tahminleri eklemeyin. Orijinal APK/AOT kanıtı yerel `work/trumore-analysis/` içindedir ve üçüncü taraf decompile dosyaları repoya alınmaz.

## Derleme, teslim ve kontroller

Sadece açık kullanıcı derleme isteğiyle `./gradlew.bat :app:assembleDebug --offline --console=plain` çalıştırın; Windows’ta Android Studio JBR `JAVA_HOME`, SDK `ANDROID_HOME` gerekir. Offline cache yoksa bağımlılık erişimi ayrıca gerekir. Yeni APK’yi `outputs/` içine taşıyın; eski APK/teslim raporlarını göreli yollarını koruyan manifest ile `obsolete/` altına arşivleyin. APK’ler Git’e eklenmez; kullanıcı yayın isterse GitHub Release asset olarak yüklenir.

KB için `node scripts/validate-kb.cjs`, kaynak diff için `git diff --check` kullanın. Testleri kullanıcı istediğinde çalıştırın; eski110 test sonucu yeni kaynakların test edildiği anlamına gelmez. Son telefon ve saat debug derlemeleri başarılıdır; cihaz/emülatör/BLE/ABRP canlı testleri yapılmaz. Kaynak commit SHA, APK SHA256, boyut ve derleme günlüğünü teslimde kayıt altına alın. Git author mevcut kullanıcının kimliği olmalıdır; yeni yazar uydurmayın.

## Saat APK’sı kurulumu ve eşitleme

Saat paketi yalnız Wear OS cihaza kurulmalıdır; telefon APK’sı saatte, saat APK’sı telefonda kullanılmaz. Saat minSDK30/targetSDK34/compileSDK36; package ID telefonla aynı `com.togg.companion`, namespace `.wear`’dır. Data Layer için telefon ve saat uygulama kimliği ile imza eşleşmelidir; aynı makinede aynı debug anahtarıyla derlenmiştir. Saatte token girmeyin: telefon oturumu/araç ayarları ve telemetriyi yönetir.

Telefonda son sürümü kurup giriş/araç seçimini tamamlayın, telefonu saatle eşleyin; saatte yeni APK’yı kurup uygulamayı açın. Saat yenilemesi telefonun canlı veri isteğine gider. İstenirse Galaxy Watch Tile’ını ekleyin; “Araca yürü” için saat konum izni ve harita uygulaması gerekir. Veri gelmiyorsa Data Layer düğüm erişimini, iki taraftaki paket/imzayı, telefon arka plan erişimini ve veri zamanını kontrol edin. Eşitleme veya BLE komut çalışması derleme başarısından çıkarılamaz.

Saat APK’sı yayını telefon widget’ını veya ABRP zamanlamasını değiştirmez; ABRP4 dakikalık işler telefondadır. Depoda kaynak değişikliği gerektirmeyen paketleme yapıldı; bu tur yalnız dokümantasyon/KB teslim bilgisi değişti. Workspace tek aktif APK kuralı gereği telefonun yerel teslimi arşivlenir; önceki telefon release asset’i korunur.
