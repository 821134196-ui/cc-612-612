<script>
  import { onMount } from 'svelte'
  import { api } from '../api.js'

  let owners = []
  let ownerId = null
  let count = 5
  let bags = []
  let issuedJustNow = []
  let error = ''
  let message = ''

  // 退袋 / 报失 / 破损 操作
  let opBagNo = ''
  let damageNote = ''

  onMount(async () => {
    owners = await api.owners()
    if (owners.length) { ownerId = owners[0].id; loadBags() }
  })

  async function loadBags() {
    if (!ownerId) return
    bags = await api.bags(ownerId)
  }

  async function issue() {
    error = ''; message = ''; issuedJustNow = []
    try {
      const r = await api.issue(ownerId, count)
      issuedJustNow = r.map(x => x.bagNo)
      message = `已发放 ${r.length} 只袋（只登记领用，不产生费用）`
      loadBags()
    } catch (e) { error = e.message }
  }

  async function doReturn() {
    error = ''; message = ''
    try {
      await api.returnBag(opBagNo.trim())
      message = `袋号 ${opBagNo.trim()} 已退回（不产生任何费用）`
      opBagNo = ''
      loadBags()
    } catch (e) { error = e.message }
  }

  async function doLost() {
    error = ''; message = ''
    try {
      await api.markLost(opBagNo.trim())
      message = `袋号 ${opBagNo.trim()} 已登记遗失，将按约定赔付计入账单`
      opBagNo = ''
      loadBags()
    } catch (e) { error = e.message }
  }

  async function doDamage() {
    error = ''; message = ''
    try {
      await api.markDamaged(opBagNo.trim(), damageNote)
      message = `袋号 ${opBagNo.trim()} 已登记破损（沿用原袋号，不另建记录）`
      opBagNo = ''; damageNote = ''
      loadBags()
    } catch (e) { error = e.message }
  }

  const STATUS_LABEL = { ISSUED: '已发放', RETURNED: '已退回', CLEARED: '已清运', LOST: '已报失' }
</script>

<div class="card">
  <label>业主</label>
  <select bind:value={ownerId} on:change={loadBags}>
    {#each owners as o}<option value={o.id}>{o.name}（{o.unit}）</option>{/each}
  </select>
  <label>发袋数量</label>
  <div class="row">
    <input type="number" min="1" max="500" bind:value={count} />
    <button class="shrink" on:click={issue} disabled={!ownerId}>登记发袋</button>
  </div>
  {#if message}<p class="muted">{message}</p>{/if}
  {#if issuedJustNow.length}
    <div class="bag-nos">
      {#each issuedJustNow as no}<span>{no}</span>{/each}
    </div>
  {/if}
</div>

{#if error}<div class="error-box">{error}</div>{/if}

<div class="card">
  <div class="muted" style="margin-bottom:6px">退袋 / 报失 / 破损（输入袋号）</div>
  <input bind:value={opBagNo} placeholder="袋号，如 B000123" autocapitalize="characters" />
  <div class="row" style="margin-top:10px">
    <button class="ghost" on:click={doReturn} disabled={!opBagNo.trim()}>退回（免费）</button>
    <button class="ghost" on:click={doLost} disabled={!opBagNo.trim()}>报失（约定赔付）</button>
  </div>
  <div class="row" style="margin-top:10px">
    <input bind:value={damageNote} placeholder="破损说明（可选）" />
    <button class="ghost shrink" on:click={doDamage} disabled={!opBagNo.trim()}>登记破损</button>
  </div>
</div>

<div class="card">
  <div class="muted" style="margin-bottom:8px">该业主名下袋号（{bags.length}）</div>
  <table class="list">
    <thead><tr><th>袋号</th><th>状态</th><th>破损</th></tr></thead>
    <tbody>
      {#each bags as b}
        <tr>
          <td>{b.bagNo}</td>
          <td><span class="chip {b.status}">{STATUS_LABEL[b.status] || b.status}</span></td>
          <td>{b.damaged ? '是' : ''}</td>
        </tr>
      {:else}
        <tr><td colspan="3" class="muted">暂无记录</td></tr>
      {/each}
    </tbody>
  </table>
</div>

<style>
  .bag-nos { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
  .bag-nos span {
    font-family: ui-monospace, monospace;
    font-size: 16px;
    font-weight: 700;
    background: #eef2ff;
    color: var(--primary);
    border-radius: 8px;
    padding: 6px 10px;
  }
</style>
