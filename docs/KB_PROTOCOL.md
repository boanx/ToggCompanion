\# KB Maintenance Protocol



Use this guide when asking an LLM to update the <feature-slug> knowledge base files:



\- `docs/KB\_INDEX.md`

\- `docs/<feature-slug>/IDMAP.md`

\- `docs/<feature-slug>/KG.adj`

\- `docs/<feature-slug>/FACTS.jsonl`



\## KB Index Policy

\- `docs/KB\_INDEX.md` is the router for KB selection.

\- Always use the index before loading feature KB files into context.

\- Do not load all KB folders by default.

\- Update `docs/KB\_INDEX.md` whenever KB routing would otherwise become stale or misleading.



Update the index when:

\- a new KB folder is added

\- an existing folder scope changes materially

\- a new cross-feature dependency becomes important for retrieval

\- a new bridge file becomes a common multi-KB entry point

\- a file/path named in the index moves or is renamed



\## Prompt for choosing KB context with the index

```text

Use `docs/KB\_INDEX.md` first to choose the smallest relevant KB context set for this task.



Protocol (must follow):

1\. Read `docs/KB\_INDEX.md`.

2\. Select one primary KB folder from the router.

3\. Add secondary KB folders only if the task crosses a boundary listed in the index.

4\. If the task touches a bridge file listed in the index, check whether the task is multi-KB.

5\. Then load only the selected KB folders using the KB loading protocol below.

6\. If no listed folder cleanly matches, return:

&#x20;  - MISSING\_INDEX\_ROUTE: what routing entry is missing

&#x20;  - RECOMMENDED\_INDEX\_UPDATE: the new or changed entry needed

```



\## Prompt for loading KB to context

```

Use `docs/KB\_INDEX.md` first, then load the selected KB context.



Selected KB folders:

\- docs/<feature-slug>

\- docs/<secondary-feature-slug>  # only if needed



For each selected folder, use:

\- docs/<feature-slug>/IDMAP.md

\- docs/<feature-slug>/KG.adj

\- docs/<feature-slug>/FACTS.jsonl



Protocol (must follow):

1\. Read `docs/KB\_INDEX.md` first and choose the smallest relevant folder set.

2\. In each selected folder, read IDMAP first (IDs and meaning).

3\. Use KG.adj to find relevant node neighborhood for the task.

4\. Pull only matching atomic facts from FACTS.jsonl.

5\. If multiple KB folders are loaded, merge only the facts required to answer the task.

6\. Answer strictly from those facts; do not invent behavior.

7\. Cite evidence as AF IDs (and F IDs when relevant).

8\. If facts are missing/conflicting, return:

&#x20;  - MISSING: what fact is needed

&#x20;  - CONFLICT: conflicting AF IDs

9\. If the task exposed an index routing gap, also return:

&#x20;  - MISSING\_INDEX\_ROUTE: what routing rule is absent or misleading

10\. Keep response token-efficient and implementation-focused.

```



\## Prompt for creating a new feature KB

```text

Create a new feature knowledge base for: <FeatureName>



Output folder:

\- docs/<feature-slug>/



Create these files:

\- docs/<feature-slug>/IDMAP.md

\- docs/<feature-slug>/KG.adj

\- docs/<feature-slug>/FACTS.jsonl

\- update `docs/KB\_INDEX.md`



Primary sources (must inspect first):

\- <source file/path 1>

\- <source file/path 2>

\- <source file/path 3>



Optional design/spec sources (if feature is not fully implemented):

\- <spec doc path 1>

\- <spec doc path 2>

\- Decisions provided in this prompt:

&#x20; 1) <decision>

&#x20; 2) <decision>



Rules:

1\. Build a token-efficient KB using:

&#x20;  - ID-based knowledge graph adjacency list (`KG.adj`)

&#x20;  - atomic fact records (`FACTS.jsonl`)

2\. Read only relevant files (start from the primary sources, then follow imports/usages as needed).

3\. Separate implemented facts from design-only facts.

4\. Every fact must cite source IDs in `src`.

5\. Do not invent behavior. If unknown, emit explicit MISSING facts or a gap list.

6\. Keep IDs stable and compact:

&#x20;  - `Fxx` for files

&#x20;  - short entity IDs (`A0`, `C1`, `U1`, `D1`, etc.)

&#x20;  - `AF###` for facts

7\. Prefer atomic facts (one claim per line).

8\. Include runtime precedence/interaction facts where relevant (state -> UI -> runtime -> persistence).

9\. Include tests coverage facts if tests exist.

10\. Add a routing entry to `docs/KB\_INDEX.md` for the feature:

&#x20;  - one-line "Use for"

&#x20;  - when to add secondary folders

&#x20;  - bridge-file guidance if needed



FACTS.jsonl format (strict JSONL):

{"id":"AF###","s":"<ID>","p":"<predicate>","o":"<object>","src":\["F.."]}



Required sections in IDMAP.md:

\- Scope

\- File IDs

\- Entity IDs

\- Predicate vocabulary

\- Notes



Required validation before finishing:

1\. FACTS.jsonl parses as valid JSON per line

2\. All `src` file IDs in facts exist in IDMAP

3\. All IDs used in KG.adj exist in IDMAP

4\. No duplicate AF IDs

5\. `docs/KB\_INDEX.md` includes the new feature and correct routing guidance



Output summary:

1\. What was created

2\. New file IDs

3\. Entity ID categories used

4\. Fact count

5\. Index updates made

6\. Gaps / MISSING items

```



\## Prompt for updating KB

```text

Update the <feature-slug> KB in:

\- docs/<feature-slug>/IDMAP.md

\- docs/<feature-slug>/KG.adj

\- docs/<feature-slug>/FACTS.jsonl

\- update `docs/KB\_INDEX.md` if routing changed



Input decisions/facts to encode:

1\) <decision 1>

2\) <decision 2>



Evidence files:

\- <file path A>

\- <file path B>



Rules:

1\. Do not rewrite unrelated content.

2\. Keep existing IDs stable. Never renumber existing F/A/C/etc IDs.

3\. If new source files are needed, add new Fxx IDs in IDMAP.

4\. If new concepts are needed, add new entity IDs in IDMAP.

5\. Update KG.adj with minimal new edges reflecting only the input decisions.

6\. Append new atomic facts to FACTS.jsonl using the next AF IDs.

7\. Fact format must stay JSONL:

&#x20;  {"id":"AF###","s":"...","p":"...","o":"...","src":\["F.."]}.

8\. If an old fact is now wrong, add a corrective fact (do not silently delete history).

9\. Update `docs/KB\_INDEX.md` if the feature scope, cross-feature routing, bridge files, or referenced paths changed.

10\. If evidence is missing or conflicting, return MISSING/CONFLICT and stop.



Validation required:

1\. FACTS.jsonl parses as valid JSON per line.

2\. All src F IDs in FACTS exist in IDMAP.

3\. All node IDs in KG exist in IDMAP.

4\. No duplicate AF IDs.

5\. If routing changed, `docs/KB\_INDEX.md` was updated in the same change.



Output:

1\. What changed (short)

2\. New IDs added

3\. New AF IDs added

4\. Index updates made

5\. Any MISSING/CONFLICT

```



\## Contributor Rules

\- `docs/KB\_INDEX.md` is the canonical router for KB selection.

\- `IDMAP.md` is the canonical dictionary for IDs.

\- `KG.adj` should model structure, not prose.

\- `FACTS.jsonl` should store one atomic claim per line.

\- Prefer short `o` values, but keep them specific enough for implementation use.

\- Always include `src` with one or more `Fxx` IDs.

\- If KB scope or routing expectations changed, update the index in the same change.



\## Validation Snippets

Run from repo root.



Validate JSONL:

```bash

node -e 'const fs=require("fs");const lines=fs.readFileSync("docs/<feature-slug>/FACTS.jsonl","utf8").trim().split(/\\n+/);for(let i=0;i<lines.length;i++){JSON.parse(lines\[i])}console.log("jsonl ok",lines.length)'

```



Validate `src` IDs in facts:

```bash

node - <<'NODE'

const fs=require('fs');

const map=fs.readFileSync('docs/<feature-slug>/IDMAP.md','utf8');

const fIds=new Set(\[...map.matchAll(/`(F\\d\\d)` =/g)].map(m=>m\[1]));

const facts=fs.readFileSync('docs/<feature-slug>/FACTS.jsonl','utf8').trim().split(/\\n+/).map(JSON.parse);

const bad=\[];

for(const fact of facts){

&#x20; for(const src of (fact.src||\[])){

&#x20;   if(!fIds.has(src)) bad.push(\[fact.id,src]);

&#x20; }

}

if(bad.length){console.log('bad src ids',bad.slice(0,10));process.exit(1)}

console.log('src ids ok',facts.length);

NODE

```



Validate KG node IDs:

```bash

node - <<'NODE'

const fs=require('fs');

const map=fs.readFileSync('docs/<feature-slug>/IDMAP.md','utf8');

const ids=new Set(\[...map.matchAll(/`(\[A-Z]\\d+)`/g)].map(m=>m\[1]));

const lines=fs.readFileSync('docs/<feature-slug>/KG.adj','utf8').split(/\\n+/).filter(l=>l \&\& !l.startsWith('#'));

const missing=\[];

for(const line of lines){

&#x20; for(const token of line.replace(/\[,]/g,' ').split(/\\s+/)){

&#x20;   if(/^\[A-Z]\\d+$/.test(token) \&\& !ids.has(token)) missing.push(\[line,token]);

&#x20; }

}

if(missing.length){console.log('missing ids',missing.slice(0,10));process.exit(1)}

console.log('kg ids ok',lines.length);

NODE

```



Validate duplicate AF IDs:

```bash

node - <<'NODE'

const fs=require('fs');

const facts=fs.readFileSync('docs/<feature-slug>/FACTS.jsonl','utf8').trim().split(/\\n+/).map(JSON.parse);

const seen=new Set();

const dup=\[];

for(const fact of facts){

&#x20; if(seen.has(fact.id)) dup.push(fact.id);

&#x20; seen.add(fact.id);

}

if(dup.length){console.log('duplicate AF ids',dup);process.exit(1)}

console.log('af ids unique',facts.length);

NODE

```

