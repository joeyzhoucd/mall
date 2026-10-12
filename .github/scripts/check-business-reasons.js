#!/usr/bin/env node
// =============================================================================
// 业务指标：代码里的 (flow, reason) ↔ BusinessFlow.FAILURE_REASONS ↔ 各服务 owned-flows 必须一致（2026-10-12）
// =============================================================================
// 各服务启动时按 BusinessFlow.FAILURE_REASONS 给自己负责的流（application.yml 的
// mall.metrics.business.owned-flows）预注册 0 值计数器，零流量时序列是 0 而不是不存在。
// 三份事实不一致时都不会报错，只会悄悄退化：
//   1. 代码用了清单里没有的 reason     -> 照常计数，但那条序列零流量时不存在（又回到 No data）
//   2. 服务计数的流没写进 owned-flows  -> 这条流整条都不预注册
//   3. owned-flows 写了服务不计数的流  -> 发布一条永远是 0 的幽灵序列，面板上像「没流量」
// 所以这里静态检查三者。reason 能解析的形式：BusinessFlow 常量（含全限定）、本模块里指向它们的别名常量、
// reasonOf(ErrorCode.X)。运行时才算得出的 reason（变量）只允许出现在下面 DYNAMIC 登记过的位置 ——
// 新增一处就会失败，逼人去确认它可能产出哪些值、清单里有没有。
//
// 用法：node .github/scripts/check-business-reasons.js [--selftest]   （在 mall-backend 根目录；退出码 0 = 一致）
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const FLOW_FILE = 'mall-common/src/main/java/com/mall/common/metrics/BusinessFlow.java';
const OWNED_KEY = 'owned-flows';

// 运行时 reason 的登记处：文件名 -> 允许的表达式（去空白后精确匹配）。每一条都人工核对过取值范围在清单里。
const DYNAMIC = {
  // reasonOf(code)：failReceive(code) 只被传入 COUPON_UNAUTHENTICATED/NOT_FOUND/PER_LIMIT_EXCEEDED/SOLD_OUT
  // 和 checkReceivable() 的返回值（NOT_FOUND/NOT_PUBLISHED/RECEIVE_WINDOW_CLOSED/MEMBER_LEVEL_NOT_MATCH/SOLD_OUT）。
  // reasonOf(validated.code())：validateForUse 只返回 NOT_OWNED/THRESHOLD_NOT_MET/ALREADY_USED/EXPIRED/SCOPE_NOT_MATCH。
  'CouponClaimServiceImpl.java': ['reasonOf(code)', 'reasonOf(validated.code())'],
  // grab()/submitAddress() 的 result.getFailReason()：取值见 SeckillGrabServiceImpl 里的 setFailReason。
  'SeckillGrabServiceImpl.java': ['reason==null?null:BusinessFlow.reasonOf(reason)'],
};

const stripComments = s => s.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^[ \t]*\/\/.*$/gm, '');

function walk(d, pred, out = []) {
  for (const e of fs.readdirSync(d, { withFileTypes: true })) {
    if (['target', 'node_modules', '.git', 'test'].includes(e.name)) continue;
    const p = path.join(d, e.name);
    if (e.isDirectory()) walk(p, pred, out); else if (pred(p)) out.push(p);
  }
  return out;
}

function splitArgs(s) {
  const args = []; let depth = 0, cur = '';
  for (const ch of s) {
    if (ch === '(') depth++;
    if (ch === ')') depth--;
    if (ch === ',' && depth === 0) { args.push(cur.trim()); cur = ''; } else cur += ch;
  }
  if (cur.trim()) args.push(cur.trim());
  return args;
}

// ---------------------------------------------------------------- BusinessFlow：常量 + 清单
function parseCatalog(src) {
  src = stripComments(src);
  const consts = {};
  for (const m of src.matchAll(/static final String (\w+)\s*=\s*"([^"]+)"/g)) consts[m[1]] = m[2];
  const at = src.indexOf('FAILURE_REASONS = Map.of(');
  if (at < 0) throw new Error('BusinessFlow 里找不到 FAILURE_REASONS = Map.of(');
  const body = src.slice(at + 'FAILURE_REASONS = Map.of('.length, src.indexOf(');', at));
  const catalog = {};
  // 按括号配对切出每个 List.of(...)：里面有 reasonOf(...) 嵌套，正则会在第一个 ')' 截断（第一版就是这么坏的）
  const entries = [];
  const keyRe = /(\w+)\s*,\s*List\.of\(/g;
  let km;
  while ((km = keyRe.exec(body))) {
    let i = keyRe.lastIndex, depth = 1;
    while (i < body.length && depth > 0) { if (body[i] === '(') depth++; else if (body[i] === ')') depth--; i++; }
    entries.push([km[1], body.slice(keyRe.lastIndex, i - 1)]);
    keyRe.lastIndex = i;
  }
  for (const [key, list] of entries) {
    const flow = consts[key];
    if (!flow) throw new Error('清单的键 ' + key + ' 不是 BusinessFlow 常量');
    catalog[flow] = splitArgs(list).map(e => {
      const ec = e.match(/^reasonOf\(ErrorCode\.(\w+)\)$/);
      if (ec) return ec[1].toLowerCase();
      if (consts[e]) return consts[e];
      throw new Error('清单里解析不了的原因表达式: ' + e);
    });
  }
  return { consts, catalog };
}

// ---------------------------------------------------------------- 调用点
// files: [{ module, name, src }]；返回 { problems, used: Map<module, Set<flow>> }
function checkCallSites(files, consts, catalog) {
  const problems = [];
  const used = new Map();
  const flowOf = expr => {
    const m = expr.replace(/\s+/g, '').match(/^(?:com\.mall\.common\.metrics\.)?(?:BusinessFlow\.)?([A-Z_]+)$/);
    return m && consts[m[1]] && !m[1].startsWith('REASON_') ? consts[m[1]] : null;
  };
  for (const f of files) {
    const src = stripComments(f.src);
    // 本模块里的别名常量（如 SubmitGate.REASON_BUSY = BusinessFlow.REASON_GATE_BUSY）
    const moduleAliases = {};
    for (const g of files.filter(x => x.module === f.module)) {
      for (const m of stripComments(g.src).matchAll(/static final String (\w+)\s*=\s*([^;]+);/g)) {
        moduleAliases[m[1]] = m[2].trim();
      }
    }
    const reasonOf = (expr, seen = 0) => {
      const e = expr.replace(/\s+/g, '');
      if (seen > 5) return null;
      let m = e.match(/^(?:(?:com\.mall\.common\.metrics\.)?BusinessFlow\.)?reasonOf\(ErrorCode\.(\w+)\)$/);
      if (m) return m[1].toLowerCase();
      m = e.match(/^"([^"]*)"$/);
      if (m) return m[1];
      m = e.match(/^(?:[\w.]*\.)?([A-Z_]+)$/);
      if (m) {
        if (consts[m[1]] && (e.startsWith('BusinessFlow.') || e.startsWith('com.mall.common.metrics.') || e === m[1] && !moduleAliases[m[1]])) return consts[m[1]];
        if (moduleAliases[m[1]]) return reasonOf(moduleAliases[m[1]], seen + 1);
        if (consts[m[1]]) return consts[m[1]];
      }
      return null;
    };
    for (const m of src.matchAll(/\b\w*[mM]etrics\.(success|failure)\(([^;]*?)\)\s*;/g)) {
      const args = splitArgs(m[2]);
      const line = src.slice(0, m.index).split('\n').length;
      const where = `${f.module}/${f.name}:${line}`;
      const flow = flowOf(args[0]);
      if (!flow) { problems.push(`${where} 业务流解析不了或不是 BusinessFlow 常量: ${args[0]}`); continue; }
      if (!catalog[flow]) { problems.push(`${where} 业务流 ${flow} 不在 FAILURE_REASONS 里`); continue; }
      if (!used.has(f.module)) used.set(f.module, new Set());
      used.get(f.module).add(flow);
      if (m[1] === 'success') continue;
      const reason = reasonOf(args[1] || '');
      if (reason === null) {
        const compact = (args[1] || '').replace(/\s+/g, '');
        if (!(DYNAMIC[f.name] || []).includes(compact)) {
          problems.push(`${where} 运行时才知道的失败原因 \`${args[1]}\`：确认它可能产出的值都在清单里，再登记到本脚本的 DYNAMIC`);
        }
        continue;
      }
      if (!catalog[flow].includes(reason)) problems.push(`${where} ${flow} 的失败原因 '${reason}' 不在 FAILURE_REASONS 里`);
    }
  }
  return { problems, used };
}

// ---------------------------------------------------------------- owned-flows ↔ 实际计数的流
function readOwned(yml) {
  const m = yml.match(new RegExp('^\\s+' + OWNED_KEY + ':\\s*(.+)$', 'm'));
  return m ? m[1].split(',').map(s => s.trim()).filter(Boolean) : [];
}
function checkOwned(ownedByModule, used, catalog) {
  const problems = [];
  for (const mod of new Set([...ownedByModule.keys(), ...used.keys()])) {
    const owned = new Set(ownedByModule.get(mod) || []);
    const counts = used.get(mod) || new Set();
    for (const f of counts) if (!owned.has(f)) problems.push(`${mod} 计数了 ${f}，但没写进 application.yml 的 ${OWNED_KEY}（零流量时不会预注册）`);
    for (const f of owned) {
      if (!catalog[f]) problems.push(`${mod} 的 ${OWNED_KEY} 有清单里没有的流 ${f}（服务会启动失败）`);
      else if (!counts.has(f)) problems.push(`${mod} 的 ${OWNED_KEY} 有 ${f}，但代码里从不计数（会发布幽灵序列）`);
    }
  }
  return problems;
}

// ---------------------------------------------------------------- 自检：先证明它能抓到问题
function selftest(consts, catalog) {
  const flowKey = Object.keys(consts).find(k => consts[k] === 'stock.lock');
  const files = [
    { module: 'm1', name: 'A.java', src: `class A { void a(){ businessMetrics.failure(BusinessFlow.${flowKey}, "no_such_reason"); } }` },
    { module: 'm1', name: 'B.java', src: `class B { void b(){ businessMetrics.failure(BusinessFlow.${flowKey}, someVariable); } }` },
    { module: 'm2', name: 'C.java', src: `class C { void c(){ businessMetrics.failure(BusinessFlow.${flowKey}, BusinessFlow.REASON_INSUFFICIENT); } }` },
  ];
  const { problems, used } = checkCallSites(files, consts, catalog);
  const owned = new Map([['m2', ['stock.lock', 'stock.unlock']]]);
  const all = problems.concat(checkOwned(owned, used, catalog));
  const expect = [/no_such_reason/, /someVariable/, /m1 计数了 stock\.lock/, /stock\.unlock，但代码里从不计数/];
  const missing = expect.filter(re => !all.some(p => re.test(p)));
  const falsePos = all.filter(p => /m2\/C\.java/.test(p));
  if (missing.length || falsePos.length) {
    console.log('SELFTEST FAILED'); console.log(' 没抓到:', missing.map(String)); console.log(' 误报:', falsePos);
    process.exit(2);
  }
  console.log(`selftest ok：${expect.length} 类问题都抓到，合法写法 0 误报`);
}

// ---------------------------------------------------------------- main
const { consts, catalog } = parseCatalog(fs.readFileSync(path.join(ROOT, FLOW_FILE), 'utf8'));
// 正向对照：清单解析坏了要直接失败，而不是「什么都没比所以通过」
if (!catalog['order.submit'] || !catalog['order.submit'].includes('persist_failed')
    || !catalog['seckill.grab'] || !catalog['seckill.grab'].includes('seckill_mq_failed')) {
  console.log('CONTROL FAILED: FAILURE_REASONS 解析结果不对', catalog); process.exit(2);
}
selftest(consts, catalog);
if (process.argv.includes('--selftest')) process.exit(0);

const modules = fs.readdirSync(ROOT).filter(d => d.startsWith('mall-') && d !== 'mall-common'
  && fs.existsSync(path.join(ROOT, d, 'src/main/java')));
const files = [];
const owned = new Map();
for (const mod of modules) {
  for (const f of walk(path.join(ROOT, mod, 'src/main/java'), p => p.endsWith('.java'))) {
    files.push({ module: mod, name: path.basename(f), src: fs.readFileSync(f, 'utf8') });
  }
  const yml = path.join(ROOT, mod, 'src/main/resources/application.yml');
  if (fs.existsSync(yml)) { const o = readOwned(fs.readFileSync(yml, 'utf8')); if (o.length) owned.set(mod, o); }
}
const { problems, used } = checkCallSites(files, consts, catalog);
problems.push(...checkOwned(owned, used, catalog));
const sites = [...used.values()].reduce((a, s) => a + s.size, 0);
if (!used.get('mall-order') || !used.get('mall-order').has('order.submit')) {
  console.log('CONTROL FAILED: 没扫到 mall-order 的 order.submit 计数，调用点解析坏了'); process.exit(2);
}
console.log(`清单 ${Object.keys(catalog).length} 条流；计数的服务 ${used.size} 个、流 ${sites} 条；owned-flows 声明 ${owned.size} 个服务`);
if (problems.length) {
  console.log('\n不一致：'); problems.forEach(p => console.log('  - ' + p)); process.exit(1);
}
console.log('一致');
