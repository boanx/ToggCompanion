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

- Use for: Togg T10F phone companion (`app/`) and Samsung Galaxy Watch Wear OS app (`wear/`). Covers vehicle telemetry parsing, vehicle BLE connection and command gating, secure token storage, SQLite caching, Trumore/TVA API client, headless actions & deep links, Wear OS Tile provider, Wearable Data Layer sync, and UI screens.
- Primary KB: `docs/togg-companion`; read `IDMAP.md`, then `KG.adj`, then matching `FACTS.jsonl` records.
- Secondary folders: none; this project currently has one consolidated feature ecosystem.
- Bridge files: none. Phone and watch components communicate over the Wearable Data Layer protocol documented in this KB.
- Standalone verification entry points: `app/src/test/java/com/togg/companion/core/TelemetryParserTest.java` and `scripts/validate-kb.cjs`.

## Cross-Feature Dependencies

None registered yet.

## Bridge Files

None registered yet.
