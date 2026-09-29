// KB_PROTOCOL.md validation script for ToggCompanion
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const root = path.resolve(__dirname, '..');
const read = file => fs.readFileSync(path.join(root, file), 'utf8');
const folder = 'docs/togg-companion/';

const lines = read(folder + 'FACTS.jsonl').trim().split(/\n+/);
for (let i = 0; i < lines.length; i++) JSON.parse(lines[i]);
console.log('jsonl ok', lines.length);

const facts = lines.map(JSON.parse);
const map = read(folder + 'IDMAP.md');
const fileMappings = [...map.matchAll(/^- `(F\d+)` = `([^`]+)`( \[retired\])?$/gm)];
const declaredFiles = [...map.matchAll(/^- `(F\d+)` =/gm)];
assert.equal(fileMappings.length, declaredFiles.length, 'invalid file mapping syntax');

const fIds = new Set(fileMappings.map(m => m[1]));
assert.equal(fIds.size, fileMappings.length, 'duplicate file IDs');
assert.equal(new Set(fileMappings.map(m => m[2])).size, fileMappings.length, 'duplicate file paths');
console.log('file IDs unique', fIds.size);

const bad = [];
for (const fact of facts) {
  assert.ok(fact.src && fact.src.length > 0, `fact ${fact.id} has empty src`);
  for (const src of fact.src || []) {
    if (!fIds.has(src)) bad.push([fact.id, src]);
  }
}
assert.deepEqual(bad, [], 'bad src ids');
console.log('src ids ok', facts.length);

const ids = new Set([...map.matchAll(/`([A-Z]\d+)`/g)].map(m => m[1]));
const graph = read(folder + 'KG.adj').split(/\n+/).filter(l => l && !l.startsWith('#'));
const missing = [];
for (const line of graph) {
  for (const token of line.replace(/[,]/g, ' ').split(/\s+/)) {
    if (/^[A-Z]\d+$/.test(token) && !ids.has(token)) missing.push([line, token]);
  }
}
assert.deepEqual(missing, [], 'missing ids');
console.log('kg ids ok', graph.length);

const seen = new Set();
const dup = [];
for (const fact of facts) {
  if (seen.has(fact.id)) dup.push(fact.id);
  seen.add(fact.id);
  assert.ok(ids.has(fact.s), `unknown fact subject ${fact.s} in ${fact.id}`);
}
assert.deepEqual(dup, [], 'duplicate AF ids');
console.log('af ids unique', facts.length);

assert.ok(read('docs/KB_INDEX.md').includes('docs/togg-companion'), 'missing route in docs/KB_INDEX.md');

for (const match of fileMappings) {
  const exists = fs.existsSync(path.join(root, match[2]));
  assert.equal(exists, !match[3], (match[3] ? 'retired source still exists ' : 'missing source ') + match[2]);
}
console.log('index route and source files ok');
