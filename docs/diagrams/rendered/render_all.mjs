import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';

const root = 'docs';
const outDir = 'docs/diagrams/rendered';
fs.mkdirSync(outDir, { recursive: true });

function walk(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...walk(p));
    else if (entry.name.endsWith('.md')) out.push(p);
  }
  return out;
}
function encode(s) {
  return zlib.deflateSync(Buffer.from(s, 'utf8')).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}
const files = walk(root).sort();
const results = [];
for (const file of files) {
  const text = fs.readFileSync(file, 'utf8');
  const re = /```(mermaid|plantuml)\n([\s\S]*?)```/g;
  let m, i = 0;
  while ((m = re.exec(text)) !== null) {
    i++;
    const type = m[1] === 'plantuml' ? 'plantuml' : 'mermaid';
    const code = m[2].trim();
    const encoded = encode(code);
    const base = file.replace(/[\\/]/g, '__').replace(/\.md$/, '') + '__' + i;
    const svgPath = path.join(outDir, base + '.svg');
    const pngPath = path.join(outDir, base + '.png');
    try {
      const [svgRes, pngRes] = await Promise.all([
        fetch('https://kroki.io/' + type + '/svg/' + encoded),
        fetch('https://kroki.io/' + type + '/png/' + encoded),
      ]);
      const svg = await svgRes.text();
      const png = Buffer.from(await pngRes.arrayBuffer());
      const svgBad = !svgRes.ok || /Syntax Error|Parse error|Cannot parse|No diagram/i.test(svg);
      const pngBad = !pngRes.ok || png.length < 500;
      if (!svgBad) fs.writeFileSync(svgPath, svg);
      if (!pngBad) fs.writeFileSync(pngPath, png);
      results.push({ file, index: i, type, svg: svgBad ? 'FAIL' : 'OK', png: pngBad ? 'FAIL' : 'OK', pngBytes: png.length });
    } catch (e) {
      results.push({ file, index: i, type, svg: 'ERROR', png: 'ERROR', error: String(e) });
    }
  }
}
fs.writeFileSync(path.join(outDir, 'manifest.json'), JSON.stringify(results, null, 2));
console.log('TOTAL', results.length, 'SVG_OK', results.filter(r=>r.svg==='OK').length, 'PNG_OK', results.filter(r=>r.png==='OK').length);
for (const r of results.filter(r => r.svg !== 'OK' || r.png !== 'OK')) console.log(JSON.stringify(r));
