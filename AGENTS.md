# Repository Agent Contract

All agents working in this repository must follow:

- `docs/KB_PROTOCOL.md`
- `docs/KB_INDEX.md`

## Knowledge Base Selection

Before using feature knowledge:

1. Read `docs/KB_INDEX.md`.
2. Select the smallest relevant KB context.
3. Prefer exactly one primary KB.
4. Add secondary KBs only when the index documents a relevant boundary.
5. Never load every KB folder by default.
6. For each selected KB:
   - read `IDMAP.md` first;
   - use `KG.adj` to identify the relevant neighborhood;
   - retrieve only matching atomic facts from `FACTS.jsonl`.
7. Cite implementation evidence using AF IDs and F IDs.
8. Never invent undocumented repository behavior.

If evidence is insufficient, report:

MISSING: <required fact>

If evidence conflicts, report:

CONFLICT: <conflicting AF IDs>

If routing is inadequate, report:

MISSING_INDEX_ROUTE: <missing or misleading route>

RECOMMENDED_INDEX_UPDATE: <required index change>

## Implementation Rules

When repository behavior changes:

- make the smallest implementation change necessary;
- do not rewrite unrelated code;
- preserve existing KB IDs;
- never renumber existing IDs;
- update the relevant KB when represented behavior changes;
- update `docs/KB_INDEX.md` when routing or scope changes;
- run applicable tests;
- run the KB validation commands from `docs/KB_PROTOCOL.md` whenever KB files change.

If an existing fact becomes incorrect, follow the corrective-fact policy in
`docs/KB_PROTOCOL.md`. Do not silently rewrite history.

## Repository Infrastructure Exception

Repository-meta work such as:

- `.gitignore`
- `AGENTS.md`
- `.agents/`
- MCP configuration
- KB infrastructure
- documentation infrastructure

does not require a feature KB route unless the change also defines or alters
application behavior.

## Completion Report

Implementation work must finish with:

STATUS:
FILES_CHANGED:
IMPLEMENTATION:
TESTS_RUN:
TEST_RESULTS:
KB_CHANGED:
NEW_IDS:
NEW_AF_IDS:
INDEX_UPDATE:
EVIDENCE_USED:
MISSING:
CONFLICT:
NOTES:
