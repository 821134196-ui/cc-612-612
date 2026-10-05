<script>
  import { api } from '../api.js';

  let bagCode = '';
  let collector = '';
  let busy = false;
  let result = null; // {kind:'ok'|'dup'|'err'|'reject', title, detail, bag}
  let recent = [];

  // 尝试调用设备摄像头扫码（支持的浏览器），否则手动输入/粘贴
  let useCamera = false;

  async function doScan() {
    const code = bagCode.trim();
    if (!code) {
      result = { kind: 'err', title: '请输入或扫描袋号' };
      return;
    }
    busy = true;
    try {
      const r = await api.scan(code, collector.trim() || '清运员');
      if (r.result === 'ACCEPTED') {
        result = {
          kind: 'ok',
          title: '✓ 清运成功，已计费一次',
          detail: r.detail,
          bag: r.bag
        };
      } else if (r.result === 'DUPLICATE') {
        result = {
          kind: 'dup',
          title: '⟳ 重复扫码，不重复收费',
          detail: r.detail,
          bag: r.bag
        };
      } else {
        result = { kind: 'reject', title: '✕ 拒绝清运', detail: r.detail, bag: r.bag };
      }
      bagCode = '';
      await refresh();
    } catch (e) {
      result = { kind: 'err', title: '✕ 扫码失败', detail: e.message };
      await refresh();
    } finally {
      busy = false;
    }
  }

  async function refresh() {
    recent = await api.recentScans(8);
  }

  // 外接扫码枪通常以回车结尾
  function onKey(e) {
    if (e.key === 'Enter') doScan();
  }

  refresh();
</script>

<div class="card">
  <h2>清运员扫码</h2>
  <label>清运员</label>
  <input bind:value={collector} placeholder="姓名或工号" />

  <label>袋号（扫码枪扫描后自动提交，或手动输入）</label>
  <input
    bind:value={bagCode}
    placeholder="如 B101-0001"
    autocomplete="off"
    on:keydown={onKey}
  />
  <button class="btn" on:click={doScan} disabled={busy}>{busy ? '提交中…' : '确认收走'}</button>

  {#if result}
    <div class="banner {result.kind === 'ok' ? 'ok' : result.kind === 'err' ? 'err' : 'warn'}">
      <strong>{result.title}</strong>
      {#if result.detail}<div style="margin-top:4px">{result.detail}</div>{/if}
      {#if result.bag}
        <div style="margin-top:6px" class="muted">
          袋号 {result.bag.bagCode} · {result.bag.room} {result.bag.ownerName} ·
          当前状态 {result.bag.status}
        </div>
      {/if}
    </div>
  {/if}
</div>

<div class="card scan-list">
  <h2>最近扫码回执</h2>
  {#if recent.length === 0}
    <div class="muted">暂无记录</div>
  {/if}
  {#each recent as s}
    <div class="item">
      <span class="scan-code">{s.bagCode || '-'}</span>
      <span class="pill {s.result}">{s.result}</span>
      <div class="muted">{s.detail}</div>
    </div>
  {/each}
</div>
