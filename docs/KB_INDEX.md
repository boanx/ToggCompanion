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

- Doğrudan araç rota aktarımı: `docs/DIRECT_VEHICLE_ROUTE_TRANSFER.md`; cloud-only PlannedJourneys gönderimi, manuel hedef/rota önizleme, HTTP sonuçları, hesap/araç bağlamı ve araç ekranı tesliminin doğrulama sınırı için kullanın. HTTP 301 ve doğru navigasyon API alan adı için `docs/HTTP301_ROUTE_FIX.md` düzeltmesini de okuyun.

- Güncel kaynak/APK durumu ve geliştirme notları: `docs/PROJECT_STATUS_2026_10_12.md`; derleme kısıtı, manuel doğrulama sınırları ve sıradaki ABRP aşaması için başlangıç belgesi.

- ABRP otomatik rota: `docs/ABRP_AUTOMATIC_ROUTE_SYNC.md`; değişiklik imzası, yaklaşık 4 dakikalık kontrol, gelecek hedeflerin origin/waypoint ayrımı, belirsiz sonuçta tekrar önleme ve ayar akışı için kullanın.

## Cross-Feature Dependencies

None registered yet.

## Bridge Files

None registered yet.

- OLED Glass tema ve arayüz: `docs/OLED_GLASS_DESIGN.md`; telefon/saat pastel vurgu paleti, gerçek siyah, cam katmanları, tipografi ve eski tema tercihinin davranışı için aynı `docs/togg-companion` KB kullanılmalıdır.

- Canlı One UI widget tasarımı: `docs/WIDGET_ONE_UI_DESIGN.md`; widgeta özel mavi/mint palet ve batarya kırmızı/amber/mavi/yeşil eşikleri, gerçek siyah yüzey ve okunabilirlik için aynı KB içindeki U6 mahallesini kullanın.

- Kullanım ve geliştirme devri: `docs/AGENT_HANDOFF.md`; kurulum, değişiklik gerekçeleri, dosya haritası, teslim süreci ve manuel doğrulama sınırları.
