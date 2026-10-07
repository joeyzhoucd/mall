#!/usr/bin/env node
// =============================================================================
// 每个 Feign 方法指向的提供方接口，必须标 @InternalApi（缺陷 #5 第二层）
// =============================================================================
// 为什么要有这个检查：服务端对 @InternalApi 接口校验 X-Internal-Token，没标的接口不校验。
// 新加一个 Feign 调用而忘了在提供方标注解 —— 在 enforce 模式下不会出错（没标就不拦），
// 但那个接口就又回到「集群里谁都能调」的状态，而且没有任何信号。所以在 CI 里拦。
//
// 【先剔掉注释再解析】2026-10-07 踩过：一个 controller 的类注释里写了 {@code @InternalApi} 这几个字，
// 类级检测把它当成了注解，那个类的【所有】接口都被判为已标注 —— 这是会放过漏标的方向（假通过）。
// 所以下面读文件时先去掉块注释和整行 // 注释（不动行尾 //：字符串里的 "http://" 会被误剪）。
//
// 解析是正则级别的（不是完整 Java 语法），所以刻意让它只会【误报】不会【漏报】：
//   - 找不到 Feign 方法对应的处理方法 -> 失败（不允许「什么都没比对到所以通过」）
//   - 注解范围取「上一个成员结束 ~ 方法签名」；如果中间有带花括号参数的注解把范围截断，
//     结果是报「未标注」（CI 变红），不会把漏标放过去。遇到这种误报，把 @InternalApi 紧挨着 mapping 注解写即可。
//
// 用法：node .github/scripts/check-internal-api.js   （在 mall-backend 根目录；退出码 0 = 全部已标注）
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');

function walk(d, out = []) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (p.endsWith('.java')) out.push(p);
  }
  return out;
}

const norm = p => ('/' + p).replace(/\{[^}]*\}/g, '{}').replace(/\/+/g, '/').replace(/(.)\/$/, '$1');
// 注解参数里的全部路径字符串：@GetMapping("/a")、@PostMapping({"/a","/b"})、@RequestMapping(value = "/a", method = ...)
function mappingPaths(args) {
  if (args === undefined || args.trim() === '') return [''];
  const named = args.match(/(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")/);
  const part = named ? named[1] : (/^\s*[{"]/.test(args) ? args.match(/^\s*(\{[^}]*\}|"[^"]*")/)[1] : '');
  const strs = [...part.matchAll(/"([^"]*)"/g)].map(m => m[1]);
  return strs.length ? strs : [''];
}
function httpMethod(kind, args) {
  if (kind !== 'Request') return kind.toUpperCase();
  const m = (args || '').match(/RequestMethod\.(\w+)/);
  return m ? m[1] : 'ANY';
}
// 去掉块注释（含 Javadoc）和整行 // 注释，见文件头说明
const stripComments = src => src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^[ \t]*\/\/.*$/gm, '');
const MAPPING = /@(Get|Post|Put|Delete|Patch|Request)Mapping\b(?:\(([^)]*)\))?/g;

// ---- 提供方：module -> [{ method, path, annotated, where }]
const handlers = {};
for (const mod of fs.readdirSync(ROOT).filter(d => d.startsWith('mall-'))) {
  const src = path.join(ROOT, mod, 'src/main/java');
  if (!fs.existsSync(src)) continue;
  for (const f of walk(src)) {
    const s = stripComments(fs.readFileSync(f, 'utf8'));
    if (!/@(Rest)?Controller\b/.test(s)) continue;
    const classAt = s.search(/\bclass\s+\w+/);
    if (classAt < 0) continue;
    const head = s.slice(0, classAt);
    const classInternal = /@InternalApi\b/.test(head);
    const classPaths = (() => {
      const m = [...head.matchAll(/@RequestMapping\b(?:\(([^)]*)\))?/g)].pop();
      return m ? mappingPaths(m[1]) : [''];
    })();
    const bodyStart = s.indexOf('{', classAt);
    MAPPING.lastIndex = bodyStart;
    let m;
    while ((m = MAPPING.exec(s))) {
      const start = Math.max(s.lastIndexOf('}', m.index), s.lastIndexOf(';', m.index), bodyStart);
      const sig = s.slice(m.index).search(/\b(public|protected|private)\b[^;{=]*?\(/);
      if (sig < 0) continue;
      const chunk = s.slice(start, m.index + sig);
      const annotated = classInternal || /@InternalApi\b/.test(chunk);
      const fn = (s.slice(m.index + sig).match(/(\w+)\s*\(/) || [, '?'])[1];
      for (const cp of classPaths) {
        for (const mp of mappingPaths(m[2])) {
          (handlers[mod] = handlers[mod] || []).push({
            method: httpMethod(m[1], m[2]),
            path: norm(cp + '/' + mp),
            annotated,
            where: `${path.relative(ROOT, f).replace(/\\/g, '/')}#${fn}`,
          });
        }
      }
    }
  }
}

// ---- 调用方：每个 Feign 方法（解析方式同 feign 清点脚本）
const calls = [];
for (const mod of fs.readdirSync(ROOT).filter(d => d.startsWith('mall-'))) {
  const src = path.join(ROOT, mod, 'src/main/java');
  if (!fs.existsSync(src)) continue;
  for (const f of walk(src)) {
    const s = stripComments(fs.readFileSync(f, 'utf8'));
    const fc = s.match(/@FeignClient\(([^)]*)\)/);
    if (!fc) continue;
    const target = (fc[1].match(/(?:name|value)\s*=\s*"([^"]+)"/) || fc[1].match(/^\s*"([^"]+)"/) || [])[1];
    const base = (fc[1].match(/path\s*=\s*"([^"]*)"/) || [, ''])[1];
    const ifaceAt = s.search(/\binterface\b/);
    const cls = (s.slice(0, ifaceAt).match(/@RequestMapping\(\s*(?:value\s*=\s*)?"([^"]*)"/) || [, ''])[1];
    MAPPING.lastIndex = ifaceAt;
    let m;
    while ((m = MAPPING.exec(s))) {
      const fn = (s.slice(m.index + m[0].length).match(/(\w+)\s*\(/) || [, '?'])[1];
      for (const mp of mappingPaths(m[2])) {
        calls.push({
          caller: `${mod}/${path.basename(f, '.java')}.${fn}`,
          target,
          method: httpMethod(m[1], m[2]),
          path: norm(base + '/' + cls + '/' + mp),
        });
      }
    }
  }
}

// ---- 比对
const problems = [];
let ok = 0;
for (const c of calls) {
  const cands = (handlers[c.target] || []).filter(h => h.path === c.path
    && (h.method === 'ANY' || c.method === 'ANY' || h.method === c.method));
  const label = `${c.method.padEnd(6)} ${c.target}${c.path}  <- ${c.caller}`;
  if (!cands.length) problems.push(`找不到处理方法  ${label}`);
  else if (!cands.every(h => h.annotated)) problems.push(`未标 @InternalApi  ${label}  -> ${cands.filter(h => !h.annotated).map(h => h.where).join(', ')}`);
  else ok++;
}
const internalCount = Object.values(handlers).flat().filter(h => h.annotated).length;
console.log(`Feign 方法 ${calls.length} 个：已标注 ${ok}，有问题 ${problems.length}；@InternalApi 处理方法共 ${internalCount} 个`);
if (calls.length === 0) {
  console.log('!! 一个 Feign 方法都没解析到 —— 解析本身坏了，不能当作通过');
  process.exit(1);
}
for (const p of problems) console.log('  ' + p);
process.exit(problems.length ? 1 : 0);
