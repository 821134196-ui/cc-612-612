<script>
  import { api, fen, fmtTime } from '../api.js';
  import OwnerSelect from './OwnerSelect.svelte';

  export let owners;
  export let ownerCode;

  let mode = 'issue';
  let count = 5;
  let manualCodes = '';
  let bags = [];
  let message = null;

  // 退袋 / 遗失 / 备注
  let selected = new Set();
  let lostMonth = '';
  let lostReason = '';
  let noteText = '';
  let noteCode = '';

  async function load() {
    if (ownerCode) bags = await api.listBags(ownerCode);
  }

  $: if (ownerCode) load();

  function nowMonth() {
    const d = new Date();
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}`;
  }
  lostMonth = lostMonth || nowMonth();

  async function issue() {
    message = null;
    try {
      const codes = manualCodes
        .split(/[\s,，;；]+/)
        .map((s) => s.trim())
        .filter(Boolean);
      const payload = codes.length
        ? { ownerCode, bagCodes: codes }
        : { ownerCode, count: Number(count) };
      const r = await api.issue(payload);
      message = { kind: 'ok', text: `已登记发放 ${r.length} 个袋号（仅发放不计费，清运/约定遗失才计费）` };
      manualCodes = '';
      await load();
    } catch (e) {
      message = { kind: 'err', text: e.message };
    }
  }

  async function returnBags() {
    message = null;
    const codes = [...selected];
    if (!codes.length) {
      message = { kind: 'err', text: '请勾选要退回的未使用袋子' };
      return;
    }
    try {
      await api.returnBags(codes);
      message = { kind: 'ok', text: `已退回 ${codes.length} 个袋号，退袋不产生清运费` };
      selected = new Set();
      await load();
    } catch (e) {
      message = { kind: 'err', text: e.message };
    }
  }

  async function markLost() {
    message = null;
    const codes = [...selected];
    if (codes.length !== 1) {
      message = { kind: 'err', text: '约定遗失请勾选单个袋号' };
      return;
    }
    try {
      await api.markLost({ bagCode: codes[0], month: lostMonth, reason: lostReason });
      message = { kind: 'ok', text: `袋号 ${codes[0]} 已按约定遗失计入 ${lostMonth} 月（一次）` };
      selected = new Set();
      lostReason = '';
      await load();
    } catch (e) {
      message = { kind: 'err', text: e.message };
    }
  }

  async function addNote() {
    message = null;
    if (!noteCode.trim() || !noteText.trim()) {
      message = { kind: 'err', text: '请填写袋号与备注（破损沿用原袋号）' };
      return;
    }
    try {
      await api.note({ bagCode: noteCode.trim(), note: noteText.trim() });
      message = { kind: 'ok', text: `袋号 ${noteCode} 备注已追加，未另建记录、不重复收费` };
      noteText = '';
      await load();
    } catch (e) {
      message = { kind: 'err', text: e.message };
    }
  }

  function toggle(code) {
    if (selected.has(code)) selected.delete(code);
    else selected.add(code);
    selected = new Set(selected);
  }

  const statusText = {
    ISSUED: '已发放',
    RETURNED: '已退回',
    COLLECTED: '已清运',
    LOST: '约定遗失'
  };
</script>

<div class="card">
  <OwnerSelect {owners} {ownerCode} />

  <div class="row" style="margin-top:12px">
    <button class="btn small {mode === 'issue' ? '' : 'ghost'}" on:click={() => (mode = 'issue')}>发袋登记</button>
    <button class="btn small {mode === 'return' ? '' : 'ghost'}" on:click={() => (mode = 'return')}>退袋/遗失</button>
    <button class="btn small ghost" on:click={() => (mode = 'note')}>破损备注</button>
  </div>

  {#if mode === 'issue'}
    <label>按数量自动生成袋号</label>
    <input type="number" bind:value={count} min="1" />
    <label>或直接录入袋号（空格/逗号分隔，优先于数量）</label>
    <textarea rows="2" bind:value={manualCodes} placeholder="B101-0001, B101-0002"></textarea>
    <button class="btn" on:click={issue}>登记发袋</button>
  {:else if mode === 'return'}
    <p class="muted" style="margin:10px 0 0">勾选未使用的袋子退回（退回永不计费）；约定遗失请勾单个袋子。</p>
    <div class="row">
      <button class="btn small" on:click={returnBags}>退回所选</button>
    </div>
    <label>约定遗失归属月份</label>
    <input type="month" bind:value={lostMonth} />
    <label>遗失说明</label>
    <input bind:value={lostReason} placeholder="如：袋体破损后与物业确认遗失" />
    <button class="btn small ghost" on:click={markLost}>标记约定遗失</button>
  {:else}
    <label>袋号</label>
    <input bind:value={noteCode} placeholder="如 B101-0001" />
    <label>破损/跨月说明（沿用原袋号，不另建记录）</label>
    <input bind:value={noteText} placeholder="如：袋子破损，现场换袋沿用原号" />
    <button class="btn" on:click={addNote}>追加备注</button>
  {/if}

  {#if message}
    <div class="banner {message.kind === 'ok' ? 'ok' : 'err'}">{message.text}</div>
  {/if}
</div>

<div class="card">
  <h2>该业主全部袋号（{bags.length}）</h2>
  <table>
    <thead>
      <tr><th></th><th>袋号</th><th>状态</th><th>清运月</th><th>发放时间</th></tr>
    </thead>
    <tbody>
      {#each bags as b (b.id)}
        <tr>
          <td>
            {#if mode === 'return' && (b.status === 'ISSUED')}
              <input type="checkbox" checked={selected.has(b.bagCode)} on:change={() => toggle(b.bagCode)} style="width:auto" />
            {/if}
          </td>
          <td>
            <strong>{b.bagCode}</strong>
            {#if b.note}<div class="muted">📝 {b.note}</div>{/if}
            {#if b.collector}<div class="muted">清运：{b.collector}</div>{/if}
          </td>
          <td><span class="tag {b.status}">{statusText[b.status]}</span></td>
          <td>{b.collectedMonth || b.lostMonth || '—'}</td>
          <td>{fmtTime(b.issuedAt)}</td>
        </tr>
      {/each}
    </tbody>
  </table>
</div>
