# Togg Companion IDs

## Scope

Implemented native Android companion phone application (`app/`) and Wear OS Galaxy Watch application (`wear/`) for Togg T10F vehicles. Includes vehicle telemetry parsing, TVA cloud API integration, secure token storage, local SQLite persistence, quick action shortcuts, Wear OS Tile service, and Bluetooth sync via the Wearable Data Layer.

## File IDs

- `F01` = `app/src/main/AndroidManifest.xml`
- `F02` = `app/src/main/java/com/togg/companion/core/TelemetryParser.java`
- `F03` = `app/src/main/java/com/togg/companion/core/VehicleTelemetry.java`
- `F04` = `app/src/main/java/com/togg/companion/core/ToggAuthManager.java`
- `F05` = `app/src/main/java/com/togg/companion/core/TrumoreBridge.java`
- `F06` = `app/src/main/java/com/togg/companion/data/SecureTokenStorage.java`
- `F07` = `app/src/main/java/com/togg/companion/data/TelemetryDatabaseHelper.java`
- `F08` = `app/src/main/java/com/togg/companion/data/remote/TvaApiClient.java`
- `F09` = `app/src/main/java/com/togg/companion/ui/MainActivity.java`
- `F10` = `app/src/main/java/com/togg/companion/ui/ToggActionActivity.java`
- `F11` = `app/src/main/java/com/togg/companion/ui/ToggActionReceiver.java`
- `F12` = `app/src/main/java/com/togg/companion/ui/ToggLoginActivity.java`
- `F13` = `app/src/main/java/com/togg/companion/wearable/PhoneWearListenerService.java`
- `F14` = `app/src/main/java/com/togg/companion/wearable/WearSyncManager.java`
- `F15` = `wear/src/main/AndroidManifest.xml`
- `F16` = `wear/src/main/java/com/togg/companion/wear/MainActivity.java`
- `F17` = `wear/src/main/java/com/togg/companion/wear/ToggTileService.java`
- `F18` = `wear/src/main/java/com/togg/companion/wear/WatchDataStore.java`
- `F19` = `wear/src/main/java/com/togg/companion/wear/WatchTelemetry.java`
- `F20` = `wear/src/main/java/com/togg/companion/wear/WatchWearableListenerService.java`
- `F21` = `app/src/main/res/values/strings.xml`
- `F22` = `app/src/main/res/values/colors.xml`
- `F23` = `app/src/main/res/values/styles.xml`
- `F24` = `app/src/main/res/layout/activity_main.xml`
- `F25` = `app/src/main/res/layout/activity_togg_login.xml`
- `F26` = `app/src/main/res/layout/dialog_settings.xml`
- `F27` = `app/src/main/res/xml/shortcuts.xml`
- `F28` = `wear/src/main/res/values/strings.xml`
- `F29` = `wear/src/main/res/values/colors.xml`
- `F30` = `wear/src/main/res/layout/activity_main.xml`
- `F31` = `app/src/test/java/com/togg/companion/core/TelemetryParserTest.java`
- `F32` = `docs/togg-companion/IDMAP.md`
- `F33` = `docs/togg-companion/KG.adj`
- `F34` = `docs/togg-companion/FACTS.jsonl`
- `F35` = `docs/KB_INDEX.md`
- `F36` = `scripts/validate-kb.cjs`
- `F37` = `app/build.gradle.kts`
- `F38` = `wear/build.gradle.kts`
- `F39` = `README.md`
- `F40` = `app/src/main/java/com/togg/companion/core/DigitalKey.java`
- `F41` = `app/src/main/java/com/togg/companion/core/DigitalKeyManager.java`
- `F42` = `app/src/test/java/com/togg/companion/core/DigitalKeyTest.java`
- `F43` = `app/src/main/res/drawable/car_t10f_kula.jpg`
- `F44` = `app/src/main/res/drawable/glow_headlights.xml`
- `F45` = `app/src/main/res/drawable/bg_map_frame.xml`
- `F46` = `app/src/main/java/com/togg/companion/widget/ToggAppWidgetProvider.java`
- `F47` = `app/src/main/res/layout/widget_togg.xml`
- `F48` = `app/src/main/res/xml/widget_togg_info.xml`
- `F49` = `app/src/main/res/drawable/bg_glass_widget.xml`
- `F50` = `app/src/main/java/com/togg/companion/core/VehicleBleManager.java`
- `F51` = `app/src/main/res/xml/network_security_config.xml`
- `F52` = `app/proguard-rules.pro`
- `F53` = `app/src/main/res/drawable/btn_action_circle.xml`
- `F54` = `app/src/main/res/drawable/btn_action_circle_active.xml`
- `F55` = `app/src/main/res/drawable/tire_subcard_bg.xml`
- `F56` = `app/src/main/res/drawable/tire_subcard_low.xml`
- `F57` = `app/src/main/res/values-night/`
- `F58` = `app/src/main/java/com/togg/companion/data/remote/AbrpClient.java`
- `F59` = `app/src/main/java/com/togg/companion/service/AbrpJobService.java`
- `F60` = `app/src/test/java/com/togg/companion/data/remote/AbrpClientTest.java`
- `F61` = `app/src/main/java/com/togg/companion/core/GezRouteBridge.java`
- `F62` = `app/src/test/java/com/togg/companion/core/GezRouteBridgeTest.java`
- `F63` = `app/src/test/java/com/togg/companion/core/VehicleBleManagerTest.java`

## Entity IDs

- `A0` = Phone Android companion application
- `W0` = Wear OS smartwatch application
- `C1` = Telemetry parser and vehicle telemetry data model
- `C2` = Authentication and secure token storage
- `C3` = TVA remote API client and network communication
- `C4` = Trumore Bridge deep linking and package dispatcher
- `C5` = SQLite database telemetry caching and persistence
- `C6` = Trumore Digital Key domain model, redemption parser, and persistence
- `S1` = Wearable Data Layer sync between phone and watch
- `U1` = Phone dashboard UI and telemetry view lifecycle
- `U2` = Headless action dispatcher and automation receiver
- `U3` = Login activity and credential management
- `U4` = Galaxy Watch UI, telemetry display, and compass navigation
- `U5` = Wear OS Tile quick glance provider
- `T1` = Telemetry parser JVM unit tests
- `T2` = Knowledge Base validation script
- `T3` = Digital Key unit tests
- `D0` = Android layout, color, and string XML resources
- `U6` = Resizable Android home screen vehicle telemetry widget
- `U7` = Home dashboard trip card, odometer, and dual update timestamp UI
- `C7` = Vehicle BLE connection and command feedback boundary
- `C8` = ABRP telemetry client, secure preferences, and periodic background sync
- `T4` = ABRP telemetry JSON unit tests
- `C9` = Trumore Gez route parsing and deep link dispatch
- `T5` = Gez route bridge unit tests
- `T6` = Vehicle BLE manager readiness unit tests

## Predicate vocabulary

- `declares`
- `parses`
- `stores`
- `communicates`
- `dispatches`
- `renders`
- `synchronizes`
- `validates`
