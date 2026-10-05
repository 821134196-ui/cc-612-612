<script>
  import { onMount, onDestroy } from 'svelte'
  import { api } from '../api.js'

  let bagNo = ''
  let operator = '清运员'
  let result = null        // 最近一次扫码回执
  let history = []         // 本机本次会话的扫码记录
  let error = ''
  let inputEl
  let scanning = false
  let scanner = null
  let cameraError = ''

  async function submit() {
    const no = bagNo.trim()
    if (!no) return
    error = ''
    try {
      const r = await api.scan(no, operator)
      result = r
      history = [r, ...history].slice(0, 20)
      bagNo = ''
      inputEl && inputEl.focus()
    } catch (e) {
      error = e.message
    }
  }

  function reset() {
    result = null
    bagNo = ''
    inputEl && inputEl.focus()
  }

  async function toggleCamera() {
    if (scanning) { stopCamera(); return }
    cameraError = ''
    try {
      const { Html5Qrcode } = await import('html5-qrcode')
      scanner = new Html5Qrcode('camera-box')
      await scanner.start(
        { facingMode: 'environment' },
        { fps: 8, qrbox: { width: 220, height: 220 } },
        (text) => { bagNo = text.trim(); submit() },
        () => {}
      )
      scanning = true
    } catch (e) {
      cameraError = '无法打开摄像头（需 https 或 localhost 环境），请改用手工输入'
      scanning = false
    }
  }

  function stopCamera() {
    if (scanner) {
      scanner.stop().catch(() => {}).then(() => scanner && scanner.clear())
      scanner = null
    }
    scanning = false
  }

  onMount(() => { inputEl && inputEl.focus() })
  onDestroy(stopCamera)
</script>

<div class="card">
  <label for="bagNo">袋号（扫码或手工输入）</label>
  <form on:submit|preventDefault={submit}>
    <div class="row">
      <input
        id="bagNo"
        bind:this={inputEl}
        bind:value={bagNo}
        placeholder="如 B000123"
        autocomplete="off"
        autocapitalize="characters"
        inputmode="text"
      />
      <button class="shrink" type="submit" disabled={!bagNo.trim()}>确认清运</button>
    </div>
  </form>
  <div class="row" style="margin-top:10px">
    <input bind:value={operator} placeholder="操作员" style="max-width:140px" />
    <button type="button" class="ghost shrink" on:click={toggleCamera}>
      {scanning ? '关闭摄像头' : '打开摄像头扫码'}
    </button>
  </div>
  {#if cameraError}<p class="muted">{cameraError}</p>{/if}
  <div id="camera-box" class:show={scanning}></div>
</div>

{#if error}<div class="error-box">{error}</div>{/if}

{#if result}
  <div class="result {result.result === 'CLEARED' ? 'ok' : result.result === 'DUPLICATE' ? 'dup' : 'bad'}">
    <div class="big">
      {#if result.result === 'CLEARED'}✓ 清运成功
      {:else if result.result === 'DUPLICATE'}⚠ 重复扫码
      {:else}✗ 已拒绝{/if}
    </div>
    <div class="line">袋号 <b>{result.bagNo}</b></div>
    {#if result.fee != null}<div class="line">费用 <span class="money">{result.fee} 元</span>（账期 {result.month}）</div>{/if}
    <div class="line">{result.message}</div>
    <button class="ghost" style="margin-top:12px" on:click={reset}>再扫一只</button>
  </div>
{/if}

{#if history.length}
  <div class="card">
    <div class="muted" style="margin-bottom:8px">本次扫码记录（最近 {history.length} 条）</div>
    <table class="list">
      <thead><tr><th>袋号</th><th>结果</th><th>费用</th></tr></thead>
      <tbody>
        {#each history as h}
          <tr>
            <td>{h.bagNo}</td>
            <td>
              {#if h.result === 'CLEARED'}<span class="chip CLEARED">已清运</span>
              {:else if h.result === 'DUPLICATE'}<span class="chip SETTLED">重复</span>
              {:else}<span class="chip LOST">拒绝</span>{/if}
            </td>
            <td class="money">{h.result === 'CLEARED' ? h.fee + ' 元' : '—'}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}

<style>
  .result {
    margin: 12px;
    padding: 18px 16px;
    border-radius: 14px;
    font-size: 15px;
  }
  .result.ok { background: #dcfce7; color: #14532d; }
  .result.dup { background: var(--warn-bg); color: var(--warn); }
  .result.bad { background: #fee2e2; color: var(--err); }
  .result .big { font-size: 22px; font-weight: 700; margin-bottom: 8px; }
  .result .line { margin: 4px 0; }
  #camera-box { display: none; margin-top: 12px; border-radius: 12px; overflow: hidden; }
  #camera-box.show { display: block; }
</style>
