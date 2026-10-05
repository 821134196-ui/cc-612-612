<script>
  import { onMount } from 'svelte'
  import { api, currentMonth } from '../api.js'

  let month = currentMonth()
  let bills = []
  let error = ''
  let message = ''
  let expanded = null

  // 补差单表单
  let adjustFor = null
  let adjustReason = ''
  let adjustItems = [{ type: 'ADJUST_DEBIT', amount: '', description: '', bagNo: '' }]

  onMount(load)

  async function load() {
    error = ''
    try { bills = await api.bills({ month }) } catch (e) { error = e.message }
  }

  async function generate() {
    error = ''; message = ''
    try {
      const r = await api.generate(month, null)
      message = `出账完成：生成/更新账单 ${r.bills.length} 张，补差单 ${r.adjustments.length} 张`
      load()
    } catch (e) { error = e.message }
  }

  async function settle(bill) {
    error = ''
    try { await api.settle(bill.id); load() } catch (e) { error = e.message }
  }

  async function payBill(bill) {
    error = ''
    try {
      const r = await api.pay({ billId: bill.id, amount: bill.total })
      message = `支付成功，交易号 ${r.txnId}（本地模拟）`
      load()
    } catch (e) { error = e.message }
  }

  async function payAdjustment(bill, adj) {
    error = ''
    try {
      const r = await api.pay({ adjustmentId: adj.id, amount: adj.total })
      message = `补差单支付成功，交易号 ${r.txnId}（本地模拟）`
      load()
    } catch (e) { error = e.message }
  }

  function openAdjust(bill) {
    adjustFor = bill.id
    adjustReason = ''
    adjustItems = [{ type: 'ADJUST_DEBIT', amount: '', description: '', bagNo: '' }]
  }

  async function submitAdjust() {
    error = ''
    try {
      const items = adjustItems
        .filter(i => i.amount && Number(i.amount) > 0)
        .map(i => ({ ...i, amount: Number(i.amount), bagNo: i.bagNo || null }))
      if (!items.length) { error = '请至少填写一条有效补差明细'; return }
      await api.adjust(adjustFor, adjustReason || '差错更正', items)
      adjustFor = null
      message = '补差单已生成，原账单保持不变'
      load()
    } catch (e) { error = e.message }
  }

  const STATUS_LABEL = { OPEN: '出账中', SETTLED: '已结算', PAID: '已支付' }
  const TYPE_LABEL = { CLEARANCE_FEE: '清运费', LOST_FEE: '遗失赔付', ADJUST_DEBIT: '补收', ADJUST_CREDIT: '退回' }
</script>

<div class="card">
  <div class="row">
    <div>
      <label>账期</label>
      <input type="month" bind:value={month} on:change={load} />
    </div>
    <div class="shrink" style="align-self:end">
      <button on:click={generate}>生成/更新账单</button>
    </div>
  </div>
  {#if message}<p class="muted">{message}</p>{/if}
</div>

{#if error}<div class="error-box">{error}</div>{/if}

{#each bills as b}
  <div class="card">
    <div class="row" style="cursor:pointer" on:click={() => expanded = expanded === b.id ? null : b.id}>
      <div>
        <b>{b.ownerName}（{b.unit}）</b>
        <div class="muted">{b.month} · {b.items.length} 条明细</div>
      </div>
      <div class="shrink" style="text-align:right">
        <div class="money" style="font-size:18px">{b.total} 元</div>
        <span class="chip {b.status}">{STATUS_LABEL[b.status]}</span>
      </div>
    </div>

    {#if expanded === b.id}
      <table class="list" style="margin-top:10px">
        <thead><tr><th>类型</th><th>袋号</th><th>金额</th></tr></thead>
        <tbody>
          {#each b.items as it}
            <tr>
              <td>{TYPE_LABEL[it.type]}</td>
              <td>{it.bagNo || '—'}</td>
              <td class="money">{it.amount} 元</td>
            </tr>
          {/each}
        </tbody>
      </table>

      <div class="row" style="margin-top:12px">
        {#if b.status === 'OPEN'}
          <button class="small" on:click={() => settle(b)}>结算（冻结账单）</button>
        {/if}
        {#if b.status === 'SETTLED'}
          <button class="small" on:click={() => payBill(b)}>模拟支付 {b.total} 元</button>
        {/if}
        {#if b.status !== 'OPEN'}
          <button class="small ghost" on:click={() => openAdjust(b)}>差错补差</button>
        {/if}
      </div>

      {#if b.adjustments.length}
        <div class="muted" style="margin:12px 0 6px">补差单（原账单不变，差额在此调整）</div>
        {#each b.adjustments as adj}
          <div class="adj">
            <div class="row">
              <div>
                <b>补差单 #{adj.id}</b> <span class="chip {adj.status === 'PAID' ? 'PAID' : 'SETTLED'}">{adj.status === 'PAID' ? '已支付' : '待支付'}</span>
                <div class="muted">{adj.reason}</div>
              </div>
              <div class="shrink money">{adj.total} 元</div>
            </div>
            <table class="list" style="margin-top:6px">
              <tbody>
                {#each adj.items as it}
                  <tr><td>{TYPE_LABEL[it.type]}</td><td>{it.bagNo || '—'}</td><td class="money">{it.amount} 元</td></tr>
                {/each}
              </tbody>
            </table>
            {#if adj.status !== 'PAID'}
              <button class="small" style="margin-top:8px" on:click={() => payAdjustment(b, adj)}>模拟支付 {adj.total} 元</button>
            {/if}
          </div>
        {/each}
      {/if}

      {#if adjustFor === b.id}
        <div class="adj-form">
          <div class="muted" style="margin-bottom:6px">新建补差单（仅已结算账单可用）</div>
          <input bind:value={adjustReason} placeholder="补差原因，如：漏扫补录 / 误收退回" />
          {#each adjustItems as item, i}
            <div class="row" style="margin-top:8px">
              <select bind:value={item.type} style="max-width:110px">
                <option value="ADJUST_DEBIT">补收</option>
                <option value="ADJUST_CREDIT">退回</option>
              </select>
              <input type="number" min="0.01" step="0.01" bind:value={item.amount} placeholder="金额" style="max-width:110px" />
              <input bind:value={item.description} placeholder="说明" />
            </div>
          {/each}
          <div class="row" style="margin-top:10px">
            <button class="small ghost" on:click={() => adjustItems = [...adjustItems, { type: 'ADJUST_DEBIT', amount: '', description: '', bagNo: '' }]}>加一行</button>
            <button class="small" on:click={submitAdjust}>提交补差单</button>
            <button class="small ghost" on:click={() => adjustFor = null}>取消</button>
          </div>
        </div>
      {/if}
    {/if}
  </div>
{:else}
  <div class="card muted">该账期暂无账单，点击「生成/更新账单」出账。</div>
{/each}

<style>
  .adj {
    border: 1px dashed var(--line);
    border-radius: 10px;
    padding: 10px;
    margin-top: 8px;
  }
  .adj-form {
    background: #f9fafb;
    border-radius: 10px;
    padding: 12px;
    margin-top: 12px;
  }
</style>
