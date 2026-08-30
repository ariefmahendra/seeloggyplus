<script lang="ts">
  import { onMount } from 'svelte';
  import { activeModal, searchQuery, isRegex, activeLevel } from '../../stores/appState';
  import { API } from '../../api';
  import { toast } from '../../stores/toast';
  import type { SavedFilter } from '../../types';
  import {
    Dialog,
    DialogContent,
    DialogHeader,
    DialogTitle,
    DialogDescription
  } from '../ui/dialog';
  import { Button } from '../ui/button';
  import { Input } from '../ui/input';
  import { Badge } from '../ui/badge';
  import { Bookmark, Plus, Trash2, Check } from 'lucide-svelte';

  let filters: SavedFilter[] = [];
  let name = '';
  let pattern = '';
  let level = 'ALL';

  let isOpen = false;
  $: isOpen = $activeModal === 'filter-manager';

  function handleOpenChange(open: boolean) {
    if (!open) activeModal.set(null);
  }

  onMount(() => {
    loadFilters();
  });

  async function loadFilters() {
    try {
      const res = await API.getFilters();
      if (res.success && res.data) filters = res.data;
    } catch (e) {
      console.error(e);
    }
  }

  async function addFilter() {
    if (!name.trim()) {
      toast.warning('Filter Name is required');
      return;
    }
    await API.saveFilter({
      name,
      pattern: pattern || $searchQuery || '',
      level: level || $activeLevel || 'ALL',
      regex: $isRegex
    });
    toast.success('Filter preset saved');
    name = '';
    pattern = '';
    loadFilters();
  }

  async function deleteFilter(id: string) {
    await API.deleteFilter(id);
    toast.info('Filter removed');
    loadFilters();
  }

  function apply(f: SavedFilter) {
    searchQuery.set(f.pattern || '');
    activeLevel.set(f.level || 'ALL');
    isRegex.set(!!f.regex);
    activeModal.set(null);
    toast.success(`Filter applied: "${f.name}"`);
  }
</script>

<Dialog open={isOpen} onOpenChange={handleOpenChange}>
  <DialogContent class="max-w-md flex flex-col p-4 gap-3.5 border-border">
    <DialogHeader class="border-b border-border pb-3 shrink-0">
      <DialogTitle class="text-sm font-semibold flex items-center gap-2">
        <Bookmark class="w-4 h-4 text-primary" />
        <span>Saved Filters & Presets</span>
      </DialogTitle>
      <DialogDescription class="text-xs">
        Bookmark recurring regex query patterns and log level thresholds.
      </DialogDescription>
    </DialogHeader>

    <!-- Create form -->
    <div class="flex items-center gap-2">
      <Input placeholder="Filter Name (e.g. NullPointer)" bind:value={name} class="h-8 text-xs flex-1" />
      <Button variant="default" size="sm" class="h-8 gap-1 text-xs shrink-0" on:click={addFilter}>
        <Plus class="w-3.5 h-3.5" />
        <span>Save Current</span>
      </Button>
    </div>

    <!-- Filters List -->
    <div class="max-h-64 overflow-y-auto flex flex-col gap-1.5 py-1">
      {#if filters.length === 0}
        <div class="p-6 text-center text-xs text-muted-foreground border border-dashed border-border rounded-sm">
          No saved filter presets yet.
        </div>
      {:else}
        {#each filters as f}
          {@const isCurrent = ($searchQuery === (f.pattern || '') || (!f.pattern && !$searchQuery)) && $activeLevel === (f.level || 'ALL')}
          <div class="flex items-center justify-between p-2 rounded-[var(--radius-sm)] border text-xs transition-all {isCurrent ? 'bg-primary/15 border-primary shadow-xs ring-1 ring-primary/40' : 'border-border hover:bg-accent bg-background'}">
            <button class="text-left cursor-pointer flex-1" on:click={() => apply(f)}>
              <div class="flex items-center gap-1.5">
                <span class="font-semibold text-foreground">{f.name}</span>
                {#if isCurrent}
                  <Badge variant="default" class="text-[9px] px-1 py-0 h-4 bg-primary text-primary-foreground font-mono">ACTIVE</Badge>
                {/if}
              </div>
              <div class="text-[10.5px] text-muted-foreground font-mono mt-0.5">{f.pattern || '*'} ({f.level || 'ALL'})</div>
            </button>
            <div class="flex items-center gap-1 shrink-0 ml-2">
              <Button variant={isCurrent ? 'default' : 'secondary'} size="xs" class="gap-1 text-[10.5px]" on:click={() => apply(f)}>
                <Check class="w-3 h-3" />
                <span>{isCurrent ? 'Active' : 'Apply'}</span>
              </Button>
              <Button variant="ghost" size="icon" class="h-6 w-6 text-muted-foreground hover:text-destructive" on:click={() => deleteFilter(f.id)}>
                <Trash2 class="w-3 h-3" />
              </Button>
            </div>
          </div>
        {/each}
      {/if}
    </div>

  </DialogContent>
</Dialog>
