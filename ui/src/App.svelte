<script>
  import { onMount } from 'svelte';
  import { api } from './api.js';
  import ScanView from './views/ScanView.svelte';
  import BagsView from './views/BagsView.svelte';
  import BillsView from './views/BillsView.svelte';
  import LogsView from './views/LogsView.svelte';

  let owners = [];
  let ownerCode = '';
  let tab = 'scan';

  const tabs = [
    { key: 'scan', label: '扫码清运', ico: '📷' },
    { key: 'bags', label: '发袋退袋', ico: '👜' },
    { key: 'bills', label: '账单核对', ico: '🧾' },
    { key: 'logs', label: '扫码记录', ico: '📋' }
  ];

  onMount(async () => {
    owners = await api.listOwners();
    if (owners.length) ownerCode = owners[0].code;
  });
</script>

<header class="topbar">
  <h1>装修垃圾袋领用与清运计费</h1>
  <div class="sub">按实际清运/约定遗失计费 · 退袋不收费</div>
</header>

<main>
  {#if tab === 'scan'}
    <ScanView {owners} {ownerCode} />
  {:else if tab === 'bags'}
    <BagsView {owners} bind:ownerCode />
  {:else if tab === 'bills'}
    <BillsView {owners} bind:ownerCode />
  {:else}
    <LogsView />
  {/if}
</main>

<nav class="tabs">
  {#each tabs as t}
    <button class={tab === t.key ? 'active' : ''} on:click={() => (tab = t.key)}>
      <span class="ico">{t.ico}</span>
      {t.label}
    </button>
  {/each}
</nav>
