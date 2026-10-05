<script>
  import { api, fen, fmtTime, fmtTs } from '../api.js';
  import OwnerSelect from './OwnerSelect.svelte';

  export let owners;
  export let ownerCode;

  let month = '';
  let detail = null;
  let billList = [];
  let message = null;
  let busy = false;

  // 补差表单
  let adjMode = 'MISSED_COLLECTION';
  let adjBag = '';
  let adjReason = '';

  function nowMonth() {
    const d = new Date();
    const p = (n) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}`;
  }
  month = month || nowMonth();

  async function load() {
    if (!ownerCode) return;
    message = null;
    try {
      detail = await api.monthlyBill(ownerCode, month);
      billList = await api.listBills(ownerCode);
    } catch (e) {
      message = { kind: 'err', text: e.message };
    }
  }

  $: if (ownerCode) load();

  async function settle() {
    busy = true;
    message = null;
    try {
      detail = await api.settle(detail.bill.id, '本地模拟支付');
      billList = await api.listBills(ownerCode);
      message = { kind: 'ok', text: '支付成功（本地模拟），账单已结算冻结' };
    } catch (e) {
      message = { kind: 'err', text: e.message };
    } finally {
      busy = false;
    }
  }

  async function submitAdjustment() {
    message = null;
    if (!adjBag.trim()) {
      message = { kind: 'err', text: '请填写补差涉及的袋号' };
      return;
    }
    try {
      detail = await api.adjust({
        ownerCode,
        month,
        lines: [{ bagCode: adjBag.trim(), correctionType: adjMode, reason: adjReason }]
      });
      message = {
        kind: 'ok',
        text: adjMode === 'MISSED_COLLECTION'
          ? '补差单已生成（补收），原账单保持不变'
          : '补差单已生成（退费），原账单保持不变'
      };
      adjBag = '';
      adjReason = '';
      billList = await api.listBills(ownerCode);
    } catch (e) {
      message = { kind: 'err', text: e.message };
    }
  }

  async function openBill(id) {
    try {
      detail = await api.bill(id);
      month = detail.bill.month;
      message = null;
    } catch (e) {
      message = { kind: 'err', text: e.message };
    }
  }

  const eventText = { COLLECTED: '扫码清运', LOST: '约定遗失' };
  const corrText = { MISSED_COLLECTION: '漏收补收', WRONG_COLLECTION: '错收退费' };
  const statusText = { ISSUED: '已发放', RETURNED: '已退回', COLLECTED: '已清运', LOST: '约定遗失' };
</script>

<div class="card">
  <OwnerSelect {owners} {ownerCode} />
  <label>账单月份</label>
  <div class="row">
    <input type="month" bind:value={month} />
    <button class="btn small" on:click={load}>核对</button>
  </div>

  {#if billList.length}
    <label>历史账单（正式单 / 补差单）</label>
    <select on:change={(e) => e.target.value && openBill(Number(e.target.value))} value="">
      <option value="">— 选择账单查看 —</option>
      {#each billList as b}
        <option value={b.id}>
          {b.billType === 'ADJUSTMENT' ? '补差单' : '正式单'} {b.billNo}（{b.status === 'SETTLED' ? '已结算' : '待结算'} ¥{fen(b.amountFen)}）
        </option>
      {/each}
    </select>
  {/if}
</div>

{#if detail}
  <div class="card">
    <div class="bill-head">
      <h2 style="margin:0">
        {detail.bill.billType === 'ADJUSTMENT' ? '补差单' : '月度账单'} {detail.bill.billNo}
      </h2>
      <span>
        <span class="tag {detail.bill.billType}">{detail.bill.billType === 'ADJUSTMENT' ? '补差单' : '正式单'}</span>
        <span class="tag {detail.bill.status}">{detail.bill.status === 'SETTLED' ? '已结算' : '待结算'}</span>
      </span>
    </div>
    {#if detail.bill.originalBillNo}
      <div class="muted" style="margin-top:6px">针对原账单 {detail.bill.originalBillNo} 的差错更正，原账单不变</div>
    {/if}

    <div class="stats" style="margin-top:12px">
      <div class="stat"><div class="n">{detail.issuedCount}</div><div class="t">累计领用</div></div>
      <div class="stat"><div class="n">{detail.returnedCount}</div><div class="t">已退回(免费)</div></div>
      <div class="stat"><div class="n">{detail.collectedInMonth + detail.lostInMonth}</div><div class="t">本月计费袋数</div></div>
      <div class="stat"><div class="n">¥{fen(detail.unitPriceFen)}</div><div class="t">单价/袋</div></div>
    </div>

    <div style="margin-top:14px; display:flex; justify-content:space-between; align-items:baseline">
      <strong>{detail.bill.billType === 'ADJUSTMENT' ? '补差合计' : '应付清运费'}</strong>
      <span class="amount" class:neg={detail.bill.amountFen < 0} style="font-size:22px">
        {detail.bill.amountFen < 0 ? '-' : ''}¥{fen(Math.abs(detail.bill.amountFen))}
      </span>
    </div>

    {#if detail.bill.status === 'OPEN'}
      <button class="btn" on:click={settle} disabled={busy}>
        {busy ? '支付中…' : detail.bill.billType === 'ADJUSTMENT' && detail.bill.amountFen < 0 ? '确认退费（模拟支付）' : '立即支付（模拟支付）'}
      </button>
    {:else}
      <div class="banner ok" style="margin-top:12px">
        已于 {fmtTs(detail.bill.settledAt)} 结算，流水号 {detail.bill.paymentRef}。账单已冻结。
      </div>
    {/if}
    {#if message}
      <div class="banner {message.kind === 'ok' ? 'ok' : 'err'}">{message.text}</div>
    {/if}
  </div>

  {#if detail.chargedItems.length}
    <div class="card">
      <h2>收费来源（{detail.chargedItems.length} 袋，每袋只计一次）</h2>
      <table>
        <thead><tr><th>袋号</th><th>计费事件</th><th>事件月</th><th>金额</th></tr></thead>
        <tbody>
          {#each detail.chargedItems as i (i.id)}
            <tr>
              <td class="scan-code">{i.bagCode}</td>
              <td>{eventText[i.eventType]}</td>
              <td>{i.eventMonth}</td>
              <td>¥{fen(i.amountFen)}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  {/if}

  {#if detail.adjustmentLines.length}
    <div class="card">
      <h2>补差明细（原账单不变）</h2>
      <table>
        <thead><tr><th>袋号</th><th>更正类型</th><th>差额</th><th>原因</th></tr></thead>
        <tbody>
          {#each detail.adjustmentLines as l (l.id)}
            <tr>
              <td class="scan-code">{l.bagCode}</td>
              <td>{corrText[l.correctionType]}</td>
              <td class="amount {l.deltaFen < 0 ? 'neg' : ''}">{l.deltaFen < 0 ? '-' : ''}¥{fen(Math.abs(l.deltaFen))}</td>
              <td>{l.reason || '—'}</td>
            </tr>
          {/each}
        </tbody>
      </table>
    </div>
  {/if}

  <div class="card">
    <h2>费用核对：领用 / 退袋 / 清运记录并排</h2>
    <table>
      <thead>
        <tr><th>袋号</th><th>发放</th><th>退回</th><th>清运/遗失</th><th>计费月</th></tr>
      </thead>
      <tbody>
        {#each detail.bags as b (b.id)}
          <tr>
            <td>
              <strong class="scan-code">{b.bagCode}</strong>
              {#if b.note}<div class="muted">📝{b.note}</div>{/if}
            </td>
            <td>{fmtTime(b.issuedAt)}<div class="muted">{b.issueBatch}</div></td>
            <td>
              {#if b.status === 'RETURNED'}
                <span class="tag RETURNED">已退回·免费</span>
                <div class="muted">{fmtTime(b.returnedAt)}</div>
              {:else}—{/if}
            </td>
            <td>
              {#if b.status === 'COLLECTED'}
                <span class="tag COLLECTED">清运</span>
                <div class="muted">{fmtTime(b.collectedAt)} · {b.collector || ''}</div>
              {:else if b.status === 'LOST'}
                <span class="tag LOST">约定遗失</span>
                <div class="muted">{fmtTime(b.markedLostAt)}</div>
              {:else if b.status === 'RETURNED'}
                <span class="muted">不收费</span>
              {:else}
                <span class="tag ISSUED">在户·未计费</span>
              {/if}
            </td>
            <td>{b.collectedMonth || b.lostMonth || '—'}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>

  {#if detail.bill.billType === 'REGULAR' && detail.bill.status === 'SETTLED'}
    <div class="card">
      <h2>已结算月份发现差错？生成补差单</h2>
      <p class="muted" style="margin-top:0">原账单保持不变，仅按差额补收/退费；同一袋号同种更正只能提交一次。</p>
      <label>更正类型</label>
      <select bind:value={adjMode}>
        <option value="MISSED_COLLECTION">漏收：当月实际清运/遗失却没计费 → 补收 ¥{fen(detail.unitPriceFen)}</option>
        <option value="WRONG_COLLECTION">错收：并未清运却被收费 → 退费 ¥{fen(detail.unitPriceFen)}</option>
      </select>
      <label>袋号（沿用原袋号）</label>
      <input bind:value={adjBag} placeholder="如 B101-0003" />
      <label>差错说明</label>
      <input bind:value={adjReason} placeholder="如：月底清运漏扫码，监控确认已收走" />
      <button class="btn ghost" on:click={submitAdjustment}>生成补差单</button>
    </div>
  {/if}
{/if}
