#!/usr/bin/env node
// =============================================================================
// 网关白名单 ↔ @PublicApi 必须一致（服务端默认拒绝，2026-10-07）
// =============================================================================
// 服务端对接口默认拒绝：既不是 @InternalApi 也不是 @PublicApi 的接口要管理端 JWT（AdminTokenInterceptor）。
// 于是「公网能到哪些接口」有了两份事实：网关的前台路由白名单，和代码里的 @PublicApi。两份不一致都是事故：
//   1. 白名单放行了、方法没标 @PublicApi  -> enforce 之后这个前台页面/AJAX 直接 401
//   2. 方法标了 @PublicApi、白名单没放行  -> 被当成公开接口免了鉴权，却没人打算公开它（或白名单漏了）
// 这个脚本按【方法】双向比对（一个方法常映射多条路径，如 "/" 和 "/list.html"，任一条被放行即算一致）。
//
// 放行来源：mall-gateway application.yml 里非 /api 的路由（Path 列表 + uri 指向的服务），
// 加上 AdminAuthFilter.EXEMPT_PATHS（/api/sys/login、/api/captcha.jpg，去掉 /api 前缀后归属对应服务）。
// 和 check-internal-api.js 一样先剥注释、正则级解析、带正向对照（解析坏了直接失败，而不是「什么都没比所以通过」）。
//
// 用法：node .github/scripts/check-public-api.js   （在 mall-backend 根目录；退出码 0 = 一致）
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
const stripComments = src => src.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^[ \t]*\/\/.*$/gm, '');
const norm = p => ('/' + p).replace(/\{[^}]*\}/g, '{}').replace(/\/+/g, '/').replace(/(.)\/$/, '$1');
function mappingPaths(args) {
  if (args === undefined || args.trim() === '') return [''];
  const named = args.match(/(?:value|path)\s*=\s*(\{[^}]*\}|"[^"]*")/);
  const part = named ? named[1] : (/^\s*[{"]/.test(args) ? args.match(/^\s*(\{[^}]*\}|"[^"]*")/)[1] : '');
  const strs = [...part.matchAll(/"([^"]*)"/g)].map(m => m[1]);
  return strs.length ? strs : [''];
}
const MAPPING = /@(Get|Post|Put|Delete|Patch|Request)Mapping\b(?:\(([^)]*)\))?/g;

// ---- handlers grouped by method: key = module/File#fn
const methods = new Map();
for (const mod of fs.readdirSync(ROOT).filter(d => d.startsWith('mall-'))) {
  const src = path.join(ROOT, mod, 'src/main/java');
  if (!fs.existsSync(src)) continue;
  for (const f of walk(src)) {
    const s = stripComments(fs.readFileSync(f, 'utf8'));
    if (!/@(Rest)?Controller\b/.test(s)) continue;
    const classAt = s.search(/\bclass\s+\w+/);
    if (classAt < 0) continue;
    const head = s.slice(0, classAt);
    // 全限定写法 @com.mall.common.annotation.PublicApi 运行时同样生效，也必须认出来 ——
    // 只认短名的话，用全限定名标在后台接口上的 @PublicApi 会绕过「标了但网关没放行」这一侧（实测过）
    const classPublic = /@(?:[\w.]+\.)?PublicApi\b/.test(head);
    const classInternal = /@(?:[\w.]+\.)?InternalApi\b/.test(head);
    const cm = [...head.matchAll(/@RequestMapping\b(?:\(([^)]*)\))?/g)].pop();
    const classPaths = cm ? mappingPaths(cm[1]) : [''];
    const bodyStart = s.indexOf('{', classAt);
    MAPPING.lastIndex = bodyStart;
    let m;
    while ((m = MAPPING.exec(s))) {
      const start = Math.max(s.lastIndexOf('}', m.index), s.lastIndexOf(';', m.index), bodyStart);
      const sig = s.slice(m.index).search(/\b(public|protected|private)\b[^;{=]*?\(/);
      if (sig < 0) continue;
      const chunk = s.slice(start, m.index + sig);
      const fn = (s.slice(m.index + sig).match(/(\w+)\s*\(/) || [, '?'])[1];
      const key = `${mod}/${path.basename(f, '.java')}#${fn}`;
      const e = methods.get(key) || { mod, key, paths: [],
        isPublic: classPublic || /@(?:[\w.]+\.)?PublicApi\b/.test(chunk),
        isInternal: classInternal || /@(?:[\w.]+\.)?InternalApi\b/.test(chunk) };
      for (const cp of classPaths) for (const mp of mappingPaths(m[2])) e.paths.push(norm(cp + '/' + mp));
      methods.set(key, e);
    }
  }
}

// ---- gateway: storefront routes (svc + non-/api Path patterns) and admin exempt paths
const yml = fs.readFileSync(path.join(ROOT, 'mall-gateway/src/main/resources/application.yml'), 'utf8').split(/\r?\n/);
const routes = []; let cur = null;
for (const line of yml) {
  const id = line.match(/^\s+- id:\s*(\S+)/); if (id) { cur = { id: id[1], svc: null, paths: [] }; routes.push(cur); continue; }
  if (!cur) continue;
  const uri = line.match(/^\s+uri:\s*lb:\/\/(\S+)/); if (uri) cur.svc = uri[1];
  const p = line.match(/^\s+- Path=(.+)$/); if (p) cur.paths.push(...p[1].split(',').map(x => x.trim()));
}
const toRe = pat => new RegExp('^' + norm(pat.replace(/\{[^}:]*:[^}]*\}/g, '{}'))
  .replace(/[.+?^$()|[\]\\]/g, '\\$&').replace(/\*\*/g, '\u0000').replace(/\*/g, '[^/]*')
  .replace(/\u0000/g, '.*').replace(/\{\}/g, '[^/]+') + '$');
const allowed = []; // { svc, re, src }
for (const r of routes) {
  if (!r.svc) continue;
  for (const p of r.paths) if (!p.startsWith('/api')) allowed.push({ svc: r.svc, re: toRe(p), src: `${r.id} ${p}` });
}
const filter = fs.readFileSync(path.join(ROOT, 'mall-gateway/src/main/java/com/mall/gateway/filter/AdminAuthFilter.java'), 'utf8');
const exempt = [...((filter.match(/EXEMPT_PATHS\s*=\s*Set\.of\(([^)]*)\)/) || [, ''])[1]).matchAll(/"([^"]+)"/g)].map(m => m[1]);
for (const p of exempt) {
  const r = routes.find(r => r.svc && r.paths.some(x => x.startsWith('/api') && toRe(x).test(p)));
  if (r) allowed.push({ svc: r.svc, re: toRe(p.replace(/^\/api/, '')), src: `AdminAuthFilter exempt ${p}` });
}
const reachable = e => e.paths.some(p => allowed.some(a => a.svc === e.mod && a.re.test(p.replace(/\{\}/g, 'X'))));

// ---- positive controls
const all = [...methods.values()];
const controls = [
  ['parsed >= 300 handler methods', all.length >= 300],
  ['parsed storefront routes', allowed.filter(a => !a.src.startsWith('AdminAuthFilter')).length >= 20],
  ['parsed AdminAuthFilter exempt paths (login + captcha)', exempt.length >= 2],
  ['/order/payment.html is reachable', all.some(e => e.paths.includes('/order/payment.html') && reachable(e))],
  ['/sys/login is reachable via exempt', all.some(e => e.mod === 'mall-admin' && e.paths.includes('/sys/login') && reachable(e))],
  ['/product/brand/list is NOT reachable', all.some(e => e.paths.includes('/product/brand/list') && !reachable(e))],
];
const failedControls = controls.filter(([, ok]) => !ok);
if (failedControls.length) {
  for (const [label] of failedControls) console.log('!! 正向对照失败（解析坏了，不能当作通过）: ' + label);
  process.exit(2);
}

// ---- compare
const problems = [];
for (const e of all) {
  if (e.isInternal) continue;
  const r = reachable(e);
  if (r && !e.isPublic) problems.push(`网关放行但未标 @PublicApi（enforce 后会 401）  ${e.key}  ${e.paths.join(' ')}`);
  if (!r && e.isPublic) problems.push(`标了 @PublicApi 但网关没放行  ${e.key}  ${e.paths.join(' ')}`);
}
console.log(`处理方法 ${all.length}，@PublicApi ${all.filter(e => e.isPublic).length}，网关放行 ${all.filter(e => !e.isInternal && reachable(e)).length}，不一致 ${problems.length}`);
for (const p of problems) console.log('  ' + p);
process.exit(problems.length ? 1 : 0);
