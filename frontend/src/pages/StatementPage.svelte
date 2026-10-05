<script>
  import { onMount } from 'svelte'
  import { api, currentMonth } from '../api.js'

  let owners = []
  let ownerId = null
  let month = currentMonth()
  let st = null
  let error = ''

  onMount(async () => {
    owners = await api.owners()
    if (owners.length) { ownerId = owners[0].id; load() }
  })

  async function load() {
    if (!ownerId || !month) return
    error = ''
    try { st = await api.statement(ownerId, month) } catch (e) { error = e.message }
  }
</script>

<div class="card">
  <div class="row">
    <div>
      <label>业主</label>
      <select bind:value={ownerId} on:change={load}>
        {#each owners as o}<option value={o.id}>{o.name}（{o.unit}）</option>{/each}
      </select>
    </div>
    <div>
      <label>账期</label>
      <input type="month" bind:value={month} on:change={load} />
    </div>
  </div>
</div>

{#if error}<div class="error-box">{error}</div>{/if}

{#if st}
  <div class="card summary">
    <div class="row" style="text-align:center">
      <div><div class="num">{st.totals.issuedCount}</div><div class="muted">领用</div></div>
      <div><div class="num">{st.totals.returnedCount}</div><div class="muted">退回</div></div>
      <div><div class="num">{st.totals.clearedCount}</div><div class="muted">清运</div></div>
      <div><div class="num">{st.totals.lostCount}</div><div class="muted">报失</div></div>
    </div>
    <div class="total-line">
      应付合计 <span class="money">{st.totals.total} 元</span>
      <span class="muted">（清运 {st.totals.clearanceFees} + 报失 {st.totals.lostFees}）</span>
      {#if st.billStatus}<span class="chip {st.billStatus}">{st.billStatus}</span>{/if}
    </div>
    <p class="muted" style="margin:8px 0 0">费用只来自实际清运与约定报失；领用、退回不计费。</p>
  </div>

  <div class="columns">
    <div class="card col">
      <h3>领用（{st.issued.length}）</h3>
      {#each st.issued as r}
        <div class="item"><span class="no">{r.bagNo}</span><span class="muted">{r.at.slice(5, 16).replace('T', ' ')}</span></div>
      {:else}<div class="muted">无</div>{/each}
    </div>
    <div class="card col">
      <h3>退袋（{st.returned.length}）</h3>
      {#each st.returned as r}
        <div class="item"><span class="no">{r.bagNo}</span><span class="muted">{r.at.slice(5, 16).replace('T', ' ')}</span></div>
      {:else}<div class="muted">无</div>{/each}
    </div>
    <div class="card col">
      <h3>清运（{st.cleared.length}）</h3>
      {#each st.cleared as r}
        <div class="item"><span class="no">{r.bagNo}</span><span class="money">{r.fee} 元</span></div>
      {:else}<div class="muted">无</div>{/each}
      {#if st.lost.length}
        <h3 style="margin-top:12px">报失（{st.lost.length}）</h3>
        {#each st.lost as r}
          <div class="item"><span class="no">{r.bagNo}</span><span class="money">{r.fee} 元</span></div>
        {/each}
      {/if}
    </div>
  </div>
{/if}

<style>
  .summary .num { font-size: 26px; font-weight: 700; }
  .total-line { text-align: center; margin-top: 10px; font-size: 16px; }
  .total-line .money { font-size: 20px; color: var(--primary); }
  .columns { display: grid; grid-template-columns: 1fr; gap: 0; }
  @media (min-width: 640px) {
    .columns { grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
    .columns .card { margin: 0; }
    .columns { margin: 12px; }
  }
  .col h3 { margin: 0 0 8px; font-size: 15px; }
  .item { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px dashed var(--line); font-size: 14px; }
  .item .no { font-family: ui-monospace, monospace; font-weight: 600; }
</style>
