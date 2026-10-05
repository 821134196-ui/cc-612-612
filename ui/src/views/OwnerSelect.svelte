<script>
  export let owners;
  export let ownerCode;
</script>

<label>业主（发袋/账单使用）</label>
<select bind:value={ownerCode}>
  {#each owners as o}
    <option value={o.code}>{o.room} · {o.name}（{o.code}）</option>
  {/each}
</select>
