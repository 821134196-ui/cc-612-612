<script>
  import { onMount } from 'svelte'
  import ScanPage from './pages/ScanPage.svelte'
  import IssuePage from './pages/IssuePage.svelte'
  import StatementPage from './pages/StatementPage.svelte'
  import BillsPage from './pages/BillsPage.svelte'
  import OwnersPage from './pages/OwnersPage.svelte'

  const routes = {
    '#/scan': { comp: ScanPage, title: '扫码清运' },
    '#/issue': { comp: IssuePage, title: '发袋管理' },
    '#/statement': { comp: StatementPage, title: '业主对账' },
    '#/bills': { comp: BillsPage, title: '账单结算' },
    '#/owners': { comp: OwnersPage, title: '业主管理' }
  }

  let hash = window.location.hash || '#/scan'

  function onHashChange() {
    hash = window.location.hash || '#/scan'
  }

  onMount(() => {
    if (!window.location.hash) window.location.hash = '#/scan'
    window.addEventListener('hashchange', onHashChange)
    return () => window.removeEventListener('hashchange', onHashChange)
  })

  $: current = routes[hash] || routes['#/scan']
</script>

<div class="shell">
  <header>
    <h1>{current.title}</h1>
    <span class="sub">装修垃圾袋清运计费</span>
  </header>

  <main>
    <svelte:component this={current.comp} />
  </main>

  <nav>
    {#each Object.entries(routes) as [path, r]}
      <a href={path} class:active={hash === path}>
        <span class="icon">
          {#if path === '#/scan'}📷{:else if path === '#/issue'}🛍{:else if path === '#/statement'}🧾{:else if path === '#/bills'}💰{:else}👤{/if}
        </span>
        {r.title}
      </a>
    {/each}
  </nav>
</div>

<style>
  .shell {
    max-width: 720px;
    margin: 0 auto;
    min-height: 100vh;
    display: flex;
    flex-direction: column;
  }
  header {
    background: var(--primary);
    color: #fff;
    padding: 14px 16px;
    position: sticky;
    top: 0;
    z-index: 10;
  }
  header h1 { margin: 0; font-size: 19px; }
  header .sub { font-size: 12px; opacity: 0.85; }
  main { flex: 1; padding-bottom: 76px; }
  nav {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    margin: 0 auto;
    max-width: 720px;
    display: flex;
    background: #fff;
    border-top: 1px solid var(--line);
    z-index: 10;
  }
  nav a {
    flex: 1;
    text-align: center;
    padding: 8px 0 10px;
    font-size: 12px;
    color: var(--muted);
    text-decoration: none;
  }
  nav a.active { color: var(--primary); font-weight: 600; }
  nav .icon { display: block; font-size: 20px; margin-bottom: 2px; }
</style>
