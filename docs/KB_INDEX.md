# Knowledge Base Index

This file is the canonical router for selecting feature knowledge bases.

Always read this file before loading any feature KB.

## Routing Rules

1. Select the smallest relevant feature KB.
2. Select one primary KB whenever possible.
3. Add secondary KBs only when a documented cross-feature boundary requires them.
4. Do not load all KB folders by default.
5. Check documented bridge files when a task crosses feature boundaries.
6. If no route matches, return:

   MISSING_INDEX_ROUTE: <missing routing entry>

   RECOMMENDED_INDEX_UPDATE: <recommended route>

7. For a genuinely new feature, follow the "creating a new feature KB" procedure in:
   `docs/KB_PROTOCOL.md`

## Feature Routes

### togg-companion — `docs/togg-companion/`

- Use for: Togg T10F phone companion (`app/`) and Samsung Galaxy Watch Wear OS app (`wear/`). Covers vehicle telemetry parsing, vehicle BLE connection, authenticated Smart Access certificates/session and command gating, secure token storage, SQLite caching, Trumore/TVA API client, headless actions & deep links, Wear OS Tile provider, Wearable Data Layer sync, and UI screens.
- Primary KB: `docs/togg-companion`; read `IDMAP.md`, then `KG.adj`, then matching `FACTS.jsonl` records.
- Secondary folders: none; this project currently has one consolidated feature ecosystem.
- Bridge files: none. Phone and watch components communicate over the Wearable Data Layer protocol documented in this KB.
- Standalone verification entry points: `app/src/test/java/com/togg/companion/core/TelemetryParserTest.java`, `app/src/test/java/com/togg/companion/data/remote/TvaApiClientTest.java`, and `scripts/validate-kb.cjs`.
- Original Trumore 2.1.25 APK verification: `docs/TRUMORE_2_1_25_APK_VERIFICATION.md`; use for independently sourced protocol evidence, cryptographic key requirements, and unverified BLE/PlannedJourneys assumptions.

- Smart Access provisioning/session/manual verification: `docs/SMART_ACCESS_IMPLEMENTATION.md`; use for AndroidKeyStore, authenticated certificate API contracts, BLE handshake/status/ACK decoding, manual setup and safe diagnostic feedback. Tests: `SmartAccessKeyStoreTest`, `SmartAccessCertificateTest`, `SmartAccessProtocolTest`, `SmartAccessSessionTest`, `SmartAccessCertificateClientTest`.

- Google adres/link çözümleme ve doğrudan araç rota aktarımı: `docs/DIRECT_VEHICLE_ROUTE_TRANSFER.md`; 25 Ekim Companions DELETE→POST araç paylaşımı ve tarihsel PlannedJourneys kaydı ayrımı, manuel hedef/rota önizleme, HTTP sonuçları (16Kasım kabul/doğrulandı/belirsiz/hata ayrımı AF291–AF292), hesap/araç bağlamı ve araç ekranı tesliminin doğrulama sınırı için kullanın. 16 Kasım encoded/wrapper/canonical link ve viewport-hedef ayrımı için AF289–AF290/F61–F62. HTTP 301 ve doğru navigasyon API alan adı için `docs/HTTP301_ROUTE_FIX.md` düzeltmesini de okuyun.

- Güncel kaynak/APK durumu ve geliştirme notları: `docs/PROJECT_STATUS_2026_10_12.md`; 18 Kasım1.1/kod2şarj durum regresyonu paketi (AF314;önceki güç/tanı paketi AF311), derleme kısıtı ve fiziksel doğrulama sınırları için başlangıç belgesi.

- Yol Tarifi sekmesi, ABRP senkronizasyonu ve paylaşılabilir günlükler: `docs/ABRP_SYNC.md`; F99–F105/F108–F111 ve AF268–AF272/AF279–AF282. Bir dakikalık kullanıcı başlatımlı arka plan telemetri servisi, Ayarlar sekmesi, veri tazeliği ve Android süre sınırları. `docs/ABRP_AUTOMATIC_ROUTE_SYNC.md` yalnız tarihsel kayıttır; otomatik rota özelliği kaldırıldı.

## Cross-Feature Dependencies

None registered yet.

## Bridge Files

None registered yet.

- OLED Glass tema ve arayüz: `docs/OLED_GLASS_DESIGN.md`; telefon/saat pastel vurgu paleti, gerçek siyah, cam katmanları, tipografi ve eski tema tercihinin davranışı için aynı `docs/togg-companion` KB kullanılmalıdır.

- Widget harita kaydı ve canlı One UI widget tasarımı: `docs/WIDGET_ONE_UI_DESIGN.md`; 16 Kasım yanlış pencere görüntüsü/araç-konum önbelleği düzeltmesi AF288–AF290/F112–F113; widgeta özel mavi/mint palet ve batarya kırmızı/amber/mavi/yeşil eşikleri, gerçek siyah yüzey ve okunabilirlik için aynı KB içindeki U6 mahallesini kullanın. Araç snapshot zamanı ve JobService ile manuel widget yenilemesi için AF273–AF275/F106–F107; genişletilmiş ABRP aç/kapat ve yükseklik varyantları için AF280–AF282/F109–F111.

- Kullanım ve geliştirme devri: `docs/AGENT_HANDOFF.md`; kurulum, değişiklik gerekçeleri, dosya haritası, teslim süreci ve manuel doğrulama sınırları.

- Smart Access sertifika hataları/tanısı: `docs/SMART_ACCESS_CERTIFICATE_DIAGNOSTICS.md`; HTTP500, Base64/schema hata ayrımı ve GATT aşama kayıtları için C10 mahallesi.

- Smart Access ilk kayıt ve davet kabulü: `docs/SMART_ACCESS_ENROLLMENT_RESEARCH.md`; resmi araç onayı, owner/shared boolean düzeltmesi, ShareProcess alanları ve eksik entegrasyon planı için C10.

- Orijinal Trumore sürüm karşılaştırması: `docs/TRUMORE_2_1_26_COMPARISON.md`; 2.1.25/661 ile telefondan alınan 2.1.26/663 paket farkları, ham AOT literal adresleri, widget/saat hazırlığı ve doğrulanmamış sözleşmeler için aynı KB'nin C10/C9/U6 mahalleleri.

- Mevcut Trumore anahtarını koruyan ayrı hesap/paylaşım hazırlığı: `docs/SMART_ACCESS_SHARED_ACCOUNT_PREPARATION.md`; canlı OverwriteDetection, eksik ClaimCarToUser adımı, davet seId/no imza zinciri ve guest oturum ayrımı için C10. 21 Ekim güncel bölümünde davet kabulü, manuel sahip onayı ve canlı doğrulanmış guest sertifika kaydı vardır; BLE oturumu/komutları henüz doğrulanmadı.

- Paylaşılan hesap girişi ve davet önizlemesi: aynı F96/C10 rotası; SmartAccessAccountStorage ana telemetri oturumundan bağımsızdır. SmartAccessInvitationClient sessionDetail, imzalı ReplyToSession ve isOwner=false sertifika almayı uygular; VehicleBleManager ayrı guest PhoneKey bağlamını kullanır.

- Ana sayfa kart sırası ve toplam kilometre: `docs/HOME_LAYOUT_AND_ODOMETER.md`; AF293–AF295/F114–F116. Geniş kartların kayıtlı konumu, görünürlük/geçiş, gerçek odometre birimi ve bulut güncelliği sınırı için U1/C1 mahalleleri.

- Yolculuk süresi/birimleri: `docs/TRIP_DURATION.md`; AF298–AF299/F02/F31/F117. Dakika10a-bölme hatası, saat+bileşen/total ayrımı ve eski cache sınırları için C1/T1.

- Sürüş modu gösterimi: `docs/DRIVE_MODE_DISPLAY.md`; AF301–AF302/F118–F120. Eksik veride Konfor varsayımının kaldırılması,metinsel enum/unknown ve son bulut verisi sınırı için C1/U1.

- Google Drive / ajan için yalnız toplam kilometre JSON paylaşımı: `docs/ODOMETER_JSON_EXPORT.md`; C11/F121–F126/AF303–AF305. Ayarlar ekranı, SAF tek-belge izni, kullanıcı başlangıç/aralık seçimi, persisted JobScheduler ve araç veri zamanı; gerçek Drive eşitlemesi/Gemini dosya okuması henüz doğrulanmadı. Aynı birincil KB kullanılır.

- Şarj gücü ve sabit11kW hatası: `docs/CHARGING_POWER.md`; C1/U1/U4/F127–F129/AF307–AF313. AC11/DC120 varsayımlarının kaldırılması, gerçek0 ile bilinmeyen-1 ayrımı, tür/güç etiketleri, ölçüm yoksa yalnız Şarj oluyor, CHARGING alan/değer tanısı ve kaynak/APK/cache sınırı; 18Kasım CHRGMODE_CHARGE/metinsel durum regresyonu için kullanın.
