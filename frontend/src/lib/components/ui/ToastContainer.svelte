<script lang="ts">
  import { toasts } from '../../stores/toast';
  import { CheckCircle, AlertTriangle, Info } from 'lucide-svelte';
</script>

<div class="fixed bottom-8 right-5 flex flex-col gap-2 z-50 pointer-events-none">
  {#each $toasts as t (t.id)}
    <div
      class="bg-popover text-popover-foreground border px-3 py-2 rounded-[var(--radius-sm)] shadow-md text-[11.5px] flex items-center gap-2 pointer-events-auto max-w-[380px] animate-in slide-in-from-bottom-2 duration-150 {t.type === 'success' ? 'border-[var(--level-info-border)]' : t.type === 'error' ? 'border-[var(--level-error-border)]' : 'border-border'}"
    >
      {#if t.type === 'success'}
        <CheckCircle class="w-3.5 h-3.5 text-[var(--level-info-text)] shrink-0" />
      {:else if t.type === 'error'}
        <AlertTriangle class="w-3.5 h-3.5 text-[var(--level-error-text)] shrink-0" />
      {:else}
        <Info class="w-3.5 h-3.5 text-muted-foreground shrink-0" />
      {/if}
      <span>{t.message}</span>
    </div>
  {/each}
</div>
