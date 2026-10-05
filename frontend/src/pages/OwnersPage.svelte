<script>
  import { onMount } from 'svelte'
  import { api } from '../api.js'

  let owners = []
  let name = ''
  let unit = ''
  let phone = ''
  let error = ''

  onMount(load)

  async function load() {
    owners = await api.owners()
  }

  async function add() {
    error = ''
    try {
      await api.createOwner({ name, unit, phone })
      name = ''; unit = ''; phone = ''
      load()
    } catch (e) { error = e.message }
  }
</script>

<div class="card">
  <div class="muted" style="margin-bottom:8px">新增业主</div>
  <label>姓名</label>
  <input bind:value={name} placeholder="业主姓名" />
  <label>房号</label>
  <input bind:value={unit} placeholder="如 3-2-501" />
  <label>电话</label>
  <input bind:value={phone} placeholder="手机号" />
  <button style="margin-top:12px" on:click={add} disabled={!name.trim() || !unit.trim()}>保存</button>
</div>

{#if error}<div class="error-box">{error}</div>{/if}

<div class="card">
  <div class="muted" style="margin-bottom:8px">业主列表（{owners.length}）</div>
  <table class="list">
    <thead><tr><th>姓名</th><th>房号</th><th>电话</th></tr></thead>
    <tbody>
      {#each owners as o}
        <tr><td>{o.name}</td><td>{o.unit}</td><td>{o.phone || '—'}</td></tr>
      {/each}
    </tbody>
  </table>
</div>
