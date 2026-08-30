<script lang="ts">
  import { onMount } from 'svelte';
  import {
    currentFile,
    leftPanelVisible,
    recentFilesStore,
    refreshRecentFiles,
    removeRecentFile,
    clearAllRecentFiles
  } from '../stores/appState';
  import { toast } from '../stores/toast';
  import type { RecentFileItem } from '../types';
  import { Button } from './ui/button';
  import { Input } from './ui/input';
  import { Badge } from './ui/badge';
  import { ChevronsLeft, Search, FileText, Globe, X, XCircle } from 'lucide-svelte';

  export let onOpenFile: (req: { path: string; source: string; serverId?: string }) => void = () => {};

  let filterText: string = '';

  export async function loadRecent() {
    await refreshRecentFiles();
  }

  onMount(() => {
    refreshRecentFiles();
  });

  $: filteredItems = $recentFilesStore.filter(item => {
    if (!filterText) return true;
    const q = filterText.toLowerCase();
    const lf = item.logFile;
    if (!lf) return false;
    return (lf.name && lf.name.toLowerCase().includes(q)) || (lf.filePath && lf.filePath.toLowerCase().includes(q));
  });

  async function deleteEntry(id: string, e: MouseEvent) {
    e.stopPropagation();
    await removeRecentFile(id);
    toast.info('Removed from recent files');
  }

  async function clearAll() {
    if (!confirm('Clear all recent files history?')) return;
    await clearAllRecentFiles();
    toast.info('Recent files cleared');
  }
</script>

<aside class="h-full w-full bg-background flex flex-col select-none overflow-hidden">
  <!-- Header -->
  <div class="h-8 border-b border-border flex items-center justify-between px-2.5 bg-background shrink-0">
    <span class="font-semibold text-[11px] text-foreground tracking-wide">Recent Log Files</span>
    <Button
      variant="ghost"
      size="icon"
      class="h-6 w-6 text-muted-foreground hover:text-foreground"
      on:click={() => leftPanelVisible.set(false)}
      title="Collapse Recent Files (Ctrl+B)"
    >
      <ChevronsLeft class="w-3.5 h-3.5" />
    </Button>
  </div>

  <!-- Search Filter -->
  <div class="p-1.5 border-b border-border flex items-center gap-1 bg-background shrink-0">
    <div class="relative w-full flex items-center">
      <Search class="absolute left-2 w-3 h-3 text-muted-foreground pointer-events-none" />
      <Input
        type="text"
        placeholder="Filter recent files..."
        bind:value={filterText}
        class="pl-7 h-7 text-[11px]"
      />
    </div>
  </div>

  <!-- List -->
  <div class="flex-1 overflow-y-auto p-1.5 flex flex-col gap-1">
    {#if filteredItems.length === 0}
      <div class="p-4 text-center text-muted-foreground text-[11px]">
        {filterText ? 'No matching logs' : 'No recent log files'}
      </div>
    {:else}
      {#each filteredItems as item (item.logFile ? item.logFile.id || item.logFile.filePath : item.id || item.fileId)}
        {@const lf = item.logFile}
        {#if lf}
          {@const isSelected = Boolean($currentFile && ($currentFile.filePath === lf.filePath || $currentFile.fileId === lf.id || $currentFile.fileName === lf.name))}
          <div
            role="button"
            tabindex="0"
            class="p-2 rounded-[var(--radius-sm)] cursor-pointer transition-all group flex flex-col text-left {isSelected ? 'bg-primary/15 font-medium' : 'hover:bg-accent'}"
            on:click={() => onOpenFile({ path: lf.filePath, source: lf.isRemote ? 'REMOTE' : 'LOCAL', serverId: lf.sshServerID })}
            on:keydown={(e) => e.key === 'Enter' && onOpenFile({ path: lf.filePath, source: lf.isRemote ? 'REMOTE' : 'LOCAL', serverId: lf.sshServerID })}
          >

            <div class="flex items-center justify-between gap-1">
              <span class="text-[11.5px] font-semibold text-foreground flex items-center gap-1.5 truncate">
                {#if lf.isRemote}
                  <Globe class="w-3.5 h-3.5 text-blue-400 shrink-0" />
                {:else}
                  <FileText class="w-3.5 h-3.5 text-muted-foreground shrink-0" />
                {/if}
                <span class="truncate">{lf.name}</span>
              </span>
              <div class="flex items-center gap-1">
                {#if isSelected}
                  <Badge variant="default" class="text-[9px] px-1 py-0 h-4 bg-primary text-primary-foreground font-mono">
                    ACTIVE
                  </Badge>
                {/if}
                <button
                  class="opacity-0 group-hover:opacity-100 p-0.5 text-muted-foreground hover:text-destructive rounded transition-opacity"
                  on:click={(e) => deleteEntry(lf.id, e)}
                  title="Remove from history"
                >
                  <X class="w-3 h-3" />
                </button>
              </div>
            </div>
            <div class="text-[10px] text-muted-foreground truncate mt-0.5 font-mono" title={lf.filePath}>
              {lf.filePath}
            </div>
            <div class="flex items-center gap-1.5 text-[9.5px] text-muted-foreground mt-1.5">
              <Badge variant="outline" class="text-[9px] px-1 py-0 font-mono">
                {lf.isRemote ? (item.serverName ? `SSH: ${item.serverName}` : 'REMOTE') : 'LOCAL'}
              </Badge>
              {#if lf.size}
                <span>{lf.size}</span>
              {/if}
            </div>
          </div>
        {/if}
      {/each}

    {/if}
  </div>

  <!-- Footer -->
  <div class="p-2 border-t border-border bg-background shrink-0">
    <Button
      variant="outline"
      size="sm"
      class="w-full justify-center gap-1.5 text-muted-foreground hover:text-foreground"
      on:click={clearAll}
    >
      <XCircle class="w-3.5 h-3.5" />
      <span>Clear Recent Files</span>
    </Button>
  </div>
</aside>
