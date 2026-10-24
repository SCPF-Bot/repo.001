import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

const SOURCE_URL = 'https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json';
const OUTPUT_WASM = path.join(rootDir, 'anistream_store_plugin.wasm');
const OUTPUT_META = path.join(rootDir, 'anistream_store_plugin.json');

function encodeVarUint(value) {
  const bytes = [];
  while (value >= 0x80) {
    bytes.push((value & 0x7f) | 0x80);
    value = Math.floor(value / 128);
  }
  bytes.push(value);
  return Uint8Array.from(bytes);
}

function encodeString(value) {
  const bytes = new TextEncoder().encode(value);
  return new Uint8Array([...encodeVarUint(bytes.length), ...bytes]);
}

function buildCustomSection(name, payloadBytes) {
  const nameBytes = encodeString(name);
  const content = new Uint8Array([...nameBytes, ...payloadBytes]);
  return new Uint8Array([0x00, ...encodeVarUint(content.length), ...content]);
}

function buildWasm(metadata) {
  const metadataBytes = new TextEncoder().encode(JSON.stringify(metadata, null, 2));

  const typeSectionContent = new Uint8Array([
    0x01,
    0x60,
    0x00,
    0x00,
  ]);
  const typeSection = new Uint8Array([
    0x01,
    ...encodeVarUint(typeSectionContent.length),
    ...typeSectionContent,
  ]);

  const funcSectionContent = new Uint8Array([
    0x01,
    0x00,
  ]);
  const funcSection = new Uint8Array([
    0x03,
    ...encodeVarUint(funcSectionContent.length),
    ...funcSectionContent,
  ]);

  const body = new Uint8Array([0x00, 0x0b]);
  const codeSectionContent = new Uint8Array([
    0x01,
    ...encodeVarUint(body.length + 1),
    ...encodeVarUint(body.length),
    ...body,
  ]);
  const codeSection = new Uint8Array([
    0x0a,
    ...encodeVarUint(codeSectionContent.length),
    ...codeSectionContent,
  ]);

  const exportSectionContent = new Uint8Array([
    0x01,
    ...encodeString('plugin_init'),
    0x00,
    0x00,
  ]);
  const exportSection = new Uint8Array([
    0x07,
    ...encodeVarUint(exportSectionContent.length),
    ...exportSectionContent,
  ]);

  const customSection = buildCustomSection('plugin_meta', metadataBytes);
  const module = new Uint8Array([
    0x00, 0x61, 0x73, 0x6d,
    0x01, 0x00, 0x00, 0x00,
    ...customSection,
    ...typeSection,
    ...funcSection,
    ...exportSection,
    ...codeSection,
  ]);

  return module;
}

async function main() {
  const response = await fetch(SOURCE_URL, { headers: { 'Accept': 'application/json' } });
  if (!response.ok) {
    throw new Error(`Failed to fetch ${SOURCE_URL}: ${response.status} ${response.statusText}`);
  }

  const sourceText = await response.text();
  const parsed = JSON.parse(sourceText);
  const count = Array.isArray(parsed) ? parsed.length : 0;

  const metadata = {
    name: 'anistream_store_plugin',
    source_url: SOURCE_URL,
    source_count: count,
    note: 'Minimal manual-install WASM stub for AniStream plugin stores.',
    generated_at: new Date().toISOString(),
    kind: 'manual-install',
    compatibility: 'AniStream plugin directory / manual placement only',
  };

  const wasmBytes = buildWasm(metadata);

  await fs.writeFile(OUTPUT_WASM, Buffer.from(wasmBytes));
  await fs.writeFile(OUTPUT_META, JSON.stringify(metadata, null, 2) + '\n');

  console.log(`Generated ${OUTPUT_WASM}`);
  console.log(`Generated ${OUTPUT_META}`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
