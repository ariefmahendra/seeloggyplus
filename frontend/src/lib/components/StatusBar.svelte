<script lang="ts">
  import {
    totalLinesCount,
    visibleLinesCount,
    selectedRowIndex,
    selectedLogEntry,
    isTailing,
    currentFile
  } from '../stores/appState';

  import { API } from '../api';
  import { toast } from '../stores/toast';
  import { Button } from './ui/button';
  import { Badge } from './ui/badge';
  import { Separator } from './ui/separator';
  import { Sparkles, FileText, Globe } from 'lucide-svelte';

  async function triggerGC() {
    try {
      const res = await API.triggerGC();
      if (res.success) {
        toast.success(`GC Run: Cleaned ${res.data?.freedMemoryFormatted || 'memory'}`);
      }
    } catch {
      toast.error('Failed to trigger Java Garbage Collector');
    }
  }
</script>

<footer class="h-[26px] bg-muted/40 border-t border-border flex items-center justify-between px-3 text-[11px] text-muted-foreground select-none shrink-0 font-sans">
  <!-- Left info -->
  <div class="flex items-center gap-3">
    {#if $currentFile}
      <span class="flex items-center gap-1 font-medium text-foreground">
        {#if $currentFile.source === 'REMOTE'}
          <Globe class="w-3 h-3 text-blue-400" />
        {:else}
          <FileText class="w-3 h-3 text-muted-foreground" />
        {/if}
        <span>{$currentFile.fileName}</span>
      </span>
      <Separator orientation="vertical" class="h-3" />
      <span>Total: <b class="text-foreground font-mono">{$totalLinesCount.toLocaleString()}</b> lines</span>
      {#if $visibleLinesCount !== $totalLinesCount}
        <span class="text-muted-foreground">(Filtered: <b class="text-foreground font-mono">{$visibleLinesCount.toLocaleString()}</b>)</span>
      {/if}
      {#if $selectedRowIndex >= 0}
        <Separator orientation="vertical" class="h-3" />
        {#if $visibleLinesCount !== $totalLinesCount && $selectedLogEntry}
          <span>Line: <b class="text-foreground font-mono">{$selectedLogEntry.lineNumber || ($selectedRowIndex + 1)}</b> <span class="text-[10px] text-muted-foreground">(Match {$selectedRowIndex + 1} of {$visibleLinesCount})</span></span>
        {:else}
          <span>Line: <b class="text-foreground font-mono">{($selectedRowIndex + 1).toLocaleString()}</b></span>
        {/if}
      {/if}
    {:else}
      <span>No file opened</span>
    {/if}

  </div>

  <!-- Right live tail status & GC -->
  <div class="flex items-center gap-2.5">
    {#if $isTailing}
      <Badge variant="outline" class="gap-1 px-1.5 py-0 text-[10px] text-emerald-400 border-emerald-500/40">
        <span class="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-ping"></span>
        <span class="font-mono">LIVE SSE</span>
      </Badge>
    {/if}

    <Button
      variant="ghost"
      size="xs"
      class="h-5 px-1.5 gap-1 text-[10px] text-muted-foreground hover:text-foreground"
      on:click={triggerGC}
      title="Request Java Virtual Machine Garbage Collection"
    >
      <Sparkles class="w-2.5 h-2.5" />
      <span>Clean RAM</span>
    </Button>
  </div>
</footer>
