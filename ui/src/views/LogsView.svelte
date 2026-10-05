<script>
  import { api, fmtTs } from '../api.js';

  let logs = [];
  async function load() {
    logs = await api.recentScans(50);
  }
  load();
</script>

<div class="card scan-list">
  <h2>扫码回执记录</h2>
  <button class="btn small ghost" on:click={load}>刷新</button>
  {#if logs.length === 0}
    <div class="muted" style="margin-top:10px">暂无扫码记录</div>
  {/if}
  {#each logs as s (s.id)}
    <div class="item">
      <span class="scan-code">{s.bagCode || '-'}</span>
      <span class="pill {s.result}">{s.result}</span>
      <div>{s.detail}</div>
      <div class="muted">{fmtTs(s.scannedAt)} · {s.collector || '-'}</div>
    </div>
  {/each}
</div>
